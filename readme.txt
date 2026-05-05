Six Degrees of Wikipedia
Group members: Kieran, Andreaa, Jayme

Six Degrees of Wikipedia is a Java project that finds the shortest hyperlink path between two Wikipedia articles. It uses the Stanford SNAP wiki-topcats dataset, a 2011 Wikipedia hyperlink graph with about 1.79 million articles and 28.5 million directed links. The main program supports one-shot shortest-path queries, an interactive REPL for repeated queries after one graph load, and statistics commands that estimate average path length and identify common bridge articles. A small browser demo and demo-data folder are included so the core idea can be shown without downloading the full dataset.

Categories used: Graph and graph algorithms, and Information Networks / World Wide Web. The graph is represented in compressed sparse row form, shortest paths are found with BFS and parent pointers, and the statistics runner studies the small-world structure of the Wikipedia link network.

Work breakdown: Kieran handled the data loader, byte-level parser, compressed graph representation, and title index. Andreaa wrote the BFS shortest-path and reconstruction logic. Jayme prepared the demo data and browser demo, checked the command-line workflow, and helped write the README and user manual. All three members collaborated on the statistics module, command-line interface, testing, and final write-up.

AI usage: We used Claude as a planning and sanity-check tool. It helped us compare the full Wikipedia dump against the SNAP wiki-topcats dataset, reason about memory requirements, and check the sampling methodology for the statistics section, including confidence intervals and bridge-frequency estimates. We did not use Claude to generate the submitted Java code or final empirical results.

Changes from proposal: The final project adds an interactive REPL, a statistics runner for average path length and bridge-article analysis, and a small browser demo in addition to the originally proposed shortest-path finder.
