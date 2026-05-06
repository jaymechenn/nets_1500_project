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
- Betweenness centrality: articles that appear inside many sampled shortest
  paths are treated as high-betweenness bridge articles.
- Local clustering coefficient: checks whether an article's neighbors also
  link to each other.
- Neighborhood overlap: measures how similar the neighborhoods are for two
  linked articles.
- Local bridges: edges with zero neighborhood overlap connect parts of the
  graph that do not share neighbors.

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
This is a sampled version of betweenness centrality. Exact betweenness
centrality would require checking shortest paths between all pairs of
articles, which is too much for this intro-level project and a graph this
large. Sampling gives a simpler estimate.

The analyzer also reports local clustering coefficient and neighborhood
overlap. Local clustering is high when an article links to several articles
that also link to each other. Neighborhood overlap is high when two linked
articles point to many of the same other articles. A sampled shortest-path
edge with zero neighborhood overlap is counted as a local bridge.

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

## Small-World Interpretation

If the sampled average path length is small compared with the number of
articles in the graph, that supports the small-world idea. Wikipedia has
millions of pages, but because broad articles link many topics together,
many pages can still be connected by only a few hyperlinks.

## Concepts We Do Not Directly Measure

Strong and weak ties are harder to measure here because the SNAP file only
says whether one article links to another; it does not say how strong that
link is. Strong triadic closure is also more natural in social networks
than in a hyperlink graph, because Wikipedia articles do not have
friendship strengths.

Homophily would require article attributes, such as topic categories,
political labels, country labels, or subject areas. The basic edge list and
title map do not provide those labels, so we do not claim to measure
homophily directly.
