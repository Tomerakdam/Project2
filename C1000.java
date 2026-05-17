import java.util.*;
import java.io.*;
import java.awt.Desktop;
import java.net.URI;

/**
 * Greedy Spanner Algorithm (Althöfer et al., 1993) — Test on C1000
 *
 * Graph  : Undirected cycle C1000 (1000 vertices, 1000 edges)
 * Weights: Random integers in [1, 45]
 * k      : 5  →  (2k-1) = 9-spanner
 * Seed   : 42 (fixed for reproducibility)
 *
 * Shortest paths use Dijkstra (weighted graph, adjacency list).
 * Full all-pairs verification is performed (499,500 pairs checked).
 * Distance tables are printed for a 10-node sample (every 100th vertex).
 */
public class C1000 {

    // -------------------------------------------------------------------------
    // DOT export  (circo layout — good for cycles)
    // For C1000, writing all 1000 nodes + 1000 edges is fine as a file;
    // GraphViz Online handles it but rendering may be slow — use fdp/neato too.
    // -------------------------------------------------------------------------

    static void exportDot(List<int[]> allEdges, List<int[]> spannerEdges,
                          String title, String filename) throws IOException {
        Set<String> spannerSet = new HashSet<>();
        if (spannerEdges != null)
            for (int[] e : spannerEdges)
                spannerSet.add(Math.min(e[0],e[1]) + "_" + Math.max(e[0],e[1]));

        StringBuilder sb = new StringBuilder();
        sb.append("graph \"").append(title).append("\" {\n");
        sb.append("  layout=circo;\n");
        sb.append("  node [shape=point, width=0.15, label=\"\"];\n\n");

        // Nodes (no labels for 1000-node graph — too cluttered)
        for (int i = 0; i < N; i++)
            sb.append("  v").append(i).append(";\n");
        sb.append("\n");

        for (int[] e : allEdges) {
            int u = e[0], v = e[1], w = e[2];
            String key = Math.min(u,v) + "_" + Math.max(u,v);
            boolean inSpanner = (spannerEdges == null) || spannerSet.contains(key);
            sb.append("  v").append(u).append(" -- v").append(v);
            if (inSpanner) sb.append(" [color=red, penwidth=2]");
            else           sb.append(" [color=grey, style=dashed, penwidth=1]");
            sb.append(";\n");
        }
        sb.append("}\n");

        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            pw.print(sb);
        }
        System.out.println("  DOT file written: " + filename);
        System.out.println("  Paste at: https://dreampuf.github.io/GraphvizOnline/");
        System.out.println();
    }

    static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported())
                Desktop.getDesktop().browse(new URI(url));
        } catch (Exception ignored) {}
    }

    static final int N   = 1000;
    static final int INF = Integer.MAX_VALUE / 2;

    // -------------------------------------------------------------------------
    // Dijkstra — single-source, adjacency list representation
    // adj.get(u) = list of {neighbor, weight}
    // -------------------------------------------------------------------------

    static int[] dijkstra(int src, List<List<int[]>> adj) {
        int[] dist = new int[N];
        Arrays.fill(dist, INF);
        dist[src] = 0;
        PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingInt(a -> a[1]));
        pq.add(new int[]{src, 0});
        while (!pq.isEmpty()) {
            int[] cur = pq.poll();
            int u = cur[0], dU = cur[1];
            if (dU > dist[u]) continue;          // stale entry
            for (int[] nb : adj.get(u)) {
                int v = nb[0], w = nb[1];
                int nd = dist[u] + w;
                if (nd < dist[v]) {
                    dist[v] = nd;
                    pq.add(new int[]{v, nd});
                }
            }
        }
        return dist;
    }

    // -------------------------------------------------------------------------
    // Graph helpers
    // -------------------------------------------------------------------------

    static List<List<int[]>> buildAdj(List<int[]> edges) {
        List<List<int[]>> adj = new ArrayList<>();
        for (int i = 0; i < N; i++) adj.add(new ArrayList<>());
        for (int[] e : edges) {
            adj.get(e[0]).add(new int[]{e[1], e[2]});
            adj.get(e[1]).add(new int[]{e[0], e[2]});
        }
        return adj;
    }

    /** Runs Dijkstra from every vertex; returns dist[src][dst]. */
    static int[][] allPairsSP(List<List<int[]>> adj) {
        int[][] dist = new int[N][];
        for (int i = 0; i < N; i++) dist[i] = dijkstra(i, adj);
        return dist;
    }

    // -------------------------------------------------------------------------
    // Greedy Spanner (Althöfer et al., 1993) — weighted version
    // Add (u,v,w) iff dist_H(u,v) > (2k-1) · w
    // -------------------------------------------------------------------------

    static List<int[]> greedySpanner(List<int[]> edges, int k) {
        int stretch = 2 * k - 1;

        List<int[]> sorted = new ArrayList<>(edges);
        sorted.sort(Comparator.comparingInt(e -> e[2]));   // non-decreasing weight

        List<List<int[]>> hAdj = new ArrayList<>();
        for (int i = 0; i < N; i++) hAdj.add(new ArrayList<>());
        List<int[]> spanner = new ArrayList<>();

        for (int[] edge : sorted) {
            int u = edge[0], v = edge[1], w = edge[2];
            int[] dH = dijkstra(u, hAdj);
            if (dH[v] > stretch * w) {
                spanner.add(edge);
                hAdj.get(u).add(new int[]{v, w});
                hAdj.get(v).add(new int[]{u, w});
            }
        }
        return spanner;
    }

    // -------------------------------------------------------------------------
    // Output helpers
    // -------------------------------------------------------------------------

    /** Sample distance table: only show rows/cols for vertices in 'sample'. */
    static void printSampleTable(String title, int[][] dist, int[] sample) {
        System.out.println(title);
        System.out.print("          ");
        for (int v : sample) System.out.printf("  v%-4d", v);
        System.out.println();
        System.out.print("          ");
        System.out.println("-------".repeat(sample.length));
        for (int u : sample) {
            System.out.printf("  v%-4d | ", u);
            for (int v : sample) {
                if (dist[u][v] >= INF) System.out.printf("     ∞ ");
                else                   System.out.printf(" %5d ", dist[u][v]);
            }
            System.out.println();
        }
        System.out.println();
    }

    /** Edge-count bound check: |E_H| <= n^(1+1/k) */
    static void checkEdgeCountBound(int n, int edgeCount, int k) {
        double bound = Math.pow(n, 1.0 + 1.0 / k);
        boolean ok = edgeCount <= bound;
        System.out.println("------------------------------------------------------------");
        System.out.println("  Edge-count bound check:  |E_H| <= n^(1 + 1/k)");
        System.out.println("------------------------------------------------------------");
        System.out.printf("  n              = %d%n",   n);
        System.out.printf("  k              = %d%n",   k);
        System.out.printf("  1 + 1/k        = %.4f%n", 1.0 + 1.0 / k);
        System.out.printf("  n^(1+1/k)      = %.4f  (theoretical bound)%n", bound);
        System.out.printf("  |E_G| (input)  = %d%n",  n);           // cycle: |E|=n
        System.out.printf("  |E_H| (actual) = %d%n",  edgeCount);
        System.out.printf("  %d <= %.4f  -->  %s%n",
                edgeCount, bound, ok ? "PASS" : "FAIL");
        System.out.println();
    }

    /** Full all-pairs stretch verification — prints summary, not every pair. */
    static void verify(int[][] gDist, int[][] hDist, int k) {
        int    stretch    = 2 * k - 1;
        int    violations = 0;
        int    pairs      = 0;
        double maxRatio   = 0.0;
        int    worstU = -1, worstV = -1;

        for (int u = 0; u < N; u++) {
            for (int v = u + 1; v < N; v++) {
                if (gDist[u][v] >= INF) continue;
                pairs++;
                int dG = gDist[u][v];
                int dH = hDist[u][v];
                if (dH > (long) stretch * dG) violations++;
                double ratio = (double) dH / dG;
                if (ratio > maxRatio) { maxRatio = ratio; worstU = u; worstV = v; }
            }
        }

        System.out.println("------------------------------------------------------------");
        System.out.println("  Stretch verification:  dist_H(u,v) <= " + stretch + " * dist_G(u,v)");
        System.out.println("------------------------------------------------------------");
        System.out.printf("  Total pairs checked : %,d%n",   pairs);
        System.out.printf("  Violations found    : %d%n",     violations);
        System.out.printf("  Max stretch ratio   : %.6f  (at pair v%d, v%d)%n",
                maxRatio, worstU, worstV);
        System.out.printf("  Allowed max ratio   : %d.000000%n", stretch);
        System.out.printf("  Result              : %s%n",
                violations == 0 ? "ALL PAIRS PASS -- valid " + stretch + "-spanner"
                                : violations + " VIOLATIONS DETECTED");
        System.out.println();
    }

    // -------------------------------------------------------------------------
    // Main
    // -------------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        final long SEED    = 42L;
        final int  K       = 5;
        final int  STRETCH = 2 * K - 1;

        Random rng = new Random(SEED);

        System.out.println("============================================================");
        System.out.println("  Greedy Spanner Test  --  Cycle Graph C1000, Weighted");
        System.out.println("============================================================");
        System.out.println("  Seed    : " + SEED);
        System.out.println("  k       : " + K);
        System.out.println("  Stretch : " + STRETCH + "  (this is a " + STRETCH + "-spanner)");
        System.out.println("  |V|     : " + N);
        System.out.println("  |E_G|   : " + N + "  (cycle: one edge per vertex)");
        System.out.println("  Weights : uniform random integers in [1, 45]");
        System.out.println();

        // ── Build C1000 ──────────────────────────────────────────────────────
        List<int[]> gEdges = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            int w = rng.nextInt(45) + 1;
            gEdges.add(new int[]{i, (i + 1) % N, w});
        }

        // Print all edge weights (cycle edges in order)
        System.out.println("------------------------------------------------------------");
        System.out.println("  C1000 edge weights  (v_i -- v_{i+1 mod 1000}):");
        System.out.println("------------------------------------------------------------");
        for (int[] e : gEdges)
            System.out.printf("    (v%4d, v%4d)  w = %2d%n", e[0], e[1], e[2]);
        System.out.println();

        List<List<int[]>> gAdj = buildAdj(gEdges);

        // ── All-pairs shortest paths in G ────────────────────────────────────
        System.out.println("  Computing all-pairs shortest paths in G ...");
        long t0 = System.currentTimeMillis();
        int[][] gDist = allPairsSP(gAdj);
        System.out.printf("  Done in %d ms.%n%n", System.currentTimeMillis() - t0);

        // Sample table: every 100th vertex → 10 representative nodes
        int[] sample = {0, 100, 200, 300, 400, 500, 600, 700, 800, 900};
        printSampleTable("[TABLE 1] dist_G  (sample: every 100th vertex)", gDist, sample);

        // ── Greedy Spanner ───────────────────────────────────────────────────
        System.out.println("  Running greedy spanner ...");
        t0 = System.currentTimeMillis();
        List<int[]> spannerEdges = greedySpanner(gEdges, K);
        System.out.printf("  Done in %d ms.%n%n", System.currentTimeMillis() - t0);

        System.out.println("------------------------------------------------------------");
        System.out.printf("  Spanner H: |E_H| = %d  (from |E_G| = %d)%n",
                spannerEdges.size(), N);
        System.out.printf("  Edge reduction    : %.1f%%%n",
                100.0 * (N - spannerEdges.size()) / N);
        System.out.println("------------------------------------------------------------");
        System.out.println("  Spanner edges kept:");
        for (int[] e : spannerEdges)
            System.out.printf("    (v%4d, v%4d)  w = %2d%n", e[0], e[1], e[2]);
        System.out.println();

        // ── All-pairs shortest paths in H ────────────────────────────────────
        List<List<int[]>> hAdj = buildAdj(spannerEdges);
        System.out.println("  Computing all-pairs shortest paths in H ...");
        t0 = System.currentTimeMillis();
        int[][] hDist = allPairsSP(hAdj);
        System.out.printf("  Done in %d ms.%n%n", System.currentTimeMillis() - t0);

        printSampleTable("[TABLE 2] dist_H  (sample: every 100th vertex)", hDist, sample);

        // ── Edge-count bound check ───────────────────────────────────────────
        checkEdgeCountBound(N, spannerEdges.size(), K);

        // ── Full all-pairs verification ───────────────────────────────────────
        System.out.println("  Verifying all " + (N * (N - 1) / 2) + " pairs ...");
        t0 = System.currentTimeMillis();
        verify(gDist, hDist, K);
        System.out.printf("  Verification completed in %d ms.%n", System.currentTimeMillis() - t0);

        // DOT visualisation
        System.out.println();
        System.out.println("------------------------------------------------------------");
        System.out.println("  Exporting DOT visualisation files ...");
        System.out.println("------------------------------------------------------------");
        exportDot(gEdges, null,         "C1000 — G (original cycle)", "C1000_G_before.dot");
        exportDot(gEdges, spannerEdges, "C1000 — H (spanner)",        "C1000_H_after.dot");
        openBrowser("https://dreampuf.github.io/GraphvizOnline/");
    }
}
