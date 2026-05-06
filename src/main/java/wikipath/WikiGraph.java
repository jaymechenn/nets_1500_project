package wikipath;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Stores the Wikipedia hyperlink graph as an adjacency list.
 *
 * Nodes are article IDs and directed edges are hyperlinks from one article to
 * another. For example, an edge "10 25" means article 10 links to article 25.
 */
public class WikiGraph {

    private final HashMap<Integer, ArrayList<Integer>> adjacencyList;
    private final HashMap<String, Integer> titleToId;
    private final HashMap<Integer, String> idToTitle;
    private final HashSet<Integer> articleIds;
    private int edgeCount;

    public WikiGraph() {
        adjacencyList = new HashMap<>();
        titleToId = new HashMap<>();
        idToTitle = new HashMap<>();
        articleIds = new HashSet<>();
        edgeCount = 0;
    }

    /**
     * Loads a whitespace-separated edge list from disk.
     *
     * Lines beginning with # are ignored. Each normal line should contain two
     * integers: sourceID targetID.
     */
    public void loadEdges(String edgeFile) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(edgeFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] parts = line.split("\\s+");
                if (parts.length < 2) {
                    continue;
                }

                try {
                    int from = Integer.parseInt(parts[0]);
                    int to = Integer.parseInt(parts[1]);
                    addEdge(from, to);
                } catch (NumberFormatException e) {
                    // Skip malformed rows instead of stopping the whole load.
                }
            }
        }
    }

    /**
     * Loads optional article title mappings from disk.
     *
     * Expected format: articleID title. Titles may contain underscores.
     */
    public void loadTitles(String titleFile) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(titleFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int firstSpace = line.indexOf(' ');
                if (firstSpace < 0) {
                    continue;
                }

                try {
                    int id = Integer.parseInt(line.substring(0, firstSpace));
                    String title = line.substring(firstSpace + 1).trim();
                    if (!title.isEmpty()) {
                        idToTitle.put(id, title);
                        titleToId.put(normalizeTitle(title), id);
                        articleIds.add(id);
                    }
                } catch (NumberFormatException e) {
                    // Skip malformed title rows.
                }
            }
        }
    }

    public void addEdge(int from, int to) {
        adjacencyList.putIfAbsent(from, new ArrayList<>());
        adjacencyList.putIfAbsent(to, new ArrayList<>());
        adjacencyList.get(from).add(to);
        articleIds.add(from);
        articleIds.add(to);
        edgeCount++;
    }

    public List<Integer> getNeighbors(int articleId) {
        ArrayList<Integer> neighbors = adjacencyList.get(articleId);
        if (neighbors == null) {
            return new ArrayList<>();
        }
        return neighbors;
    }

    public boolean containsArticle(int articleId) {
        return articleIds.contains(articleId);
    }

    public boolean containsArticle(String title) {
        return getIdFromTitle(title) != -1;
    }

    public int getIdFromTitle(String title) {
        if (title == null) {
            return -1;
        }
        Integer id = titleToId.get(normalizeTitle(title));
        if (id == null) {
            return -1;
        }
        return id;
    }

    public String getTitleFromId(int id) {
        String title = idToTitle.get(id);
        if (title == null) {
            return "Article_" + id;
        }
        return title;
    }

    public ArrayList<Integer> getAllArticleIds() {
        return new ArrayList<>(articleIds);
    }

    public int getNodeCount() {
        return articleIds.size();
    }

    public int getEdgeCount() {
        return edgeCount;
    }

    public boolean hasTitles() {
        return !titleToId.isEmpty();
    }

    private String normalizeTitle(String title) {
        return title.trim().replace(' ', '_').toLowerCase();
    }
}
