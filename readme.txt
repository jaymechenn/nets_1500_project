WikiPath
Group members: Kieran, Andreaa, Jayme

WikiPath is a Java application for finding the shortest hyperlink path
between two Wikipedia articles. It treats Wikipedia as a directed graph:
articles are nodes, and hyperlinks are directed edges from one article to
another. This is a programmatic version of the "Six Degrees of Wikipedia"
or WikiRace problem.

Project category: Implementation

Categories used: Graphs and graph algorithms, and Information Networks /
World Wide Web. Wikipedia is a directed information network, BFS applies
because each hyperlink has equal weight, and the adjacency list
representation fits a large sparse graph better than an adjacency matrix.

Work breakdown: Kieran handled the file loading and graph representation.
Andreaa implemented BFS, discovered sets, parent maps, and path
reconstruction. Jayme worked on the menu interface, random-pair analysis,
bridge article output, demo data, and documentation. Everyone contributed
to testing and the final writeup.

AI usage: We used Claude to brainstorm and refine the original project
idea. Near final submission, we used Codex to simplify the implementation
and documentation so the project matched introductory NETS 1500 graph
algorithms. We reviewed the code and writeup before submission.

Changes from proposal and TA feedback: The final version keeps the
proposed WikiRace-style shortest path finder and makes the graph scale and
sampling plan explicit. It uses the SNAP Wikipedia graph, supports title
lookup when the mapping file is present, includes random-pair analysis for
average shortest path length, and prints the top 10 bridge articles from
sampled paths. This bridge score is a simple sampled version of betweenness
centrality: an article scores higher when it appears inside more shortest
paths. We also added class concepts from network structure: local
clustering coefficient, neighborhood overlap, and local bridge edges. We
discuss strong/weak ties and homophily as limitations because the SNAP edge
list has hyperlinks but no tie strengths or article attribute labels.

Dataset

The project uses the Stanford SNAP wiki-topcats dataset:

https://snap.stanford.edu/data/wiki-topcats.html

The full dataset files are:

- wiki-topcats.txt: edge list, one directed edge per line.
- wiki-topcats-page-names.txt: article ID to title mapping.

The graph is large enough to show real network behavior. It has about
1.79 million article IDs and 28.5 million directed hyperlinks. The
repository also includes a small demo-data/ folder for quick testing,
including a disconnected example for the "no path found" case.

Course Concepts

This project uses introductory graph and information network concepts:

- Directed graphs: a hyperlink from article A to article B is not the
  same as a hyperlink from B to A.
- Nodes and edges: articles are nodes, hyperlinks are edges.
- Adjacency lists: the graph is stored as
  HashMap<Integer, ArrayList<Integer>>.
- BFS: shortest paths are found with breadth-first search.
- Queues: BFS uses Queue<Integer> to explore by levels.
- Discovered sets: HashSet<Integer> prevents repeated work and cycles.
- Parent maps: HashMap<Integer, Integer> reconstructs the final path.
- Information networks: Wikipedia is a web graph.
- Small-world structure: random-pair analysis checks whether article
  pairs are often connected by only a few links.
- Betweenness centrality: the analyzer uses a sampled version by counting
  how often articles appear inside shortest paths.
- Local clustering coefficient: the analyzer estimates whether an
  article's neighbors also link to one another.
- Neighborhood overlap: the analyzer measures how much two linked
  articles' outgoing neighborhoods overlap.
- Local bridges: a shortest-path edge with zero neighborhood overlap is
  counted as a local bridge edge.

How BFS Finds The Shortest Path

Every hyperlink counts as one hop, so this is an unweighted graph. BFS is
the right algorithm because it explores all articles one hop away, then all
articles two hops away, then all articles three hops away, and so on. The
first time BFS reaches the target article, it has found the minimum number
of hyperlinks needed to get there.

The implementation uses a queue, a discovered set, and a parent map. The
parent map remembers which article first discovered each article, so once
the target is found, the program can walk backward from target to source
and then reverse the list.

Files

- src/main/java/wikipath/Main.java: menu and user input.
- src/main/java/wikipath/WikiGraph.java: adjacency list and file loading.
- src/main/java/wikipath/BFSPathFinder.java: BFS shortest path.
- src/main/java/wikipath/WikiAnalyzer.java: random-pair analysis, sampled
  betweenness, and bridge counts.
- demo-data/edges.txt: small test graph.
- demo-data/names.txt: names for the small test graph.
- scripts/download-data.sh: downloads the full SNAP dataset.

Compile

```bash
mkdir -p target/classes
javac --release 17 -d target/classes src/main/java/wikipath/*.java
```

Run With Demo Data

```bash
java -cp target/classes wikipath.Main demo-data/edges.txt demo-data/names.txt
```

Then choose from the menu:

```text
WikiPath Menu
1. Find shortest path between two articles
2. Run random pair analysis
3. Print top bridge articles
4. Exit
Choose an option:
```

Run With Full SNAP Data

Download the data once:

```bash
bash scripts/download-data.sh
```

Then run:

```bash
java -Xmx4g -cp target/classes wikipath.Main data/wiki-topcats.txt data/wiki-topcats-page-names.txt
```

The full graph is large. If Java runs out of memory, use a larger heap,
for example -Xmx6g.

Sample Outputs

Successful path:

```text
Source article: Kanye_West
Target article: Quantum_mechanics
Shortest path:
Kanye_West -> Hip_hop_music -> Music -> Physics -> Quantum_mechanics
Number of hops: 4
Time to compute: 1 ms
```

Invalid article:

```text
Invalid source article: Not A Real Page
```

No path found:

```text
Source article: Isolated_target
Target article: Kanye_West
No hyperlink path was found.
Time to compute: 2 ms
```

Average path length analysis:

```text
Random pair analysis complete.
Trials: 100
Reachable pairs: 96
Average shortest path length: 2.81 hops
Time to compute: 21 ms
```

Top bridge articles / sampled betweenness:

```text
Top 10 bridge articles / sampled betweenness scores:
1. United_States appeared inside 18 sampled shortest paths
2. World_War_II appeared inside 10 sampled shortest paths
3. Mathematics appeared inside 7 sampled shortest paths
```

Extra network measures:

```text
Extra network measures:
Average local clustering coefficient: 0.100
Average neighborhood overlap on path edges: 0.007
Local bridge edges on sampled shortest paths: 44 out of 45
```

Final Writeup

WikiPath connects directly to NETS 1500 because Wikipedia is an
information network and a directed web graph. The project stores the graph
as an adjacency list, which is appropriate for a huge sparse network. It
uses BFS, an introductory graph algorithm, to find shortest paths in an
unweighted graph. The queue controls the level-by-level search, the
discovered set prevents cycles and repeated work, and the parent map
reconstructs the actual path.

The analysis part samples many random source and target articles. For each
pair, it runs BFS, records the path length if the target is reachable, and
counts intermediate articles as bridge nodes. This is a sampled version of
betweenness centrality: exact betweenness would require considering every
source-target pair, but sampling random pairs gives an understandable
intro-level estimate of which articles lie between many others.

It also computes simple network measures from class. Local clustering
coefficient asks whether an article's outgoing neighbors also link to one
another. Neighborhood overlap asks how similar the outgoing neighborhoods
are for two linked articles. An edge with zero neighborhood overlap is
treated as a local bridge because it connects two parts of the graph whose
neighborhoods do not otherwise overlap.

If many random pairs have short paths, that supports the small-world idea:
even a very large information network can have surprisingly small distances
between nodes. Homophily and strong/weak ties are useful class ideas, but
this dataset only gives hyperlink edges. It does not label article topics
or give tie strengths, so the project discusses those ideas as limitations
rather than pretending to measure them directly.
