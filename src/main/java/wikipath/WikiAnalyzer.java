package wikipath;

import java.util.ArrayList;
import java.util.HashMap;
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
    private int lastReachablePairs;
    private int lastTrials;

    public WikiAnalyzer(WikiGraph graph) {
        this.graph = graph;
        this.pathFinder = new BFSPathFinder(graph);
        this.bridgeCounts = new HashMap<>();
        this.lastAveragePathLength = 0.0;
        this.lastReachablePairs = 0;
        this.lastTrials = 0;
    }

    /**
     * Samples random source/target pairs, runs BFS for each pair, and counts
     * path lengths and bridge articles.
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
        bridgeCounts.clear();

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < trials; i++) {
            int source = articleIds.get(random.nextInt(articleIds.size()));
            int target = articleIds.get(random.nextInt(articleIds.size()));

            while (target == source) {
                target = articleIds.get(random.nextInt(articleIds.size()));
            }

            List<Integer> path = pathFinder.findShortestPath(source, target);
            if (!path.isEmpty()) {
                reachablePairs++;
                totalHops += path.size() - 1;
                countBridgeArticles(path);
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
        System.out.println("Top " + howMany + " bridge articles:");
        for (int i = 0; i < entries.size() && i < howMany; i++) {
            int articleId = entries.get(i).getKey();
            int count = entries.get(i).getValue();
            System.out.println((i + 1) + ". " + graph.getTitleFromId(articleId)
                    + " appeared in " + count + " shortest paths");
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
