WikiPath
Group members: Kieran Chetty, Andreea Rusu, Jayme Chen

WikiPath is a Java application that finds the shortest hyperlink path
between any two Wikipedia articles, a programmatic take on the "Six
Degrees of Wikipedia" problem. The application loads a pre-built
Wikipedia hyperlink graph from the Stanford SNAP dataset (wiki-topcats),
constructs an adjacency list representation, and uses BFS to find the
minimum-hop path between a source and target article. The adjacency list
representation fits a large sparse graph better than an adjacency matrix.
The implementation uses a queue, a discovered set, and a parent map. The
parent map remembers which article first discovered each article, so once
the target is found, the program can walk backward from target to source
and then reverse the list. Beyond simple path finding, WikiPath also runs
network analysis by sampling random article pairs to compute statistics
such as average path length, local clustering coefficients, neighborhood
overlap, and betweenness style bridge scores for the most connected hub
articles. The project includes both a terminal menu interface and an
optional local web server that serves a browser based UI for querying
the graph interactively.

Dataset:
The wiki-topcats dataset from Stanford SNAP contains approximately 1.79
million nodes (articles) and 28.5 million directed edges (hyperlinks).
Loading and storing a graph this size requires several implementation decisions.
The adjacency list is stored as a HashMap<Integer, ArrayList<Integer>>
mapping integer article IDs to neighbor lists, which avoids the overhead
of string keys across 28 million entries. Title mappings are kept in a
separate pair of HashMaps (title → ID and ID → title) and loaded optionally.
So, if the title file is missing, the application still functions using raw
integer IDs. The sorted prefix index for autocomplete is built lazily on
first use so it does not add to startup time.

Project category: Implementation

Categories used: Graphs and graph algorithms, Social Networks, and
Information Networks (World Wide Web).

1. Graph and Graph Algorithms
The Wikipedia hyperlink graph is modeled as a large directed graph where
nodes are articles and directed edges are hyperlinks. BFS is the core
algorithm used for shortest path finding, since every hyperlink counts
as one unweighted hop and BFS guarantees the fewest hop path by exploring
level by level. A parent map is maintained during traversal to reconstruct
the full path. The analyzer also computes a sampled version of betweenness
centrality by tracking which interior nodes appear most frequently across 
many BFS runs, identifying hub "bridge" articles. Local clustering
coefficients and neighborhood overlap are computed per the definitions from class.

2. Social Networks
The analyzer computes several metrics that we learned during the Social
Networks unit. Local clustering coefficients measure the probability that
two neighbors of a given article also link to each other. A high coefficient
suggests a tightly knit topic cluster, often as a result of triadic closure.
Neighborhood overlap is computed for each consecutive edge on a sampled
shortest path. Edges with zero overlap are local bridges, connecting parts
of the graph whose neighborhoods don't otherwise intersect. The bridge
scoring (sampled betweenness centrality) identifies hub articles that act
as important connectors across many shortest paths, analogous to nodes
with high betweenness. The SNAP edge list encodes only hyperlinks with
no tie-strength labels or article attribute metadata, so concepts like
strong/weak ties and homophily do not directly apply. The dataset has
no way to distinguish a strong link from a weak one, and articles carry
no attributes to test for homophily.

3. Information Networks (World Wide Web)
Wikipedia is a real world instance of the World Wide Web that we studied
in the Information Networks unit. Articles are resources identified by
URLs, hyperlinks are HTTP-navigable connections between them, and the
resulting network of pages has a giant strongly connected component,
short average path lengths, and a small number of extremely high degree
hub pages that dominate connectivity. WikiRace, the game of navigating
from one Wikipedia article to another by clicking hyperlinks as fast
as possible, is basically a human exploration of this structure. Our
project automates that game with BFS, which always finds the optimal
route. The fact that most random article pairs are reachable in just
4–5 hops (even across a graph of 1.79 million articles) is a direct
 demonstration of the small-world property of web graphs. The hub articles
that appear repeatedly in our bridge rankings (pages like "United States"
or "United Kingdom") reflect the uneven degree distribution characteristic
of real web graphs, where a small number of pages attract a disproportionate 
hare of incoming links.

Random Pair Analysis
The random pair analysis works by uniformly sampling source and target
article IDs at random from the full set of articles in the graph, then
running BFS on each pair. If BFS finds a path, the hop count is recorded,
all interior nodes are credited one point toward their bridge score, and
each consecutive edge on the path is evaluated for neighborhood overlap.
If BFS finds no path (the target is unreachable from the source in the
directed graph), the pair is counted as unreachable and excluded from
the path-length average.
The number of trials is configurable. In practice, 100–500 trials is
enough to get a stable average path length and a reasonable bridge
ranking, since the wiki-topcats graph is highly connected and most
random pairs are reachable. Running 1,000+ trials produces more stable
bridge scores at the cost of a few extra seconds of compute.

Changes from proposal and TA feedback:

The core idea from the proposal, a WikiRace-style shortest path finder
over the SNAP Wikipedia graph, is unchanged. This is what we added,
clarified, or expanded in response to TA feedback and through
implementation:
  - Scale and sampling made explicit: The final version documents the
    dataset size (1.79M nodes, 28.5M edges) and explains the adjacency
    list design choices that follow from it. The random pair analysis
    uses uniform random sampling with a configurable trial count.
    100–500 trials produces stable average path lengths and 1,000+
    trials produces more reliable bridge rankings.
  - Bridge scoring formalized: The proposal mentioned identifying
    "common bridge articles" informally. The final implementation uses
    a sampled betweenness centrality score: each interior node on a
    found shortest path receives one point, and articles are ranked
    by total score across all trials. This is a sampled approximation,
    not true global betweenness.
  - Additional network metrics added: Beyond path length and bridge scores,
    the final version computes local clustering coefficients, neighborhood
    overlap for each edge on sampled paths, and a count of local bridge
    edges (those with zero neighborhood overlap).
  - Social Networks added as a course category: The proposal listed Graphs
    and Graph Algorithms and Information Networks. Through implementation
    we realized the analysis maps closely onto Social Networks concepts
    from class (clustering coefficients, neighborhood overlap, local
    bridges, and the connection to strong/weak tiea) so we added that as
    a third category.
  - Web UI added. The proposal described a terminal application. The
    final version also includes an optional local web server and browser
    based UI for interactive querying, path visualization, and analysis results.
  - Limitations noted. Because the SNAP edge list contains only hyperlinks
    with no tie-strength labels or article attribute metadata, we cannot
    directly measure homophily or classify ties as strong vs. weak. We
    discuss these as theoretical connections to the course material
    rather than implemented features.

Work breakdown: Kieran handled the file loading and graph representation.
Andreaa implemented BFS, discovered sets, parent maps, and path
reconstruction. Jayme worked on the menu interface, random-pair analysis,
bridge article output, demo data, and documentation. Everyone contributed
to testing and the final writeup.

Jayme built the graph engine (WikiGraph.java), including edge list
and title file parsing, adjacency list construction, the prefix search
autocomplete index, and the article lookup utilities.
Andreea implemented BFS path finding (BFSPathFinder.java) and path
reconstruction, and wrote the network analysis logic in WikiAnalyzer.java
(bridge counting, neighborhood overlap, local clustering coefficient,
and summary reporting).
Kieran built the web server (WebServer.java) and the browser-based UI
(the web/ directory), and integrated everything in Main.java including
CLI argument parsing and the terminal menu.


AI usage: We used Claude in two ways during this project. For the
browser-based UI, we used Claude to help generate and refine the HTML,
CSS, and JavaScript, as web front-end development was not covered in
this course. For brainstorming and scoping, we used Claude early on to
help refine the project proposal.


Course Concepts
- Directed graphs: a hyperlink from article A to article B is not the
  same as a hyperlink from B to A.
- Nodes and edges: articles are nodes, hyperlinks are edges.
- Adjacency lists: the graph is stored as
  HashMap<Integer, ArrayList<Integer>>.
- BFS: shortest paths are found with breadth first search.
- Queues: BFS uses Queue<Integer> to explore by levels.
- Discovered sets: HashSet<Integer> prevents repeated work and cycles.
- Parent maps: HashMap<Integer, Integer> reconstructs the final path.
- Information networks: Wikipedia is a web graph.
- Small-world structure: random pair analysis checks whether article
  pairs are often connected by only a few links.
- Betweenness centrality: the analyzer uses a sampled version by counting
  how often articles appear inside shortest paths.
- Local clustering coefficient: the analyzer estimates whether an
  article's neighbors also link to one another.
- Neighborhood overlap: the analyzer measures how much two linked
  articles' outgoing neighborhoods overlap.
- Local bridges: a shortest path edge with zero neighborhood overlap is
  counted as a local bridge edge.

Files

- src/main/java/wikipath/Main.java: menu and user input.
- src/main/java/wikipath/WikiGraph.java: adjacency list and file loading.
- src/main/java/wikipath/BFSPathFinder.java: BFS shortest path.
- src/main/java/wikipath/WikiAnalyzer.java: random pair analysis, sampled
  betweenness, and bridge counts.
- demo-data/edges.txt: small test graph.
- demo-data/names.txt: names for the small test graph.
- scripts/download-data.sh: downloads the full SNAP dataset.
