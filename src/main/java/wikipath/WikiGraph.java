package wikipath;

/**
 * Directed graph in CSR (compressed sparse row) form.
 *
 * <p>Memory footprint for the SNAP wiki-topcats graph (1.79M nodes,
 * 28.5M edges): about 7&nbsp;MB for offsets and 114&nbsp;MB for the
 * edge array, plus the title index. This fits comfortably in a
 * 1&ndash;2&nbsp;GB heap.</p>
 */
public final class WikiGraph {

    private final int[] offsets;
    private final int[] edges;

    WikiGraph(int[] offsets, int[] edges) {
        this.offsets = offsets;
        this.edges = edges;
    }

    /** Number of nodes (article IDs are in {@code [0, numNodes())}). */
    public int numNodes() {
        return offsets.length - 1;
    }

    /** Number of directed edges (hyperlinks). */
    public long numEdges() {
        return edges.length;
    }

    /** Out-degree of node {@code u}. */
    public int outDegree(int u) {
        return offsets[u + 1] - offsets[u];
    }

    /** Inclusive start index of node {@code u}'s neighbors in the edge array. */
    public int neighborStart(int u) {
        return offsets[u];
    }

    /** Exclusive end index of node {@code u}'s neighbors in the edge array. */
    public int neighborEnd(int u) {
        return offsets[u + 1];
    }

    /** Direct access to the contiguous edge array (read-only contract). */
    public int[] edgeArray() {
        return edges;
    }
}
