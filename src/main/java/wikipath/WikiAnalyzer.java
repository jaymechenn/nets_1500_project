package wikipath;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Runs simple network analysis by repeatedly finding shortest paths between
 * randomly chosen article pairs.
 */
public class WikiAnalyzer {

    private final WikiGraph graph;
    private final BFSPathFinder pathFinder;
    private final HashMap<Integer, Integer> bridgeCounts;
    private double lastAveragePathLength;
    private double lastAverageNeighborhoodOverlap;
    private double lastAverageLocalClustering;
    private int lastPathEdgesChecked;
    private int lastLocalBridgeEdges;
    private int lastReachablePairs;
    private int lastTrials;

    public WikiAnalyzer(WikiGraph graph) {
        this.graph = graph;
        this.pathFinder = new BFSPathFinder(graph);
        this.bridgeCounts = new HashMap<>();
        this.lastAveragePathLength = 0.0;
        this.lastAverageNeighborhoodOverlap = 0.0;
        this.lastAverageLocalClustering = 0.0;
        this.lastPathEdgesChecked = 0;
        this.lastLocalBridgeEdges = 0;
        this.lastReachablePairs = 0;
        this.lastTrials = 0;
    }

    /**
     * Samples random source/target pairs, runs BFS for each pair, and counts
     * path lengths and bridge articles.
     *
     * The bridge count is a simple sampled version of betweenness centrality:
     * articles get higher scores when they appear inside many shortest paths.
     */
    public void runRandomPairAnalysis(int trials) {
        ArrayList<Integer> articleIds = graph.getAllArticleIds();
        if (articleIds.size() < 2) {
            System.out.println("Not enough articles loaded to run analysis.");
            return;
        }

        Random random = new Random();
        int reachablePairs = 0;
        int totalHops = 0;
        int pathEdgesChecked = 0;
        int localBridgeEdges = 0;
        double totalNeighborhoodOverlap = 0.0;
        double totalLocalClustering = 0.0;
        bridgeCounts.clear();

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < trials; i++) {
            int source = articleIds.get(random.nextInt(articleIds.size()));
            int target = articleIds.get(random.nextInt(articleIds.size()));

            while (target == source) {
                target = articleIds.get(random.nextInt(articleIds.size()));
            }

            totalLocalClustering += localClusteringCoefficient(source);

            List<Integer> path = pathFinder.findShortestPath(source, target);
            if (!path.isEmpty()) {
                reachablePairs++;
                totalHops += path.size() - 1;
                countBridgeArticles(path);

                for (int j = 0; j < path.size() - 1; j++) {
                    double overlap = neighborhoodOverlap(path.get(j), path.get(j + 1));
                    totalNeighborhoodOverlap += overlap;
                    pathEdgesChecked++;
                    if (overlap == 0.0) {
                        localBridgeEdges++;
                    }
                }
            }
        }

        long elapsedTime = System.currentTimeMillis() - startTime;
        lastTrials = trials;
        lastReachablePairs = reachablePairs;
        if (reachablePairs == 0) {
            lastAveragePathLength = 0.0;
        } else {
            lastAveragePathLength = (double) totalHops / reachablePairs;
        }
        if (pathEdgesChecked == 0) {
            lastAverageNeighborhoodOverlap = 0.0;
        } else {
            lastAverageNeighborhoodOverlap = totalNeighborhoodOverlap / pathEdgesChecked;
        }
        lastAverageLocalClustering = totalLocalClustering / trials;
        lastPathEdgesChecked = pathEdgesChecked;
        lastLocalBridgeEdges = localBridgeEdges;

        printAnalysisSummary(elapsedTime);
    }

    public void printTopBridgeArticles(int howMany) {
        if (bridgeCounts.isEmpty()) {
            System.out.println("No bridge data yet. Run random pair analysis first.");
            return;
        }

        ArrayList<Map.Entry<Integer, Integer>> entries =
                new ArrayList<>(bridgeCounts.entrySet());
        entries.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        System.out.println();
        System.out.println("Top " + howMany
                + " bridge articles / sampled betweenness scores:");
        for (int i = 0; i < entries.size() && i < howMany; i++) {
            int articleId = entries.get(i).getKey();
            int count = entries.get(i).getValue();
            System.out.println((i + 1) + ". " + graph.getTitleFromId(articleId)
                    + " appeared inside " + count + " sampled shortest paths");
        }
        System.out.println();
    }

    public double getLastAveragePathLength() {
        return lastAveragePathLength;
    }

    public int getLastReachablePairs() {
        return lastReachablePairs;
    }

    private void countBridgeArticles(List<Integer> path) {
        for (int i = 1; i < path.size() - 1; i++) {
            int articleId = path.get(i);
            int oldCount = bridgeCounts.getOrDefault(articleId, 0);
            bridgeCounts.put(articleId, oldCount + 1);
        }
    }

    /**
     * Neighborhood overlap for an edge u -> v:
     * |neighbors(u) intersect neighbors(v)| / |neighbors(u) union neighbors(v)|.
     *
     * In social network language, an edge with zero neighborhood overlap is a
     * local bridge because it connects two nodes whose neighborhoods do not
     * otherwise overlap.
     */
    private double neighborhoodOverlap(int first, int second) {
        HashSet<Integer> firstNeighbors = new HashSet<>(graph.getNeighbors(first));
        HashSet<Integer> secondNeighbors = new HashSet<>(graph.getNeighbors(second));

        if (firstNeighbors.isEmpty() && secondNeighbors.isEmpty()) {
            return 0.0;
        }

        HashSet<Integer> union = new HashSet<>(firstNeighbors);
        union.addAll(secondNeighbors);

        HashSet<Integer> intersection = new HashSet<>(firstNeighbors);
        intersection.retainAll(secondNeighbors);

        return (double) intersection.size() / union.size();
    }

    /**
     * Local clustering coefficient for one article.
     *
     * We look at the article's outgoing neighbors. If many of those neighbors
     * also link to each other, the local clustering coefficient is high.
     */
    private double localClusteringCoefficient(int articleId) {
        ArrayList<Integer> neighbors = new ArrayList<>(graph.getNeighbors(articleId));
        int degree = neighbors.size();
        if (degree < 2) {
            return 0.0;
        }

        int connectedNeighborPairs = 0;
        int possibleNeighborPairs = degree * (degree - 1);

        for (int i = 0; i < degree; i++) {
            int first = neighbors.get(i);
            HashSet<Integer> firstNeighborSet = new HashSet<>(graph.getNeighbors(first));
            for (int j = 0; j < degree; j++) {
                if (i == j) {
                    continue;
                }
                int second = neighbors.get(j);
                if (firstNeighborSet.contains(second)) {
                    connectedNeighborPairs++;
                }
            }
        }

        return (double) connectedNeighborPairs / possibleNeighborPairs;
    }

    private void printAnalysisSummary(long elapsedTime) {
        System.out.println();
        System.out.println("Random pair analysis complete.");
        System.out.println("Trials: " + lastTrials);
        System.out.println("Reachable pairs: " + lastReachablePairs);

        if (lastReachablePairs == 0) {
            System.out.println("Average shortest path length: no reachable pairs found");
        } else {
            System.out.printf("Average shortest path length: %.2f hops%n",
                    lastAveragePathLength);
        }

        System.out.println("Time to compute: " + elapsedTime + " ms");
        System.out.println();

        System.out.println("Extra network measures:");
        System.out.printf("Average local clustering coefficient: %.3f%n",
                lastAverageLocalClustering);
        if (lastPathEdgesChecked == 0) {
            System.out.println("Average neighborhood overlap on path edges: no path edges checked");
        } else {
            System.out.printf("Average neighborhood overlap on path edges: %.3f%n",
                    lastAverageNeighborhoodOverlap);
            System.out.println("Local bridge edges on sampled shortest paths: "
                    + lastLocalBridgeEdges + " out of " + lastPathEdgesChecked);
        }
        System.out.println();

        System.out.println("Small-world interpretation:");
        if (lastReachablePairs > 0 && lastAveragePathLength <= 6.0) {
            System.out.println("The sampled paths are short, which supports the idea "
                    + "that Wikipedia behaves like a small-world information network.");
        } else if (lastReachablePairs > 0) {
            System.out.println("Some paths are longer, but the analysis still shows how "
                    + "BFS can measure distances in a directed web graph.");
        }
        System.out.println();
    }
}
