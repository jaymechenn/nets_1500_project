# WikiPath User Manual

## Requirements

- Java 17 or newer.
- No external Java libraries are required.
- The full SNAP graph is large, so use a larger heap such as `-Xmx4g`.

## Build

```bash
mkdir -p target/classes
javac --release 17 -d target/classes src/main/java/wikipath/*.java
```

## Data Files

The project expects:

- An edge list file where each line is `sourceID targetID`.
- An optional title file where each line is `articleID title`.

The full dataset comes from Stanford SNAP:
https://snap.stanford.edu/data/wiki-topcats.html

Download it with:

```bash
bash scripts/download-data.sh
```

For quick testing, use the included small files:

- `demo-data/edges.txt`
- `demo-data/names.txt`

The demo graph includes a normal connected portion and a small disconnected
example for testing "no path found" output.

## Run

Demo data:

```bash
java -cp target/classes wikipath.Main demo-data/edges.txt demo-data/names.txt
```

Full data:

```bash
java -Xmx4g -cp target/classes wikipath.Main data/wiki-topcats.txt data/wiki-topcats-page-names.txt
```

If no command-line arguments are given, the program tries to load:

- `data/wiki-topcats.txt`
- `data/wiki-topcats-page-names.txt`

## Menu Options

```text
WikiPath Menu
1. Find shortest path between two articles
2. Run random pair analysis
3. Print top bridge articles
4. Exit
Choose an option:
```

### Option 1: Shortest Path

Enter either article titles or numeric IDs. Titles are case-insensitive,
and spaces are treated like underscores.

Example input:

```text
Source article title or ID: Kanye West
Target article title or ID: Quantum mechanics
```

Example output:

```text
Source article: Kanye_West
Target article: Quantum_mechanics
Shortest path:
Kanye_West -> Hip_hop_music -> Music -> Physics -> Quantum_mechanics
Number of hops: 4
Time to compute: 1 ms
```

Invalid title output:

```text
Invalid source article: Not A Real Page
```

No path output:

```text
Source article: Isolated_target
Target article: Kanye_West
No hyperlink path was found.
Time to compute: 2 ms
```

### Option 2: Random Pair Analysis

Enter how many random pairs to sample. The program runs BFS for each pair,
computes the average shortest path length among reachable pairs, and counts
bridge articles.

Example:

```text
How many random pairs should be sampled? 100

Random pair analysis complete.
Trials: 100
Reachable pairs: 96
Average shortest path length: 4.83 hops
Time to compute: 1250 ms

Small-world interpretation:
The sampled paths are short, which supports the idea that Wikipedia behaves like a small-world information network.
```

### Option 3: Top Bridge Articles

This prints the articles that appeared most often as intermediate nodes in
the shortest paths from the most recent analysis run.

Example:

```text
Top 10 bridge articles:
1. United_States appeared in 18 shortest paths
2. World_War_II appeared in 10 shortest paths
3. Mathematics appeared in 7 shortest paths
```

If analysis has not been run yet:

```text
No bridge data yet. Run random pair analysis first.
```

## Troubleshooting

If the graph file cannot be loaded, check that the path is correct.

If the title file cannot be loaded, the program can still search by numeric
article IDs.

If the full graph runs out of memory, rerun Java with a larger heap, such as
`-Xmx6g`.
