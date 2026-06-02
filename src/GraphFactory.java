import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Graph generators used by sanity tests and experiments.
 */
public final class GraphFactory {
    private GraphFactory() {
        // Utility class; do not instantiate.
    }

    public static SimpleWeightedGraph<Integer, DefaultWeightedEdge> emptyGraph(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n must be nonnegative");
        }
        SimpleWeightedGraph<Integer, DefaultWeightedEdge> graph =
                new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        for (int i = 0; i < n; i++) {
            graph.addVertex(i);
        }
        return graph;
    }

    /**
     * Triangle with two light edges and one heavy edge.
     * For k=2, the heavy edge should be skipped by the greedy spanner.
     */
    public static SimpleWeightedGraph<Integer, DefaultWeightedEdge> smallTriangle() {
        SimpleWeightedGraph<Integer, DefaultWeightedEdge> graph = emptyGraph(3);
        addEdge(graph, 0, 1, 1.0);
        addEdge(graph, 1, 2, 1.0);
        addEdge(graph, 0, 2, 3.0);
        return graph;
    }

    /**
     * Path graph with n vertices and unit weights.
     */
    public static SimpleWeightedGraph<Integer, DefaultWeightedEdge> pathGraph(int n) {
        if (n < 1) {
            throw new IllegalArgumentException("n must be >= 1");
        }
        SimpleWeightedGraph<Integer, DefaultWeightedEdge> graph = emptyGraph(n);
        for (int i = 0; i + 1 < n; i++) {
            addEdge(graph, i, i + 1, 1.0);
        }
        return graph;
    }

    /**
     * Cycle graph with n vertices and unit weights.
     */
    public static SimpleWeightedGraph<Integer, DefaultWeightedEdge> cycleGraph(int n) {
        if (n < 3) {
            throw new IllegalArgumentException("cycle graph requires n >= 3");
        }
        SimpleWeightedGraph<Integer, DefaultWeightedEdge> graph = pathGraph(n);
        addEdge(graph, n - 1, 0, 1.0);
        return graph;
    }

    public static SimpleWeightedGraph<Integer, DefaultWeightedEdge> completeGraph(
            int n,
            int minWeight,
            int maxWeight,
            long seed
    ) {
        validateWeightRange(minWeight, maxWeight);
        SimpleWeightedGraph<Integer, DefaultWeightedEdge> graph = emptyGraph(n);
        Random random = new Random(seed);

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                addEdge(graph, i, j, randomWeight(random, minWeight, maxWeight));
            }
        }

        return graph;
    }

    /**
     * Random tree, hence already sparse and connected.
     */
    public static SimpleWeightedGraph<Integer, DefaultWeightedEdge> randomTree(
            int n,
            int minWeight,
            int maxWeight,
            long seed
    ) {
        if (n < 1) {
            throw new IllegalArgumentException("n must be >= 1");
        }
        validateWeightRange(minWeight, maxWeight);

        SimpleWeightedGraph<Integer, DefaultWeightedEdge> graph = emptyGraph(n);
        Random random = new Random(seed);

        List<Integer> vertices = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            vertices.add(i);
        }
        Collections.shuffle(vertices, random);

        for (int i = 1; i < n; i++) {
            int child = vertices.get(i);
            int parent = vertices.get(random.nextInt(i));
            addEdge(graph, parent, child, randomWeight(random, minWeight, maxWeight));
        }

        return graph;
    }

    /**
     * Connected graph built from a random tree plus extra random edges.
     */
    public static SimpleWeightedGraph<Integer, DefaultWeightedEdge> randomConnectedGraph(
            int n,
            int extraEdges,
            int minWeight,
            int maxWeight,
            long seed
    ) {
        if (extraEdges < 0) {
            throw new IllegalArgumentException("extraEdges must be nonnegative");
        }
        validateWeightRange(minWeight, maxWeight);

        SimpleWeightedGraph<Integer, DefaultWeightedEdge> graph = randomTree(n, minWeight, maxWeight, seed);
        Random random = new Random(seed ^ 0x9E3779B97F4A7C15L);

        int maxEdges = n * (n - 1) / 2;
        int targetEdges = Math.min(maxEdges, graph.edgeSet().size() + extraEdges);

        while (graph.edgeSet().size() < targetEdges) {
            int u = random.nextInt(n);
            int v = random.nextInt(n);
            if (u == v || graph.containsEdge(u, v)) {
                continue;
            }
            addEdge(graph, u, v, randomWeight(random, minWeight, maxWeight));
        }

        return graph;
    }


    /**
     * Rectangular grid graph with rows*cols vertices and positive integer weights.
     * Vertex id is row * cols + col. This is a planar structured graph family.
     */
    public static SimpleWeightedGraph<Integer, DefaultWeightedEdge> gridGraph(
            int rows,
            int cols,
            int minWeight,
            int maxWeight,
            long seed
    ) {
        if (rows < 2 || cols < 2) {
            throw new IllegalArgumentException("grid graph requires rows >= 2 and cols >= 2");
        }
        validateWeightRange(minWeight, maxWeight);

        SimpleWeightedGraph<Integer, DefaultWeightedEdge> graph = emptyGraph(rows * cols);
        Random random = new Random(seed);

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int v = r * cols + c;
                if (c + 1 < cols) {
                    addEdge(graph, v, r * cols + (c + 1), randomWeight(random, minWeight, maxWeight));
                }
                if (r + 1 < rows) {
                    addEdge(graph, v, (r + 1) * cols + c, randomWeight(random, minWeight, maxWeight));
                }
            }
        }

        return graph;
    }

    /**
     * Planar theta-style lower-bound graph: many internally-disjoint unit-weight chains
     * between two terminals. For targetK, each chain has targetK internal vertices and
     * targetK + 1 edges. For stretch 2*targetK - 1, deleting any edge forces an alternate
     * path of length at least 2*targetK + 1, so this is a useful "hard to sparsify" case.
     */
    public static SimpleWeightedGraph<Integer, DefaultWeightedEdge> thetaLowerBoundGraph(
            int targetK,
            int chainCount
    ) {
        if (targetK < 1) {
            throw new IllegalArgumentException("targetK must be >= 1");
        }
        if (chainCount < 2) {
            throw new IllegalArgumentException("chainCount must be >= 2");
        }

        int n = 2 + chainCount * targetK;
        SimpleWeightedGraph<Integer, DefaultWeightedEdge> graph = emptyGraph(n);
        int source = 0;
        int target = 1;
        int nextVertex = 2;

        for (int chain = 0; chain < chainCount; chain++) {
            int previous = source;
            for (int i = 0; i < targetK; i++) {
                int current = nextVertex++;
                addEdge(graph, previous, current, 1.0);
                previous = current;
            }
            addEdge(graph, previous, target, 1.0);
        }

        return graph;
    }

    /**
     * Adds an undirected weighted edge. Throws on self loops, duplicate edges, or nonpositive weights.
     */
    public static <V> DefaultWeightedEdge addEdge(
            SimpleWeightedGraph<V, DefaultWeightedEdge> graph,
            V source,
            V target,
            double weight
    ) {
        Objects.requireNonNull(graph, "graph must not be null");
        if (source.equals(target)) {
            throw new IllegalArgumentException("self loops are not allowed");
        }
        if (!Double.isFinite(weight) || weight <= 0.0) {
            throw new IllegalArgumentException("weight must be positive and finite");
        }
        if (!graph.containsVertex(source)) {
            graph.addVertex(source);
        }
        if (!graph.containsVertex(target)) {
            graph.addVertex(target);
        }
        DefaultWeightedEdge edge = graph.addEdge(source, target);
        if (edge == null) {
            throw new IllegalArgumentException("duplicate edge: " + source + " -- " + target);
        }
        graph.setEdgeWeight(edge, weight);
        return edge;
    }

    private static void validateWeightRange(int minWeight, int maxWeight) {
        if (minWeight <= 0 || maxWeight < minWeight) {
            throw new IllegalArgumentException("require 0 < minWeight <= maxWeight");
        }
    }

    private static double randomWeight(Random random, int minWeight, int maxWeight) {
        return minWeight + random.nextInt(maxWeight - minWeight + 1);
    }
}
