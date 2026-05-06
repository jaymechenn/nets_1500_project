package wikipath;

import java.io.IOException;
import java.util.List;
import java.util.Scanner;

/**
 * Menu-driven program for the WikiPath project.
 */
public class Main {

    private static final String DEFAULT_EDGE_FILE = "data/wiki-topcats.txt";
    private static final String DEFAULT_TITLE_FILE = "data/wiki-topcats-page-names.txt";

    public static void main(String[] args) {
        String edgeFile = DEFAULT_EDGE_FILE;
        String titleFile = DEFAULT_TITLE_FILE;

        if (args.length >= 1) {
            edgeFile = args[0];
        }
        if (args.length >= 2) {
            titleFile = args[1];
        }

        WikiGraph graph = new WikiGraph();

        try {
            System.out.println("Loading edge list from " + edgeFile + "...");
            graph.loadEdges(edgeFile);
            System.out.println("Loaded " + graph.getNodeCount() + " articles and "
                    + graph.getEdgeCount() + " hyperlinks.");

            try {
                System.out.println("Loading title map from " + titleFile + "...");
                graph.loadTitles(titleFile);
                System.out.println("Loaded article titles.");
            } catch (IOException e) {
                System.out.println("Could not load title file. You can still search by ID.");
            }
        } catch (IOException e) {
            System.out.println("Could not load graph file: " + e.getMessage());
            System.out.println("Try passing file paths as command-line arguments:");
            System.out.println("java -cp target/classes wikipath.Main "
                    + "demo-data/edges.txt demo-data/names.txt");
            return;
        }

        BFSPathFinder pathFinder = new BFSPathFinder(graph);
        WikiAnalyzer analyzer = new WikiAnalyzer(graph);

        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        while (running) {
            printMenu();
            String choice = scanner.nextLine().trim();

            if (choice.equals("1")) {
                findPathMenu(scanner, graph, pathFinder);
            } else if (choice.equals("2")) {
                runAnalysisMenu(scanner, analyzer);
            } else if (choice.equals("3")) {
                running = false;
            } else {
                System.out.println("Please enter 1, 2, or 3.");
            }
        }

        scanner.close();
        System.out.println("Goodbye.");
    }

    private static void printMenu() {
        System.out.println();
        System.out.println("WikiPath Menu");
        System.out.println("1. Find shortest path between two articles");
        System.out.println("2. Run random pair analysis (also prints top bridge articles)");
        System.out.println("3. Exit");
        System.out.print("Choose an option: ");
    }

    private static void findPathMenu(
            Scanner scanner, WikiGraph graph, BFSPathFinder pathFinder) {
        System.out.print("Source article title or ID: ");
        String sourceInput = scanner.nextLine().trim();

        System.out.print("Target article title or ID: ");
        String targetInput = scanner.nextLine().trim();

        int sourceId = getArticleId(graph, sourceInput);
        int targetId = getArticleId(graph, targetInput);

        if (sourceId == -1) {
            System.out.println("Invalid source article: " + sourceInput);
            return;
        }
        if (targetId == -1) {
            System.out.println("Invalid target article: " + targetInput);
            return;
        }

        long startTime = System.currentTimeMillis();
        List<Integer> path = pathFinder.findShortestPath(sourceId, targetId);
        long elapsedTime = System.currentTimeMillis() - startTime;

        System.out.println();
        System.out.println("Source article: " + graph.getTitleFromId(sourceId));
        System.out.println("Target article: " + graph.getTitleFromId(targetId));

        if (path.isEmpty()) {
            System.out.println("No hyperlink path was found.");
            System.out.println("Time to compute: " + elapsedTime + " ms");
            return;
        }

        System.out.println("Shortest path:");
        printPath(graph, path);
        System.out.println("Number of hops: " + (path.size() - 1));
        System.out.println("Time to compute: " + elapsedTime + " ms");
    }

    private static void runAnalysisMenu(Scanner scanner, WikiAnalyzer analyzer) {
        System.out.print("How many random pairs should be sampled? ");
        String input = scanner.nextLine().trim();

        try {
            int trials = Integer.parseInt(input);
            if (trials <= 0) {
                System.out.println("Number of trials must be positive.");
                return;
            }
            analyzer.runRandomPairAnalysis(trials);
            analyzer.printTopBridgeArticles(10);
        } catch (NumberFormatException e) {
            System.out.println("Please enter a whole number.");
        }
    }

    private static int getArticleId(WikiGraph graph, String input) {
        try {
            int id = Integer.parseInt(input);
            if (graph.containsArticle(id)) {
                return id;
            }
            return -1;
        } catch (NumberFormatException e) {
            return graph.getIdFromTitle(input);
        }
    }

    private static void printPath(WikiGraph graph, List<Integer> path) {
        for (int i = 0; i < path.size(); i++) {
            if (i > 0) {
                System.out.print(" -> ");
            }
            System.out.print(graph.getTitleFromId(path.get(i)));
        }
        System.out.println();
    }
}
