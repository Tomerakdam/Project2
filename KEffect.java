import org.jgrapht.*;
import org.jgrapht.graph.*;
import org.jgrapht.alg.shortestpath.*;

import java.util.*;
import java.io.*;
import java.awt.Desktop;
import java.net.URI;

/**
 * Effect of increasing k on the Greedy Spanner (Althöfer et al., 1993).
 *
 * Uses JGraphT for graph representation and shortest-path computation.
 *
 * Graph G : K10 (complete graph, 10 vertices, 45 edges)
 *           Random weights in [1, 20], seed = 42
 *
 * k tested: 1, 2, 3, 4, 5
 *
 * For each k we observe:
 *   - Stretch  r = 2k-1        (allowed distance blow-up)
 *   - |E_H|                    (edges kept in spanner)
 *   - Edge reduction %
 *   - Theoretical bound  n^(1+1/k)
 *   - Max stretch ratio  max over pairs of  dist_H / dist_G
 *   - Whether the (2k-1)-spanner property holds
 *
 * Expected trend:  larger k  →  fewer edges, more stretch allowed.
 */
public class KEffect {

    static final int  N    = 10;
    static final int  KMIN = 1;
    static final int  KMAX = 5;
    static final long SEED = 42L;

    // -------------------------------------------------------------------------
    // Graph construction using JGraphT
    // -------------------------------------------------------------------------

    /** Builds K_n with random integer weights in [1, maxW] using JGraphT. */
    static Graph<Integer, DefaultWeightedEdge> buildKn(int n, int maxW, Random rng) {
        Graph<Integer, DefaultWeightedEdge> g =
                new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        for (int i = 1; i <= n; i++) g.addVertex(i);
        for (int i = 1; i <= n; i++)
            for (int j = i + 1; j <= n; j++) {
                DefaultWeightedEdge e = g.addEdge(i, j);
                g.setEdgeWeight(e, rng.nextInt(maxW) + 1);
            }
        return g;
    }

    // -------------------------------------------------------------------------
    // Greedy Spanner using JGraphT API
    // -------------------------------------------------------------------------

    /**
     * Greedy (2k-1)-spanner of g, built using JGraphT's DijkstraShortestPath.
     *
     * Algorithm:
     *   Sort edges by weight (non-decreasing).
     *   For each edge (u,v,w): query dist_H(u,v) via Dijkstra on current H.
     *   Add edge iff dist_H(u,v) > (2k-1) * w.
     */
    static Graph<Integer, DefaultWeightedEdge> greedySpanner(
            Graph<Integer, DefaultWeightedEdge> g, int k) {

        int r = 2 * k - 1;

        // Sort G's edges by weight
        List<DefaultWeightedEdge> edges = new ArrayList<>(g.edgeSet());
        edges.sort(Comparator.comparingDouble(g::getEdgeWeight));

        // H starts empty, same vertex set as G
        Graph<Integer, DefaultWeightedEdge> h =
                new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        for (int v : g.vertexSet()) h.addVertex(v);

        for (DefaultWeightedEdge e : edges) {
            int    u = g.getEdgeSource(e);
            int    v = g.getEdgeTarget(e);
            double w = g.getEdgeWeight(e);

            // JGraphT Dijkstra query on the current (growing) H
            GraphPath<Integer, DefaultWeightedEdge> path =
                    DijkstraShortestPath.findPathBetween(h, u, v);
            double distH = (path == null) ? Double.MAX_VALUE : path.getWeight();

            if (distH > r * w) {
                DefaultWeightedEdge he = h.addEdge(u, v);
                h.setEdgeWeight(he, w);
            }
        }
        return h;
    }

    // -------------------------------------------------------------------------
    // All-pairs shortest paths using JGraphT FloydWarshallShortestPaths
    // -------------------------------------------------------------------------

    /**
     * Returns a 2-D distance matrix indexed by position in 'vertices'.
     * Uses JGraphT's Floyd-Warshall for all-pairs computation.
     */
    static double[][] allPairsSP(
            Graph<Integer, DefaultWeightedEdge> graph, List<Integer> vertices) {

        FloydWarshallShortestPaths<Integer, DefaultWeightedEdge> fw =
                new FloydWarshallShortestPaths<>(graph);

        int n = vertices.size();
        double[][] dist = new double[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                GraphPath<Integer, DefaultWeightedEdge> p =
                        fw.getPath(vertices.get(i), vertices.get(j));
                dist[i][j] = (p == null) ? Double.MAX_VALUE / 2 : p.getWeight();
            }
        return dist;
    }

    // -------------------------------------------------------------------------
    // Analysis helpers
    // -------------------------------------------------------------------------

    /** Returns {violations, maxRatio, worstI, worstJ} */
    static double[] analyze(double[][] gDist, double[][] hDist, int k) {
        int    r          = 2 * k - 1;
        int    violations = 0;
        double maxRatio   = 0;
        int    wi = -1, wj = -1;
        int    n  = gDist.length;

        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++) {
                if (gDist[i][j] > 1e9) continue;
                double ratio = hDist[i][j] / gDist[i][j];
                if (ratio > maxRatio) { maxRatio = ratio; wi = i; wj = j; }
                if (hDist[i][j] > r * gDist[i][j] + 1e-9) violations++;
            }
        return new double[]{violations, maxRatio, wi, wj};
    }

    /** Weighted diameter: maximum finite dist over all pairs. */
    static double diameter(double[][] dist) {
        double d = 0;
        for (double[] row : dist)
            for (double v : row)
                if (v < 1e9) d = Math.max(d, v);
        return d;
    }

    // -------------------------------------------------------------------------
    // Printing
    // -------------------------------------------------------------------------

    static void printEdgeWeights(Graph<Integer, DefaultWeightedEdge> g) {
        List<DefaultWeightedEdge> edges = new ArrayList<>(g.edgeSet());
        edges.sort(Comparator.comparingDouble(g::getEdgeWeight));
        for (DefaultWeightedEdge e : edges)
            System.out.printf("    (v%2d, v%2d)  w = %2.0f%n",
                    g.getEdgeSource(e), g.getEdgeTarget(e), g.getEdgeWeight(e));
    }

    static void printDistTable(String title, double[][] dist, List<Integer> verts) {
        System.out.println(title);
        System.out.print("        ");
        for (int v : verts) System.out.printf(" v%2d", v);
        System.out.println();
        System.out.print("        ");
        System.out.println("-----".repeat(verts.size()));
        for (int i = 0; i < verts.size(); i++) {
            System.out.printf("  v%2d |", verts.get(i));
            for (int j = 0; j < verts.size(); j++) {
                if (dist[i][j] > 1e9) System.out.printf("    ∞");
                else                   System.out.printf(" %4.0f", dist[i][j]);
            }
            System.out.println();
        }
        System.out.println();
    }

    // -------------------------------------------------------------------------
    // DOT export  (GraphViz format)
    // -------------------------------------------------------------------------

    /**
     * Writes a GraphViz DOT file for the given graph.
     * Edges are labelled with their weight.
     * The label parameter appears as the graph title.
     */
    static void exportDot(Graph<Integer, DefaultWeightedEdge> graph,
                          String label, String filename) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("graph \"").append(label).append("\" {\n");
        sb.append("  layout=neato;\n");
        sb.append("  overlap=false;\n");
        sb.append("  node [shape=circle, style=filled, fillcolor=lightblue, fontsize=12];\n");
        sb.append("  edge [fontsize=10];\n\n");

        // vertices
        for (int v : new TreeSet<>(graph.vertexSet()))
            sb.append("  ").append(v).append(";\n");
        sb.append("\n");

        // edges sorted by weight
        List<DefaultWeightedEdge> edges = new ArrayList<>(graph.edgeSet());
        edges.sort(Comparator.comparingDouble(graph::getEdgeWeight));
        for (DefaultWeightedEdge e : edges) {
            int    u = graph.getEdgeSource(e);
            int    v = graph.getEdgeTarget(e);
            double w = graph.getEdgeWeight(e);
            sb.append(String.format("  %d -- %d [label=\"%.0f\", len=%.1f];\n",
                    u, v, w, 1.0 + w * 0.15));
        }
        sb.append("}\n");

        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            pw.print(sb);
        }
        System.out.println("  DOT file saved: " + filename);
    }

    /** Opens the GraphViz online editor in the default browser. */
    static void openGraphvizOnline() {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(
                    new URI("https://dreampuf.github.io/GraphvizOnline/"));
                System.out.println("  Browser opened: https://dreampuf.github.io/GraphvizOnline/");
                System.out.println("  Paste the content of any .dot file there to see the graph.");
            }
        } catch (Exception e) {
            System.out.println("  Could not open browser automatically.");
            System.out.println("  Go to: https://dreampuf.github.io/GraphvizOnline/");
        }
    }

    // -------------------------------------------------------------------------
    // Main
    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        Random rng = new Random(SEED);

        System.out.println("============================================================");
        System.out.println("  Effect of Increasing k  --  Greedy Spanner on K10");
        System.out.println("  JGraphT: SimpleWeightedGraph + DijkstraShortestPath");
        System.out.println("           + FloydWarshallShortestPaths");
        System.out.println("============================================================");
        System.out.printf("  Graph  : K%d (complete), %d edges%n", N, N*(N-1)/2);
        System.out.printf("  Weights: random integers in [1, 20], seed=%d%n", SEED);
        System.out.printf("  k range: %d to %d%n%n", KMIN, KMAX);

        // Build G
        Graph<Integer, DefaultWeightedEdge> g = buildKn(N, 20, rng);
        List<Integer> vertices = new ArrayList<>(new TreeSet<>(g.vertexSet()));

        System.out.println("------------------------------------------------------------");
        System.out.println("  Input graph G (K10) edge weights:");
        System.out.println("------------------------------------------------------------");
        printEdgeWeights(g);
        System.out.println();

        // All-pairs SP in G
        double[][] gDist = allPairsSP(g, vertices);
        printDistTable("[TABLE G] Original distances  dist_G", gDist, vertices);
        System.out.printf("  Diameter of G = %.0f%n%n", diameter(gDist));

        // ── Per-k analysis ────────────────────────────────────────────────────
        // Store results for summary table
        int[]    edgeCounts = new int[KMAX + 1];
        double[] maxRatios  = new double[KMAX + 1];
        int[]    violations = new int[KMAX + 1];
        double[] diameters  = new double[KMAX + 1];

        for (int k = KMIN; k <= KMAX; k++) {
            int r = 2 * k - 1;

            System.out.println("============================================================");
            System.out.printf("  k = %d  →  r = %d  (%d-spanner)%n", k, r, r);
            System.out.println("============================================================");

            // Run greedy spanner
            Graph<Integer, DefaultWeightedEdge> h = greedySpanner(g, k);
            int eH = h.edgeSet().size();
            int eG = g.edgeSet().size();
            double bound = Math.pow(N, 1.0 + 1.0 / k);

            System.out.printf("  |E_G| = %d   |E_H| = %d   reduction = %.1f%%%n",
                    eG, eH, 100.0 * (eG - eH) / eG);
            System.out.printf("  n^(1+1/k) = %d^%.4f = %.4f  -->  %d <= %.4f  %s%n",
                    N, 1.0+1.0/k, bound, eH, bound,
                    eH <= bound ? "PASS" : "FAIL");
            System.out.println();

            // Edges kept in H
            System.out.println("  Spanner H edges:");
            List<DefaultWeightedEdge> hEdges = new ArrayList<>(h.edgeSet());
            hEdges.sort(Comparator.comparingDouble(h::getEdgeWeight));
            for (DefaultWeightedEdge e : hEdges)
                System.out.printf("    (v%2d, v%2d)  w = %2.0f%n",
                        h.getEdgeSource(e), h.getEdgeTarget(e), h.getEdgeWeight(e));
            System.out.println();

            // All-pairs SP in H
            double[][] hDist = allPairsSP(h, vertices);
            printDistTable("[TABLE H_k] Spanner distances  dist_H", hDist, vertices);

            // Stretch analysis
            double[] res = analyze(gDist, hDist, k);
            int    viol     = (int) res[0];
            double maxRatio = res[1];
            int    wi       = (int) res[2], wj = (int) res[3];
            double diam     = diameter(hDist);

            System.out.printf("  Max stretch ratio : %.6f  (pair v%d, v%d)%n",
                    maxRatio, vertices.get(wi), vertices.get(wj));
            System.out.printf("  Stretch limit     : %d.000000%n", r);
            System.out.printf("  Violations        : %d%n", viol);
            System.out.printf("  Diameter of H     : %.0f  (G diameter: %.0f)%n",
                    diam, diameter(gDist));
            System.out.printf("  Spanner property  : %s%n%n",
                    viol == 0 ? "PASS" : "FAIL");

            // Store for summary
            edgeCounts[k] = eH;
            maxRatios[k]  = maxRatio;
            violations[k] = viol;
            diameters[k]  = diam;
        }

        // ── Summary comparison table ──────────────────────────────────────────
        System.out.println("============================================================");
        System.out.println("  SUMMARY: Effect of k on the Spanner");
        System.out.println("============================================================");
        System.out.printf("  %-4s  %-6s  %-6s  %-8s  %-12s  %-12s  %-10s  %-10s%n",
                "k", "r=2k-1", "|E_H|", "Reduc%",
                "n^(1+1/k)", "Bound check", "MaxRatio", "Property");
        System.out.println("  " + "-".repeat(80));
        for (int k = KMIN; k <= KMAX; k++) {
            int    r     = 2 * k - 1;
            int    eH    = edgeCounts[k];
            double bound = Math.pow(N, 1.0 + 1.0 / k);
            System.out.printf("  %-4d  %-6d  %-6d  %-8.1f  %-12.4f  %-12s  %-10.4f  %-10s%n",
                    k, r, eH,
                    100.0 * (N*(N-1)/2 - eH) / (N*(N-1)/2),
                    bound,
                    eH <= bound ? "PASS" : "FAIL",
                    maxRatios[k],
                    violations[k] == 0 ? "PASS" : "FAIL");
        }
        System.out.println("  " + "-".repeat(80));
        System.out.println();
        System.out.println("  Observation: as k increases,");
        System.out.println("    |E_H| decreases (sparser spanner),");
        System.out.println("    r = 2k-1 increases (more stretch allowed),");
        System.out.println("    max stretch ratio grows (but stays <= r).");

        // ── DOT export for visual inspection ─────────────────────────────────
        System.out.println();
        System.out.println("============================================================");
        System.out.println("  Exporting DOT files for GraphViz visualization");
        System.out.println("============================================================");
        try {
            rng = new Random(SEED);  // reset seed so graph is identical
            Graph<Integer, DefaultWeightedEdge> gViz = buildKn(N, 20, rng);
            exportDot(gViz, "G = K10 (original)", "G_original.dot");

            rng = new Random(SEED);
            gViz = buildKn(N, 20, rng);
            for (int k = KMIN; k <= KMAX; k++) {
                Graph<Integer, DefaultWeightedEdge> hViz = greedySpanner(gViz, k);
                exportDot(hViz,
                        "H (k=" + k + ", " + (2*k-1) + "-spanner, |E|=" + hViz.edgeSet().size() + ")",
                        "H_k" + k + ".dot");
            }
        } catch (IOException ex) {
            System.out.println("  Error writing DOT files: " + ex.getMessage());
        }

        System.out.println();
        System.out.println("  How to view:");
        System.out.println("  1. Open a .dot file in Notepad and copy its content.");
        System.out.println("  2. Paste it at  https://dreampuf.github.io/GraphvizOnline/");
        System.out.println("  3. The graph renders instantly in your browser.");
        System.out.println();
        openGraphvizOnline();
    }
}
