import java.util.*;

/**
 * Lemma 3 Verification  —  Althöfer et al. (1993)
 *
 * Lemma 3: Let C be any cycle in H (the spanner), and let e be any edge in C.
 *          Then  Weight(C - {e})  >  r · Weight(e),  where r = 2k-1.
 *
 * Computational interpretation:
 *   For every edge e=(u,v,w) in H, let H\{e} be H with e removed.
 *   - If dist_{H\{e}}(u,v) = INF  →  e is a bridge (no cycle through e)
 *                                     lemma holds vacuously.
 *   - If dist_{H\{e}}(u,v) < INF  →  there exists a cycle C through e, and
 *                                     Weight(C-{e}) >= dist_{H\{e}}(u,v).
 *                                     Lemma 3 requires dist_{H\{e}}(u,v) > r·w.
 *
 * Test graph: K5 where
 *   - Cycle edges  (v1-v2-v3-v4-v5-v1) :  weight = 1
 *   - Diagonal edges                    :  weight = 2
 *   k = 2,  r = 3
 *
 * Why H has a cycle here:
 *   Edges are processed lightest first. The 5 cycle edges are added one by one.
 *   When the last cycle edge (v5,v1,1) is processed:
 *     dist_H(v5,v1) = 4  (path v5→v4→v3→v2→v1)
 *     4 > r·1 = 3  →  edge ADDED  →  H contains the full C5 cycle.
 *   All diagonals are then skipped (dist_H = 2 ≤ 6 = 3·2).
 *
 * Expected Lemma 3 result:
 *   For each edge e=(vi,vj,1) in C5:
 *     dist_{H\{e}}(vi,vj) = 4   (the other 4 unit edges)
 *     4 > 3·1 = 3  →  PASS
 */
public class Lemma3 {

    static final int N   = 5;
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
    // Greedy Spanner — with step-by-step trace
    // -------------------------------------------------------------------------

    static List<int[]> greedySpanner(List<int[]> edges, int k) {
        int r = 2 * k - 1;
        List<int[]> sorted = new ArrayList<>(edges);
        sorted.sort(Comparator.comparingInt(e -> e[2]));

        List<List<int[]>> hAdj = new ArrayList<>();
        for (int i = 0; i < N; i++) hAdj.add(new ArrayList<>());
        List<int[]> spanner = new ArrayList<>();

        System.out.println("  Step-by-step decisions (sorted by weight):");
        System.out.println("  " + "-".repeat(65));
        System.out.printf("  %-14s  %4s  %12s  %10s  %8s%n",
                "Edge", "w", "dist_H(u,v)", "r*w="+r+"*w", "Decision");
        System.out.println("  " + "-".repeat(65));

        for (int[] edge : sorted) {
            int u = edge[0], v = edge[1], w = edge[2];
            int[] dH  = dijkstra(u, hAdj);
            int   dHv = dH[v];
            int   thr = r * w;
            boolean add = dHv > thr;

            System.out.printf("  (v%d, v%d)        %4d  %12s  %10d  %8s%n",
                    u+1, v+1, w,
                    dHv >= INF ? "INF" : String.valueOf(dHv),
                    thr, add ? "ADD" : "SKIP");

            if (add) {
                spanner.add(edge);
                hAdj.get(u).add(new int[]{v, w});
                hAdj.get(v).add(new int[]{u, w});
            }
        }
        System.out.println("  " + "-".repeat(65));
        System.out.println();
        return spanner;
    }

    // -------------------------------------------------------------------------
    // Lemma 3 check
    // -------------------------------------------------------------------------

    static void checkLemma3(List<int[]> spannerEdges, int k) {
        int r      = 2 * k - 1;
        boolean allOk = true;

        System.out.println("  For each edge e = (u,v,w) in H:");
        System.out.println("    Remove e from H, run Dijkstra u→v in H\\{e}.");
        System.out.println("    Lemma 3 requires:  dist_{H\\{e}}(u,v)  >  r · w");
        System.out.println();
        System.out.println("  " + "-".repeat(75));
        System.out.printf("  %-14s %5s %20s %12s %16s%n",
                "Edge e", "w(e)", "dist_{H\\{e}}(u,v)", r+"·w(e)", "Lemma 3 status");
        System.out.println("  " + "-".repeat(75));

        for (int[] e : spannerEdges) {
            int eu = e[0], ev = e[1], ew = e[2];
            int thr = r * ew;

            // Build H \ {e}
            List<int[]> reduced = new ArrayList<>();
            for (int[] other : spannerEdges)
                if (!( (other[0]==eu && other[1]==ev) ||
                       (other[0]==ev && other[1]==eu) ))
                    reduced.add(other);

            List<List<int[]>> rAdj = buildAdj(reduced);
            int dist = dijkstra(eu, rAdj)[ev];

            boolean bridge  = (dist >= INF);
            boolean pass;
            String  status;
            String  distStr;

            if (bridge) {
                distStr = "INF";
                pass    = true;
                status  = "PASS  (bridge — no cycle)";
            } else {
                distStr = String.valueOf(dist);
                pass    = dist > thr;
                status  = pass ? "PASS  (" + dist + " > " + thr + ")"
                               : "FAIL  (" + dist + " <= " + thr + ")";
                if (!pass) allOk = false;
            }

            System.out.printf("  (v%d, v%d)       %5d %20s %12d %16s%n",
                    eu+1, ev+1, ew, distStr, thr, status);
        }

        System.out.println("  " + "-".repeat(75));
        System.out.println();
        System.out.println("  Lemma 3 result: " +
                (allOk ? "ALL EDGES PASS -- Lemma 3 holds for this spanner"
                        : "VIOLATION FOUND -- Lemma 3 failed"));
        System.out.println();
    }

    // -------------------------------------------------------------------------
    // Output helpers
    // -------------------------------------------------------------------------

    static void printTable(String title, int[][] dist) {
        System.out.println(title);
        System.out.print("        ");
        for (int j = 0; j < N; j++) System.out.printf("  v%d", j+1);
        System.out.println();
        System.out.print("        ");
        System.out.println("----".repeat(N));
        for (int i = 0; i < N; i++) {
            System.out.printf("  v%d  | ", i+1);
            for (int j = 0; j < N; j++) {
                if (dist[i][j] >= INF) System.out.printf("   ∞");
                else                   System.out.printf("  %2d", dist[i][j]);
            }
            System.out.println();
        }
        System.out.println();
    }

    static void checkEdgeCountBound(int n, int edgeCount, int k) {
        double  bound = Math.pow(n, 1.0 + 1.0 / k);
        boolean ok    = edgeCount <= bound;
        System.out.println("------------------------------------------------------------");
        System.out.println("  Edge-count bound:  |E_H| <= n^(1+1/k)");
        System.out.println("------------------------------------------------------------");
        System.out.printf("  n^(1+1/k) = %d^%.1f = %.4f%n", n, 1.0+1.0/k, bound);
        System.out.printf("  |E_H|     = %d%n", edgeCount);
        System.out.printf("  %d <= %.4f  -->  %s%n", edgeCount, bound, ok ? "PASS" : "FAIL");
        System.out.println();
    }

    static void checkSpannerProperty(int[][] gDist, int[][] hDist, int k) {
        int r = 2 * k - 1;
        System.out.println("------------------------------------------------------------");
        System.out.println("  Spanner property:  dist_H(u,v)  <=  r · dist_G(u,v)");
        System.out.println("------------------------------------------------------------");
        System.out.printf("  %-14s %8s %12s %8s %10s%n",
                "Pair", "dist_G", "r·dist_G", "dist_H", "Status");
        System.out.println("  " + "-".repeat(55));
        boolean allOk = true;
        for (int u = 0; u < N; u++) {
            for (int v = u+1; v < N; v++) {
                int dG = gDist[u][v], dH = hDist[u][v];
                int bound = r * dG;
                boolean ok = dH <= bound;
                if (!ok) allOk = false;
                System.out.printf("  (v%d, v%d)       %8d %12d %8d %10s%n",
                        u+1, v+1, dG, bound, dH, ok ? "OK" : "VIOLATION");
            }
        }
        System.out.println("  " + "-".repeat(55));
        System.out.println("  Result: " + (allOk ? "ALL PAIRS PASS" : "VIOLATIONS FOUND"));
        System.out.println();
    }

    // -------------------------------------------------------------------------
    // Main
    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        final int K = 2;
        final int R = 2 * K - 1;

        System.out.println("============================================================");
        System.out.println("  Lemma 3 Verification  --  Althofer et al. (1993)");
        System.out.println("============================================================");
        System.out.println("  Lemma 3: for any cycle C in H and any edge e in C:");
        System.out.println("           Weight(C - {e})  >  r * Weight(e)");
        System.out.println();
        System.out.println("  Equivalently: for each edge e=(u,v,w) in H,");
        System.out.println("    if there is a cycle through e in H, then");
        System.out.println("    dist_{H\\{e}}(u,v)  >  r * w");
        System.out.println();
        System.out.printf("  k = %d  -->  r = %d%n", K, R);
        System.out.println();

        System.out.println("  Graph: K5");
        System.out.println("    Cycle edges  (v1-v2-v3-v4-v5-v1) : w = 1");
        System.out.println("    Diagonal edges                    : w = 2");
        System.out.println();
        System.out.println("  Why the last cycle edge (v5,v1) is added:");
        System.out.printf("    When processed: dist_H(v5,v1) = 4  (v5->v4->v3->v2->v1)%n");
        System.out.printf("    Condition: 4 > r*1 = %d  -->  TRUE  -->  ADD%n", R);
        System.out.printf("    This creates a cycle in H (the full C5).%n");
        System.out.println();

        // Build K5
        List<int[]> gEdges = new ArrayList<>();
        // C5 cycle edges, weight 1
        gEdges.add(new int[]{0,1,1}); gEdges.add(new int[]{1,2,1});
        gEdges.add(new int[]{2,3,1}); gEdges.add(new int[]{3,4,1});
        gEdges.add(new int[]{4,0,1});
        // Diagonal edges, weight 2
        gEdges.add(new int[]{0,2,2}); gEdges.add(new int[]{1,3,2});
        gEdges.add(new int[]{2,4,2}); gEdges.add(new int[]{0,3,2});
        gEdges.add(new int[]{1,4,2});

        System.out.println("  Input edges:");
        System.out.println("    Cycle (w=1)   : (v1,v2),(v2,v3),(v3,v4),(v4,v5),(v5,v1)");
        System.out.println("    Diagonals(w=2): (v1,v3),(v2,v4),(v3,v5),(v1,v4),(v2,v5)");
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
        System.out.printf("  Spanner H: |E_H| = %d  (from |E_G| = %d,  %.0f%% reduction)%n",
                spannerEdges.size(), gEdges.size(),
                100.0 * (gEdges.size() - spannerEdges.size()) / gEdges.size());
        System.out.println("------------------------------------------------------------");
        System.out.println("  Edges in H:");
        for (int[] e : spannerEdges)
            System.out.printf("    (v%d, v%d)  w=%d%n", e[0]+1, e[1]+1, e[2]);
        System.out.println();

        // All-pairs SP in H
        List<List<int[]>> hAdj = buildAdj(spannerEdges);
        int[][] hDist = allPairsSP(hAdj);
        printTable("[TABLE 2] Spanner distances  dist_H", hDist);

        // Edge-count bound
        checkEdgeCountBound(N, spannerEdges.size(), K);

        // Lemma 3
        System.out.println("------------------------------------------------------------");
        System.out.println("  Lemma 3 Check");
        System.out.println("------------------------------------------------------------");
        checkLemma3(spannerEdges, K);

        // Spanner property
        checkSpannerProperty(gDist, hDist, K);
    }
}
