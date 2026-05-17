import java.util.*;

/**
 * Greedy Spanner Algorithm (Althöfer et al., 1993) — Test on a random Tree, n=22
 *
 * Graph  : Random tree on 22 vertices (generated with seed 42)
 *          Built by: for each node i in [1..21], connect to a random parent in [0..i-1]
 *          This guarantees a valid tree (connected, no cycles, exactly 21 edges).
 * Weights: Random positive integers in [1, 20], seed 42
 * k      : 2  →  (2k-1) = 3-spanner
 *
 * Expected behaviour:
 *   A tree has no cycles. Every edge is a bridge — removing it disconnects the
 *   graph, making dist_H(u,v) = INF for some pair, which violates the spanner
 *   property for any finite stretch. Therefore the greedy algorithm MUST keep
 *   all 21 edges and |E_H| = |E_G| = n-1 = 21.
 *
 *   Since H = G:  dist_H = dist_G  for all pairs  →  stretch ratio = 1.0 everywhere.
 */
public class Tree22 {

    static final int N   = 22;
    static final int INF = Integer.MAX_VALUE / 2;

    // -------------------------------------------------------------------------
    // Dijkstra — single-source, adjacency list
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
    // Greedy Spanner (Althöfer et al., 1993)
    // -------------------------------------------------------------------------

    static List<int[]> greedySpanner(List<int[]> edges, int k) {
        int stretch = 2 * k - 1;

        List<int[]> sorted = new ArrayList<>(edges);
        sorted.sort(Comparator.comparingInt(e -> e[2]));

        List<List<int[]>> hAdj = new ArrayList<>();
        for (int i = 0; i < N; i++) hAdj.add(new ArrayList<>());
        List<int[]> spanner = new ArrayList<>();
        List<int[]> skipped = new ArrayList<>();

        System.out.println("  Edge-by-edge decisions (sorted by weight):");
        System.out.println("  " + "-".repeat(65));
        System.out.printf("  %-20s  %-10s  %-12s  %-8s%n",
                "Edge", "w", "dist_H(u,v)", "Decision");
        System.out.println("  " + "-".repeat(65));

        for (int[] edge : sorted) {
            int u = edge[0], v = edge[1], w = edge[2];
            int[] dH  = dijkstra(u, hAdj);
            int   dHv = dH[v];
            long  thr = (long) stretch * w;
            boolean add = dHv > thr;

            System.out.printf("  (v%-2d, v%-2d)           %3d      %10s    %s%n",
                    u, v, w,
                    dHv >= INF ? "INF" : String.valueOf(dHv),
                    add ? "ADD" : "SKIP");

            if (add) {
                spanner.add(edge);
                hAdj.get(u).add(new int[]{v, w});
                hAdj.get(v).add(new int[]{u, w});
            } else {
                skipped.add(edge);
            }
        }
        System.out.println("  " + "-".repeat(65));
        System.out.println();

        System.out.println("  Skipped edges: " + (skipped.isEmpty() ? "(none — all edges necessary)" : ""));
        for (int[] e : skipped)
            System.out.printf("    (v%d, v%d)  w=%d%n", e[0], e[1], e[2]);
        System.out.println();

        return spanner;
    }

    // -------------------------------------------------------------------------
    // Output helpers
    // -------------------------------------------------------------------------

    /** Full n×n distance table (feasible since n=22). */
    static void printFullTable(String title, int[][] dist) {
        System.out.println(title);
        // header
        System.out.print("       ");
        for (int j = 0; j < N; j++) System.out.printf(" %3d", j);
        System.out.println();
        System.out.print("       ");
        System.out.println("----".repeat(N));
        // rows
        for (int i = 0; i < N; i++) {
            System.out.printf("  v%-2d |", i);
            for (int j = 0; j < N; j++) {
                if (dist[i][j] >= INF) System.out.printf("   ∞");
                else                   System.out.printf(" %3d", dist[i][j]);
            }
            System.out.println();
        }
        System.out.println();
    }

    static void checkEdgeCountBound(int n, int edgeCount, int k) {
        double  bound = Math.pow(n, 1.0 + 1.0 / k);
        boolean ok    = edgeCount <= bound;
        System.out.println("------------------------------------------------------------");
        System.out.println("  Edge-count bound check:  |E_H| <= n^(1 + 1/k)");
        System.out.println("------------------------------------------------------------");
        System.out.printf("  n              = %d%n",   n);
        System.out.printf("  k              = %d%n",   k);
        System.out.printf("  1 + 1/k        = %.4f%n", 1.0 + 1.0 / k);
        System.out.printf("  n^(1+1/k)      = %.4f  (theoretical bound)%n", bound);
        System.out.printf("  |E_G| (input)  = %d  (tree: n-1 edges)%n", n - 1);
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
                int    dG    = gDist[u][v];
                int    dH    = hDist[u][v];
                if (dH > (long) stretch * dG) violations++;
                double ratio = (double) dH / dG;
                if (ratio > maxRatio) { maxRatio = ratio; worstU = u; worstV = v; }
            }
        }

        System.out.println("------------------------------------------------------------");
        System.out.println("  Stretch verification:  dist_H(u,v) <= " + stretch + " * dist_G(u,v)");
        System.out.println("------------------------------------------------------------");
        System.out.printf("  Total pairs checked : %d%n",  pairs);
        System.out.printf("  Violations found    : %d%n",  violations);
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
        final long SEED    = 42L;
        final int  K       = 2;
        final int  STRETCH = 2 * K - 1;

        Random rng = new Random(SEED);

        System.out.println("============================================================");
        System.out.println("  Greedy Spanner Test  --  Random Tree, n=22");
        System.out.println("============================================================");
        System.out.printf("  Seed    : %d%n",  SEED);
        System.out.printf("  k       : %d%n",  K);
        System.out.printf("  Stretch : %d  (this is a %d-spanner)%n", STRETCH, STRETCH);
        System.out.printf("  |V|     : %d%n",  N);
        System.out.printf("  |E_G|   : %d  (tree: n-1 edges)%n", N - 1);
        System.out.printf("  Weights : random integers in [1, 20]%n");
        System.out.println();

        // ── Build random tree ─────────────────────────────────────────────────
        // For each node i in [1..21], pick a random parent in [0..i-1]
        List<int[]> gEdges = new ArrayList<>();
        for (int i = 1; i < N; i++) {
            int parent = rng.nextInt(i);
            int w      = rng.nextInt(20) + 1;
            gEdges.add(new int[]{parent, i, w});
        }

        System.out.println("------------------------------------------------------------");
        System.out.println("  Tree edges (parent → child, with weight):");
        System.out.println("------------------------------------------------------------");
        for (int[] e : gEdges)
            System.out.printf("    (v%-2d, v%-2d)  w = %2d%n", e[0], e[1], e[2]);
        System.out.println();

        // Print adjacency (children per node)
        Map<Integer, List<String>> children = new TreeMap<>();
        for (int i = 0; i < N; i++) children.put(i, new ArrayList<>());
        for (int[] e : gEdges)
            children.get(e[0]).add("v" + e[1] + "(w=" + e[2] + ")");
        System.out.println("  Tree structure (node: children):");
        for (int i = 0; i < N; i++) {
            List<String> ch = children.get(i);
            if (!ch.isEmpty())
                System.out.printf("    v%-2d --> %s%n", i, String.join(", ", ch));
        }
        System.out.println();

        List<List<int[]>> gAdj = buildAdj(gEdges);

        // ── All-pairs shortest paths in G ────────────────────────────────────
        int[][] gDist = allPairsSP(gAdj);
        printFullTable("[TABLE 1] Original distances  dist_G  (full 22x22)", gDist);

        // ── Greedy Spanner ───────────────────────────────────────────────────
        System.out.println("------------------------------------------------------------");
        System.out.println("  Running greedy spanner ...");
        System.out.println("------------------------------------------------------------");
        List<int[]> spannerEdges = greedySpanner(gEdges, K);

        System.out.println("------------------------------------------------------------");
        System.out.printf("  Spanner H: |E_H| = %d  (from |E_G| = %d)%n",
                spannerEdges.size(), N - 1);
        System.out.printf("  Edge reduction    : %.1f%%%n",
                100.0 * ((N - 1) - spannerEdges.size()) / (N - 1));
        System.out.println("------------------------------------------------------------");
        System.out.println("  Spanner edges:");
        for (int[] e : spannerEdges)
            System.out.printf("    (v%-2d, v%-2d)  w = %2d%n", e[0], e[1], e[2]);
        System.out.println();

        // ── All-pairs shortest paths in H ────────────────────────────────────
        List<List<int[]>> hAdj = buildAdj(spannerEdges);
        int[][] hDist = allPairsSP(hAdj);
        printFullTable("[TABLE 2] Spanner distances  dist_H  (full 22x22)", hDist);

        // ── Edge-count bound check ───────────────────────────────────────────
        checkEdgeCountBound(N, spannerEdges.size(), K);

        // ── Full verification ─────────────────────────────────────────────────
        System.out.printf("  Verifying all %d pairs ...%n", N * (N - 1) / 2);
        verify(gDist, hDist, K);
    }
}
