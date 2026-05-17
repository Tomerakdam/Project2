import java.util.*;
import java.io.*;
import java.awt.Desktop;
import java.net.URI;

/**
 * MST-Containment Test for the Greedy Spanner  (Althöfer et al., 1993)
 *
 * Graph  : Random connected graph, N=10, ~30 edges  (O(N²) class but sparse)
 * Weights: Random integers in [1, 50], seed 42
 * k vals : 5, 4, 3, 2, 1  (stretch 9, 7, 5, 3, 1)
 *
 * Generation:
 *   1. Build a random spanning tree (guarantees connectivity, 9 edges).
 *   2. Add random non-duplicate edges until |E| = 30.
 *
 * Key Theorem  (see MSTSpanner.java for full proof):
 *   The greedy (2k-1)-spanner always contains every MST edge.
 *   For k=1 the spanner equals the MST exactly.
 *
 * DOT colour legend:
 *   Blue  thick   = MST edge kept in spanner   (always happens)
 *   Red            = non-MST edge kept
 *   Grey  dashed   = non-MST edge removed
 *   Orange dashed  = MST edge removed          (NEVER happens — theorem)
 */
public class N10Spanner {

    static final int N    = 10;
    static final int INF  = Integer.MAX_VALUE / 2;
    static final int[] KS = {5, 4, 3, 2, 1};

    // =========================================================================
    // Dijkstra
    // =========================================================================
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
                if (nd < dist[v]) { dist[v] = nd; pq.add(new int[]{v, nd}); }
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

    // =========================================================================
    // Kruskal's MST  (Union-Find)
    // =========================================================================
    static int[] ufParent, ufRank;

    static int find(int x) {
        if (ufParent[x] != x) ufParent[x] = find(ufParent[x]);
        return ufParent[x];
    }

    static boolean union(int x, int y) {
        int px = find(x), py = find(y);
        if (px == py) return false;
        if (ufRank[px] < ufRank[py]) { int t = px; px = py; py = t; }
        ufParent[py] = px;
        if (ufRank[px] == ufRank[py]) ufRank[px]++;
        return true;
    }

    static Set<String> buildMST(List<int[]> edges) {
        List<int[]> sorted = new ArrayList<>(edges);
        sorted.sort(Comparator.comparingInt(e -> e[2]));
        ufParent = new int[N]; ufRank = new int[N];
        for (int i = 0; i < N; i++) ufParent[i] = i;
        Set<String> mst = new LinkedHashSet<>();
        for (int[] e : sorted)
            if (union(e[0], e[1]))
                mst.add(eKey(e[0], e[1]));
        return mst;
    }

    static String eKey(int u, int v) { return Math.min(u,v) + "_" + Math.max(u,v); }

    // =========================================================================
    // Greedy Spanner — full step trace with MST column
    // =========================================================================
    static List<int[]> greedySpanner(List<int[]> edges, int k, Set<String> mstKeys) {
        int stretch = 2 * k - 1;
        List<int[]> sorted = new ArrayList<>(edges);
        sorted.sort(Comparator.comparingInt(e -> e[2]));

        List<List<int[]>> hAdj = new ArrayList<>();
        for (int i = 0; i < N; i++) hAdj.add(new ArrayList<>());
        List<int[]> spanner = new ArrayList<>();

        System.out.println("  " + "-".repeat(72));
        System.out.printf("  %-14s  %5s  %12s  %10s  %6s  %8s%n",
                "Edge", "w", "dist_H(u,v)", stretch + "·w", "MST?", "Decision");
        System.out.println("  " + "-".repeat(72));

        for (int[] edge : sorted) {
            int u = edge[0], v = edge[1], w = edge[2];
            boolean inMST = mstKeys.contains(eKey(u, v));
            int[]   dH    = dijkstra(u, hAdj);
            int     dHv   = dH[v];
            long    thr   = (long) stretch * w;
            boolean add   = dHv > thr;

            System.out.printf("  (v%2d, v%2d)     %5d  %12s  %10d  %6s  %8s%n",
                    u+1, v+1, w,
                    dHv >= INF ? "INF" : String.valueOf(dHv),
                    thr,
                    inMST ? "MST" : "",
                    add ? "ADD" : "SKIP");

            if (add) {
                spanner.add(edge);
                hAdj.get(u).add(new int[]{v, w});
                hAdj.get(v).add(new int[]{u, w});
            }
        }
        System.out.println("  " + "-".repeat(72));
        System.out.println();
        return spanner;
    }

    // =========================================================================
    // MST containment check — detailed table
    // =========================================================================
    static boolean checkMSTContainment(List<int[]> spannerEdges, Set<String> mstKeys,
                                       Map<String, Integer> wMap, int k) {
        Set<String> spannerSet = new HashSet<>();
        for (int[] e : spannerEdges)
            spannerSet.add(eKey(e[0], e[1]));

        System.out.printf("  MST containment in H_k%d:%n", k);
        System.out.println("  " + "-".repeat(48));
        System.out.printf("  %-20s  %6s  %10s%n", "MST edge", "weight", "in H_k" + k + "?");
        System.out.println("  " + "-".repeat(48));

        boolean allIn = true;
        for (String mk : mstKeys) {
            boolean in = spannerSet.contains(mk);
            if (!in) allIn = false;
            String[] p = mk.split("_");
            System.out.printf("  (v%-2s, v%-2s)            %6d  %10s%n",
                    p[0], p[1], wMap.getOrDefault(mk, -1),
                    in ? "YES  ✓" : "NO  ✗  ← VIOLATION");
        }
        System.out.println("  " + "-".repeat(48));
        System.out.println("  " + (allIn
                ? "ALL " + mstKeys.size() + " MST edges are in H_k" + k + "  ✓"
                : "VIOLATION: MST edge(s) missing!  ✗"));
        System.out.println();
        return allIn;
    }

    // =========================================================================
    // Stretch verification
    // =========================================================================
    static double verifyStretch(int[][] gDist, int[][] hDist, int k) {
        int stretch = 2 * k - 1;
        int violations = 0;
        double maxRatio = 0;
        int wu = -1, wv = -1;
        for (int u = 0; u < N; u++)
            for (int v = u+1; v < N; v++) {
                if (gDist[u][v] >= INF) continue;
                if (hDist[u][v] > (long) stretch * gDist[u][v]) violations++;
                double r = (double) hDist[u][v] / gDist[u][v];
                if (r > maxRatio) { maxRatio = r; wu = u; wv = v; }
            }
        System.out.printf("  Stretch check (bound=%d): violations=%d, max ratio=%.4f" +
                        " at (v%d,v%d)  %s%n",
                stretch, violations, maxRatio, wu+1, wv+1,
                violations == 0 ? "PASS" : "FAIL");
        return maxRatio;
    }

    static void checkBound(int edgeCount, int k) {
        double bound = Math.pow(N, 1.0 + 1.0/k);
        System.out.printf("  Edge-count bound: |E_H|=%d <= n^(1+1/k)=%.2f  %s%n",
                edgeCount, bound, edgeCount <= bound ? "PASS" : "FAIL");
    }

    // =========================================================================
    // DOT export
    //   Blue  thick   = MST edge kept      → always present, proves theorem
    //   Red           = non-MST edge kept
    //   Grey  dashed  = non-MST removed
    //   Orange dashed = MST removed        → should NEVER appear
    //
    //  For n=10 / ~30 edges we show weight labels on ALL edges (readable).
    // =========================================================================
    static void exportDot(List<int[]> allEdges, List<int[]> spannerEdges,
                          Set<String> mstKeys, String title, String filename)
            throws IOException {
        Set<String> spannerSet = new HashSet<>();
        if (spannerEdges != null)
            for (int[] e : spannerEdges)
                spannerSet.add(eKey(e[0], e[1]));

        StringBuilder sb = new StringBuilder();
        sb.append("graph \"").append(title).append("\" {\n");
        sb.append("  layout=neato;\n");
        sb.append("  overlap=false;\n");
        sb.append("  sep=\"+18\";\n");           // extra padding between nodes
        sb.append("  node [shape=circle, style=filled, fillcolor=lightyellow,")
          .append(" fontsize=14, width=0.55, fixedsize=true];\n\n");

        for (int i = 0; i < N; i++)
            sb.append("  v").append(i+1)
              .append(" [label=\"v").append(i+1).append("\"];\n");
        sb.append("\n");

        for (int[] e : allEdges) {
            int u = e[0], v = e[1], w = e[2];
            String key = eKey(u, v);
            boolean inSpanner = (spannerEdges == null) || spannerSet.contains(key);
            boolean inMST     = mstKeys.contains(key);

            sb.append("  v").append(u+1).append(" -- v").append(v+1);
            sb.append(" [label=\"").append(w).append("\"");

            if      ( inMST &&  inSpanner)
                sb.append(", color=blue, penwidth=4, fontcolor=blue, fontsize=11");
            else if (!inMST &&  inSpanner)
                sb.append(", color=red,  penwidth=2, fontsize=10");
            else if ( inMST && !inSpanner)
                sb.append(", color=orange, penwidth=3, style=dashed, fontsize=10");
            else
                sb.append(", color=\"#bbbbbb\", style=dashed, penwidth=1, fontsize=9," +
                          " fontcolor=\"#999999\"");
            sb.append("];\n");
        }
        sb.append("}\n");

        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            pw.print(sb);
        }
        System.out.println("  DOT written: " + filename);
    }

    static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported())
                Desktop.getDesktop().browse(new URI(url));
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // Main
    // =========================================================================
    public static void main(String[] args) throws Exception {
        final long SEED       = 42L;
        final int  TARGET_E   = 30;
        Random rng = new Random(SEED);

        System.out.println("============================================================");
        System.out.println("  MST-Containment Test  --  Greedy Spanner, N=10, ~30 edges");
        System.out.println("  Althöfer et al. (1993)");
        System.out.println("============================================================");
        System.out.printf("  N      : %d%n", N);
        System.out.printf("  |E_G|  : %d  (target)%n", TARGET_E);
        System.out.printf("  Seed   : %d%n", SEED);
        System.out.printf("  Weights: random integers in [1, 50]%n");
        System.out.printf("  k vals : %s%n%n", Arrays.toString(KS));
        System.out.println("  Theorem: MST ⊆ H_k for every k >= 1.");
        System.out.println("  For k=1 the greedy spanner = MST (Kruskal's algorithm).");
        System.out.println();

        // ── Build random connected graph with exactly TARGET_E edges ──────────
        // Step 1: random spanning tree (guarantees connectivity)
        List<int[]> gEdges = new ArrayList<>();
        Set<String> existingKeys = new HashSet<>();

        // Shuffle node order so the tree isn't always a path from 0
        int[] perm = new int[N];
        for (int i = 0; i < N; i++) perm[i] = i;
        for (int i = N-1; i > 0; i--) {
            int j = rng.nextInt(i+1);
            int t = perm[i]; perm[i] = perm[j]; perm[j] = t;
        }
        for (int i = 1; i < N; i++) {
            int u = perm[rng.nextInt(i)];
            int v = perm[i];
            int w = rng.nextInt(50) + 1;
            gEdges.add(new int[]{Math.min(u,v), Math.max(u,v), w});
            existingKeys.add(eKey(u, v));
        }

        // Step 2: add random extra edges until TARGET_E
        int attempts = 0;
        while (gEdges.size() < TARGET_E && attempts < 100_000) {
            int u = rng.nextInt(N);
            int v = rng.nextInt(N);
            if (u == v) { attempts++; continue; }
            String key = eKey(u, v);
            if (existingKeys.contains(key)) { attempts++; continue; }
            int w = rng.nextInt(50) + 1;
            gEdges.add(new int[]{Math.min(u,v), Math.max(u,v), w});
            existingKeys.add(key);
            attempts = 0;
        }
        gEdges.sort(Comparator.comparingInt(e -> e[0] * N + e[1]));

        System.out.println("------------------------------------------------------------");
        System.out.printf("  Graph generated: |V|=%d, |E|=%d%n", N, gEdges.size());
        System.out.println("------------------------------------------------------------");
        System.out.println("  Edge list:");
        for (int[] e : gEdges)
            System.out.printf("    (v%2d, v%2d)  w = %2d%n", e[0]+1, e[1]+1, e[2]);
        System.out.println();

        // ── All-pairs SP in G ─────────────────────────────────────────────────
        List<List<int[]>> gAdj = buildAdj(gEdges);
        int[][] gDist = allPairsSP(gAdj);

        // ── Print distance table ──────────────────────────────────────────────
        System.out.println("------------------------------------------------------------");
        System.out.println("  [TABLE] All-pairs shortest-path distances in G:");
        System.out.println("------------------------------------------------------------");
        System.out.print("         ");
        for (int j = 0; j < N; j++) System.out.printf(" v%2d", j+1);
        System.out.println();
        System.out.print("         ");
        System.out.println("----".repeat(N));
        for (int i = 0; i < N; i++) {
            System.out.printf("  v%2d  |", i+1);
            for (int j = 0; j < N; j++) {
                if (gDist[i][j] >= INF) System.out.printf("   ∞");
                else                    System.out.printf(" %3d", gDist[i][j]);
            }
            System.out.println();
        }
        System.out.println();

        // ── Kruskal MST ───────────────────────────────────────────────────────
        Set<String> mstKeys = buildMST(gEdges);
        Map<String, Integer> wMap = new HashMap<>();
        for (int[] e : gEdges) wMap.put(eKey(e[0], e[1]), e[2]);

        List<int[]> mstEdges = new ArrayList<>();
        for (int[] e : gEdges)
            if (mstKeys.contains(eKey(e[0], e[1]))) mstEdges.add(e);

        System.out.println("------------------------------------------------------------");
        System.out.println("  MST  (Kruskal's algorithm):  blue in DOT files");
        System.out.println("------------------------------------------------------------");
        int mstWeight = 0;
        for (int[] e : mstEdges) {
            System.out.printf("    (v%2d, v%2d)  w = %2d%n", e[0]+1, e[1]+1, e[2]);
            mstWeight += e[2];
        }
        System.out.printf("  |MST| = %d edges  (= N-1 = %d  ✓),  total weight = %d%n%n",
                mstEdges.size(), N-1, mstWeight);

        // Export original with MST in blue
        exportDot(gEdges, gEdges, mstKeys,
                "G original — N=10, 30 edges  (blue = MST)",
                "N10_G_original.dot");
        System.out.println("  Blue = MST.  Paste at https://dreampuf.github.io/GraphvizOnline/");
        System.out.println();

        // ── Summary accumulators ──────────────────────────────────────────────
        int[]     spanSizes = new int[KS.length];
        double[]  maxRatios = new double[KS.length];
        boolean[] mstOk     = new boolean[KS.length];

        // ── Per-k detailed runs ───────────────────────────────────────────────
        for (int ki = 0; ki < KS.length; ki++) {
            int k = KS[ki];
            int stretch = 2 * k - 1;

            System.out.println("============================================================");
            System.out.printf("  k = %d  →  %d-spanner  (dist_H <= %d · dist_G)%n",
                    k, stretch, stretch);
            System.out.println("============================================================");
            System.out.println();

            List<int[]> spannerEdges = greedySpanner(gEdges, k, mstKeys);
            spanSizes[ki] = spannerEdges.size();

            mstOk[ki] = checkMSTContainment(spannerEdges, mstKeys, wMap, k);

            List<List<int[]>> hAdj = buildAdj(spannerEdges);
            int[][] hDist = allPairsSP(hAdj);

            maxRatios[ki] = verifyStretch(gDist, hDist, k);
            checkBound(spanSizes[ki], k);
            System.out.println();

            // H distance table
            System.out.printf("  [TABLE H_k%d] All-pairs shortest-path distances in H:%n", k);
            System.out.print("         ");
            for (int j = 0; j < N; j++) System.out.printf(" v%2d", j+1);
            System.out.println();
            System.out.print("         ");
            System.out.println("----".repeat(N));
            for (int i = 0; i < N; i++) {
                System.out.printf("  v%2d  |", i+1);
                for (int j = 0; j < N; j++) {
                    if (hDist[i][j] >= INF) System.out.printf("   ∞");
                    else                    System.out.printf(" %3d", hDist[i][j]);
                }
                System.out.println();
            }
            System.out.println();

            // DOT
            String fname = "N10_H_k" + k + ".dot";
            exportDot(gEdges, spannerEdges, mstKeys,
                    "H_k" + k + " (" + stretch + "-spanner)  blue=MST  red=extra  grey=removed",
                    fname);
            System.out.println("  Paste at: https://dreampuf.github.io/GraphvizOnline/");
            System.out.println();
        }

        // ── Summary table ─────────────────────────────────────────────────────
        System.out.println("============================================================");
        System.out.println("  SUMMARY");
        System.out.println("============================================================");
        System.out.printf("  %-4s  %-8s  %-6s  %-6s  %-11s  %-11s  %-13s  %-10s%n",
                "k", "stretch", "|E_G|", "|E_H|",
                "n^(1+1/k)", "reduction", "max stretch", "MST ⊆ H?");
        System.out.println("  " + "-".repeat(80));
        for (int ki = 0; ki < KS.length; ki++) {
            int k = KS[ki];
            double bound  = Math.pow(N, 1.0 + 1.0/k);
            double redPct = 100.0 * (gEdges.size() - spanSizes[ki]) / gEdges.size();
            System.out.printf("  %-4d  %-8d  %-6d  %-6d  %-11.2f  %-10.1f%%  %-13.4f  %s%n",
                    k, 2*k-1, gEdges.size(), spanSizes[ki],
                    bound, redPct, maxRatios[ki],
                    mstOk[ki] ? "YES  ✓" : "NO  ✗");
        }
        System.out.println("  " + "-".repeat(80));
        System.out.println();
        System.out.println("  DOT files generated:");
        System.out.println("    N10_G_original.dot   ← full graph, MST in blue");
        for (int k : KS)
            System.out.printf("    N10_H_k%d.dot         ← %d-spanner" +
                            "  (blue=MST, red=extra, grey=removed)%n", k, 2*k-1);
        System.out.println();
        System.out.println("  Paste any file at: https://dreampuf.github.io/GraphvizOnline/");
        openBrowser("https://dreampuf.github.io/GraphvizOnline/");
    }
}
