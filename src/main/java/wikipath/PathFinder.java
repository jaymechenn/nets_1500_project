package wikipath;

import java.util.Arrays;

/**
 * WE used a BFS-based shortest path engine over the wiki graph, because every
 * hyperlink has the same weight, so BFS is
 * clearly optimal for shortest path here.
 *
 */
public final class PathFinder {

/**
 * Define vars
 */
    public static final int NO_PATH = -1;
    private final WikiGraph graph;
    private final int[] parentGen;
    private final int[] parent;
    private final int[] distance;
    private int currentGen;
    private final IntQueue queue;

    /** Result of BFS query */
    public static final class Result {
        private final int[] path;
        private final int hops;
        private final long elapsedNanos;
        private final int nodesVisited;

        Result(int[] path, int hops, long elapsedNanos, int nodesVisited) {
            this.path = path;
            this.hops = hops;
            this.elapsedNanos = elapsedNanos;
            this.nodesVisited = nodesVisited;
        }

        /** Path of node IDs from src to tgt, or empty array if unreachable */
        public int[] path() { return path; }
        /** Number of hops in the shortest path, or {@link #NO_PATH} if unreachable. */
        public int hops() { return hops; }
        /** Wall-clock duration of the BFS in nanoseconds. */
        public long elapsedNanos() { return elapsedNanos; }
        /** Total nodes dequeued during BFS (a rough proxy for work done). */
        public int nodesVisited() { return nodesVisited; }
        public boolean found() { return hops != NO_PATH; }
    }

    public PathFinder(WikiGraph graph) {
        this.graph = graph;
        int n = graph.numNodes();
        this.parentGen = new int[n];
        this.parent = new int[n];
        this.distance = new int[n];
        this.queue = new IntQueue(1 << 16);
    }

    /**
     * Find the shortest hyperlink path from src to tgt
     * Both must be valid node IDs
     */
    public Result shortestPath(int source, int target) {
        if (source < 0 || source >= graph.numNodes()
                || target < 0 || target >= graph.numNodes()) {
            throw new IllegalArgumentException("source/target out of range");
        }
        long t0 = System.nanoTime();
        if (source == target) {
            return new Result(new int[] { source }, 0, System.nanoTime() - t0, 1);
        }
        bumpGeneration();
        queue.clear();

        int[] edges = graph.edgeArray();
        int gen = currentGen;
        parent[source] = source;
        parentGen[source] = gen;
        distance[source] = 0;
        queue.add(source);

        int visited = 1;
        boolean found = false;
        while (!queue.isEmpty()) {
            int u = queue.poll();
            int end = graph.neighborEnd(u);
            int du = distance[u];
            for (int i = graph.neighborStart(u); i < end; i++) {
                int v = edges[i];
                if (parentGen[v] != gen) {
                    parentGen[v] = gen;
                    parent[v] = u;
                    distance[v] = du + 1;
                    if (v == target) {
                        visited++;
                        found = true;
                        break;
                    }
                    queue.add(v);
                    visited++;
                }
            }
            if (found) break;
        }
        long elapsed = System.nanoTime() - t0;
        if (!found) {
            return new Result(new int[0], NO_PATH, elapsed, visited);
        }
        int[] path = reconstruct(source, target);
        return new Result(path, path.length - 1, elapsed, visited);
    }

    /**
     * Run BFS from src over the entire subgraph and
     * leave the parent / dist arrays populated
     */
    public int runFullBfs(int source) {
        if (source < 0 || source >= graph.numNodes()) {
            throw new IllegalArgumentException("source out of range");
        }
        bumpGeneration();
        queue.clear();
        int[] edges = graph.edgeArray();
        int gen = currentGen;
        parent[source] = source;
        parentGen[source] = gen;
        distance[source] = 0;
        queue.add(source);

        int reached = 1;
        while (!queue.isEmpty()) {
            int u = queue.poll();
            int end = graph.neighborEnd(u);
            int du = distance[u];
            for (int i = graph.neighborStart(u); i < end; i++) {
                int v = edges[i];
                if (parentGen[v] != gen) {
                    parentGen[v] = gen;
                    parent[v] = u;
                    distance[v] = du + 1;
                    queue.add(v);
                    reached++;
                }
            }
        }
        return reached;
    }

    public boolean wasReached(int node) {
        return parentGen[node] == currentGen;
    }

    /** dist from the most recent BFS src to curr node */
    public int distanceTo(int node) {
        return parentGen[node] == currentGen ? distance[node] : NO_PATH;
    }

    /**
     * Reconstruct the path src after running BFS.
     */
    public int[] reconstruct(int source, int target) {
        if (parentGen[target] != currentGen) return new int[0];
        int len = distance[target] + 1;
        int[] path = new int[len];
        int cur = target;
        for (int i = len - 1; i >= 0; i--) {
            path[i] = cur;
            if (cur == source) break;
            cur = parent[cur];
        }
        return path;
    }

    private void bumpGeneration() {
        currentGen++;
        if (currentGen == 0) {
            // Wrapped around (essentially never, but be safe): reset.
            Arrays.fill(parentGen, 0);
            currentGen = 1;
        }
    }
}
