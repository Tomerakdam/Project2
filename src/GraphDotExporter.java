import org.jgrapht.alg.spanning.KruskalMinimumSpanningTree;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * DOT export utilities for weighted graph/spanner visual examples.
 *
 * The overlay export draws the original graph and highlights the greedy spanner:
 * - blue bold edges: one minimum spanning tree contained in the spanner
 * - red bold edges: extra spanner edges
 * - gray dashed edges: original edges removed by the spanner
 *
 * In graphs with equal-weight ties there may be several valid MSTs.  For
 * visualization we compute the highlighted MST from the spanner itself, so
 * every blue edge is actually present in the displayed spanner.
 *
 * Render with Graphviz, for example:
 *   dot -Tpng results/dot/example.dot -o results/dot/example.png
 */
public final class GraphDotExporter {
    private static final double EPS = 1e-9;

    private GraphDotExporter() {
        // Utility class; do not instantiate.
    }

    /**
     * Export one graph with all edge weights shown.
     */
    public static <V> void writeGraph(
            SimpleWeightedGraph<V, DefaultWeightedEdge> graph,
            Path outputPath,
            String title
    ) throws IOException {
        Objects.requireNonNull(graph, "graph must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");

        createParentDirectories(outputPath);

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("graph G {\n");
            writeGraphAttributes(writer, title);
            writeVertices(writer, graph);

            for (DefaultWeightedEdge edge : sortedEdges(graph)) {
                V u = graph.getEdgeSource(edge);
                V v = graph.getEdgeTarget(edge);
                double weight = graph.getEdgeWeight(edge);
                writer.write(String.format(
                        Locale.US,
                        "  %s -- %s [label=\"%s\", color=\"#444444\", penwidth=1.8];%n",
                        dotId(u),
                        dotId(v),
                        formatWeight(weight)
                ));
            }

            writer.write("}\n");
        }
    }

    /**
     * Export the original graph as an overlay where spanner edges are highlighted.
     */
    public static <V> void writeOverlay(
            SimpleWeightedGraph<V, DefaultWeightedEdge> original,
            SimpleWeightedGraph<V, DefaultWeightedEdge> spanner,
            Path outputPath,
            String title
    ) throws IOException {
        Objects.requireNonNull(original, "original must not be null");
        Objects.requireNonNull(spanner, "spanner must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");
        GraphMetrics.validateSameVertices(original, spanner);

        createParentDirectories(outputPath);
        Set<EdgeKey> mstEdges = mstEdgeKeys(spanner);

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("graph G {\n");
            writeGraphAttributes(writer, title + " | blue=chosen MST in spanner, red=extra spanner, gray dashed=removed");
            writeVertices(writer, original);

            for (DefaultWeightedEdge edge : sortedEdges(original)) {
                V u = original.getEdgeSource(edge);
                V v = original.getEdgeTarget(edge);
                double originalWeight = original.getEdgeWeight(edge);
                DefaultWeightedEdge spannerEdge = spanner.getEdge(u, v);
                boolean inSpanner = spannerEdge != null;
                boolean sameWeight = !inSpanner || Math.abs(spanner.getEdgeWeight(spannerEdge) - originalWeight) <= EPS;

                if (inSpanner && !sameWeight) {
                    throw new IllegalArgumentException("spanner edge weight differs from original edge weight for " + u + " -- " + v);
                }

                EdgeStyle style;
                if (!inSpanner) {
                    style = EdgeStyle.REMOVED;
                } else if (mstEdges.contains(EdgeKey.of(u, v))) {
                    style = EdgeStyle.MST;
                } else {
                    style = EdgeStyle.EXTRA_SPANNER;
                }

                writer.write(String.format(
                        Locale.US,
                        "  %s -- %s [label=\"%s\", color=\"%s\", style=\"%s\", penwidth=%.1f];%n",
                        dotId(u),
                        dotId(v),
                        formatWeight(originalWeight),
                        style.color,
                        style.lineStyle,
                        style.penWidth
                ));
            }

            writer.write("}\n");
        }
    }

    private static void createParentDirectories(Path outputPath) throws IOException {
        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static void writeGraphAttributes(BufferedWriter writer, String title) throws IOException {
        writer.write("  graph [layout=neato, overlap=false, splines=true, outputorder=edgesfirst");
        if (title != null && !title.isBlank()) {
            writer.write(", label=\"" + escape(title) + "\", labelloc=t, fontsize=18");
        }
        writer.write("];\n");
        writer.write("  node [shape=circle, style=filled, fillcolor=white, color=\"#222222\", fontname=\"Arial\", fontsize=11];\n");
        writer.write("  edge [fontname=\"Arial\", fontsize=9];\n");
    }

    private static <V> void writeVertices(BufferedWriter writer, SimpleWeightedGraph<V, DefaultWeightedEdge> graph) throws IOException {
        for (V vertex : sortedVertices(graph)) {
            writer.write(String.format(Locale.US, "  %s [label=\"%s\"];%n", dotId(vertex), escape(String.valueOf(vertex))));
        }
    }

    private static <V> List<V> sortedVertices(SimpleWeightedGraph<V, DefaultWeightedEdge> graph) {
        List<V> vertices = new ArrayList<>(graph.vertexSet());
        vertices.sort(Comparator.comparing(String::valueOf));
        return vertices;
    }

    private static <V> List<DefaultWeightedEdge> sortedEdges(SimpleWeightedGraph<V, DefaultWeightedEdge> graph) {
        List<DefaultWeightedEdge> edges = new ArrayList<>(graph.edgeSet());
        edges.sort(Comparator
                .comparing((DefaultWeightedEdge e) -> String.valueOf(graph.getEdgeSource(e)))
                .thenComparing(e -> String.valueOf(graph.getEdgeTarget(e)))
                .thenComparingDouble(graph::getEdgeWeight));
        return edges;
    }

    private static <V> Set<EdgeKey> mstEdgeKeys(SimpleWeightedGraph<V, DefaultWeightedEdge> graph) {
        Set<EdgeKey> keys = new HashSet<>();
        for (DefaultWeightedEdge edge : new KruskalMinimumSpanningTree<>(graph).getSpanningTree().getEdges()) {
            keys.add(EdgeKey.of(graph.getEdgeSource(edge), graph.getEdgeTarget(edge)));
        }
        return keys;
    }

    private static String dotId(Object value) {
        return "\"" + escape(String.valueOf(value)) + "\"";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String formatWeight(double weight) {
        if (Math.abs(weight - Math.rint(weight)) < EPS) {
            return String.format(Locale.US, "%.0f", weight);
        }
        return String.format(Locale.US, "%.2f", weight);
    }

    private enum EdgeStyle {
        MST("#1f77b4", "solid", 3.2),
        EXTRA_SPANNER("#d62728", "solid", 2.6),
        REMOVED("#aaaaaa", "dashed", 1.1);

        private final String color;
        private final String lineStyle;
        private final double penWidth;

        EdgeStyle(String color, String lineStyle, double penWidth) {
            this.color = color;
            this.lineStyle = lineStyle;
            this.penWidth = penWidth;
        }
    }

    private static final class EdgeKey {
        private final String a;
        private final String b;

        private EdgeKey(String a, String b) {
            this.a = a;
            this.b = b;
        }

        private static EdgeKey of(Object u, Object v) {
            String first = String.valueOf(u);
            String second = String.valueOf(v);
            if (first.compareTo(second) <= 0) {
                return new EdgeKey(first, second);
            }
            return new EdgeKey(second, first);
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
            return a.equals(that.a) && b.equals(that.b);
        }

        @Override
        public int hashCode() {
            return Objects.hash(a, b);
        }
    }
}
