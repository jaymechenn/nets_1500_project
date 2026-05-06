# WikiPath Final Writeup

## Project Summary

WikiPath is a Java application that finds the shortest hyperlink path
between two Wikipedia articles. It uses a real Wikipedia hyperlink graph
from Stanford SNAP. Each article is a node, and each hyperlink is a
directed edge.

The main feature is a shortest-path search. A user enters a source article
and a target article, and the program prints the shortest chain of
hyperlinks between them if one exists.

## NETS 1500 Concepts

This project uses several topics from class:

- Graphs: Wikipedia is represented as a graph.
- Directed graphs: hyperlinks have direction.
- Nodes and edges: articles are nodes, hyperlinks are edges.
- Adjacency lists: the graph is stored as
  `HashMap<Integer, ArrayList<Integer>>`.
- BFS: breadth-first search finds shortest paths in an unweighted graph.
- Queues: BFS uses a queue to explore articles by distance from the source.
- Discovered sets: a `HashSet<Integer>` prevents revisiting articles.
- Parent maps: a `HashMap<Integer, Integer>` reconstructs the final path.
- Information networks: Wikipedia is an example of a web information network.
- Small-world structure: random samples can show whether paths tend to be
  short even in a very large graph.

## Why BFS Is Correct

Every hyperlink counts as one hop. Since all edges have the same weight,
BFS is the correct shortest-path algorithm. BFS first checks all articles
one hyperlink away from the source, then all articles two hyperlinks away,
and so on. Therefore, the first time BFS discovers the target article, the
path it found has the minimum possible number of hyperlinks.

The program does not use Dijkstra's algorithm because there are no edge
weights. It does not use an adjacency matrix because Wikipedia is huge and
sparse, so a matrix would waste memory.

## Analysis Features

After shortest-path search works, the program can sample random article
pairs. For each pair, it runs BFS. If the target is reachable, the program:

1. Adds the path length to the average.
2. Counts each intermediate article as a bridge article.
3. Later prints the top 10 bridge articles.

Bridge articles are articles that often appear in the middle of shortest
paths. In a real Wikipedia graph, these tend to be broad, central topics
such as countries, time periods, sciences, or other common reference pages.

## Sample Results Format

Successful path:

```text
Source article: Kanye_West
Target article: Quantum_mechanics
Shortest path:
Kanye_West -> Hip_hop_music -> Music -> Physics -> Quantum_mechanics
Number of hops: 4
Time to compute: 1 ms
```

No path found:

```text
Source article: Isolated_target
Target article: Kanye_West
No hyperlink path was found.
Time to compute: 2 ms
```

Random-pair analysis:

```text
Random pair analysis complete.
Trials: 100
Reachable pairs: 96
Average shortest path length: 4.83 hops
Time to compute: 1250 ms
```

Top bridge articles:

```text
Top 10 bridge articles:
1. United_States appeared in 18 shortest paths
2. World_War_II appeared in 10 shortest paths
3. Mathematics appeared in 7 shortest paths
```

## Small-World Interpretation

If the sampled average path length is small compared with the number of
articles in the graph, that supports the small-world idea. Wikipedia has
millions of pages, but because broad articles link many topics together,
many pages can still be connected by only a few hyperlinks.
