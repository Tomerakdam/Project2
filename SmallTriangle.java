import java.util.*;
import java.io.*;
import java.awt.Desktop;
import java.net.URI;

/**
 * Greedy Spanner Algorithm (Althöfer et al., 1993)
 * Test on a small triangle graph: V={1,2,3}
 *
 *   w(1,2) = 1
 *   w(2,3) = 1
 *   w(1,3) = 2
 *
 * k = 2  →  stretch = 3  (3-spanner)
 *
 * Expected behaviour:
 *   Edges sorted by weight: (1,2,1), (2,3,1), (1,3,2)
 *   (1,2): dist_H = INF  > 3*1=3  → ADD
 *   (2,3): dist_H = INF  > 3*1=3  → ADD
 *   (1,3): dist_H = 2    > 3*2=6  → 2 > 6 is FALSE → SKIP
 *
 *   The direct edge (1,3) is skipped because the path 1→2→3
 *   already costs 2, which equals dist_G(1,3)=2 and is within
 *   the stretch bound of 6.  H is a path: 1-2-3.
 */
public class SmallTriangle {

    // -------------------------------------------------------------------------
    // DOT export
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
        sb.append("  node [shape=circle, style=filled, fillcolor=lightblue, fontsize=14];\n\n");

        // Vertices
        for (int i = 0; i < N; i++)
            sb.append("  v").append(i+1).append(" [label=\"v").append(i+1).append("\"];\n");
        sb.append("\n");

        // Edges
        for (int[] e : allEdges) {
            int u = e[0], v = e[1], w = e[2];
            String key = Math.min(u,v) + "_" + Math.max(u,v);
            boolean inSpanner = (spannerEdges == null) || spannerSet.contains(key);
            sb.append("  v").append(u+1).append(" -- v").append(v+1);
            sb.append(" [label=\"").append(w).append("\"");
            if (inSpanner) sb.append(", color=red, penwidth=3");
            else           sb.append(", color=grey, style=dashed, penwidth=1");
            sb.append("];\n");
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

    // Nodes stored as 0-indexed internally, displayed as v1/v2/v3
    static final int N   = 3;
    static final int INF = Integer.MAX_VALUE / 2;

    // -------------------------------------------------------------------------
    // Dijkstra
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
            if (dU > dist[u]) continue;
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

    static List<List<int[]>> buildAdj(List<int[]> edges) {
        List<List<int[]>> adj = new ArrayList<>();
        for (int i = 0; i < N; i++) adj.add(new ArrayList<>());
        for (int[] e : edges) {
            adj.get(e[0]).add(new int[]{e[1], e[2]});
            adj.get(e[1]).add(new int[]{e[0], e[2]});
        }
        return adj;
    }

    static int[][] allPairsSP(List<List<int[]>> adj) {
        int[][] d = new int[N][];
        for (int i = 0; i < N; i++) d[i] = dijkstra(i, adj);
        return d;
    }

    // -------------------------------------------------------------------------
    // Greedy Spanner
    // -------------------------------------------------------------------------

    static List<int[]> greedySpanner(List<int[]> edges, int k) {
        int stretch = 2 * k - 1;

        List<int[]> sorted = new ArrayList<>(edges);
        sorted.sort(Comparator.comparingInt(e -> e[2]));

        List<List<int[]>> hAdj = new ArrayList<>();
        for (int i = 0; i < N; i++) hAdj.add(new ArrayList<>());
        List<int[]> spanner = new ArrayList<>();

        System.out.println("  Step-by-step decisions (edges sorted by weight):");
        System.out.println("  " + "-".repeat(70));
        System.out.printf("  %-14s  %4s  %12s  %12s  %8s%n",
                "Edge", "w", "dist_H(u,v)", "threshold("+stretch+"*w)", "Decision");
        System.out.println("  " + "-".repeat(70));

        for (int[] edge : sorted) {
            int u = edge[0], v = edge[1], w = edge[2];
            int[] dH  = dijkstra(u, hAdj);
            int   dHv = dH[v];
            int   thr = stretch * w;
            boolean add = dHv > thr;

            System.out.printf("  (v%d, v%d)        %4d  %12s  %12d  %8s%n",
                    u + 1, v + 1, w,
                    dHv >= INF ? "INF" : String.valueOf(dHv),
                    thr,
                    add ? "ADD" : "SKIP");

            if (add) {
                spanner.add(edge);
                hAdj.get(u).add(new int[]{v, w});
                hAdj.get(v).add(new int[]{u, w});
            }
        }
        System.out.println("  " + "-".repeat(70));
        System.out.println();
        return spanner;
    }

    // -------------------------------------------------------------------------
    // Output helpers
    // -------------------------------------------------------------------------

    static void printTable(String title, int[][] dist) {
        System.out.println(title);
        System.out.println("         v1    v2    v3");
        System.out.println("       ----------------");
        String[] labels = {"v1", "v2", "v3"};
        for (int i = 0; i < N; i++) {
            System.out.printf("  %s  |", labels[i]);
            for (int j = 0; j < N; j++) {
                if (dist[i][j] >= INF) System.out.printf("     ∞");
                else                   System.out.printf("  %4d", dist[i][j]);
            }
            System.out.println();
        }
        System.out.println();
    }

    static void checkEdgeCountBound(int n, int edgeCount, int k) {
        double  bound = Math.pow(n, 1.0 + 1.0 / k);
        boolean ok    = edgeCount <= bound;
        System.out.println("  Edge-count bound check:  |E_H| <= n^(1 + 1/k)");
        System.out.println("  " + "-".repeat(45));
        System.out.printf("  n              = %d%n",   n);
        System.out.printf("  k              = %d%n",   k);
        System.out.printf("  1 + 1/k        = %.4f%n", 1.0 + 1.0 / k);
        System.out.printf("  n^(1+1/k)      = %.4f%n", bound);
        System.out.printf("  |E_G| (input)  = %d%n",  n * (n - 1) / 2);
        System.out.printf("  |E_H| (actual) = %d%n",  edgeCount);
        System.out.printf("  %d <= %.4f  -->  %s%n",
                edgeCount, bound, ok ? "PASS" : "FAIL");
        System.out.println();
    }

    static void verify(int[][] gDist, int[][] hDist, int k) {
        int stretch = 2 * k - 1;
        System.out.println("  Pair-by-pair stretch check:");
        System.out.println("  " + "-".repeat(65));
        System.out.printf("  %-12s  %10s  %12s  %10s  %8s%n",
                "Pair", "dist_G", "bound("+stretch+"*dG)", "dist_H", "Status");
        System.out.println("  " + "-".repeat(65));

        boolean allOk = true;
        for (int u = 0; u < N; u++) {
            for (int v = u + 1; v < N; v++) {
                int dG    = gDist[u][v];
                int dH    = hDist[u][v];
                int bound = stretch * dG;
                boolean ok = dH <= bound;
                if (!ok) allOk = false;
                System.out.printf("  (v%d, v%d)       %10d  %12d  %10d  %8s%n",
                        u + 1, v + 1, dG, bound, dH, ok ? "OK" : "VIOLATION");
            }
        }
        System.out.println("  " + "-".repeat(65));
        System.out.println("  Result: " + (allOk ? "ALL PAIRS PASS" : "VIOLATIONS FOUND"));
        System.out.println();
    }

    // -------------------------------------------------------------------------
    // Main
    // -------------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        final int K       = 2;
        final int STRETCH = 2 * K - 1;

        System.out.println("============================================================");
        System.out.println("  Greedy Spanner Test  --  Small Triangle");
        System.out.println("  V={1,2,3}, w(1,2)=1, w(2,3)=1, w(1,3)=2");
        System.out.println("============================================================");
        System.out.printf("  k       : %d%n", K);
        System.out.printf("  Stretch : %d  (this is a %d-spanner)%n", STRETCH, STRETCH);
        System.out.printf("  |V|     : %d%n", N);
        System.out.printf("  |E_G|   : 3%n");
        System.out.println();

        // Nodes 0,1,2 represent v1,v2,v3
        List<int[]> gEdges = new ArrayList<>();
        gEdges.add(new int[]{0, 1, 1});   // w(v1,v2) = 1
        gEdges.add(new int[]{1, 2, 1});   // w(v2,v3) = 1
        gEdges.add(new int[]{0, 2, 2});   // w(v1,v3) = 2

        System.out.println("  Input edges:");
        System.out.println("    w(v1, v2) = 1");
        System.out.println("    w(v2, v3) = 1");
        System.out.println("    w(v1, v3) = 2");
        System.out.println();

        // All-pairs SP in G
        List<List<int[]>> gAdj = buildAdj(gEdges);
        int[][] gDist = allPairsSP(gAdj);
        printTable("[TABLE 1] Original distances  dist_G", gDist);

        // Greedy spanner
        System.out.println("------------------------------------------------------------");
        System.out.println("  Running greedy spanner ...");
        System.out.println("------------------------------------------------------------");
        List<int[]> spannerEdges = greedySpanner(gEdges, K);

        System.out.println("------------------------------------------------------------");
        System.out.printf("  Spanner H: |E_H| = %d  (from |E_G| = 3)%n", spannerEdges.size());
        System.out.printf("  Edge reduction    : %.1f%%%n",
                100.0 * (3 - spannerEdges.size()) / 3);
        System.out.println("------------------------------------------------------------");
        System.out.println("  Spanner edges:");
        for (int[] e : spannerEdges)
            System.out.printf("    (v%d, v%d)  w = %d%n", e[0]+1, e[1]+1, e[2]);
        System.out.println();

        // All-pairs SP in H
        List<List<int[]>> hAdj = buildAdj(spannerEdges);
        int[][] hDist = allPairsSP(hAdj);
        printTable("[TABLE 2] Spanner distances  dist_H", hDist);

        // Edge-count bound check
        checkEdgeCountBound(N, spannerEdges.size(), K);

        // Verification
        verify(gDist, hDist, K);

        // DOT visualisation
        System.out.println("------------------------------------------------------------");
        System.out.println("  Exporting DOT visualisation files ...");
        System.out.println("------------------------------------------------------------");
        exportDot(gEdges, null,         "SmallTriangle — G (original)", "SmallTriangle_G_before.dot");
        exportDot(gEdges, spannerEdges, "SmallTriangle — H (spanner)",  "SmallTriangle_H_after.dot");
        openBrowser("https://dreampuf.github.io/GraphvizOnline/");
    }
}
