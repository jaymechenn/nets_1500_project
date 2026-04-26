package wikipath;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Network-level analysis over the loaded graph: average shortest-path
 * length and the most common &quot;bridge&quot; articles, with explicit
 * uncertainty quantification.
 *
 * <h2>Why we sample</h2>
 *
 * <p>The full pair-count for the SNAP {@code wiki-topcats} graph is
 * {@code 1,791,489 × 1,791,488 ≈ 3.21 × 10^12} ordered pairs &mdash;
 * pre-computing every shortest path is infeasible. We therefore
 * estimate the network statistics from a uniform random sample.</p>
 *
 * <h2>Sampling design (source-batch sampling)</h2>
 *
 * <p>For each of {@code S} <em>source</em> articles drawn uniformly at
 * random (we reject sources with zero out-edges), we run one full BFS,
 * which yields shortest-path distances and parent pointers to every
 * reachable node in {@code O(N + E)} time. From each BFS we then sample
 * {@code T} reachable targets uniformly at random, giving
 * {@code S × T} sampled pairs at the cost of {@code S} BFS runs &mdash;
 * roughly {@code T &times;} more efficient than fresh-pair sampling.</p>
 *
 * <p>This is a slight variation on Brandes &amp; Pich's <em>source
 * sampling</em> estimator for betweenness centrality (TKDD 2007). When
 * {@code T = N - 1} every reachable target is enumerated, recovering
 * exact source-sampling. With smaller {@code T} we add an additional
 * uniform-target draw whose effect is captured in the reported
 * confidence intervals.</p>
 *
 * <h2>Estimators we report</h2>
 *
 * <ul>
 *   <li><b>Mean shortest-path length.</b> Online (Welford) sample
 *       variance over connected pairs, giving a standard error of
 *       {@code s/&radic;n} and a normal-approximation 95% CI of
 *       {@code mean &plusmn; 1.96 &middot; SE}. Connected-pair counts
 *       are typically &ge; 10,000 in our presets, so the normal
 *       approximation is sound.</li>
 *   <li><b>Bridge frequency.</b> For each candidate node {@code v} we
 *       record {@code c(v)} = number of sampled paths in which
 *       {@code v} appears as an <em>internal</em> node. We report
 *       {@code c(v)} and the point estimate
 *       {@code &beta;(v) = c(v) / n_pairs}, which is an unbiased
 *       estimator (up to the source-sampling caveat above) of the
 *       probability that a uniformly random shortest path between two
 *       articles passes through {@code v}. We attach a Wilson 95%
 *       confidence interval, which is well-behaved even for small
 *       counts.</li>
 *   <li><b>Stability check.</b> We split the sources into two
 *       independent halves A and B (alternating by source index) and
 *       maintain separate bridge counters per half. We then report:
 *       (i) the overlap of the top-{@code k} bridge sets from the two
 *       halves, and (ii) Spearman's rank correlation
 *       {@code &rho;} between the per-half counts of those top-{@code
 *       k} nodes. Stable rankings score &gt; 0.9.</li>
 * </ul>
 */
public final class StatsRunner {

    private static final double Z_95 = 1.959963984540054; // 0.975-quantile of N(0,1)

    private final WikiGraph graph;
    private final TitleIndex titles;

    public StatsRunner(WikiGraph graph, TitleIndex titles) {
        this.graph = graph;
        this.titles = titles;
    }

    /** A single bridge article, with point estimate and 95% Wilson CI. */
    public static final class Bridge {
        public final int id;
        public final long count;          // total appearances across all sampled paths
        public final long countHalfA;     // appearances in half A (odd source indices)
        public final long countHalfB;     // appearances in half B (even source indices)
        public final double frequency;    // count / pairsConnected
        public final double ciLow;        // Wilson 95% lower bound on frequency
        public final double ciHigh;       // Wilson 95% upper bound on frequency

        Bridge(int id, long count, long countHalfA, long countHalfB,
               double frequency, double ciLow, double ciHigh) {
            this.id = id;
            this.count = count;
            this.countHalfA = countHalfA;
            this.countHalfB = countHalfB;
            this.frequency = frequency;
            this.ciLow = ciLow;
            this.ciHigh = ciHigh;
        }
    }

    /** Result of a sampling run. */
    public static final class Stats {
        public final int sampledSources;
        public final int targetsPerSource;
        public final long pairsAttempted;
        public final long pairsConnected;

        public final double meanPathLength;
        public final double pathLengthSampleSD;
        public final double pathLengthSE;
        public final double pathLengthCI95Low;
        public final double pathLengthCI95High;

        public final int medianPathLength;
        public final int p90PathLength;
        public final int p99PathLength;
        public final int minPathLengthSeen;
        public final int maxPathLengthSeen;
        public final long[] hopHistogram;

        public final Bridge[] topBridges;

        // Split-half stability for the top-k:
        public final int splitHalfOverlap;       // |top-k(A) ∩ top-k(B)|
        public final double splitHalfSpearman;   // ρ over top-k union

        public final long elapsedMillis;

        Stats(int sampledSources, int targetsPerSource,
              long pairsAttempted, long pairsConnected,
              double meanPathLength, double pathLengthSampleSD, double pathLengthSE,
              double pathLengthCI95Low, double pathLengthCI95High,
              int medianPathLength, int p90PathLength, int p99PathLength,
              int minPathLengthSeen, int maxPathLengthSeen, long[] hopHistogram,
              Bridge[] topBridges,
              int splitHalfOverlap, double splitHalfSpearman,
              long elapsedMillis) {
            this.sampledSources = sampledSources;
            this.targetsPerSource = targetsPerSource;
            this.pairsAttempted = pairsAttempted;
            this.pairsConnected = pairsConnected;
            this.meanPathLength = meanPathLength;
            this.pathLengthSampleSD = pathLengthSampleSD;
            this.pathLengthSE = pathLengthSE;
            this.pathLengthCI95Low = pathLengthCI95Low;
            this.pathLengthCI95High = pathLengthCI95High;
            this.medianPathLength = medianPathLength;
            this.p90PathLength = p90PathLength;
            this.p99PathLength = p99PathLength;
            this.minPathLengthSeen = minPathLengthSeen;
            this.maxPathLengthSeen = maxPathLengthSeen;
            this.hopHistogram = hopHistogram;
            this.topBridges = topBridges;
            this.splitHalfOverlap = splitHalfOverlap;
            this.splitHalfSpearman = splitHalfSpearman;
            this.elapsedMillis = elapsedMillis;
        }
    }

    /**
     * Run the sampling experiment.
     *
     * @param numSources       number of random source articles ({@code S})
     * @param targetsPerSource targets sampled per source ({@code T})
     * @param topBridges       number of bridge articles to surface
     * @param seed             RNG seed for reproducibility
     * @param log              progress sink, may be {@code null}
     */
    public Stats run(int numSources, int targetsPerSource, int topBridges, long seed, PrintStream log) {
        if (numSources <= 0 || targetsPerSource <= 0) {
            throw new IllegalArgumentException("numSources and targetsPerSource must be positive");
        }
        long t0 = System.currentTimeMillis();
        int n = graph.numNodes();
        Random rng = new Random(seed);
        PathFinder pf = new PathFinder(graph);

        long[] bridgeCountsA = new long[n];
        long[] bridgeCountsB = new long[n];

        long pairsAttempted = 0;
        long pairsConnected = 0;
        int maxHops = 0;
        int minHops = Integer.MAX_VALUE;
        long[] hopHist = new long[64];

        // Welford's online variance for the path-length distribution.
        double meanLen = 0.0;
        double m2Len = 0.0;

        for (int s = 0; s < numSources; s++) {
            int source = pickValidSource(rng, n);
            int reached = pf.runFullBfs(source);
            if (reached <= 1) {
                if (log != null) log.printf("[stats] source %d (%s) reaches no other nodes%n",
                        source, safeTitle(source));
                continue;
            }

            int needed = Math.min(targetsPerSource, reached - 1);
            int[] picks = sampleReachedTargets(pf, source, n, needed, rng);

            // Half assignment: alternate sources between A and B for split-half stability.
            long[] counter = (s & 1) == 0 ? bridgeCountsA : bridgeCountsB;

            for (int target : picks) {
                pairsAttempted++;
                int hops = pf.distanceTo(target);
                if (hops <= 0) continue;
                pairsConnected++;
                if (hops > maxHops) maxHops = hops;
                if (hops < minHops) minHops = hops;
                if (hops < hopHist.length) hopHist[hops]++;

                // Welford update.
                double delta = hops - meanLen;
                meanLen += delta / pairsConnected;
                double delta2 = hops - meanLen;
                m2Len += delta * delta2;

                int[] path = pf.reconstruct(source, target);
                for (int i = 1; i < path.length - 1; i++) {
                    counter[path[i]]++;
                }
            }

            if (log != null && (s + 1) % 25 == 0) {
                log.printf("[stats] %d/%d sources processed, %,d pairs connected so far%n",
                        s + 1, numSources, pairsConnected);
            }
        }

        // Path-length uncertainty (normal approximation).
        double sd = pairsConnected > 1 ? Math.sqrt(m2Len / (pairsConnected - 1)) : 0.0;
        double se = pairsConnected > 0 ? sd / Math.sqrt(pairsConnected) : 0.0;
        double ciLo = meanLen - Z_95 * se;
        double ciHi = meanLen + Z_95 * se;
        int median = percentileFromHist(hopHist, pairsConnected, 0.50);
        int p90 = percentileFromHist(hopHist, pairsConnected, 0.90);
        int p99 = percentileFromHist(hopHist, pairsConnected, 0.99);
        if (minHops == Integer.MAX_VALUE) minHops = 0;

        // Combined bridge counts and top-k extraction with Wilson 95% CIs.
        long[] combined = new long[n];
        for (int i = 0; i < n; i++) combined[i] = bridgeCountsA[i] + bridgeCountsB[i];
        int[] topIdsCombined = topK(combined, topBridges);

        Bridge[] bridges = new Bridge[topIdsCombined.length];
        long denom = Math.max(1, pairsConnected);
        for (int i = 0; i < topIdsCombined.length; i++) {
            int id = topIdsCombined[i];
            long total = combined[id];
            double[] wilson = wilson95CI(total, denom);
            bridges[i] = new Bridge(id, total, bridgeCountsA[id], bridgeCountsB[id],
                    (double) total / denom, wilson[0], wilson[1]);
        }

        // Split-half stability:
        //   overlap = |topK(A) ∩ topK(B)|
        //   spearman = rank correlation of (countA, countB) over topK(A ∪ B)
        int[] topA = topK(bridgeCountsA, topBridges);
        int[] topB = topK(bridgeCountsB, topBridges);
        int overlap = setOverlap(topA, topB);

        Set<Integer> union = new HashSet<>();
        for (int id : topA) union.add(id);
        for (int id : topB) union.add(id);
        double rho = spearmanRho(union, bridgeCountsA, bridgeCountsB);

        long elapsed = System.currentTimeMillis() - t0;
        return new Stats(numSources, targetsPerSource, pairsAttempted, pairsConnected,
                meanLen, sd, se, ciLo, ciHi,
                median, p90, p99, minHops, maxHops, hopHist, bridges,
                overlap, rho, elapsed);
    }

    /**
     * Discrete percentile computed straight from the hop histogram. Returns
     * the smallest hop value {@code h} such that the cumulative frequency
     * up through {@code h} is at least {@code p &middot; n_total}.
     */
    private static int percentileFromHist(long[] hist, long total, double p) {
        if (total <= 0) return 0;
        long target = (long) Math.ceil(p * total);
        long cum = 0;
        for (int i = 0; i < hist.length; i++) {
            cum += hist[i];
            if (cum >= target) return i;
        }
        return hist.length - 1;
    }

    // ------------------------------------------------------------- helpers

    private int pickValidSource(Random rng, int n) {
        for (int tries = 0; tries < 64; tries++) {
            int s = rng.nextInt(n);
            if (graph.outDegree(s) > 0) return s;
        }
        return rng.nextInt(n);
    }

    private int[] sampleReachedTargets(PathFinder pf, int source, int n, int needed, Random rng) {
        int[] out = new int[needed];
        int filled = 0;
        int budget = Math.max(needed * 8, 256);
        while (filled < needed && budget > 0) {
            int t = rng.nextInt(n);
            budget--;
            if (t == source) continue;
            if (pf.distanceTo(t) > 0) out[filled++] = t;
        }
        if (filled < needed) {
            for (int t = 0; t < n && filled < needed; t++) {
                if (t == source) continue;
                if (pf.distanceTo(t) > 0) out[filled++] = t;
            }
        }
        if (filled < out.length) out = Arrays.copyOf(out, filled);
        return out;
    }

    private static int[] topK(long[] counts, int k) {
        int n = counts.length;
        int kk = Math.min(k, n);
        int[] ids = new int[kk];
        long[] vals = new long[kk];
        Arrays.fill(vals, -1L);
        for (int i = 0; i < n; i++) {
            long c = counts[i];
            if (c <= vals[kk - 1]) continue;
            int pos = kk - 1;
            while (pos > 0 && vals[pos - 1] < c) {
                vals[pos] = vals[pos - 1];
                ids[pos] = ids[pos - 1];
                pos--;
            }
            vals[pos] = c;
            ids[pos] = i;
        }
        int real = 0;
        for (int i = 0; i < kk; i++) if (vals[i] > 0) real++;
        return real == kk ? ids : Arrays.copyOf(ids, real);
    }

    private static int setOverlap(int[] a, int[] b) {
        Set<Integer> sa = new HashSet<>();
        for (int x : a) sa.add(x);
        int n = 0;
        for (int x : b) if (sa.contains(x)) n++;
        return n;
    }

    /**
     * Spearman's rank correlation of count-vectors {@code (a, b)} restricted
     * to the given index set. Falls back to 0 if either vector is constant
     * over the index set (correlation undefined).
     */
    private static double spearmanRho(Set<Integer> ids, long[] a, long[] b) {
        int m = ids.size();
        if (m < 2) return Double.NaN;
        int[] idx = new int[m];
        long[] av = new long[m];
        long[] bv = new long[m];
        int p = 0;
        for (int id : ids) {
            idx[p] = id;
            av[p] = a[id];
            bv[p] = b[id];
            p++;
        }
        double[] ra = ranks(av);
        double[] rb = ranks(bv);
        return pearson(ra, rb);
    }

    private static double[] ranks(long[] v) {
        int n = v.length;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, (i, j) -> Long.compare(v[i], v[j]));
        double[] r = new double[n];
        int i = 0;
        while (i < n) {
            int j = i;
            while (j + 1 < n && v[order[j + 1]] == v[order[i]]) j++;
            // Average rank for ties (1-indexed).
            double avg = (i + j + 2) / 2.0;
            for (int k = i; k <= j; k++) r[order[k]] = avg;
            i = j + 1;
        }
        return r;
    }

    private static double pearson(double[] x, double[] y) {
        int n = x.length;
        double mx = 0, my = 0;
        for (int i = 0; i < n; i++) { mx += x[i]; my += y[i]; }
        mx /= n; my /= n;
        double sxx = 0, syy = 0, sxy = 0;
        for (int i = 0; i < n; i++) {
            double dx = x[i] - mx, dy = y[i] - my;
            sxx += dx * dx; syy += dy * dy; sxy += dx * dy;
        }
        if (sxx <= 0 || syy <= 0) return Double.NaN;
        return sxy / Math.sqrt(sxx * syy);
    }

    /**
     * Wilson score interval at 95% confidence for a binomial proportion
     * with {@code k} successes in {@code n} trials. Wilson is preferred
     * over the textbook normal interval because it remains accurate when
     * {@code k} is small or close to {@code n}.
     */
    private static double[] wilson95CI(long k, long n) {
        if (n <= 0) return new double[] { 0.0, 1.0 };
        double phat = (double) k / n;
        double z = Z_95;
        double z2 = z * z;
        double denom = 1.0 + z2 / n;
        double center = (phat + z2 / (2.0 * n)) / denom;
        double half = (z * Math.sqrt(phat * (1.0 - phat) / n + z2 / (4.0 * (double) n * n))) / denom;
        return new double[] { Math.max(0.0, center - half), Math.min(1.0, center + half) };
    }

    private String safeTitle(int id) {
        String t = titles.titleOf(id);
        return t == null ? "<id=" + id + ">" : t;
    }

    // -------------------------------------------------------------- output

    /**
     * Pretty-print the full stats result.
     */
    public void print(Stats s, PrintStream out) {
        printHeader(s, out);
        printPathLength(s, out);
        printHopHistogram(s, out);
        printBridges(s, out);
        printStability(s, out);
        out.println();
    }

    /**
     * Print only the bridge analysis (used by the {@code bridges} subcommand).
     */
    public void printBridgesOnly(Stats s, PrintStream out) {
        printHeader(s, out);
        printBridges(s, out);
        printStability(s, out);
        out.println();
    }

    /**
     * Print only the average-path-length analysis (used by the {@code avgpath} subcommand).
     */
    public void printAvgPathOnly(Stats s, PrintStream out) {
        printHeader(s, out);
        printPathLength(s, out);
        printHopHistogram(s, out);
        out.println();
    }

    private void printHeader(Stats s, PrintStream out) {
        out.println();
        out.println("===== Wikipedia Six Degrees: Sampled Statistics =====");
        out.println();
        out.println("Methodology");
        out.println("  Source-batch sampling (Brandes & Pich 2007 variant): ");
        out.printf ("    1) draw S = %,d sources uniformly at random (out-degree > 0)%n", s.sampledSources);
        out.printf ("    2) BFS from each source (full single-source shortest paths)%n");
        out.printf ("    3) sample T = %,d reachable targets per source (uniform)%n", s.targetsPerSource);
        out.printf ("    -> S * T = %,d candidate pairs, %,d connected (%.2f%%)%n",
                s.pairsAttempted, s.pairsConnected,
                s.pairsAttempted == 0 ? 0.0 : 100.0 * s.pairsConnected / s.pairsAttempted);
        out.printf ("  Sampling wall time: %.2fs%n", s.elapsedMillis / 1000.0);
        out.println();
    }

    private void printPathLength(Stats s, PrintStream out) {
        out.println("Average shortest-path length (over " + String.format("%,d", s.pairsConnected)
                + " connected sampled pairs)");
        out.printf ("  mean              : %.4f hops%n", s.meanPathLength);
        out.printf ("  sample SD         : %.4f hops%n", s.pathLengthSampleSD);
        out.printf ("  standard error    : %.4f hops%n", s.pathLengthSE);
        out.printf ("  95%% CI on mean    : [%.4f, %.4f] hops   (normal approx, mean +/- 1.96*SE)%n",
                s.pathLengthCI95Low, s.pathLengthCI95High);
        out.println();
        out.println("Distribution of path lengths");
        out.printf ("  min               : %d hops%n", s.minPathLengthSeen);
        out.printf ("  median (p50)      : %d hops%n", s.medianPathLength);
        out.printf ("  90th percentile   : %d hops%n", s.p90PathLength);
        out.printf ("  99th percentile   : %d hops%n", s.p99PathLength);
        out.printf ("  max               : %d hops%n", s.maxPathLengthSeen);
        out.println();
    }

    private void printHopHistogram(Stats s, PrintStream out) {
        out.println("Hop-length histogram (over connected sampled pairs)");
        long total = 0;
        for (int i = 1; i < s.hopHistogram.length; i++) total += s.hopHistogram[i];
        if (total == 0) { out.println("  (no connected pairs)"); out.println(); return; }
        for (int i = 1; i < s.hopHistogram.length; i++) {
            long c = s.hopHistogram[i];
            if (c == 0) continue;
            double pct = 100.0 * c / total;
            int bars = (int) Math.round(pct / 2.0);
            out.printf ("  %2d hops : %,9d  (%5.2f%%)  %s%n",
                    i, c, pct, "#".repeat(bars));
        }
        out.println();
    }

    private void printBridges(Stats s, PrintStream out) {
        out.printf("Top %d bridge articles%n", s.topBridges.length);
        out.println("  (count = appearances as internal node in sampled shortest paths;");
        out.println("   freq  = count / connected_pairs; 95% CI = Wilson interval on freq)");
        out.println();
        out.printf ("  %-4s %-44s %10s %8s %22s%n",
                "rank", "article", "count", "freq", "95% Wilson CI");
        out.printf ("  %-4s %-44s %10s %8s %22s%n",
                "----", "-------", "-----", "----", "--------------");
        for (int i = 0; i < s.topBridges.length; i++) {
            Bridge b = s.topBridges[i];
            String title = titles.titleOf(b.id);
            String name = title == null ? "<id=" + b.id + ">" : title;
            if (name.length() > 44) name = name.substring(0, 41) + "...";
            out.printf ("  %-4d %-44s %,10d %7.4f%%  [%6.4f%%, %6.4f%%]%n",
                    i + 1, name, b.count,
                    100.0 * b.frequency,
                    100.0 * b.ciLow, 100.0 * b.ciHigh);
        }
        out.println();
    }

    private void printStability(Stats s, PrintStream out) {
        int k = s.topBridges.length;
        out.println("Split-half stability check");
        out.println("  Sources are alternately assigned to half A vs B; bridges counted");
        out.println("  separately, and the two halves' top-k rankings compared.");
        out.printf ("    top-%d set overlap     : %d / %d  (%.0f%%)%n",
                k, s.splitHalfOverlap, k,
                k == 0 ? 0.0 : 100.0 * s.splitHalfOverlap / k);
        if (Double.isNaN(s.splitHalfSpearman)) {
            out.println("    Spearman rank corr.   : (undefined; need >= 2 distinct counts)");
        } else {
            String verdict = s.splitHalfSpearman > 0.9 ? "very stable"
                    : s.splitHalfSpearman > 0.7 ? "stable"
                    : s.splitHalfSpearman > 0.4 ? "noisy" : "unstable";
            out.printf ("    Spearman rank corr.   : %.3f  (%s)%n",
                    s.splitHalfSpearman, verdict);
        }
    }
}
