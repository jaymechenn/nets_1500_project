package wikipath;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Five sub-commands to use :
 *
 * <pre>
 *   wikipath path "Source Article" "Target Article"   # one-shot query
 *   wikipath repl                                     # interactive prompt
 *   wikipath stats   [--preset P | --sources S --targets T] [--top K] [--seed N]
 *   wikipath bridges [--preset P | --sources S --targets T] [--top K] [--seed N]
 *   wikipath avgpath [--preset P | --sources S --targets T]              [--seed N]
 * </pre>
 *
 * <p>Data file paths can be passed via {@code --edges} and {@code --names}
 * or default to {@code data/wiki-topcats.txt} and
 * {@code data/wiki-topcats-page-names.txt} (with {@code .gz} fallbacks).</p>
 */
public final class Main {

    private static final String DEFAULT_EDGES_PLAIN = "data/wiki-topcats.txt";
    private static final String DEFAULT_NAMES_PLAIN = "data/wiki-topcats-page-names.txt";
    private static final String DEFAULT_EDGES_GZ = "data/wiki-topcats.txt.gz";
    private static final String DEFAULT_NAMES_GZ = "data/wiki-topcats-page-names.txt.gz";

    public static void main(String[] args) throws IOException {
        if (args.length == 0 || isHelp(args[0])) {
            printHelp();
            return;
        }

        Args parsed = Args.parse(args);
        Path edgesPath = parsed.edges != null ? parsed.edges : defaultPath(DEFAULT_EDGES_PLAIN, DEFAULT_EDGES_GZ);
        Path namesPath = parsed.names != null ? parsed.names : defaultPath(DEFAULT_NAMES_PLAIN, DEFAULT_NAMES_GZ);

        switch (parsed.command) {
            case "path"    -> runPath(edgesPath, namesPath, parsed);
            case "repl"    -> runRepl(edgesPath, namesPath);
            case "stats"   -> runStats(edgesPath, namesPath, parsed, StatsMode.FULL);
            case "bridges" -> runStats(edgesPath, namesPath, parsed, StatsMode.BRIDGES_ONLY);
            case "avgpath" -> runStats(edgesPath, namesPath, parsed, StatsMode.AVGPATH_ONLY);
            case "help"    -> printHelp();
            default -> {
                System.err.println("Unknown command: " + parsed.command);
                printHelp();
                System.exit(2);
            }
        }
    }

    private enum StatsMode { FULL, BRIDGES_ONLY, AVGPATH_ONLY }

    // commands

    private static void runPath(Path edges, Path names, Args a) throws IOException {
        if (a.positional.size() < 2) {
            System.err.println("Usage: wikipath path \"Source Article\" \"Target Article\"");
            System.exit(2);
        }
        GraphLoader.Loaded loaded = GraphLoader.load(edges, names, System.err);
        WikiGraph g = loaded.graph;
        TitleIndex idx = loaded.titles;

        String src = a.positional.get(0);
        String tgt = a.positional.get(1);
        int sId = idx.idOf(src);
        int tId = idx.idOf(tgt);
        if (sId < 0) {
            System.err.println("Source article not found: \"" + src + "\"");
            System.exit(3);
        }
        if (tId < 0) {
            System.err.println("Target article not found: \"" + tgt + "\"");
            System.exit(3);
        }

        PathFinder pf = new PathFinder(g);
        PathFinder.Result r = pf.shortestPath(sId, tId);
        printQueryResult(r, idx, src, tgt);
    }

    private static void runRepl(Path edges, Path names) throws IOException {
        GraphLoader.Loaded loaded = GraphLoader.load(edges, names, System.err);
        WikiGraph g = loaded.graph;
        TitleIndex idx = loaded.titles;
        PathFinder pf = new PathFinder(g);

        System.out.println();
        System.out.println("Wikipedia Six Degrees, interactive mode.");
        System.out.printf("Loaded %,d articles and %,d hyperlinks.%n", g.numNodes(), g.numEdges());
        System.out.println("Type two articles separated by '|', or 'quit' to exit.");
        System.out.println("Examples:");
        System.out.println("  Kanye West | Quantum mechanics");
        System.out.println("  Philadelphia | Mongolia");
        System.out.println();

        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            while (true) {
                System.out.print("path> ");
                System.out.flush();
                String line = in.readLine();
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) break;

                int bar = line.indexOf('|');
                if (bar < 0) {
                    System.out.println("  (use 'Source | Target', e.g. 'Kanye West | Physics')");
                    continue;
                }
                String src = line.substring(0, bar).trim();
                String tgt = line.substring(bar + 1).trim();
                int sId = idx.idOf(src);
                int tId = idx.idOf(tgt);
                if (sId < 0) { System.out.println("  source not found: " + src); continue; }
                if (tId < 0) { System.out.println("  target not found: " + tgt); continue; }
                printQueryResult(pf.shortestPath(sId, tId), idx, src, tgt);
            }
        }
    }

    private static void runStats(Path edges, Path names, Args a, StatsMode mode) throws IOException {
        Preset preset = Preset.parse(a.strOpt("preset", null));
        int sources = a.intOpt("sources", preset.sources);
        int targets = a.intOpt("targets", preset.targets);
        int top = a.intOpt("top", mode == StatsMode.BRIDGES_ONLY ? 25 : 20);
        long seed = a.longOpt("seed", 42L);

        System.err.printf("[stats] preset=%s sources=%d targets=%d top=%d seed=%d -> up to %,d sampled pairs%n",
                preset.name, sources, targets, top, seed, (long) sources * targets);

        GraphLoader.Loaded loaded = GraphLoader.load(edges, names, System.err);
        StatsRunner runner = new StatsRunner(loaded.graph, loaded.titles);
        StatsRunner.Stats s = runner.run(sources, targets, top, seed, System.err);
        switch (mode) {
            case BRIDGES_ONLY -> runner.printBridgesOnly(s, System.out);
            case AVGPATH_ONLY -> runner.printAvgPathOnly(s, System.out);
            case FULL         -> runner.print(s, System.out);
        }
    }

    /** Sample-size presets */
    private enum Preset {
        QUICK    ("quick",     50,  50),  //  ~2,500 pairs
        DEFAULT_ ("default",  200, 200),  // ~40,000 pairs
        RIGOROUS ("rigorous", 500, 500);  // ~250,000 pairs

        final String name;
        final int sources;
        final int targets;

        Preset(String name, int sources, int targets) {
            this.name = name;
            this.sources = sources;
            this.targets = targets;
        }

        static Preset parse(String s) {
            if (s == null) return DEFAULT_;
            String k = s.toLowerCase();
            return switch (k) {
                case "quick"    -> QUICK;
                case "default"  -> DEFAULT_;
                case "rigorous" -> RIGOROUS;
                default -> throw new IllegalArgumentException(
                        "Unknown --preset \"" + s + "\" (choose: quick, default, rigorous)");
            };
        }
    }

    // output from the commands

    private static void printQueryResult(PathFinder.Result r, TitleIndex idx, String src, String tgt) {
        System.out.println();
        if (!r.found()) {
            System.out.printf("No path from \"%s\" to \"%s\" (BFS visited %,d nodes in %.3fs).%n",
                    src, tgt, r.nodesVisited(), r.elapsedNanos() / 1_000_000_000.0);
            return;
        }
        int[] path = r.path();
        System.out.printf("Shortest path: %d hops, %,d nodes visited, %.3fs%n",
                r.hops(), r.nodesVisited(), r.elapsedNanos() / 1_000_000_000.0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.length; i++) {
            if (i > 0) sb.append("\n  -> ");
            String t = idx.titleOf(path[i]);
            sb.append(t == null ? "<id=" + path[i] + ">" : t);
        }
        System.out.println("  " + sb);
        System.out.println();
    }

    //  args

    private static boolean isHelp(String s) {
        return s.equals("-h") || s.equals("--help") || s.equals("help");
    }

    private static Path defaultPath(String plain, String gz) {
        Path p = Paths.get(plain);
        if (Files.exists(p)) return p;
        Path q = Paths.get(gz);
        if (Files.exists(q)) return q;
        return p;
    }

    private static void printHelp() {
        System.out.println("Six Degrees of Wikipedia");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  path \"Source\" \"Target\"     find the shortest hyperlink path between two articles");
        System.out.println("  repl                         interactive prompt; type 'Source | Target' per line");
        System.out.println("  stats                        sample mean path length + bridge nodes (full output)");
        System.out.println("  avgpath                      sample average path length only, with 95% CI on the");
        System.out.println("                               mean and median/p90/p99 + hop-length histogram");
        System.out.println("  bridges                      sample bridge nodes only, with 95% Wilson CIs and");
        System.out.println("                               split-half stability check");
        System.out.println();
        System.out.println("Options (all commands):");
        System.out.println("  --edges PATH                 path to wiki-topcats.txt[.gz]    (default: data/wiki-topcats.txt[.gz])");
        System.out.println("  --names PATH                 path to wiki-topcats-page-names.txt[.gz]");
        System.out.println();
        System.out.println("Options (stats / bridges):");
        System.out.println("  --preset NAME                quick (50x50 = 2,500 pairs)        ~5s after load");
        System.out.println("                               default (200x200 = 40,000 pairs)  ~30s after load");
        System.out.println("                               rigorous (500x500 = 250,000)      ~3m after load");
        System.out.println("  --sources N                  override S (random source articles to BFS from)");
        System.out.println("  --targets N                  override T (random reachable targets per source)");
        System.out.println("  --top K                      number of bridge articles to report (default 20)");
        System.out.println("  --seed N                     RNG seed for reproducibility (default 42)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -Xmx2g -cp target/classes wikipath.Main path \"Kanye West\" \"Quantum mechanics\"");
        System.out.println("  java -Xmx2g -cp target/classes wikipath.Main repl");
        System.out.println("  java -Xmx2g -cp target/classes wikipath.Main stats   --preset default");
        System.out.println("  java -Xmx2g -cp target/classes wikipath.Main avgpath --preset default");
        System.out.println("  java -Xmx2g -cp target/classes wikipath.Main bridges --preset rigorous --top 25");
    }

    /** Tiny argv parser: supports {@code --key value}, {@code --key=value}, and positional args. */
    private static final class Args {
        String command;
        Path edges;
        Path names;
        final List<String> positional = new ArrayList<>();
        final java.util.Map<String, String> opts = new java.util.HashMap<>();

        static Args parse(String[] argv) {
            Args a = new Args();
            a.command = argv[0];
            for (int i = 1; i < argv.length; i++) {
                String s = argv[i];
                if (s.startsWith("--")) {
                    String key, val;
                    int eq = s.indexOf('=');
                    if (eq > 0) {
                        key = s.substring(2, eq);
                        val = s.substring(eq + 1);
                    } else {
                        key = s.substring(2);
                        if (i + 1 >= argv.length) {
                            throw new IllegalArgumentException("Missing value for --" + key);
                        }
                        val = argv[++i];
                    }
                    if (key.equals("edges")) a.edges = Paths.get(val);
                    else if (key.equals("names")) a.names = Paths.get(val);
                    else a.opts.put(key, val);
                } else {
                    a.positional.add(s);
                }
            }
            return a;
        }

        int intOpt(String key, int def) {
            String v = opts.get(key);
            return v == null ? def : Integer.parseInt(v);
        }

        long longOpt(String key, long def) {
            String v = opts.get(key);
            return v == null ? def : Long.parseLong(v);
        }

        String strOpt(String key, String def) {
            String v = opts.get(key);
            return v == null ? def : v;
        }
    }
}
