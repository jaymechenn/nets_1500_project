package wikipath;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Loads the SNAP {@code wiki-topcats} dataset into an in-memory CSR graph
 * plus a bidirectional title index.
 *
 * <h2>Expected files</h2>
 * <ul>
 *   <li><b>edges</b>: {@code wiki-topcats.txt[.gz]} &mdash; one edge per
 *       line as {@code "u v"} (whitespace separated). Lines starting with
 *       {@code '#'} are treated as comments.</li>
 *   <li><b>page names</b>: {@code wiki-topcats-page-names.txt[.gz]} &mdash;
 *       one article per line as {@code "id name"} where {@code id} is the
 *       integer node ID and {@code name} is the rest of the line (Wikipedia
 *       titles use underscores instead of spaces).</li>
 * </ul>
 *
 * <p>The loader does two passes over the edge file: one to count
 * out-degrees, one to fill the edge array. Going through CSR rather than
 * {@code Map<Integer, List<Integer>>} keeps the resident set under
 * &asymp;150&nbsp;MB for the full 28.5M-edge graph.</p>
 */
public final class GraphLoader {

    /** Bundles together the loaded graph and its title index. */
    public static final class Loaded {
        public final WikiGraph graph;
        public final TitleIndex titles;
        public final long loadMillis;

        Loaded(WikiGraph graph, TitleIndex titles, long loadMillis) {
            this.graph = graph;
            this.titles = titles;
            this.loadMillis = loadMillis;
        }
    }

    private GraphLoader() {}

    /**
     * Loads the graph and titles. Progress is written to {@code log}.
     *
     * @param edgesFile path to the SNAP edges file (gzipped or not)
     * @param namesFile path to the SNAP page-names file (gzipped or not)
     * @param log progress sink (e.g. {@code System.err}); may be {@code null}
     */
    public static Loaded load(Path edgesFile, Path namesFile, PrintStream log) throws IOException {
        long start = System.currentTimeMillis();
        requireFile(edgesFile, "edges file");
        requireFile(namesFile, "page-names file");

        if (log != null) log.println("[load] reading page names from " + namesFile);
        long t0 = System.currentTimeMillis();
        String[] idToTitle = readPageNames(namesFile);
        int nFromNames = idToTitle.length;
        if (log != null) {
            log.printf("[load]   %,d titles loaded in %.2fs%n",
                    nFromNames, (System.currentTimeMillis() - t0) / 1000.0);
        }

        if (log != null) log.println("[load] pass 1: counting out-degrees in " + edgesFile);
        t0 = System.currentTimeMillis();
        DegreeCount dc = countDegrees(edgesFile, nFromNames);
        int n = Math.max(nFromNames, dc.maxId + 1);
        if (n > nFromNames) {
            idToTitle = Arrays.copyOf(idToTitle, n);
        }
        if (log != null) {
            log.printf("[load]   %,d nodes, %,d edges in %.2fs%n",
                    n, dc.edgeCount, (System.currentTimeMillis() - t0) / 1000.0);
        }
        if (dc.edgeCount > Integer.MAX_VALUE) {
            throw new IOException("edge count " + dc.edgeCount + " exceeds Integer.MAX_VALUE; "
                    + "this implementation uses int-indexed CSR arrays.");
        }

        int[] offsets = new int[n + 1];
        int[] outDegree = dc.outDegree;
        for (int i = 0; i < n; i++) {
            offsets[i + 1] = offsets[i] + outDegree[i];
        }

        int[] edges = new int[(int) dc.edgeCount];
        int[] cursor = offsets.clone();

        if (log != null) log.println("[load] pass 2: filling adjacency arrays");
        t0 = System.currentTimeMillis();
        fillEdges(edgesFile, edges, cursor);
        if (log != null) {
            log.printf("[load]   adjacency built in %.2fs%n",
                    (System.currentTimeMillis() - t0) / 1000.0);
        }

        Map<String, Integer> titleToId = TitleIndex.emptyMap(n);
        int titled = 0;
        for (int i = 0; i < n; i++) {
            String t = idToTitle[i];
            if (t == null || t.isEmpty()) continue;
            titleToId.putIfAbsent(TitleIndex.normalize(t), i);
            titled++;
        }
        if (log != null) {
            log.printf("[load] indexed %,d titles%n", titled);
        }

        long total = System.currentTimeMillis() - start;
        if (log != null) {
            log.printf("[load] done in %.2fs%n", total / 1000.0);
        }
        return new Loaded(new WikiGraph(offsets, edges), new TitleIndex(idToTitle, titleToId), total);
    }

    // ---------------------------------------------------------------- I/O

    private static void requireFile(Path p, String label) throws IOException {
        if (!Files.exists(p)) {
            throw new IOException(label + " not found: " + p
                    + " (run scripts/download-data.sh to fetch the SNAP files)");
        }
    }

    private static InputStream openMaybeGzipped(Path p) throws IOException {
        InputStream raw = new BufferedInputStream(Files.newInputStream(p), 1 << 20);
        if (p.getFileName().toString().endsWith(".gz")) {
            return new GZIPInputStream(raw, 1 << 20);
        }
        return raw;
    }

    private static BufferedReader openReader(Path p) throws IOException {
        return new BufferedReader(new InputStreamReader(openMaybeGzipped(p), StandardCharsets.UTF_8), 1 << 20);
    }

    // ---------------------------------------------------------- page names

    private static String[] readPageNames(Path namesFile) throws IOException {
        // Format per line: "<id> <title>" where title may include underscores
        // and other non-space characters. Some lines may be ill-formed, in
        // which case we skip them. We accumulate into parallel growable
        // arrays, then transfer into an array of size (maxId + 1).
        int[] ids = new int[1 << 16];
        String[] names = new String[1 << 16];
        int count = 0;
        int maxId = -1;
        try (BufferedReader br = openReader(namesFile)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                int sp = line.indexOf(' ');
                if (sp <= 0 || sp == line.length() - 1) continue;
                int id;
                try {
                    id = Integer.parseInt(line, 0, sp, 10);
                } catch (NumberFormatException nfe) {
                    continue;
                }
                if (count == ids.length) {
                    ids = Arrays.copyOf(ids, ids.length * 2);
                    names = Arrays.copyOf(names, names.length * 2);
                }
                ids[count] = id;
                names[count] = line.substring(sp + 1);
                count++;
                if (id > maxId) maxId = id;
            }
        }
        String[] out = new String[Math.max(0, maxId + 1)];
        for (int i = 0; i < count; i++) {
            int id = ids[i];
            if (id >= 0 && id < out.length) {
                out[id] = names[i];
            }
        }
        return out;
    }

    // -------------------------------------------------------- edge passes

    /** Result of pass 1 over the edges file. */
    private static final class DegreeCount {
        int[] outDegree;
        long edgeCount;
        int maxId;
    }

    private static DegreeCount countDegrees(Path edgesFile, int initialCapacity) throws IOException {
        int[] degree = new int[Math.max(1, initialCapacity)];
        long edgeCount = 0;
        int maxId = -1;
        try (InputStream in = openMaybeGzipped(edgesFile)) {
            EdgeStream es = new EdgeStream(in);
            while (es.next()) {
                int u = es.u();
                int v = es.v();
                int big = Math.max(u, v);
                if (big >= degree.length) {
                    degree = grow(degree, big + 1);
                }
                degree[u]++;
                edgeCount++;
                if (big > maxId) maxId = big;
            }
        }
        DegreeCount dc = new DegreeCount();
        dc.outDegree = degree;
        dc.edgeCount = edgeCount;
        dc.maxId = maxId;
        return dc;
    }

    private static void fillEdges(Path edgesFile, int[] edges, int[] cursor) throws IOException {
        try (InputStream in = openMaybeGzipped(edgesFile)) {
            EdgeStream es = new EdgeStream(in);
            while (es.next()) {
                int u = es.u();
                edges[cursor[u]++] = es.v();
            }
        }
    }

    private static int[] grow(int[] a, int newMin) {
        int newCap = a.length;
        while (newCap < newMin) newCap = newCap < 16 ? 16 : newCap * 2;
        return Arrays.copyOf(a, newCap);
    }

    // ----------------------------------------------------- byte-level scan

    /**
     * Streaming byte-level parser for the SNAP edge format. Each record is
     * two non-negative integers separated by whitespace, terminated by a
     * newline. Lines beginning with {@code '#'} are skipped.
     *
     * <p>Operating on raw bytes (no {@code BufferedReader.readLine()} +
     * {@code String.split}) cuts the second pass roughly in half on the
     * 28.5M-edge graph.</p>
     */
    private static final class EdgeStream {
        private final InputStream in;
        private final byte[] buf = new byte[1 << 20];
        private int len;
        private int pos;
        private int u;
        private int v;

        EdgeStream(InputStream in) {
            this.in = in;
        }

        boolean next() throws IOException {
            while (true) {
                int b = readByte();
                if (b < 0) return false;
                if (b == '#') {
                    skipLine();
                    continue;
                }
                if (b == '\n' || b == '\r' || b == ' ' || b == '\t') continue;

                int x = b - '0';
                if (x < 0 || x > 9) {
                    skipLine();
                    continue;
                }
                while (true) {
                    int b2 = readByte();
                    if (b2 < 0) return false;
                    if (b2 == ' ' || b2 == '\t') break;
                    int d = b2 - '0';
                    if (d < 0 || d > 9) {
                        skipLine();
                        x = -1;
                        break;
                    }
                    x = x * 10 + d;
                }
                if (x < 0) continue;
                u = x;

                int y = -1;
                while (true) {
                    int b2 = readByte();
                    if (b2 < 0) {
                        if (y >= 0) { v = y; return true; }
                        return false;
                    }
                    if (b2 == '\n' || b2 == '\r') {
                        if (y >= 0) { v = y; return true; }
                        break;
                    }
                    if (b2 == ' ' || b2 == '\t') {
                        if (y >= 0) {
                            // Consume rest of line (extra columns, if any).
                            skipLine();
                            v = y;
                            return true;
                        }
                        continue;
                    }
                    int d = b2 - '0';
                    if (d < 0 || d > 9) {
                        skipLine();
                        y = -1;
                        break;
                    }
                    y = (y < 0 ? d : y * 10 + d);
                }
            }
        }

        int u() { return u; }
        int v() { return v; }

        private int readByte() throws IOException {
            if (pos >= len) {
                len = in.read(buf);
                pos = 0;
                if (len <= 0) return -1;
            }
            return buf[pos++] & 0xff;
        }

        private void skipLine() throws IOException {
            while (true) {
                int b = readByte();
                if (b < 0 || b == '\n') return;
            }
        }
    }
}
