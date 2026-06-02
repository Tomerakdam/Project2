import org.jgrapht.alg.interfaces.ShortestPathAlgorithm;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.alg.spanning.KruskalMinimumSpanningTree;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Metric utilities for weighted spanner experiments.
 */
public final class GraphMetrics {
    private static final double EPS = 1e-9;

    private GraphMetrics() {
        // Utility class; do not instantiate.
    }

    public static <V> double totalWeight(SimpleWeightedGraph<V, DefaultWeightedEdge> graph) {
        Objects.requireNonNull(graph, "graph must not be null");
        double sum = 0.0;
        for (DefaultWeightedEdge edge : graph.edgeSet()) {
            sum += graph.getEdgeWeight(edge);
        }
        return sum;
    }

    public static <V> double mstWeight(SimpleWeightedGraph<V, DefaultWeightedEdge> graph) {
        Objects.requireNonNull(graph, "graph must not be null");
        return new KruskalMinimumSpanningTree<>(graph).getSpanningTree().getWeight();
    }

    public static <V> int mstEdgeCount(SimpleWeightedGraph<V, DefaultWeightedEdge> graph) {
        Objects.requireNonNull(graph, "graph must not be null");
        return new KruskalMinimumSpanningTree<>(graph).getSpanningTree().getEdges().size();
    }

    public static <V> double distance(
            SimpleWeightedGraph<V, DefaultWeightedEdge> graph,
            V source,
            V target
    ) {
        Objects.requireNonNull(graph, "graph must not be null");
        if (source.equals(target)) {
            return 0.0;
        }
        if (!graph.containsVertex(source) || !graph.containsVertex(target)) {
            return Double.POSITIVE_INFINITY;
        }
        return new DijkstraShortestPath<>(graph).getPathWeight(source, target);
    }

    public static <V> double maxStretch(
            SimpleWeightedGraph<V, DefaultWeightedEdge> original,
            SimpleWeightedGraph<V, DefaultWeightedEdge> spanner
    ) {
        return stretchStats(original, spanner, Double.POSITIVE_INFINITY).maxStretch;
    }

    public static <V> int countStretchViolations(
            SimpleWeightedGraph<V, DefaultWeightedEdge> original,
            SimpleWeightedGraph<V, DefaultWeightedEdge> spanner,
            double allowedStretch
    ) {
        return stretchStats(original, spanner, allowedStretch).stretchViolations;
    }

    public static <V> SpannerResult summarize(
            String graphName,
            SimpleWeightedGraph<V, DefaultWeightedEdge> original,
            SimpleWeightedGraph<V, DefaultWeightedEdge> spanner,
            int k,
            long runtimeMs
    ) {
        Objects.requireNonNull(graphName, "graphName must not be null");
        Objects.requireNonNull(original, "original must not be null");
        Objects.requireNonNull(spanner, "spanner must not be null");
        validateSameVertices(original, spanner);

        double stretch = 2.0 * k - 1.0;
        StretchStats stats = stretchStats(original, spanner, stretch);

        return new SpannerResult(
                graphName,
                original.vertexSet().size(),
                original.edgeSet().size(),
                spanner.edgeSet().size(),
                k,
                stretch,
                totalWeight(original),
                totalWeight(spanner),
                mstWeight(original),
                stats.maxStretch,
                stats.stretchViolations,
                runtimeMs
        );
    }

    /**
     * Checks whether the spanner contains at least one minimum spanning tree of
     * the original graph.
     *
     * Do not check a single arbitrary MST edge-by-edge here: when weights have
     * ties, several different MSTs can exist, and a library's chosen MST may not
     * be the same MST implicitly selected by the greedy edge order. Equal MST
     * weight inside the spanner is the correct tie-safe condition.
     */
    public static <V> boolean containsMinimumSpanningTree(
            SimpleWeightedGraph<V, DefaultWeightedEdge> original,
            SimpleWeightedGraph<V, DefaultWeightedEdge> spanner
    ) {
        Objects.requireNonNull(original, "original must not be null");
        Objects.requireNonNull(spanner, "spanner must not be null");
        validateSameVertices(original, spanner);

        if (spanner.vertexSet().isEmpty()) {
            return original.vertexSet().isEmpty();
        }

        double originalMstWeight = mstWeight(original);
        double spannerMstWeight = mstWeight(spanner);
        return Math.abs(originalMstWeight - spannerMstWeight) <= EPS;
    }

    /**
     * Backwards-compatible alias. The semantics are tie-safe: this returns true
     * when the spanner contains some MST of the original graph, not necessarily
     * every edge of the particular MST returned by JGraphT on the original graph.
     */
    public static <V> boolean containsAllMstEdges(
            SimpleWeightedGraph<V, DefaultWeightedEdge> original,
            SimpleWeightedGraph<V, DefaultWeightedEdge> spanner
    ) {
        return containsMinimumSpanningTree(original, spanner);
    }

    public static <V> void validateSameVertices(
            SimpleWeightedGraph<V, DefaultWeightedEdge> first,
            SimpleWeightedGraph<V, DefaultWeightedEdge> second
    ) {
        if (!first.vertexSet().equals(second.vertexSet())) {
            throw new IllegalArgumentException("graphs must have the same vertex set");
        }
    }

    /**
     * Computes pairwise stretch metrics with only n Dijkstra runs on the original graph
     * and n Dijkstra runs on the spanner, instead of recomputing Dijkstra for every pair.
     */
    private static <V> StretchStats stretchStats(
            SimpleWeightedGraph<V, DefaultWeightedEdge> original,
            SimpleWeightedGraph<V, DefaultWeightedEdge> spanner,
            double allowedStretch
    ) {
        Objects.requireNonNull(original, "original must not be null");
        Objects.requireNonNull(spanner, "spanner must not be null");
        validateSameVertices(original, spanner);

        List<V> vertices = new ArrayList<>(original.vertexSet());
        List<ShortestPathAlgorithm.SingleSourcePaths<V, DefaultWeightedEdge>> originalPaths = allSingleSourcePaths(original, vertices);
        List<ShortestPathAlgorithm.SingleSourcePaths<V, DefaultWeightedEdge>> spannerPaths = allSingleSourcePaths(spanner, vertices);

        double maxStretch = 1.0;
        int stretchViolations = 0;

        for (int i = 0; i < vertices.size(); i++) {
            for (int j = i + 1; j < vertices.size(); j++) {
                V target = vertices.get(j);
                double distG = originalPaths.get(i).getWeight(target);
                if (Double.isInfinite(distG) || distG <= EPS) {
                    continue;
                }

                double distH = spannerPaths.get(i).getWeight(target);
                if (Double.isInfinite(distH)) {
                    maxStretch = Double.POSITIVE_INFINITY;
                    stretchViolations++;
                    continue;
                }

                double ratio = distH / distG;
                maxStretch = Math.max(maxStretch, ratio);
                if (ratio > allowedStretch + EPS) {
                    stretchViolations++;
                }
            }
        }

        return new StretchStats(maxStretch, stretchViolations);
    }

    private static <V> List<ShortestPathAlgorithm.SingleSourcePaths<V, DefaultWeightedEdge>> allSingleSourcePaths(
            SimpleWeightedGraph<V, DefaultWeightedEdge> graph,
            List<V> vertices
    ) {
        DijkstraShortestPath<V, DefaultWeightedEdge> dijkstra = new DijkstraShortestPath<>(graph);
        List<ShortestPathAlgorithm.SingleSourcePaths<V, DefaultWeightedEdge>> paths = new ArrayList<>(vertices.size());
        for (V source : vertices) {
            paths.add(dijkstra.getPaths(source));
        }
        return paths;
    }

    private static final class StretchStats {
        private final double maxStretch;
        private final int stretchViolations;

        private StretchStats(double maxStretch, int stretchViolations) {
            this.maxStretch = maxStretch;
            this.stretchViolations = stretchViolations;
        }
    }
}
