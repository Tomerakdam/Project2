import java.util.*;

/**
 * Greedy Spanner Algorithm — Althöfer et al. (1993)
 *
 * Given an unweighted undirected graph G = (V, E) and integer k,
 * produces a (2k-1)-spanner H ⊆ G with O(n^{1+1/k}) edges.
 */
public class GreedySpanner {

    static final int INF = Integer.MAX_VALUE / 2;

    // -------------------------------------------------------------------------
    // Graph utilities
    // -------------------------------------------------------------------------

    static List<List<Integer>> buildAdjacencyList(int n, List<int[]> edges) {
        List<List<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
        for (int[] e : edges) {
            adj.get(e[0]).add(e[1]);
            adj.get(e[1]).add(e[0]);
        }
        return adj;
    }

    /**
     * BFS shortest-path distance from src to dst in an unweighted graph.
     * Returns INF if dst is unreachable from src.
     */
    static int bfs(List<List<Integer>> adj, int src, int dst) {
        if (src == dst) return 0;
        int n = adj.size();
        int[] dist = new int[n];
        Arrays.fill(dist, -1);
        Deque<Integer> queue = new ArrayDeque<>();
        dist[src] = 0;
        queue.add(src);
        while (!queue.isEmpty()) {
            int u = queue.poll();
            for (int v : adj.get(u)) {
                if (dist[v] == -1) {
                    dist[v] = dist[u] + 1;
                    if (v == dst) return dist[v];
                    queue.add(v);
                }
            }
        }
        return INF;
    }

    // -------------------------------------------------------------------------
    // Greedy Spanner (Althöfer et al., 1993)
    // -------------------------------------------------------------------------

    /**
     * Computes a (2k-1)-spanner H of the given unweighted undirected graph G.
     *
     * Algorithm:
     *   1. Sort edges of G in non-decreasing order (trivially equal for unit weights).
     *   2. For each edge (u,v): add to H iff dist_H(u,v) > 2k-1.
     *
     * @param n      number of vertices (labelled 0 … n-1)
     * @param edges  edge list of G, each entry is {u, v}
     * @param k      stretch parameter; produces a (2k-1)-spanner
     * @return       edge list of the spanner H
     */
    static List<int[]> greedySpanner(int n, List<int[]> edges, int k) {
        int stretch = 2 * k - 1;

        // Step 1 — sort by edge weight (all weight = 1 for unweighted graphs,
        // so ordering is stable; swap in a weight field for a weighted extension).
        List<int[]> sorted = new ArrayList<>(edges);
        // Weighted extension: sorted.sort(Comparator.comparingInt(e -> e[2]));

        // Step 2 — incrementally build H
        List<List<Integer>> hAdj = new ArrayList<>();
        for (int i = 0; i < n; i++) hAdj.add(new ArrayList<>());
        List<int[]> spanner = new ArrayList<>();

        for (int[] edge : sorted) {
            int u = edge[0], v = edge[1];
            // BFS in current H: add edge only if it "stretches" distances too much
            if (bfs(hAdj, u, v) > stretch) {
                spanner.add(edge);
                hAdj.get(u).add(v);
                hAdj.get(v).add(u);
            }
        }
        return spanner;
    }

    // -------------------------------------------------------------------------
    // Spanner property verification
    // -------------------------------------------------------------------------

    /**
     * Checks the (2k-1)-spanner property for every connected pair {u,v}:
     *   dist_H(u,v)  ≤  (2k-1) · dist_G(u,v)
     *
     * Prints any violations found and returns true iff the property holds.
     */
    static boolean verifySpanner(int n, List<int[]> gEdges, List<int[]> hEdges, int k) {
        int stretch = 2 * k - 1;
        List<List<Integer>> gAdj = buildAdjacencyList(n, gEdges);
        List<List<Integer>> hAdj = buildAdjacencyList(n, hEdges);

        boolean valid = true;
        for (int u = 0; u < n; u++) {
            for (int v = u + 1; v < n; v++) {
                int dG = bfs(gAdj, u, v);
                if (dG == INF) continue;                  // disconnected in G — skip
                int dH = bfs(hAdj, u, v);
                int bound = stretch * dG;
                if (dH > bound) {
                    System.out.printf("  VIOLATION: dist_H(%d,%d) = %s  >  %d · dist_G(%d,%d) = %d%n",
                            u, v, dH == INF ? "∞" : dH, stretch, u, v, dG);
                    valid = false;
                }
            }
        }
        return valid;
    }

    // -------------------------------------------------------------------------
    // Demo helpers
    // -------------------------------------------------------------------------

    static void printDistanceTable(String label, int n, List<List<Integer>> adj) {
        System.out.println("  " + label + " distance matrix:");
        System.out.print("     ");
        for (int j = 0; j < n; j++) System.out.printf("%3d", j);
        System.out.println();
        for (int u = 0; u < n; u++) {
            System.out.printf("  %2d:", u);
            for (int v = 0; v < n; v++) {
                int d = bfs(adj, u, v);
                System.out.printf("%3s", d == INF ? "∞" : d);
            }
            System.out.println();
        }
    }

    static void runDemo(String name, int n, List<int[]> edges, int k) {
        int stretch = 2 * k - 1;
        System.out.println("══════════════════════════════════════════════════════");
        System.out.println("  " + name);
        System.out.printf("  n = %d,  |E_G| = %d,  k = %d  →  (%d)-spanner%n",
                n, edges.size(), k, stretch);
        System.out.println("──────────────────────────────────────────────────────");

        List<int[]> spanner = greedySpanner(n, edges, k);

        System.out.println("  G edges:");
        for (int[] e : edges)
            System.out.printf("    (%d, %d)%n", e[0], e[1]);

        System.out.println();
        System.out.println("  Spanner H edges (|E_H| = " + spanner.size() + "):");
        for (int[] e : spanner)
            System.out.printf("    (%d, %d)%n", e[0], e[1]);

        System.out.println();
        System.out.printf("  Sparsity: |E_G| = %d  →  |E_H| = %d  (%.0f%% fewer edges)%n",
                edges.size(), spanner.size(),
                100.0 * (edges.size() - spanner.size()) / edges.size());

        System.out.println();
        printDistanceTable("G", n, buildAdjacencyList(n, edges));
        System.out.println();
        printDistanceTable("H", n, buildAdjacencyList(n, spanner));

        System.out.println();
        System.out.print("  Verifying (" + stretch + ")-spanner property ... ");
        boolean ok = verifySpanner(n, edges, spanner, k);
        System.out.println(ok ? "PASS ✓" : "FAIL ✗");
        System.out.println();
    }

    // -------------------------------------------------------------------------
    // Main — three illustrative examples
    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        System.out.println("Greedy Spanner Algorithm — Althöfer et al. (1993)");
        System.out.println();

        // ── Example 1: Complete graph K_6, k=2 → 3-spanner ──────────────────
        // K_6 has 15 edges. The greedy algorithm produces a star (5 edges)
        // because once node 0 is connected to everyone, any other pair {i,j}
        // already has dist_H(i,j) = 2 ≤ stretch = 3.
        {
            int n = 6;
            List<int[]> edges = new ArrayList<>();
            for (int i = 0; i < n; i++)
                for (int j = i + 1; j < n; j++)
                    edges.add(new int[]{i, j});
            runDemo("Complete graph K_6  (k=2, 3-spanner)", n, edges, 2);
        }

        // ── Example 2: Cycle C_8 with extra chords, k=2 → 3-spanner ─────────
        // Cycle C_8 plus chords (0,4),(1,5),(2,6),(3,7) = 12 edges total.
        // The spanner should prune some chords while keeping all-pair distances
        // within factor 3.
        {
            int n = 8;
            List<int[]> edges = new ArrayList<>();
            for (int i = 0; i < n; i++)                         // cycle
                edges.add(new int[]{i, (i + 1) % n});
            int[][] chords = {{0,4},{1,5},{2,6},{3,7}};
            for (int[] c : chords) edges.add(c);                // diameter chords
            runDemo("Cycle C_8 + diameter chords  (k=2, 3-spanner)", n, edges, 2);
        }

        // ── Example 3: Same chord cycle, k=3 → 5-spanner ────────────────────
        // Larger k allows more stretch, so fewer edges are retained.
        {
            int n = 8;
            List<int[]> edges = new ArrayList<>();
            for (int i = 0; i < n; i++)
                edges.add(new int[]{i, (i + 1) % n});
            int[][] chords = {{0,4},{1,5},{2,6},{3,7}};
            for (int[] c : chords) edges.add(c);
            runDemo("Cycle C_8 + diameter chords  (k=3, 5-spanner)", n, edges, 3);
        }
    }
}
