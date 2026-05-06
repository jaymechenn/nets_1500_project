WikiPath
Group members: Kieran, Andreaa, Jayme

WikiPath is a Java project that finds the shortest hyperlink path between two Wikipedia articles. It uses the Stanford SNAP wiki-topcats dataset, where articles are nodes and hyperlinks are directed edges. The program loads the edge list from disk into a HashMap<Integer, ArrayList<Integer>> adjacency list, optionally loads article title mappings, and uses breadth-first search to find the shortest path in number of hyperlinks. It also samples random article pairs to estimate average path length and find common bridge articles.

Categories used: Graphs and graph algorithms, and Information Networks / World Wide Web. Wikipedia is a directed information network, BFS applies because each hyperlink has equal weight, and the adjacency list representation fits a large sparse graph better than an adjacency matrix.

Work breakdown: Kieran handled the file loading and graph representation. Andreaa implemented BFS, discovered sets, parent maps, and path reconstruction. Jayme worked on the menu interface, random-pair analysis, bridge article output, demo data, and documentation. Everyone contributed to testing and the final writeup.

AI usage: We used Claude to brainstorm and refine the original project idea. Near final submission, we used Codex to simplify the implementation and documentation so the project matched introductory NETS 1500 graph algorithms. We reviewed the code and writeup before submission.

Changes from proposal and TA feedback: The final version keeps the proposed WikiRace-style shortest path finder and makes the graph scale and sampling plan explicit. It uses the SNAP Wikipedia graph, supports title lookup when the mapping file is present, includes random-pair analysis for average shortest path length, and prints the top 10 bridge articles from sampled paths.
