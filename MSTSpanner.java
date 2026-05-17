import java.util.*;
import java.io.*;
import java.awt.Desktop;
import java.net.URI;

/**
 * MST-Containment Test for the Greedy Spanner  (Althöfer et al., 1993)
 *
 * Graph  : Complete graph K_N  —  O(N²) edges
 * N      : 15   →  105 edges
 * Weights: Random integers in [1, 200], seed 42 (all distinct with high probability)
 * k vals : 5, 4, 3, 2, 1  (stretch 9, 7, 5, 3, 1)
 *
 * ── Key Theorem ─────────────────────────────────────────────────────────────
 * The greedy (2k-1)-spanner H always contains every MST edge of G.
 *
 * Proof sketch (distinct edge weights):
 *   Sort all edges by weight and process them one by one (greedy spanner rule).
 *   Take any MST edge e = (u, v, w).
 *   Claim: when e is processed, dist_H(u,v) = INF.
 *
 *   Suppose, for contradiction, H already has a path P from u to v.
 *   Every edge on P has weight < w  (all lighter edges were processed first).
 *   So the cycle C = P ∪ {e} has e as its UNIQUE maximum-weight edge.
 *   By the MST cycle property, the max-weight edge of any cycle cannot be in
 *   any MST.  But e is in the MST — contradiction.
 *
 *   Therefore dist_H(u,v) = INF > (2k-1)·w for any k ≥ 1  →  ADD e.
 *   QED: every MST edge is always added, for every k.
 *
 * Special case k=1 (stretch=1):
 *   The greedy 1-spanner adds e iff dist_H(u,v) > w.
 *   This is exactly Kruskal's algorithm  →  H_1 = MST.
 *
 * DOT colour legend:
 *   Blue  (thick)   = MST edge, kept in spanner      ← should be ALL MST edges
 *   Red             = non-MST edge, kept in spanner
 *   Grey  (dashed)  = non-MST edge, removed
 *   Orange(dashed)  = MST edge removed  (should NEVER appear if theorem holds)
 */
public class MSTSpanner {

    static final int N    = 15;
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
    // Greedy Spanner — with step trace and MST column
    // =========================================================================
    static List<int[]> greedySpanner(List<int[]> edges, int k, Set<String> mstKeys) {
        int stretch = 2 * k - 1;
        List<int[]> sorted = new ArrayList<>(edges);
        sorted.sort(Comparator.comparingInt(e -> e[2]));

        List<List<int[]>> hAdj = new ArrayList<>();
        for (int i = 0; i < N; i++) hAdj.add(new ArrayList<>());
        List<int[]> spanner = new ArrayList<>();

        System.out.println("  " + "-".repeat(75));
        System.out.printf("  %-14s  %5s  %12s  %10s  %6s  %8s%n",
                "Edge", "w", "dist_H(u,v)", stretch+"·w", "MST?", "Decision");
        System.out.println("  " + "-".repeat(75));

        for (int[] edge : sorted) {
            int u = edge[0], v = edge[1], w = edge[2];
            boolean inMST = mstKeys.contains(eKey(u, v));
            int[] dH  = dijkstra(u, hAdj);
            int   dHv = dH[v];
            long  thr = (long) stretch * w;
            boolean add = dHv > thr;

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
        System.out.println("  " + "-".repeat(75));
        System.out.println();
        return spanner;
    }

    // =========================================================================
    // MST containment check
    // =========================================================================
    static boolean checkMSTContainment(List<int[]> spannerEdges, Set<String> mstKeys,
                                       List<int[]> allEdges, int k) {
        Set<String> spannerSet = new HashSet<>();
        for (int[] e : spannerEdges)
            spannerSet.add(eKey(e[0], e[1]));

        // Build a quick weight lookup
        Map<String, Integer> wMap = new HashMap<>();
        for (int[] e : allEdges) wMap.put(eKey(e[0], e[1]), e[2]);

        System.out.printf("  MST containment in H_k%d:%n", k);
        System.out.println("  " + "-".repeat(50));
        System.out.printf("  %-20s  %6s  %10s%n", "MST edge", "weight", "in H?");
        System.out.println("  " + "-".repeat(50));

        boolean allIn = true;
        for (String mk : mstKeys) {
            boolean contained = spannerSet.contains(mk);
            if (!contained) allIn = false;
            String[] p = mk.split("_");
            int w = wMap.getOrDefault(mk, -1);
            System.out.printf("  (v%-2s, v%-2s)           %6d  %10s%n",
                    p[0], p[1], w, contained ? "YES  ✓" : "NO  ✗  ← VIOLATION");
        }

        System.out.println("  " + "-".repeat(50));
        System.out.println("  " + (allIn
                ? "ALL " + mstKeys.size() + " MST edges are in H_k" + k + "  ✓"
                : "VIOLATION: some MST edges are missing!  ✗"));
        System.out.println();
        return allIn;
    }

    // =========================================================================
    // Stretch verification (summary line)
    // =========================================================================
    static double verifyStretch(int[][] gDist, int[][] hDist, int k) {
        int stretch = 2 * k - 1;
        int violations = 0;
        double maxRatio = 0;
        for (int u = 0; u < N; u++)
            for (int v = u+1; v < N; v++) {
                if (gDist[u][v] >= INF) continue;
                if (hDist[u][v] > (long) stretch * gDist[u][v]) violations++;
                double r = (double) hDist[u][v] / gDist[u][v];
                if (r > maxRatio) maxRatio = r;
            }
        System.out.printf("  Stretch check (bound=%d): violations=%d, max ratio=%.4f  %s%n",
                stretch, violations, maxRatio, violations == 0 ? "PASS" : "FAIL");
        return maxRatio;
    }

    // =========================================================================
    // Edge-count bound
    // =========================================================================
    static void checkBound(int edgeCount, int k) {
        double bound = Math.pow(N, 1.0 + 1.0/k);
        System.out.printf("  Edge-count bound: |E_H|=%d <= n^(1+1/k)=%.2f  %s%n",
                edgeCount, bound, edgeCount <= bound ? "PASS" : "FAIL");
    }

    // =========================================================================
    // DOT export
    //   Blue  thick  = MST edge in spanner        (the expected case)
    //   Red          = non-MST edge in spanner
    //   Grey  dashed = non-MST edge removed
    //   Orange dashed= MST edge removed  (theorem says this never happens)
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
        sb.append("  layout=sfdp;\n");
        sb.append("  overlap=prism;\n");
        sb.append("  repulsiveforce=1.5;\n");
        sb.append("  node [shape=circle, style=filled, fillcolor=lightyellow,")
          .append(" fontsize=10, width=0.3, fixedsize=true];\n\n");

        for (int i = 0; i < N; i++)
            sb.append("  v").append(i+1).append(" [label=\"v").append(i+1).append("\"];\n");
        sb.append("\n");

        for (int[] e : allEdges) {
            int u = e[0], v = e[1], w = e[2];
            String key = eKey(u, v);
            boolean inSpanner = (spannerEdges == null) || spannerSet.contains(key);
            boolean inMST     = mstKeys.contains(key);

            sb.append("  v").append(u+1).append(" -- v").append(v+1);
            // Only show weight label on MST (blue) and kept non-MST (red) edges
            // Grey removed edges get no label to avoid clutter
            if (inMST && inSpanner) {
                sb.append(" [label=\"").append(w).append("\"");
                sb.append(", color=blue, penwidth=4, fontcolor=blue, fontsize=9");
            } else if (!inMST && inSpanner) {
                sb.append(" [label=\"").append(w).append("\"");
                sb.append(", color=red, penwidth=2, fontsize=8");
            } else if (inMST && !inSpanner) {
                sb.append(" [label=\"").append(w).append("\"");
                sb.append(", color=orange, penwidth=3, style=dashed, fontsize=8");
            } else {
                sb.append(" [label=\"\"");   // no label on grey removed edges
                sb.append(", color=\"#cccccc\", style=dashed, penwidth=0.5");
            }
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
        final long SEED    = 42L;
        final int  TOTAL_E = N * (N - 1) / 2;
        Random rng = new Random(SEED);

        System.out.println("============================================================");
        System.out.println("  MST-Containment Test  --  Greedy Spanner on K15");
        System.out.println("  Althöfer et al. (1993)");
        System.out.println("============================================================");
        System.out.printf("  Graph  : K%d  (complete, %d edges = O(N²))%n", N, TOTAL_E);
        System.out.printf("  Seed   : %d%n", SEED);
        System.out.printf("  Weights: random integers in [1, 200]%n");
        System.out.printf("  k vals : %s%n%n", Arrays.toString(KS));
        System.out.println("  Theorem: the MST is a subgraph of H_k for EVERY k ≥ 1.");
        System.out.println("  Why:     for any MST edge e=(u,v,w), every other u-v path");
        System.out.println("           in G must use an edge heavier than w (MST cycle");
        System.out.println("           property). So when e is processed, dist_H(u,v)=INF");
        System.out.println("           → ADD.  For k=1 this gives H_1 = MST exactly.");
        System.out.println();

        // ── Build K_N ────────────────────────────────────────────────────────
        List<int[]> gEdges = new ArrayList<>();
        for (int u = 0; u < N; u++)
            for (int v = u+1; v < N; v++) {
                int w = rng.nextInt(200) + 1;
                gEdges.add(new int[]{u, v, w});
            }

        // ── All-pairs SP in G ─────────────────────────────────────────────────
        List<List<int[]>> gAdj = buildAdj(gEdges);
        int[][] gDist = allPairsSP(gAdj);

        // ── Kruskal MST ───────────────────────────────────────────────────────
        Set<String> mstKeys = buildMST(gEdges);
        List<int[]> mstEdges = new ArrayList<>();
        for (int[] e : gEdges)
            if (mstKeys.contains(eKey(e[0], e[1]))) mstEdges.add(e);

        System.out.println("------------------------------------------------------------");
        System.out.println("  MST  (Kruskal's algorithm):");
        System.out.println("------------------------------------------------------------");
        for (int[] e : mstEdges)
            System.out.printf("    (v%2d, v%2d)  w = %3d%n", e[0]+1, e[1]+1, e[2]);
        System.out.printf("  |MST| = %d edges  (= N-1 = %d  ✓)%n%n", mstEdges.size(), N-1);

        // Export original graph (MST in blue)
        exportDot(gEdges, gEdges, mstKeys,
                "K15 — G original (blue=MST, red=other)", "MST_G_original.dot");
        System.out.println("  Blue edges = MST.  Paste at https://dreampuf.github.io/GraphvizOnline/");
        System.out.println();

        // ── Arrays for summary ────────────────────────────────────────────────
        int[]    spanSizes   = new int[KS.length];
        double[] maxRatios   = new double[KS.length];
        boolean[] mstOk      = new boolean[KS.length];
        boolean[] boundOk    = new boolean[KS.length];

        // ── Per-k runs ────────────────────────────────────────────────────────
        for (int ki = 0; ki < KS.length; ki++) {
            int k = KS[ki];
            int stretch = 2 * k - 1;

            System.out.println("============================================================");
            System.out.printf("  k = %d  →  (%d)-spanner%n", k, stretch);
            System.out.println("============================================================");
            System.out.println();

            List<int[]> spannerEdges = greedySpanner(gEdges, k, mstKeys);
            spanSizes[ki] = spannerEdges.size();

            mstOk[ki]   = checkMSTContainment(spannerEdges, mstKeys, gEdges, k);

            List<List<int[]>> hAdj = buildAdj(spannerEdges);
            int[][] hDist = allPairsSP(hAdj);

            maxRatios[ki] = verifyStretch(gDist, hDist, k);
            double bound  = Math.pow(N, 1.0 + 1.0/k);
            boundOk[ki]   = spanSizes[ki] <= bound;
            checkBound(spanSizes[ki], k);
            System.out.println();

            // DOT
            String fname = "MST_H_k" + k + ".dot";
            exportDot(gEdges, spannerEdges, mstKeys,
                    "K15 — H_k" + k + " (" + stretch + "-spanner)  blue=MST  red=extra",
                    fname);
            System.out.println("  Paste at: https://dreampuf.github.io/GraphvizOnline/");
            System.out.println();
        }

        // ── Summary table ─────────────────────────────────────────────────────
        System.out.println("============================================================");
        System.out.println("  SUMMARY");
        System.out.println("============================================================");
        System.out.printf("  %-4s  %-8s  %-8s  %-8s  %-12s  %-12s  %-15s  %-12s%n",
                "k", "stretch", "|E_G|", "|E_H|",
                "n^(1+1/k)", "reduction", "max stretch", "MST ⊆ H?");
        System.out.println("  " + "-".repeat(90));
        for (int ki = 0; ki < KS.length; ki++) {
            int k = KS[ki];
            double bound = Math.pow(N, 1.0 + 1.0/k);
            double redPct = 100.0 * (TOTAL_E - spanSizes[ki]) / TOTAL_E;
            System.out.printf("  %-4d  %-8d  %-8d  %-8d  %-12.2f  %-11.1f%%  %-15.4f  %s%n",
                    k, 2*k-1, TOTAL_E, spanSizes[ki],
                    bound, redPct, maxRatios[ki],
                    mstOk[ki] ? "YES  ✓" : "NO  ✗");
        }
        System.out.println("  " + "-".repeat(90));
        System.out.println();
        System.out.println("  DOT files generated:");
        System.out.println("    MST_G_original.dot   ← original K15 with MST in blue");
        for (int k : KS)
            System.out.printf("    MST_H_k%d.dot         ← %d-spanner (blue=MST, red=extra, grey=removed)%n",
                    k, 2*k-1);
        System.out.println();
        System.out.println("  Paste any file at: https://dreampuf.github.io/GraphvizOnline/");

        openBrowser("https://dreampuf.github.io/GraphvizOnline/");
    }
}
