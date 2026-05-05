# Six Degrees of Wikipedia User Manual

## Requirements

- Java 17 or newer.
- Maven is optional. The repository already includes compiled classes in `target/classes` on this machine, but a grader can rebuild from source with either Maven or `javac`.
- For the full graph, use at least a 2 GB Java heap with `-Xmx2g`.

## Building

Without Maven:

```bash
mkdir -p target/classes
javac --release 17 -d target/classes src/main/java/wikipath/*.java
```

With Maven:

```bash
mvn package
```

## Dataset

The full dataset is Stanford SNAP's wiki-topcats graph:
https://snap.stanford.edu/data/wiki-topcats.html

To download it into `data/`, run:

```bash
bash scripts/download-data.sh
```

This downloads:

- `data/wiki-topcats.txt`
- `data/wiki-topcats-page-names.txt`

The repository also includes a small 16-article demo dataset in `demo-data/` for quick testing.

If the final submission system allows large files, include the downloaded `data/` files with the submission because the assignment asks for the dataset as well as the source link. If the upload limit prevents that, the download script and SNAP link document the exact dataset source.

## Running a Shortest-Path Query

Demo data:

```bash
java -cp target/classes wikipath.Main path --edges demo-data/edges.txt --names demo-data/names.txt "Kanye West" "Quantum mechanics"
```

Full dataset:

```bash
java -Xmx2g -cp target/classes wikipath.Main path "Kanye West" "Quantum mechanics"
```

Screenshot of expected demo output:

```text
[load] reading page names from demo-data/names.txt
[load]   16 titles loaded in 0.01s
[load] pass 1: counting out-degrees in demo-data/edges.txt
[load]   16 nodes, 32 edges in 0.01s
[load] pass 2: filling adjacency arrays
[load]   adjacency built in 0.00s
[load] indexed 16 titles
[load] done in 0.13s

Shortest path: 4 hops, 15 nodes visited, 0.000s
  Kanye_West
  -> Hip_hop_music
  -> Music
  -> Physics
  -> Quantum_mechanics
```

## Interactive Mode

Interactive mode loads the graph once and then answers many queries:

```bash
java -cp target/classes wikipath.Main repl --edges demo-data/edges.txt --names demo-data/names.txt
```

Then type one query per line:

```text
Kanye West | Quantum mechanics
Philadelphia | Mongolia
quit
```

## Statistics Commands

Use these after downloading the full dataset:

```bash
java -Xmx2g -cp target/classes wikipath.Main stats --preset quick
java -Xmx2g -cp target/classes wikipath.Main avgpath --preset default
java -Xmx2g -cp target/classes wikipath.Main bridges --preset rigorous --top 25
```

The available presets are:

- `quick`: 50 sources by 50 targets.
- `default`: 200 sources by 200 targets.
- `rigorous`: 500 sources by 500 targets.

You can also override the sampling size directly:

```bash
java -Xmx2g -cp target/classes wikipath.Main stats --sources 100 --targets 100 --top 20 --seed 42
```

## Browser Demo

Open `web/index.html` in a browser. It runs the same shortest-path idea on the small `web/graph.json` graph and is intended only as a visual demonstration; the graded implementation is the Java CLI.

## Troubleshooting

If the program says an edges or names file is missing, run `bash scripts/download-data.sh` for the full dataset or pass the demo paths with `--edges demo-data/edges.txt --names demo-data/names.txt`.

If Java runs out of memory on the full dataset, make sure the command includes `-Xmx2g`.

If `mvn` is not found, use the `javac` build command above or use the already compiled classes with `java -cp target/classes wikipath.Main ...`.
