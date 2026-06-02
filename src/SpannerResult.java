/**
 * Immutable summary of one greedy weighted spanner construction run.
 */
public final class SpannerResult {
    private static final double EPS = 1e-9;

    public final String graphName;
    public final int n;
    public final int originalEdges;
    public final int spannerEdges;
    public final int k;
    public final double stretch;
    public final double originalWeight;
    public final double spannerWeight;
    public final double mstWeight;
    public final double maxStretch;
    public final int stretchViolations;
    public final long runtimeMs;

    public SpannerResult(
            String graphName,
            int n,
            int originalEdges,
            int spannerEdges,
            int k,
            double stretch,
            double originalWeight,
            double spannerWeight,
            double mstWeight,
            double maxStretch,
            int stretchViolations,
            long runtimeMs
    ) {
        this.graphName = graphName;
        this.n = n;
        this.originalEdges = originalEdges;
        this.spannerEdges = spannerEdges;
        this.k = k;
        this.stretch = stretch;
        this.originalWeight = originalWeight;
        this.spannerWeight = spannerWeight;
        this.mstWeight = mstWeight;
        this.maxStretch = maxStretch;
        this.stretchViolations = stretchViolations;
        this.runtimeMs = runtimeMs;
    }

    public double edgeReductionPercent() {
        if (originalEdges == 0) {
            return 0.0;
        }
        return 100.0 * (originalEdges - spannerEdges) / originalEdges;
    }

    public double weightOverMst() {
        if (mstWeight <= EPS) {
            return Double.NaN;
        }
        return spannerWeight / mstWeight;
    }

    public double allowedStretch() {
        return 2.0 * k - 1.0;
    }

    public double stretchUtilization() {
        double allowed = allowedStretch();
        if (allowed <= EPS) {
            return Double.NaN;
        }
        return maxStretch / allowed;
    }

    /**
     * Report-friendly size-scale normalization for general (2k-1)-spanners.
     *
     * This is the standard n^(1+1/k) scale used to compare measured sparsity in
     * the experiments.  In the Althofer et al. paper the main theorem uses a
     * different parameter name, so the report should explain the parameter
     * mapping instead of quoting this expression as the theorem verbatim.
     */
    public double generalSizeBound() {
        if (n <= 0 || k <= 0) {
            return Double.NaN;
        }
        return Math.pow(n, 1.0 + 1.0 / k);
    }

    public double generalSizeBoundRatio() {
        double bound = generalSizeBound();
        if (Double.isNaN(bound) || bound <= EPS) {
            return Double.NaN;
        }
        return spannerEdges / bound;
    }

    /**
     * Planar theorem bound for graph families that are known planar in our
     * experiment suite.
     *
     * Paper notation: stretch = 2t+1.
     * Project notation: stretch = 2k-1.
     * Therefore t = k-1, so this bound is meaningful only for k >= 2.
     */
    public double planarSizeBound() {
        if (!isPlanarBenchmarkFamily() || k < 2 || n <= 0) {
            return Double.NaN;
        }
        return (n - 1.0) * (1.0 + 1.0 / (k - 1.0));
    }

    public double planarSizeBoundRatio() {
        double bound = planarSizeBound();
        if (Double.isNaN(bound) || bound <= EPS) {
            return Double.NaN;
        }
        return spannerEdges / bound;
    }

    private boolean isPlanarBenchmarkFamily() {
        return graphName.startsWith("path")
                || graphName.startsWith("cycle")
                || graphName.startsWith("random_tree")
                || graphName.startsWith("grid")
                || graphName.startsWith("theta_lower_bound")
                || graphName.startsWith("small_triangle");
    }

    public String toCsvRow() {
        return String.format(
                java.util.Locale.US,
                "%s,%d,%d,%d,%d,%.6f,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f",
                graphName,
                n,
                originalEdges,
                spannerEdges,
                k,
                stretch,
                spannerEdges,
                edgeReductionPercent(),
                originalWeight,
                spannerWeight,
                mstWeight,
                maxStretch,
                stretchViolations,
                runtimeMs,
                weightOverMst(),
                allowedStretch(),
                stretchUtilization(),
                generalSizeBound(),
                generalSizeBoundRatio(),
                planarSizeBound(),
                planarSizeBoundRatio()
        );
    }

    public static String csvHeader() {
        return "graph,n,original_edges,spanner_edges,k,stretch,selected_edges,edge_reduction_percent," +
                "original_weight,spanner_weight,mst_weight,max_stretch,stretch_violations,runtime_ms," +
                "weight_over_mst,allowed_stretch,stretch_utilization," +
                "general_size_bound,general_size_bound_ratio,planar_size_bound,planar_size_bound_ratio";
    }

    @Override
    public String toString() {
        return String.format(
                java.util.Locale.US,
                "%s: n=%d, m=%d, k=%d, stretch=%.1f, |E_H|=%d, reduction=%.2f%%, " +
                        "w(G)=%.2f, w(H)=%.2f, w(MST)=%.2f, w(H)/w(MST)=%.4f, " +
                        "maxStretch=%.4f, utilization=%.4f, violations=%d, runtime=%dms",
                graphName,
                n,
                originalEdges,
                k,
                stretch,
                spannerEdges,
                edgeReductionPercent(),
                originalWeight,
                spannerWeight,
                mstWeight,
                weightOverMst(),
                maxStretch,
                stretchUtilization(),
                stretchViolations,
                runtimeMs
        );
    }
}
