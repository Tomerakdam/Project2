import org.jgrapht.alg.spanning.KruskalMinimumSpanningTree;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Exports exact Java-generated visual-example data for scripts/make_plots.py.
 *
 * This class keeps the greedy spanner logic in Java only. Python receives the
 * already-built original graph, spanner membership flags, chosen-MST membership
 * flags, layout coordinates, and summary metrics, then only draws figures.
 *
 * The chosen MST is computed from the spanner, not directly from the original
 * graph. This avoids misleading colors when equal-weight ties allow multiple
 * valid MSTs: every blue edge is guaranteed to be an edge of the displayed
 * spanner.
 */
public final class VisualExampleExporter {
    private static final double EPS = 1e-9;

    private VisualExampleExporter() {
        // Utility class; do not instantiate.
    }

    public static void main(String[] args) throws IOException {
        Path outputDir = args.length >= 1 ? Paths.get(args[0]) : Paths.get("results", "plots", "visual_examples");
        Files.createDirectories(outputDir);

        List<Example> examples = List.of(
                new Example(
                        "complete_n10_seed102",
                        GraphFactory.completeGraph(10, 1, 30, 102L),
                        circularLayout(10, 1.0),
                        2,
                        false,
                        true,
                        true
                ),
                new Example(
                        "random_connected_n14_extra22_seed104",
                        GraphFactory.randomConnectedGraph(14, 22, 1, 30, 104L),
                        distortedCircularLayout(14, 1.0),
                        3,
                        true,
                        true,
                        true
                ),
                new Example(
                        "grid_5x5_weighted_seed601",
                        GraphFactory.gridGraph(5, 5, 1, 20, 601L),
                        gridLayout(5, 5),
                        3,
                        true,
                        true,
                        false
                ),
                new Example(
                        "theta_lower_bound_targetK3_chains6",
                        GraphFactory.thetaLowerBoundGraph(3, 6),
                        thetaLayout(3, 6),
                        3,
                        true,
                        true,
                        false
                ),
                new Example(
                        "cycle_threshold_n7",
                        GraphFactory.cycleGraph(7),
                        circularLayout(7, 1.0),
                        4,
                        false,
                        false,
                        true
                ),
                new Example(
                        "complete_unit_n20",
                        GraphFactory.completeGraph(20, 1, 1, 1000L),
                        circularLayout(20, 1.25),
                        2,
                        false,
                        false,
                        false
                )
        );

        Path edgesCsv = outputDir.resolve("visual_examples_edges.csv");
        Path summaryCsv = outputDir.resolve("visual_examples_summary.csv");

        try (BufferedWriter edgeWriter = Files.newBufferedWriter(edgesCsv, StandardCharsets.UTF_8);
             BufferedWriter summaryWriter = Files.newBufferedWriter(summaryCsv, StandardCharsets.UTF_8)) {
            edgeWriter.write("example,k,n,u,v,weight,in_spanner,in_mst,x_u,y_u,x_v,y_v,show_original_weights,show_spanner_weights,label_nodes");
            edgeWriter.newLine();

            summaryWriter.write("example,k,n,original_edges,spanner_edges,edge_reduction_percent,max_stretch,allowed_stretch,spanner_weight,mst_weight,weight_over_mst");
            summaryWriter.newLine();

            for (Example example : examples) {
                exportExample(example, edgeWriter, summaryWriter);
            }
        }

        System.out.println("Wrote " + edgesCsv.toAbsolutePath());
        System.out.println("Wrote " + summaryCsv.toAbsolutePath());
    }

    private static void exportExample(
            Example example,
            BufferedWriter edgeWriter,
            BufferedWriter summaryWriter
    ) throws IOException {
        SimpleWeightedGraph<Integer, DefaultWeightedEdge> spanner = GreedyWeightedSpanner.buildSpanner(example.graph, example.k);
        SpannerResult summary = GraphMetrics.summarize(example.name, example.graph, spanner, example.k, 0L);
        Set<EdgeKey> spannerEdges = edgeKeys(spanner);
        Set<EdgeKey> mstEdges = mstEdgeKeys(spanner);

        for (DefaultWeightedEdge edge : sortedEdges(example.graph)) {
            int u = example.graph.getEdgeSource(edge);
            int v = example.graph.getEdgeTarget(edge);
            double weight = example.graph.getEdgeWeight(edge);
            Coordinate cu = example.coordinates.get(u);
            Coordinate cv = example.coordinates.get(v);
            if (cu == null || cv == null) {
                throw new IllegalStateException("missing coordinates for edge " + u + " -- " + v);
            }

            EdgeKey key = EdgeKey.of(u, v);
            edgeWriter.write(String.format(
                    Locale.US,
                    "%s,%d,%d,%d,%d,%.6f,%s,%s,%.6f,%.6f,%.6f,%.6f,%s,%s,%s",
                    csv(example.name),
                    example.k,
                    example.graph.vertexSet().size(),
                    u,
                    v,
                    weight,
                    spannerEdges.contains(key),
                    mstEdges.contains(key),
                    cu.x,
                    cu.y,
                    cv.x,
                    cv.y,
                    example.showOriginalWeights,
                    example.showSpannerWeights,
                    example.labelNodes
            ));
            edgeWriter.newLine();
        }

        summaryWriter.write(String.format(
                Locale.US,
                "%s,%d,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f",
                csv(example.name),
                example.k,
                summary.n,
                summary.originalEdges,
                summary.spannerEdges,
                summary.edgeReductionPercent(),
                summary.maxStretch,
                summary.allowedStretch(),
                summary.spannerWeight,
                summary.mstWeight,
                summary.weightOverMst()
        ));
        summaryWriter.newLine();
    }

    private static Map<Integer, Coordinate> circularLayout(int n, double radius) {
        Map<Integer, Coordinate> coords = new HashMap<>();
        for (int i = 0; i < n; i++) {
            double angle = 2.0 * Math.PI * i / n;
            coords.put(i, new Coordinate(radius * Math.cos(angle), radius * Math.sin(angle)));
        }
        return coords;
    }

    private static Map<Integer, Coordinate> distortedCircularLayout(int n, double radius) {
        Map<Integer, Coordinate> base = circularLayout(n, radius);
        Map<Integer, Coordinate> coords = new HashMap<>();
        for (Map.Entry<Integer, Coordinate> entry : base.entrySet()) {
            int v = entry.getKey();
            Coordinate c = entry.getValue();
            double xScale = 1.0 + 0.10 * Math.sin(v);
            double yScale = 1.0 + 0.12 * Math.cos(2.0 * v);
            coords.put(v, new Coordinate(c.x * xScale, c.y * yScale));
        }
        return coords;
    }

    private static Map<Integer, Coordinate> gridLayout(int rows, int cols) {
        Map<Integer, Coordinate> coords = new HashMap<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int v = r * cols + c;
                coords.put(v, new Coordinate(c, rows - 1 - r));
            }
        }
        return coords;
    }

    private static Map<Integer, Coordinate> thetaLayout(int targetK, int chainCount) {
        Map<Integer, Coordinate> coords = new HashMap<>();
        coords.put(0, new Coordinate(-1.2, 0.0));
        coords.put(1, new Coordinate(1.2, 0.0));

        int nextVertex = 2;
        for (int chain = 0; chain < chainCount; chain++) {
            double offsetIndex = chain - (chainCount - 1) / 2.0;
            double amplitude = 0.35 + 0.22 * Math.abs(offsetIndex);
            if (chain % 2 == 1) {
                amplitude *= -1.0;
            }
            for (int i = 0; i < targetK; i++) {
                int current = nextVertex++;
                double x = -1.2 + (2.4 * (i + 1) / (targetK + 1));
                double y = amplitude * Math.sin(Math.PI * (i + 1) / (targetK + 1));
                y += 0.08 * offsetIndex;
                coords.put(current, new Coordinate(x, y));
            }
        }
        return coords;
    }

    private static Set<EdgeKey> edgeKeys(SimpleWeightedGraph<Integer, DefaultWeightedEdge> graph) {
        Set<EdgeKey> keys = new HashSet<>();
        for (DefaultWeightedEdge edge : graph.edgeSet()) {
            keys.add(EdgeKey.of(graph.getEdgeSource(edge), graph.getEdgeTarget(edge)));
        }
        return keys;
    }

    private static Set<EdgeKey> mstEdgeKeys(SimpleWeightedGraph<Integer, DefaultWeightedEdge> graph) {
        Set<EdgeKey> keys = new HashSet<>();
        for (DefaultWeightedEdge edge : new KruskalMinimumSpanningTree<>(graph).getSpanningTree().getEdges()) {
            keys.add(EdgeKey.of(graph.getEdgeSource(edge), graph.getEdgeTarget(edge)));
        }
        return keys;
    }

    private static List<DefaultWeightedEdge> sortedEdges(SimpleWeightedGraph<Integer, DefaultWeightedEdge> graph) {
        List<DefaultWeightedEdge> edges = new ArrayList<>(graph.edgeSet());
        edges.sort(Comparator
                .comparingInt((DefaultWeightedEdge e) -> Math.min(graph.getEdgeSource(e), graph.getEdgeTarget(e)))
                .thenComparingInt(e -> Math.max(graph.getEdgeSource(e), graph.getEdgeTarget(e)))
                .thenComparingDouble(graph::getEdgeWeight));
        return edges;
    }

    private static String csv(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static final class Example {
        private final String name;
        private final SimpleWeightedGraph<Integer, DefaultWeightedEdge> graph;
        private final Map<Integer, Coordinate> coordinates;
        private final int k;
        private final boolean showOriginalWeights;
        private final boolean showSpannerWeights;
        private final boolean labelNodes;

        private Example(
                String name,
                SimpleWeightedGraph<Integer, DefaultWeightedEdge> graph,
                Map<Integer, Coordinate> coordinates,
                int k,
                boolean showOriginalWeights,
                boolean showSpannerWeights,
                boolean labelNodes
        ) {
            this.name = Objects.requireNonNull(name, "name must not be null");
            this.graph = Objects.requireNonNull(graph, "graph must not be null");
            this.coordinates = Objects.requireNonNull(coordinates, "coordinates must not be null");
            this.k = k;
            this.showOriginalWeights = showOriginalWeights;
            this.showSpannerWeights = showSpannerWeights;
            this.labelNodes = labelNodes;
        }
    }

    private static final class Coordinate {
        private final double x;
        private final double y;

        private Coordinate(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    private static final class EdgeKey {
        private final int u;
        private final int v;

        private EdgeKey(int u, int v) {
            this.u = u;
            this.v = v;
        }

        private static EdgeKey of(int a, int b) {
            return a <= b ? new EdgeKey(a, b) : new EdgeKey(b, a);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof EdgeKey)) {
                return false;
            }
            EdgeKey that = (EdgeKey) other;
            return u == that.u && v == that.v;
        }

        @Override
        public int hashCode() {
            return Objects.hash(u, v);
        }
    }
}
