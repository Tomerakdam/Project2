import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Core implementation of the greedy weighted spanner algorithm of
 * Althöfer, Das, Dobkin, Joseph, and Soares (1993).
 *
 * Input:  connected or disconnected undirected graph G with positive edge weights.
 * Output: subgraph H over the same vertex set.
 *
 * For parameter k >= 1, the produced stretch is r = 2k - 1.
 * Edges are processed in nondecreasing weight order. An edge e=(u,v) is added
 * exactly when the current shortest path between u and v in H is longer than
 * r * weight(e), or when u and v are disconnected in H.
 */
public final class GreedyWeightedSpanner {
    private static final double EPS = 1e-9;

    private GreedyWeightedSpanner() {
        // Utility class; do not instantiate.
    }

    /**
     * Build a greedy weighted (2k-1)-spanner of {@code graph}.
     *
     * @param graph undirected weighted graph with positive finite edge weights
     * @param k stretch parameter; output stretch is 2k-1
     * @param <V> vertex type
     * @return a new graph containing all vertices of {@code graph} and the selected spanner edges
     */
    public static <V> SimpleWeightedGraph<V, DefaultWeightedEdge> buildSpanner(
            SimpleWeightedGraph<V, DefaultWeightedEdge> graph,
            int k
    ) {
        Objects.requireNonNull(graph, "graph must not be null");
        validateK(k);
        validateWeights(graph);

        final double stretch = 2.0 * k - 1.0;

        SimpleWeightedGraph<V, DefaultWeightedEdge> spanner =
                new SimpleWeightedGraph<>(DefaultWeightedEdge.class);

        for (V vertex : graph.vertexSet()) {
            spanner.addVertex(vertex);
        }

        List<EdgeData<V>> sortedEdges = collectSortedEdges(graph);

        for (EdgeData<V> edge : sortedEdges) {
            double currentDistance = shortestPathWeight(spanner, edge.source, edge.target);
            double allowedDistance = stretch * edge.weight;

            // Paper condition: add iff r * weight(e) < current shortest-path weight.
            // Using EPS prevents tiny floating-point noise from changing decisions.
            if (currentDistance > allowedDistance + EPS) {
                DefaultWeightedEdge added = spanner.addEdge(edge.source, edge.target);
                if (added != null) {
                    spanner.setEdgeWeight(added, edge.weight);
                }
            }
        }

        return spanner;
    }

    /**
     * Convenience helper: returns true iff {@code spanner} satisfies the expected stretch bound.
     * This is not part of the construction itself, but is useful for sanity tests.
     */
    public static <V> boolean verifiesStretch(
            SimpleWeightedGraph<V, DefaultWeightedEdge> graph,
            SimpleWeightedGraph<V, DefaultWeightedEdge> spanner,
            int k
    ) {
        Objects.requireNonNull(graph, "graph must not be null");
        Objects.requireNonNull(spanner, "spanner must not be null");
        validateK(k);

        double stretch = 2.0 * k - 1.0;
        List<V> vertices = new ArrayList<>(graph.vertexSet());

        for (int i = 0; i < vertices.size(); i++) {
            for (int j = i + 1; j < vertices.size(); j++) {
                V u = vertices.get(i);
                V v = vertices.get(j);

                double distG = shortestPathWeight(graph, u, v);
                if (Double.isInfinite(distG)) {
                    continue;
                }

                double distH = shortestPathWeight(spanner, u, v);
                if (distH > stretch * distG + EPS) {
                    return false;
                }
            }
        }

        return true;
    }

    private static void validateK(int k) {
        if (k < 1) {
            throw new IllegalArgumentException("k must be >= 1");
        }
    }

    private static <V> void validateWeights(SimpleWeightedGraph<V, DefaultWeightedEdge> graph) {
        for (DefaultWeightedEdge edge : graph.edgeSet()) {
            double weight = graph.getEdgeWeight(edge);
            if (!Double.isFinite(weight) || weight <= 0.0) {
                throw new IllegalArgumentException(
                        "all edge weights must be positive and finite; found weight " + weight
                );
            }
        }
    }

    private static <V> List<EdgeData<V>> collectSortedEdges(
            SimpleWeightedGraph<V, DefaultWeightedEdge> graph
    ) {
        List<EdgeData<V>> edges = new ArrayList<>();

        for (DefaultWeightedEdge edge : graph.edgeSet()) {
            V source = graph.getEdgeSource(edge);
            V target = graph.getEdgeTarget(edge);
            double weight = graph.getEdgeWeight(edge);
            edges.add(new EdgeData<>(source, target, weight));
        }

        edges.sort(
                Comparator.<EdgeData<V>>comparingDouble(e -> e.weight)
                        .thenComparing(e -> String.valueOf(e.source))
                        .thenComparing(e -> String.valueOf(e.target))
        );

        return edges;
    }

    private static <V> double shortestPathWeight(
            SimpleWeightedGraph<V, DefaultWeightedEdge> graph,
            V source,
            V target
    ) {
        if (source.equals(target)) {
            return 0.0;
        }
        if (!graph.containsVertex(source) || !graph.containsVertex(target)) {
            return Double.POSITIVE_INFINITY;
        }
        return new DijkstraShortestPath<>(graph).getPathWeight(source, target);
    }

    private static final class EdgeData<V> {
        private final V source;
        private final V target;
        private final double weight;

        private EdgeData(V source, V target, double weight) {
            this.source = source;
            this.target = target;
            this.weight = weight;
        }
    }
}