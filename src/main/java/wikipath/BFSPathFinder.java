package wikipath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Finds shortest paths in an unweighted directed graph using BFS.
 *
 * BFS is the correct algorithm here because every hyperlink counts as one hop.
 * It explores the graph level by level, so the first time the target is found,
 * we know we have used the fewest possible number of hyperlinks.
 */
public class BFSPathFinder {

    private final WikiGraph graph;

    public BFSPathFinder(WikiGraph graph) {
        this.graph = graph;
    }

    /**
     * Returns the shortest path from source to target as a list of article IDs.
     * If no path exists, returns an empty list.
     */
    public List<Integer> findShortestPath(int source, int target) {
        if (!graph.containsArticle(source) || !graph.containsArticle(target)) {
            return new ArrayList<>();
        }

        if (source == target) {
            ArrayList<Integer> path = new ArrayList<>();
            path.add(source);
            return path;
        }

        Queue<Integer> queue = new LinkedList<>();
        HashSet<Integer> discovered = new HashSet<>();
        HashMap<Integer, Integer> parent = new HashMap<>();

        queue.add(source);
        discovered.add(source);

        while (!queue.isEmpty()) {
            int current = queue.remove();

            for (int neighbor : graph.getNeighbors(current)) {
                if (!discovered.contains(neighbor)) {
                    discovered.add(neighbor);
                    parent.put(neighbor, current);

                    if (neighbor == target) {
                        return reconstructPath(source, target, parent);
                    }

                    queue.add(neighbor);
                }
            }
        }

        return new ArrayList<>();
    }

    private List<Integer> reconstructPath(
            int source, int target, HashMap<Integer, Integer> parent) {
        ArrayList<Integer> path = new ArrayList<>();
        int current = target;

        path.add(current);
        while (current != source) {
            Integer previous = parent.get(current);
            if (previous == null) {
                return new ArrayList<>();
            }
            current = previous;
            path.add(current);
        }

        Collections.reverse(path);
        return path;
    }
}
