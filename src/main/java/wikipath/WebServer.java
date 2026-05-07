package wikipath;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;

/**
 * Basically this exposes the loaded WikiGraph and BFSPathFinder over
 * a JSON API and serves the static files.
 *

 */
public class WebServer {

    private static final int DEFAULT_NEIGHBOR_LIMIT = 8;
    private static final int MAX_PATH_NEIGHBORHOOD_NODES = 60;

    private final WikiGraph graph;
    private final BFSPathFinder pathFinder;
    private final WikiAnalyzer analyzer;
    private final Path webRoot;
    private final Random random = new Random();

    public WebServer(WikiGraph graph, Path webRoot) {
        this.graph = graph;
        this.pathFinder = new BFSPathFinder(graph);
        this.analyzer = new WikiAnalyzer(graph);
        this.webRoot = webRoot;
    }

    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/info", new InfoHandler());
        server.createContext("/api/path", new PathHandler());
        server.createContext("/api/suggest", new SuggestHandler());
        server.createContext("/api/random", new RandomHandler());
        server.createContext("/api/analysis", new AnalysisHandler());
        server.createContext("/", new StaticFileHandler());
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();

        System.out.println();
        System.out.println("WikiPath web server listening on http://localhost:" + port);
        System.out.println("Open that URL in a browser. Press Ctrl+C to stop.");
        System.out.println("Graph: " + graph.getNodeCount() + " articles, "
                + graph.getEdgeCount() + " hyperlinks"
                + (graph.hasTitles() ? " (titles loaded)" : " (no titles, use IDs)"));
    }


    private final class InfoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            StringBuilder json = new StringBuilder();
            json.append("{\"nodes\":").append(graph.getNodeCount())
                    .append(",\"edges\":").append(graph.getEdgeCount())
                    .append(",\"hasTitles\":").append(graph.hasTitles())
                    .append("}");
            sendJson(ex, 200, json.toString());
        }
    }

    private final class PathHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            Map<String, String> q = parseQuery(ex);
            String srcText = q.get("src");
            String tgtText = q.get("tgt");
            boolean wantNeighbors = "true".equalsIgnoreCase(q.getOrDefault("neighbors", "false"));

            if (srcText == null || tgtText == null) {
                sendJson(ex, 400, errorJson("missing src or tgt query parameter"));
                return;
            }

            int srcId = resolveArticle(srcText);
            int tgtId = resolveArticle(tgtText);
            if (srcId < 0) {
                sendJson(ex, 404, errorJson("unknown source article: " + srcText));
                return;
            }
            if (tgtId < 0) {
                sendJson(ex, 404, errorJson("unknown target article: " + tgtText));
                return;
            }

            long start = System.currentTimeMillis();
            List<Integer> path = pathFinder.findShortestPath(srcId, tgtId);
            long elapsedMs = System.currentTimeMillis() - start;

            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"source\":").append(articleJson(srcId)).append(",");
            json.append("\"target\":").append(articleJson(tgtId)).append(",");
            json.append("\"ms\":").append(elapsedMs).append(",");
            if (path.isEmpty()) {
                json.append("\"found\":false,\"hops\":-1,\"path\":[]");
            } else {
                json.append("\"found\":true,");
                json.append("\"hops\":").append(path.size() - 1).append(",");
                json.append("\"path\":[");
                for (int i = 0; i < path.size(); i++) {
                    if (i > 0) json.append(',');
                    json.append(articleJson(path.get(i)));
                }
                json.append(']');

                if (wantNeighbors) {
                    appendNeighborhoods(json, path);
                }
            }
            json.append('}');
            sendJson(ex, 200, json.toString());
        }

        private void appendNeighborhoods(StringBuilder json, List<Integer> path) {

            HashSet<Integer> pathSet = new HashSet<>(path);
            int budget = MAX_PATH_NEIGHBORHOOD_NODES;
            json.append(",\"neighborhoods\":[");
            for (int i = 0; i < path.size(); i++) {
                if (i > 0) json.append(',');
                int nodeId = path.get(i);
                List<Integer> neighbors = graph.getNeighbors(nodeId);
                int allow = Math.min(DEFAULT_NEIGHBOR_LIMIT,
                        Math.max(1, budget / Math.max(1, path.size() - i)));
                ArrayList<Integer> chosen = new ArrayList<>();
                for (int n : neighbors) {
                    if (chosen.size() >= allow) break;
                    if (pathSet.contains(n)) continue;
                    chosen.add(n);
                }
                budget -= chosen.size();
                json.append("{\"id\":").append(nodeId).append(",\"neighbors\":[");
                for (int j = 0; j < chosen.size(); j++) {
                    if (j > 0) json.append(',');
                    json.append(articleJson(chosen.get(j)));
                }
                json.append("]}");
            }
            json.append(']');
        }
    }

    private final class SuggestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            Map<String, String> q = parseQuery(ex);
            String prefix = q.getOrDefault("q", "");
            int limit = parseIntSafe(q.get("limit"), 10);
            limit = Math.min(Math.max(limit, 1), 50);

            StringBuilder json = new StringBuilder();
            json.append("{\"hasTitles\":").append(graph.hasTitles())
                    .append(",\"suggestions\":[");
            if (graph.hasTitles() && !prefix.isEmpty()) {
                List<Integer> ids = graph.searchTitlePrefix(prefix, limit);
                for (int i = 0; i < ids.size(); i++) {
                    if (i > 0) json.append(',');
                    json.append(articleJson(ids.get(i)));
                }
            }
            json.append("]}");
            sendJson(ex, 200, json.toString());
        }
    }

    private final class RandomHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            ArrayList<Integer> ids = graph.getAllArticleIds();
            if (ids.size() < 2) {
                sendJson(ex, 503, errorJson("graph has fewer than 2 articles"));
                return;
            }
            int s = ids.get(random.nextInt(ids.size()));
            int t = ids.get(random.nextInt(ids.size()));
            int safety = 0;
            while (t == s && safety++ < 8) {
                t = ids.get(random.nextInt(ids.size()));
            }
            String json = "{\"source\":" + articleJson(s) + ",\"target\":" + articleJson(t) + "}";
            sendJson(ex, 200, json);
        }
    }

    private final class AnalysisHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            Map<String, String> q = parseQuery(ex);
            int trials = parseIntSafe(q.get("trials"), 100);
            trials = Math.min(Math.max(trials, 1), 5000);

            long start = System.currentTimeMillis();
            analyzer.runRandomPairAnalysis(trials);
            long elapsedMs = System.currentTimeMillis() - start;

            List<Map.Entry<Integer, Integer>> top = analyzer.getTopBridgeArticles(10);
            StringBuilder json = new StringBuilder();
            json.append('{');
            json.append("\"trials\":").append(analyzer.getLastTrials()).append(',');
            json.append("\"reachablePairs\":").append(analyzer.getLastReachablePairs()).append(',');
            json.append("\"averagePathLength\":").append(analyzer.getLastAveragePathLength()).append(',');
            json.append("\"averageLocalClustering\":").append(analyzer.getLastAverageLocalClustering()).append(',');
            json.append("\"averageNeighborhoodOverlap\":").append(analyzer.getLastAverageNeighborhoodOverlap()).append(',');
            json.append("\"localBridgeEdges\":").append(analyzer.getLastLocalBridgeEdges()).append(',');
            json.append("\"pathEdgesChecked\":").append(analyzer.getLastPathEdgesChecked()).append(',');
            json.append("\"ms\":").append(elapsedMs).append(',');
            json.append("\"topBridges\":[");
            for (int i = 0; i < top.size(); i++) {
                if (i > 0) json.append(',');
                Map.Entry<Integer, Integer> e = top.get(i);
                json.append('{')
                        .append("\"article\":").append(articleJson(e.getKey())).append(',')
                        .append("\"count\":").append(e.getValue())
                        .append('}');
            }
            json.append("]}");
            sendJson(ex, 200, json.toString());
        }
    }

    private final class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String requested = ex.getRequestURI().getPath();
            if (requested.equals("/")) {
                requested = "/index.html";
            }
            // Reject path traversal.
            if (requested.contains("..")) {
                sendText(ex, 400, "bad path");
                return;
            }
            Path file = webRoot.resolve(requested.substring(1)).normalize();
            if (!file.startsWith(webRoot) || !Files.isRegularFile(file)) {
                sendText(ex, 404, "not found");
                return;
            }
            byte[] body = Files.readAllBytes(file);
            String contentType = guessContentType(file.getFileName().toString());
            ex.getResponseHeaders().set("Content-Type", contentType);
            ex.getResponseHeaders().set("Cache-Control", "no-store");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        }
    }

    // helpers I used

    private int resolveArticle(String input) {
        String trimmed = input == null ? "" : input.trim();
        if (trimmed.isEmpty()) return -1;
        try {
            int id = Integer.parseInt(trimmed);
            if (graph.containsArticle(id)) return id;
            return -1;
        } catch (NumberFormatException e) {
            return graph.getIdFromTitle(trimmed);
        }
    }

    private String articleJson(int id) {
        return "{\"id\":" + id + ",\"title\":" + jsonString(graph.getTitleFromId(id)) + "}";
    }

    private static Map<String, String> parseQuery(HttpExchange ex) {
        HashMap<String, String> map = new HashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null || raw.isEmpty()) return map;
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            String key, val;
            if (eq < 0) {
                key = decode(part);
                val = "";
            } else {
                key = decode(part.substring(0, eq));
                val = decode(part.substring(eq + 1));
            }
            map.put(key, val);
        }
        return map;
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static int parseIntSafe(String s, int fallback) {
        if (s == null) return fallback;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String jsonString(String s) {
        if (s == null) return "null";
        StringBuilder out = new StringBuilder(s.length() + 2);
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': out.append("\\\\"); break;
                case '"':  out.append("\\\""); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
        return out.toString();
    }

    private static String errorJson(String message) {
        return "{\"error\":" + jsonString(message) + "}";
    }

    private static void sendJson(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendText(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String guessContentType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".html")) return "text/html; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (lower.endsWith(".json")) return "application/json; charset=utf-8";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    public static Path defaultWebRoot() {
        return Paths.get("web").toAbsolutePath().normalize();
    }
}
