package wikipath;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

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

    // Sorted normalized titles for prefix autocomplete. Built lazily because it
    // is only needed by the web UI, not by the menu program.
    private String[] sortedTitles;
    private int[] sortedTitleIds;

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

    /**
     * Returns up to {@code limit} article IDs whose normalized titles begin
     * with the given prefix. Useful for type-ahead suggestions in the web UI.
     *
     * The first call builds a sorted index of all titles, which is O(n log n)
     * but only happens once. Each subsequent call is O(log n + limit).
     */
    public List<Integer> searchTitlePrefix(String prefix, int limit) {
        if (prefix == null || titleToId.isEmpty() || limit <= 0) {
            return new ArrayList<>();
        }
        ensureSortedTitleIndex();

        String normalizedPrefix = normalizeTitle(prefix);
        int start = lowerBound(sortedTitles, normalizedPrefix);
        ArrayList<Integer> results = new ArrayList<>();
        for (int i = start; i < sortedTitles.length && results.size() < limit; i++) {
            if (!sortedTitles[i].startsWith(normalizedPrefix)) {
                break;
            }
            results.add(sortedTitleIds[i]);
        }
        return results;
    }

    private synchronized void ensureSortedTitleIndex() {
        if (sortedTitles != null) {
            return;
        }
        ArrayList<String> norms = new ArrayList<>(titleToId.size());
        ArrayList<Integer> ids = new ArrayList<>(titleToId.size());
        for (Map.Entry<String, Integer> e : titleToId.entrySet()) {
            norms.add(e.getKey());
            ids.add(e.getValue());
        }
        Integer[] indices = new Integer[norms.size()];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = i;
        }
        java.util.Arrays.sort(indices, (a, b) -> norms.get(a).compareTo(norms.get(b)));

        String[] sortedNorms = new String[norms.size()];
        int[] sortedIds = new int[norms.size()];
        for (int i = 0; i < indices.length; i++) {
            int idx = indices[i];
            sortedNorms[i] = norms.get(idx);
            sortedIds[i] = ids.get(idx);
        }
        sortedTitles = sortedNorms;
        sortedTitleIds = sortedIds;
    }

    private static int lowerBound(String[] arr, String key) {
        int lo = 0;
        int hi = arr.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (arr[mid].compareTo(key) < 0) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private String normalizeTitle(String title) {
        return title.trim().replace(' ', '_').toLowerCase();
    }
}
