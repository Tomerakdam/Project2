import java.util.*;

/**
 * Correctness check of the Greedy Spanner Algorithm (Althöfer et al., 1993)
 * on a complete weighted graph K6 with random edge weights.
 *
 * Setup:
 *   - Graph  : K6 (6 vertices, 15 edges), random integer weights in [1, 20]
 *   - k      : 2  →  (2k-1) = 3-spanner
 *   - Seed   : 42 (fixed for reproducibility — printed at runtime)
 *
 * For weighted graphs the greedy condition becomes:
 *   add edge (u,v) with weight w  iff  dist_H(u,v)  >  (2k-1) · w
 *
 * Shortest paths use Dijkstra (required for weighted graphs).
 */
public class K6 {

    static final int N   = 6;               // K6
    static final int INF = Integer.MAX_VALUE / 2;

    // -------------------------------------------------------------------------
    // Dijkstra — single-source shortest paths on a weight matrix
    // -------------------------------------------------------------------------

    static int[] dijkstra(int src, int[][] w) {
        int[] dist = new int[N];
        Arrays.fill(dist, INF);
        dist[src] = 0;

        // min-heap: {node, distance}
        PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingInt(a -> a[1]));
        pq.add(new int[]{src, 0});

        while (!pq.isEmpty()) {
            int[] cur  = pq.poll();
            int   u    = cur[0];
            int   dU   = cur[1];
            if (dU > dist[u]) continue;         // stale entry
            for (int v = 0; v < N; v++) {
                if (w[u][v] < INF) {
                    int nd = dist[u] + w[u][v];
                    if (nd < dist[v]) {
                        dist[v] = nd;
                        pq.add(new int[]{v, nd});
                    }
                }
            }
        }
        return dist;
    }

    /** All-pairs shortest path via repeated Dijkstra. */
    static int[][] allPairsSP(int[][] w) {
        int[][] d = new int[N][];
        for (int i = 0; i < N; i++) d[i] = dijkstra(i, w);
        return d;
    }

    // -------------------------------------------------------------------------
    // Greedy Spanner (Althöfer et al., 1993) — weighted version
    // -------------------------------------------------------------------------

    /**
     * Returns the edge list of a (2k-1)-spanner of the weighted graph given
     * by its weight matrix.  Each entry is {u, v, weight}.
     *
     * Weighted greedy rule:
     *   add (u,v,w)  iff  dist_H(u,v)  >  (2k-1) · w
     */
    static List<int[]> greedySpanner(int[][] weightMatrix, int k) {
        int stretch = 2 * k - 1;

        // Collect and sort edges by weight (non-decreasing)
        List<int[]> edges = new ArrayList<>();
        for (int i = 0; i < N; i++)
            for (int j = i + 1; j < N; j++)
                if (weightMatrix[i][j] < INF)
                    edges.add(new int[]{i, j, weightMatrix[i][j]});
        edges.sort(Comparator.comparingInt(e -> e[2]));

        // Build H incrementally
        int[][] hW = new int[N][N];
        for (int[] row : hW) Arrays.fill(row, INF);
        for (int i = 0; i < N; i++) hW[i][i] = 0;

        List<int[]> spanner = new ArrayList<>();
        for (int[] edge : edges) {
            int u = edge[0], v = edge[1], w = edge[2];
            int[] distH = dijkstra(u, hW);
            if (distH[v] > stretch * w) {       // key condition
                spanner.add(edge);
                hW[u][v] = w;
                hW[v][u] = w;
            }
        }
        return spanner;
    }

    // -------------------------------------------------------------------------
    // Pretty-print helpers
    // -------------------------------------------------------------------------

    static void printEdgeWeights(int[][] w) {
        System.out.println("  Edge weights (sorted by weight):");
        List<int[]> edges = new ArrayList<>();
        for (int i = 0; i < N; i++)
            for (int j = i + 1; j < N; j++)
                edges.add(new int[]{i, j, w[i][j]});
        edges.sort(Comparator.comparingInt(e -> e[2]));
        for (int[] e : edges)
            System.out.printf("    w(v%d, v%d) = %2d%n", e[0], e[1], e[2]);
    }

    static void printDistTable(String title, int[][] dist) {
        int col = 6;
        System.out.println(title + ":");
        // header
        System.out.print("        ");
        for (int j = 0; j < N; j++) System.out.printf("  v%d", j);
        System.out.println();
        System.out.print("        ");
        System.out.println("----".repeat(N));
        // rows
        for (int i = 0; i < N; i++) {
            System.out.printf("  v%d  | ", i);
            for (int j = 0; j < N; j++) {
                if (dist[i][j] >= INF) System.out.printf("  ∞ ");
                else                   System.out.printf(" %3d", dist[i][j]);
            }
            System.out.println();
        }
        System.out.println();
    }

    /** Prints a stretch-ratio table: dist_H(u,v) / dist_G(u,v) for u < v. */
    static void printRatioTable(int[][] gDist, int[][] hDist) {
        System.out.println("  Stretch ratios dist_H / dist_G (upper triangle):");
        System.out.print("        ");
        for (int j = 0; j < N; j++) System.out.printf("   v%d", j);
        System.out.println();
        System.out.print("        ");
        System.out.println("-----".repeat(N));
        for (int i = 0; i < N; i++) {
            System.out.printf("  v%d  | ", i);
            for (int j = 0; j < N; j++) {
                if (j <= i) { System.out.printf("      "); continue; }
                double ratio = (double) hDist[i][j] / gDist[i][j];
                System.out.printf(" %4.2f", ratio);
            }
            System.out.println();
        }
        System.out.println();
    }

    // -------------------------------------------------------------------------
    // Edge-count bound check:  |E_H| <= n^(1 + 1/k)
    // -------------------------------------------------------------------------

    static void checkEdgeCountBound(int n, int edgeCount, int k) {
        double bound     = Math.pow(n, 1.0 + 1.0 / k);
        boolean withinBound = edgeCount <= bound;
        System.out.println("------------------------------------------------------------");
        System.out.println("  Edge-count bound check:  |E_H| <= n^(1 + 1/k)");
        System.out.println("------------------------------------------------------------");
        System.out.printf("  n              = %d%n", n);
        System.out.printf("  k              = %d%n", k);
        System.out.printf("  1 + 1/k        = %.4f%n", 1.0 + 1.0 / k);
        System.out.printf("  n^(1+1/k)      = %.4f  (theoretical bound)%n", bound);
        System.out.printf("  |E_H| (actual) = %d%n", edgeCount);
        System.out.printf("  %d <= %.4f  -->  %s%n",
                edgeCount, bound, withinBound ? "PASS" : "FAIL");
        System.out.println();
    }

    // -------------------------------------------------------------------------
    // Verification
    // -------------------------------------------------------------------------

    static void verify(int[][] gDist, int[][] hDist, int k) {
        int stretch = 2 * k - 1;
        boolean allOk = true;
        System.out.printf("%-18s %-12s %-12s %-10s %-8s%n",
                "Pair (u,v)", "dist_G(u,v)", "bound="+stretch+"·d_G", "dist_H(u,v)", "Status");
        System.out.println("-".repeat(62));
        for (int u = 0; u < N; u++) {
            for (int v = u + 1; v < N; v++) {
                int dG    = gDist[u][v];
                int dH    = hDist[u][v];
                int bound = stretch * dG;
                boolean ok = (dH <= bound);
                if (!ok) allOk = false;
                System.out.printf("(v%d, v%d)          %6d      %8d      %8d   %s%n",
                        u, v, dG, bound, dH, ok ? "OK" : "VIOLATION");
            }
        }
        System.out.println("-".repeat(62));
        System.out.println("Result: " + (allOk ? "ALL PAIRS PASS — valid spanner" : "VIOLATIONS FOUND"));
        System.out.println();
    }

    // -------------------------------------------------------------------------
    // Main
    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        final long SEED = 42L;
        final int  K    = 2;           // <-- chosen k value
        final int  STRETCH = 2 * K - 1;

        Random rng = new Random(SEED);

        System.out.println("============================================================");
        System.out.println("  Greedy Spanner Correctness Check — K6 Weighted Graph");
        System.out.println("============================================================");
        System.out.println("  Seed   : " + SEED);
        System.out.println("  k      : " + K);
        System.out.println("  Stretch: " + STRETCH + "  (this is a " + STRETCH + "-spanner)");
        System.out.println("  |V|    : " + N + "   |E_G| : " + (N * (N - 1) / 2));
        System.out.println();

        // Build K6 weight matrix with random weights in [1, 20]
        int[][] wG = new int[N][N];
        for (int[] row : wG) Arrays.fill(row, INF);
        for (int i = 0; i < N; i++) wG[i][i] = 0;
        for (int i = 0; i < N; i++)
            for (int j = i + 1; j < N; j++) {
                int w = rng.nextInt(20) + 1;
                wG[i][j] = w;
                wG[j][i] = w;
            }

        System.out.println("------------------------------------------------------------");
        System.out.println("  Input graph G (K6, random weights in [1,20])");
        System.out.println("------------------------------------------------------------");
        printEdgeWeights(wG);
        System.out.println();

        // ── Step 1: All-pairs shortest paths in G ──
        int[][] gDist = allPairsSP(wG);
        printDistTable("  [TABLE 1] Original distances in G  (dist_G)", gDist);

        // ── Step 2: Run greedy spanner ──
        List<int[]> spannerEdges = greedySpanner(wG, K);

        // Build spanner weight matrix
        int[][] wH = new int[N][N];
        for (int[] row : wH) Arrays.fill(row, INF);
        for (int i = 0; i < N; i++) wH[i][i] = 0;
        for (int[] e : spannerEdges) {
            wH[e[0]][e[1]] = e[2];
            wH[e[1]][e[0]] = e[2];
        }

        System.out.println("------------------------------------------------------------");
        System.out.printf("  Spanner H: |E_H| = %d  (from |E_G| = %d, %.0f%% reduction)%n",
                spannerEdges.size(), N * (N - 1) / 2,
                100.0 * (N * (N - 1) / 2 - spannerEdges.size()) / (N * (N - 1) / 2));
        System.out.println("------------------------------------------------------------");
        System.out.println("  Spanner edges kept:");
        for (int[] e : spannerEdges)
            System.out.printf("    (v%d, v%d)  w=%2d%n", e[0], e[1], e[2]);
        System.out.println();

        // ── Step 3: All-pairs shortest paths in H ──
        int[][] hDist = allPairsSP(wH);
        printDistTable("  [TABLE 2] Spanner distances in H  (dist_H)", hDist);

        // ── Step 4: Stretch ratio table ──
        System.out.println("------------------------------------------------------------");
        System.out.println("  Stretch analysis");
        System.out.println("------------------------------------------------------------");
        printRatioTable(gDist, hDist);

        // ── Step 5: Edge-count bound check ──
        checkEdgeCountBound(N, spannerEdges.size(), K);

        // ── Step 6: Full stretch verification ──
        System.out.println("------------------------------------------------------------");
        System.out.println("  Verification:  dist_H(u,v)  <=  " + STRETCH + " * dist_G(u,v)");
        System.out.println("------------------------------------------------------------");
        verify(gDist, hDist, K);
    }
}
