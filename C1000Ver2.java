import java.util.*;

/**
 * Greedy Spanner Algorithm (Althöfer et al., 1993) — Test on C1000 Ver2
 *
 * Graph  : Undirected cycle C1000 (1000 vertices, 1000 edges)
 * Weights: All edges weight = 1, EXCEPT edge (v499, v500) which has weight = 100000
 * k      : 5  →  (2k-1) = 9-spanner
 *
 * Expected behaviour:
 *   The greedy algorithm sorts edges by weight, so all 999 unit edges are
 *   processed first and added to H (forming a path 0→1→...→499 and 500→...→999→0).
 *   When the heavy edge (499,500) is finally processed:
 *     dist_H(499,500) = 999   (going the long way around via all unit edges)
 *     skip condition : 999 > 9 × 100000 = 900,000  →  FALSE  →  edge SKIPPED
 *   So the heavy edge is removed and |E_H| = 999.
 *
 *   Also note: dist_G(499,500) = min(100000, 999) = 999  (long path is cheaper),
 *   so the heavy edge was never part of any shortest path in G either.
 */
public class C1000Ver2 {

    static final int N          = 1000;
    static final int INF        = Integer.MAX_VALUE / 2;
    static final int HEAVY_U    = 499;          // endpoints of the heavy edge
    static final int HEAVY_V    = 500;
    static final int HEAVY_W    = 100_000;

    // -------------------------------------------------------------------------
    // Dijkstra — single-source, adjacency list
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

    static int[][] allPairsSP(List<List<int[]>> adj) {
        int[][] dist = new int[N][];
        for (int i = 0; i < N; i++) dist[i] = dijkstra(i, adj);
        return dist;
    }

    // -------------------------------------------------------------------------
    // Greedy Spanner — weighted, with step-by-step trace for the heavy edge
    // -------------------------------------------------------------------------

    static List<int[]> greedySpanner(List<int[]> edges, int k) {
        int stretch = 2 * k - 1;

        List<int[]> sorted = new ArrayList<>(edges);
        sorted.sort(Comparator.comparingInt(e -> e[2]));   // lightest first

        List<List<int[]>> hAdj = new ArrayList<>();
        for (int i = 0; i < N; i++) hAdj.add(new ArrayList<>());
        List<int[]> spanner  = new ArrayList<>();
        List<int[]> skipped  = new ArrayList<>();

        for (int[] edge : sorted) {
            int u = edge[0], v = edge[1], w = edge[2];
            int[] dH = dijkstra(u, hAdj);
            long threshold = (long) stretch * w;

            boolean add = dH[v] > threshold;

            // Detailed trace for the heavy edge
            if (w == HEAVY_W) {
                System.out.println("  --- Heavy edge trace ---");
                System.out.printf("  Processing edge (v%d, v%d)  w = %d%n", u, v, w);
                System.out.printf("  dist_H(v%d, v%d) = %s%n",
                        u, v, dH[v] >= INF ? "INF" : String.valueOf(dH[v]));
                System.out.printf("  Threshold (stretch x w) = %d x %d = %d%n",
                        stretch, w, threshold);
                System.out.printf("  %s > %d  ?  %s  -->  edge %s%n",
                        dH[v] >= INF ? "INF" : String.valueOf(dH[v]),
                        threshold, add, add ? "ADDED" : "SKIPPED");
                System.out.println("  ------------------------");
                System.out.println();
            }

            if (add) {
                spanner.add(edge);
                hAdj.get(u).add(new int[]{v, w});
                hAdj.get(v).add(new int[]{u, w});
            } else {
                skipped.add(edge);
            }
        }

        // Print skipped edges
        System.out.println("  Edges skipped by the algorithm:");
        if (skipped.isEmpty()) {
            System.out.println("    (none)");
        } else {
            for (int[] e : skipped)
                System.out.printf("    (v%d, v%d)  w = %d%n", e[0], e[1], e[2]);
        }
        System.out.println();

        return spanner;
    }

    // -------------------------------------------------------------------------
    // Output helpers
    // -------------------------------------------------------------------------

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

    static void checkEdgeCountBound(int n, int edgeCount, int k) {
        double bound = Math.pow(n, 1.0 + 1.0 / k);
        boolean ok   = edgeCount <= bound;
        System.out.println("------------------------------------------------------------");
        System.out.println("  Edge-count bound check:  |E_H| <= n^(1 + 1/k)");
        System.out.println("------------------------------------------------------------");
        System.out.printf("  n              = %d%n",   n);
        System.out.printf("  k              = %d%n",   k);
        System.out.printf("  1 + 1/k        = %.4f%n", 1.0 + 1.0 / k);
        System.out.printf("  n^(1+1/k)      = %.4f  (theoretical bound)%n", bound);
        System.out.printf("  |E_G| (input)  = %d%n",   n);
        System.out.printf("  |E_H| (actual) = %d%n",   edgeCount);
        System.out.printf("  %d <= %.4f  -->  %s%n",
                edgeCount, bound, ok ? "PASS" : "FAIL");
        System.out.println();
    }

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
        System.out.printf("  Total pairs checked : %,d%n",  pairs);
        System.out.printf("  Violations found    : %d%n",    violations);
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

    public static void main(String[] args) {
        final int K       = 5;
        final int STRETCH = 2 * K - 1;

        System.out.println("============================================================");
        System.out.println("  Greedy Spanner Test  --  C1000 Ver2");
        System.out.println("  All edges weight=1, except one heavy edge weight=100000");
        System.out.println("============================================================");
        System.out.printf("  k         : %d%n", K);
        System.out.printf("  Stretch   : %d  (this is a %d-spanner)%n", STRETCH, STRETCH);
        System.out.printf("  |V|       : %d%n", N);
        System.out.printf("  |E_G|     : %d%n", N);
        System.out.printf("  Heavy edge: (v%d, v%d)  w = %d%n", HEAVY_U, HEAVY_V, HEAVY_W);
        System.out.println();

        // ── Build C1000: all edges weight=1, one heavy edge ──────────────────
        List<int[]> gEdges = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            int u = i, v = (i + 1) % N;
            int w = (u == HEAVY_U && v == HEAVY_V) ? HEAVY_W : 1;
            gEdges.add(new int[]{u, v, w});
        }

        // Print edge list, highlighting the heavy edge
        System.out.println("------------------------------------------------------------");
        System.out.println("  C1000 edge weights:");
        System.out.println("------------------------------------------------------------");
        for (int[] e : gEdges) {
            boolean heavy = (e[2] == HEAVY_W);
            System.out.printf("    (v%4d, v%4d)  w = %6d%s%n",
                    e[0], e[1], e[2], heavy ? "  <-- HEAVY EDGE" : "");
        }
        System.out.println();

        List<List<int[]>> gAdj = buildAdj(gEdges);

        // ── All-pairs shortest paths in G ────────────────────────────────────
        System.out.println("  Computing all-pairs shortest paths in G ...");
        long t0 = System.currentTimeMillis();
        int[][] gDist = allPairsSP(gAdj);
        System.out.printf("  Done in %d ms.%n%n", System.currentTimeMillis() - t0);

        // Sample includes v499 and v500 so we can see the heavy edge pair
        int[] sample = {0, 100, 200, 300, 400, 499, 500, 600, 700, 800};
        printSampleTable("[TABLE 1] dist_G  (sample includes v499 and v500)", gDist, sample);

        // Highlight the heavy edge pair in G
        System.out.printf("  Note: dist_G(v499, v500) = %d%n", gDist[HEAVY_U][HEAVY_V]);
        System.out.printf("        Direct heavy edge   = %d%n", HEAVY_W);
        System.out.printf("        Long way around     = %d  (999 unit edges)%n",
                N - 1);
        System.out.printf("        Shortest path uses  : %s%n%n",
                gDist[HEAVY_U][HEAVY_V] < HEAVY_W ? "long way (heavy edge NOT on shortest path in G)"
                                                   : "direct heavy edge");

        // ── Greedy Spanner ───────────────────────────────────────────────────
        System.out.println("------------------------------------------------------------");
        System.out.println("  Running greedy spanner ...");
        System.out.println("------------------------------------------------------------");
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
            System.out.printf("    (v%4d, v%4d)  w = %6d%n", e[0], e[1], e[2]);
        System.out.println();

        // ── All-pairs shortest paths in H ────────────────────────────────────
        List<List<int[]>> hAdj = buildAdj(spannerEdges);
        System.out.println("  Computing all-pairs shortest paths in H ...");
        t0 = System.currentTimeMillis();
        int[][] hDist = allPairsSP(hAdj);
        System.out.printf("  Done in %d ms.%n%n", System.currentTimeMillis() - t0);

        printSampleTable("[TABLE 2] dist_H  (sample includes v499 and v500)", hDist, sample);

        System.out.printf("  Note: dist_H(v499, v500) = %d  (goes the long way, heavy edge removed)%n%n",
                hDist[HEAVY_U][HEAVY_V]);

        // ── Edge-count bound check ───────────────────────────────────────────
        checkEdgeCountBound(N, spannerEdges.size(), K);

        // ── Full all-pairs verification ───────────────────────────────────────
        System.out.printf("  Verifying all %,d pairs ...%n", N * (N - 1) / 2);
        t0 = System.currentTimeMillis();
        verify(gDist, hDist, K);
        System.out.printf("  Verification completed in %d ms.%n", System.currentTimeMillis() - t0);
    }
}
