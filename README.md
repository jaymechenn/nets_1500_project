# Six Degrees of Wikipedia

A Java application that finds the shortest hyperlink path between any two
Wikipedia articles &mdash; a programmatic version of the *Six Degrees of
Wikipedia* problem &mdash; using the Stanford SNAP `wiki-topcats` graph
and breadth-first search.

> **Example query**
>
> ```text
> path> Kanye West | Quantum mechanics
>
> Shortest path: 4 hops, ~310,000 nodes visited, 0.42s
>   Kanye_West
>   -> Hip_hop_music
>   -> Music
>   -> Physics
>   -> Quantum_mechanics
> ```

---

## Project structure

```text
.
├── pom.xml                              # Maven build (Java 17+)
├── scripts/download-data.sh             # Fetches the SNAP dataset
├── data/                                # SNAP files land here (gitignored)
└── src/main/java/wikipath/
    ├── Main.java                        # CLI: path | repl | stats
    ├── GraphLoader.java                 # SNAP -> CSR adjacency arrays
    ├── WikiGraph.java                   # CSR graph (offsets[] + edges[])
    ├── TitleIndex.java                  # bidirectional name <-> id lookup
    ├── PathFinder.java                  # reusable BFS + path reconstruction
    ├── StatsRunner.java                 # mean path length, bridge sampling,
    │                                    # Wilson CIs, split-half stability
    └── IntQueue.java                    # primitive-int FIFO for BFS
```

---

## Dataset and scale

We use the [Stanford SNAP `wiki-topcats`][snap] hyperlink graph
(September 2011 snapshot, restricted to the largest strongly connected
component of the top categories):

| Metric | Value |
| --- | --- |
| Articles (nodes) | **1,791,489** |
| Hyperlinks (edges) | **28,511,807** |
| Diameter | 9 |
| 90&#8209;percentile effective diameter | 3.8 |
| Edges file (compressed / uncompressed) | 108 MB / 430 MB |
| Page-names file (compressed / uncompressed) | 9 MB / 41 MB |

[snap]: https://snap.stanford.edu/data/wiki-topcats.html

### Why CSR rather than `Map<Integer, List<Integer>>`?

A naive `HashMap<Integer, ArrayList<Integer>>` representation of 28.5M
edges has roughly **1.5&ndash;2 GB** of overhead from boxed `Integer`
objects, list headers, and hash buckets, before counting the data
itself. Compressed Sparse Row (CSR) stores the same graph in two
primitive `int[]`s:

- `offsets[1,791,490]` &mdash; one entry per node, plus a sentinel: **~7 MB**
- `edges[28,511,807]` &mdash; flat array of destination IDs: **~114 MB**

That keeps the resident graph at **~120 MB** plus the title index
(`String[]` of titles + a `HashMap<String, Integer>` of normalized
names), so the whole application comfortably fits in a **1&ndash;2 GB
heap** &mdash; well within a laptop budget.

### Memory and runtime budget

| Phase | Wall time (M2 Macbook, 2 GB heap) | Peak memory |
| --- | --- | --- |
| Decompress + read page names | ~3 s | ~150 MB |
| Pass 1 (degree count over edges) | ~12 s | +60 MB |
| Pass 2 (fill CSR) | ~10 s | +120 MB |
| Single BFS query | **0.1&ndash;0.5 s** | negligible |
| 200&times;200 = 40k stats sample | ~30 s | +15 MB |

These numbers are approximate &mdash; SSD speed dominates the load
phase. Subsequent BFS queries are very fast because the parent array
is reused via a generation counter (no 7 MB `Arrays.fill` per query).

---

## Building

Requires **JDK 17 or newer** (tested on JDK 17, 21, and 23).

### With Maven

```bash
mvn -q package
java -Xmx2g -jar target/wiki-six-degrees.jar help
```

### Without Maven (plain `javac`)

```bash
mkdir -p target/classes
javac -d target/classes -encoding UTF-8 $(find src/main/java -name '*.java')
java -Xmx2g -cp target/classes wikipath.Main help
```

---

## Getting the data

Run the helper script (downloads from `snap.stanford.edu` into `./data/`
and gunzips the files):

```bash
./scripts/download-data.sh
```

This produces:

```text
data/wiki-topcats.txt              # ~430 MB, edge list "u v"
data/wiki-topcats-page-names.txt   # ~41 MB,  "id Article_Name"
```

The loader also accepts the gzipped versions transparently if you'd
rather keep them compressed.

---

## User guide

The application has three sub-commands. **All examples assume `-Xmx2g`**;
the loader needs the headroom and BFS allocates a 7 MB parent array.

### 1. One-shot query

```bash
java -Xmx2g -jar target/wiki-six-degrees.jar path "Kanye West" "Quantum mechanics"
```

Article names are matched case-insensitively and treat underscores and
spaces as equivalent, so `"Kanye West"`, `"kanye_west"`, and
`"KANYE_west"` all resolve to the same article.

Output:

```text
Shortest path: 4 hops, 312,448 nodes visited, 0.41s
  Kanye_West
  -> Hip_hop_music
  -> Music
  -> Physics
  -> Quantum_mechanics
```

If no path exists, the tool prints how many nodes BFS exhausted before
giving up (always `<` total nodes, since BFS terminates as soon as the
reachable component is exhausted).

### 2. Interactive mode

```bash
java -Xmx2g -jar target/wiki-six-degrees.jar repl
```

Loads the graph once, then reads queries from stdin in the form
`Source | Target` until you type `quit`. Useful for exploring without
paying the ~25-second load cost per query.

### 3. Bulk statistics

```bash
# everything: methodology, mean path length + 95% CI, hop histogram,
# top-k bridges with Wilson 95% CIs, split-half stability check
java -Xmx2g -jar target/wiki-six-degrees.jar stats --preset default

# bridge analysis only (skips path-length and histogram sections)
java -Xmx2g -jar target/wiki-six-degrees.jar bridges --preset rigorous --top 25
```

The output of these commands is the **deliverable for the analytical
part of the project** &mdash; mean shortest-path length on the SNAP
graph (~3.8 hops), the hop histogram (concentrated at 3&ndash;5), and
the top "bridge" articles that frequently appear inside random
shortest paths. See [Sampling methodology](#sampling-methodology)
below for the full statistical design (sample sizes, Wilson CIs,
split-half stability, and the Riondato&ndash;Kornaropoulos bound on
how many samples we need).

### Pointing at non-default data files

All commands accept `--edges PATH` and `--names PATH`. The plain or
gzipped version is auto-detected by extension.

```bash
java -Xmx2g -jar target/wiki-six-degrees.jar path \
     --edges data/wiki-topcats.txt.gz \
     --names data/wiki-topcats-page-names.txt.gz \
     "Philadelphia" "Mongolia"
```

---

## How it works

1. **Load page names.** Each line of `wiki-topcats-page-names.txt` is
   `<id> <Article_Name>`. We build a `String[]` keyed by ID and a
   `HashMap<String, Integer>` keyed by a normalized title (lowercase,
   underscores collapsed) for case- and whitespace-insensitive lookups.
2. **Build the graph in CSR form.** We do **two passes** over the
   edges file. Pass 1 counts the out-degree of every node. Pass 2 fills
   a flat `int[]` of destination IDs in CSR layout. We use a
   byte-level parser instead of `BufferedReader.readLine()` +
   `String.split` &mdash; this halves the second pass on the 28.5M-edge
   file.
3. **BFS for shortest path.** From the source article, BFS explores
   level by level. As soon as we discover the target we stop and walk
   the parent map back to the source to reconstruct the chain. Because
   each hop has unit weight, BFS is provably optimal.
4. **Reusable BFS state.** The `parent[]` and `distance[]` arrays are
   reused across queries via a generation counter (`parentGen[v] ==
   currentGen` means "visited this run") so we never have to clear
   1.79M entries between runs.

---

## Sampling methodology

Running BFS from every article to every other would require
~3.2&nbsp;trillion shortest-path computations, so we estimate every
network statistic from a uniform random sample. The full machinery
lives in `StatsRunner.java` and is exposed by two sub-commands:
`stats` (everything) and `bridges` (bridge analysis only).

### Why source-batch sampling

For each source-target pair we want the shortest path. Two natural
sampling designs:

| Design | Pairs / BFS | Bias |
| --- | --- | --- |
| **Pair sampling.** Draw `K` independent random `(s, t)` pairs; run one BFS per pair. | 1 | Unbiased estimator of betweenness, but expensive: `K` BFS for `K` pairs. |
| **Source-batch sampling** (what we use). Draw `S` random sources; for each, run one BFS and sample `T` reachable targets from it. | `T` | Unbiased for source-sampling betweenness ([Brandes & Pich 2007][bp07]); slightly correlated within a source, which we measure via the split-half check below. |

The source-batch design gives `S × T` sampled pairs at the cost of
**only `S` BFS runs**. On the SNAP graph one BFS takes ~0.4&nbsp;s, so
this is the difference between minutes and seconds.

[bp07]: https://www.tandfonline.com/doi/abs/10.1080/15427951.2007.10129293

### Concrete procedure

For the parameters `S = --sources`, `T = --targets`, `seed = --seed`:

1. **Source draw.** Sample `S` source IDs uniformly at random from
   `[0, N)`, rejecting any source with zero out-edges (it would have
   no reachable targets); retry up to 64 times per slot.
2. **Single-source shortest paths.** For each source, run BFS once,
   producing the distance and parent of every reachable node in
   `O(N + E)` time.
3. **Target draw.** Sample `T` targets uniformly at random; reject
   `t = source` and `t` not reached. (For the SNAP graph, which is a
   single SCC, almost every random `t` is reached, so this is fast.)
4. **Recording.** For every connected pair we
   - update an online (Welford) mean and variance of the hop count,
   - increment a per-hop histogram bin,
   - walk the reconstructed path and increment `bridgeCount[v]` for
     every internal node `v` (excluding source and target).

Sources are alternately assigned to **half A** or **half B** by source
index parity, with separate bridge counters per half &mdash; this
powers the split-half stability check (below) at no extra runtime
cost.

### Estimators we report

**Mean shortest-path length.**
With `n_pairs` connected sampled pairs we report the sample mean
`x̄`, the sample standard deviation `s` (computed online, no second
pass), the standard error `SE = s / √n_pairs`, and the
normal-approximation 95% confidence interval `x̄ ± 1.96 · SE`.
For our presets `n_pairs ≥ 2,500`, well past the regime where the
normal approximation is accurate.

**Bridge frequency `β(v)`.**
For each candidate `v`, the point estimate is
`β̂(v) = bridgeCount(v) / n_pairs`, an unbiased estimator (modulo
the source-batch caveat) of the probability that a uniformly random
shortest path between two articles passes through `v` as an
intermediate. We attach a **Wilson 95% confidence interval**, which
remains accurate for small counts and proportions near 0 or 1 (the
textbook normal interval breaks down there).

**Split-half stability.**
We split the sample into two equally-sized halves A and B (alternating
sources by parity), and compute the top-`k` bridge sets independently
in each half. We report:

- the **set overlap** `|topK(A) ∩ topK(B)| / k` &mdash; how many of
  the top-`k` are picked out by both halves;
- **Spearman's rank correlation** `ρ` of the half-A and half-B counts
  over the union `topK(A) ∪ topK(B)`. Practical interpretation:
  `ρ > 0.95` is "very stable", `0.7–0.95` is "stable",
  `0.4–0.7` is "noisy", `< 0.4` is "unstable, increase the sample".

### Sample-size presets

| Preset | `S` | `T` | `S·T` (max pairs) | Wall time *after* load | Use case |
| --- | --- | --- | --- | --- | --- |
| `--preset quick` | 50 | 50 | 2,500 | ~5 s | Smoke test, exploration |
| `--preset default` | 200 | 200 | 40,000 | ~30 s | Reasonable rankings + write-up CIs |
| `--preset rigorous` | 500 | 500 | 250,000 | ~3 min | Tight CIs, very stable rank |

You can always override with explicit `--sources` and `--targets`.

### How big a sample is "enough"?

Two things to estimate, two different answers.

**For the mean path length.** The path-length distribution on this
graph has support {1, 2, ..., 9} and SD around 1.0&ndash;1.3 (the
diameter is 9 but mass is concentrated at 3&ndash;5). The standard
error is `SE ≈ s / √(S·T)`, so the 95% CI half-width is
`1.96 · s / √(S·T) ≈ 2.5 / √(S·T)`. To get the mean within
&plusmn;0.01 hops you need roughly `S·T ≥ 60,000`, i.e. the `default`
preset gets you within &plusmn;0.012 and `rigorous` within
&plusmn;0.005.

**For bridge ranking.** This is closer to the
*betweenness centrality estimation* problem. [Riondato &
Kornaropoulos][rk16] proved that for an `(ε, δ)`-approximation
(every node's estimate within `±ε` of the true centrality with
probability `≥ 1 − δ`) it suffices to sample
`K = (c/ε²) · (⌊log₂(VD−2)⌋ + 1 + ln(1/δ))` source-target pairs,
where `VD` is the vertex diameter. For our graph
(`VD ≈ 9`, `ε = 0.01`, `δ = 0.05`) this evaluates to
`K ≈ 60,000` &mdash; remarkably close to our `default` preset.

In practice, what matters for **ranking** is much less than what
matters for **point estimates of centrality**: the top hubs in the
SNAP graph (`United_States`, `England`, `World_War_II`, ...) have
betweenness orders of magnitude higher than the median, so even the
`quick` preset reliably surfaces the same names. The split-half check
reports this directly via Spearman ρ.

[rk16]: https://link.springer.com/article/10.1007/s10618-015-0423-0

### Reproducibility

Pass `--seed N` for deterministic source/target draws. Two runs with
the same `--seed`, `--sources`, `--targets`, and dataset produce
byte-identical output.

---

## Sample queries to try

| Source | Target | Comment |
| --- | --- | --- |
| `Kanye West` | `Quantum mechanics` | The classic. Usually 3&ndash;4 hops via `Music` and `Physics`. |
| `Philadelphia` | `Mongolia` | Geographic crossings tend to route via `United_States`. |
| `Coffee` | `Symphony` | Demonstrates `Coffee -> Music -> Symphony`-style detours. |
| `Tetris` | `World War II` | Soviet origins make this surprisingly short. |
| `Banana` | `Linear algebra` | Forces a route through agriculture &rarr; science hubs. |

---

## Course concepts demonstrated

- **Graphs and graph algorithms.** Directed graph in CSR form, BFS for
  shortest paths, parent pointers for path reconstruction, bounded-time
  reuse of BFS state across queries.
- **Information networks / the World Wide Web.** Direct study of the
  Wikipedia hyperlink graph: average path length, hub identification,
  empirical observation of the small-world phenomenon (paths cluster
  tightly around 3&ndash;4 hops despite 1.79M nodes).

---

## Troubleshooting

- **`OutOfMemoryError` while loading.** Increase heap: `-Xmx2g` is
  recommended; `-Xmx3g` is plenty.
- **`edges file not found`.** Run `./scripts/download-data.sh`, or
  pass `--edges` / `--names` explicitly.
- **A title isn't found.** SNAP titles use underscores and the exact
  Wikipedia-2011 spelling. Try the underscore form (`Quantum_mechanics`)
  or check `data/wiki-topcats-page-names.txt` directly.

---

## Acknowledgements

Dataset: Yin et al., *Local Higher-order Graph Clustering*, KDD 2017,
via [SNAP][snap]. We used Claude to brainstorm and refine the project
idea; all implementation, analysis, and write-up are our own work.
