import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Minimal correctness checks for the greedy weighted spanner implementation.
 *
 * These are not full experiments. They are quick sanity tests that should pass
 * before building the larger ExperimentRunner.
 */
public final class SanityTests {
    private static final double EPS = 1e-9;

    private SanityTests() {
        // Utility class; do not instantiate.
    }

    public static void main(String[] args) {
        List<Runnable> tests = Arrays.asList(
                SanityTests::triangleHeavyEdgeIsSkipped,
                SanityTests::treeRemainsUnchanged,
                SanityTests::kOnePreservesExactDistances,
                SanityTests::mstEdgesAreContained,
                SanityTests::stretchBoundHoldsOnRandomGraphs,
                SanityTests::invalidWeightsAreRejected
        );

        int passed = 0;
        for (Runnable test : tests) {
            test.run();
            passed++;
        }

        System.out.println("PASS all sanity tests (" + passed + "/" + tests.size() + ")");
    }

    private static void triangleHeavyEdgeIsSkipped() {
        SimpleWeightedGraph<Integer, DefaultWeightedEdge> graph = GraphFactory.smallTriangle();
        SimpleWeightedGraph<Integer, DefaultWeightedEdge> spanner = GreedyWeightedSpanner.buildSpanner(graph, 2);

        assertEquals(3, graph.edgeSet().size(), "triangle original edge count");
        assertEquals(2, spanner.edgeSet().size(), "triangle spanner should keep only two light edges");
        assertTrue(spanner.getEdge(0, 2) == null, "triangle heavy edge 0--2 should be skipped");
        assertNoStretchViolations(graph, spanner, 2, "triangle stretch bound");

        System.out.println("PASS triangle heavy edge skipped");
    }

    private static void treeRemainsUnchanged() {
        SimpleWeightedGraph<Integer, DefaultWeightedEdge> graph = GraphFactory.randomTree(12, 1, 20, 123L);
        SimpleWeightedGraph<Integer, DefaultWeightedEdge> spanner = GreedyWeightedSpanner.buildSpanner(graph, 3);

        assertEquals(graph.vertexSet().size(), spanner.vertexSet().size(), "tree vertex count");
        assertEquals(graph.edgeSet().size(), spanner.edgeSet().size(), "tree edge count should remain unchanged");
        assertSameWeightedEdges(graph, spanner, "tree should remain unchanged");
        assertNoStretchViolations(graph, spanner, 3, "tree stretch bound");

        System.out.println("PASS tree unchanged");
    }

    private static void kOnePreservesExactDistances() {
        SimpleWeightedGraph<Integer, DefaultWeightedEdge> graph = GraphFactory.smallTriangle();
        SimpleWeightedGraph<Integer, DefaultWeightedEdge> spanner = GreedyWeightedSpanner.buildSpanner(graph, 1);

        assertEquals(2, spanner.edgeSet().size(), "k=1 may still remove non-shortest redundant edges");
        assertTrue(spanner.getEdge(0, 2) == null, "k=1 should remove the redundant heavy edge here");
        assertAlmostEquals(1.0, GraphMetrics.maxStretch(graph, spanner), "k=1 should preserve exact distances");
        assertNoStretchViolations(graph, spanner, 1, "k=1 exact stretch bound");

        System.out.println("PASS k=1 exact distances");
    }

    private static void mstEdgesAreContained() {
        SimpleWeightedGraph<Integer, DefaultWeightedEdge> graph = GraphFactory.completeGraph(10, 1, 50, 999L);
        SimpleWeightedGraph<Integer, DefaultWeightedEdge> spanner = GreedyWeightedSpanner.buildSpanner(graph, 2);

        assertTrue(GraphMetrics.containsMinimumSpanningTree(graph, spanner), "spanner should contain a minimum spanning tree");
        assertTrue(spanner.edgeSet().size() >= GraphMetrics.mstEdgeCount(graph), "spanner should have at least MST edge count");
        assertNoStretchViolations(graph, spanner, 2, "MST graph stretch bound");

        System.out.println("PASS MST containment");
    }

    private static void stretchBoundHoldsOnRandomGraphs() {
        List<SimpleWeightedGraph<Integer, DefaultWeightedEdge>> graphs = new ArrayList<>();
        graphs.add(GraphFactory.randomConnectedGraph(20, 35, 1, 100, 1L));
        graphs.add(GraphFactory.randomConnectedGraph(30, 80, 1, 100, 2L));
        graphs.add(GraphFactory.completeGraph(12, 1, 100, 3L));
        graphs.add(GraphFactory.cycleGraph(25));

        int checked = 0;
        for (SimpleWeightedGraph<Integer, DefaultWeightedEdge> graph : graphs) {
            for (int k = 1; k <= 4; k++) {
                SimpleWeightedGraph<Integer, DefaultWeightedEdge> spanner = GreedyWeightedSpanner.buildSpanner(graph, k);
                assertNoStretchViolations(graph, spanner, k, "random stretch bound, graph=" + checked + ", k=" + k);
                checked++;
            }
        }

        System.out.println("PASS stretch bound on deterministic random graphs");
    }

    private static void invalidWeightsAreRejected() {
        SimpleWeightedGraph<Integer, DefaultWeightedEdge> graph = GraphFactory.emptyGraph(2);
        DefaultWeightedEdge edge = graph.addEdge(0, 1);
        graph.setEdgeWeight(edge, 0.0);

        boolean thrown = false;
        try {
            GreedyWeightedSpanner.buildSpanner(graph, 2);
        } catch (IllegalArgumentException expected) {
            thrown = true;
        }

        assertTrue(thrown, "zero-weight edge should be rejected");
        System.out.println("PASS invalid weights rejected");
    }

    private static void assertNoStretchViolations(
            SimpleWeightedGraph<Integer, DefaultWeightedEdge> graph,
            SimpleWeightedGraph<Integer, DefaultWeightedEdge> spanner,
            int k,
            String message
    ) {
        double stretch = 2.0 * k - 1.0;
        int violations = GraphMetrics.countStretchViolations(graph, spanner, stretch);
        assertEquals(0, violations, message + ": violations");
        assertTrue(GraphMetrics.maxStretch(graph, spanner) <= stretch + EPS, message + ": max stretch too large");
    }

    private static <V> void assertSameWeightedEdges(
            SimpleWeightedGraph<V, DefaultWeightedEdge> expected,
            SimpleWeightedGraph<V, DefaultWeightedEdge> actual,
            String message
    ) {
        for (DefaultWeightedEdge edge : expected.edgeSet()) {
            V u = expected.getEdgeSource(edge);
            V v = expected.getEdgeTarget(edge);
            double expectedWeight = expected.getEdgeWeight(edge);

            DefaultWeightedEdge actualEdge = actual.getEdge(u, v);
            assertTrue(actualEdge != null, message + ": missing edge " + u + "--" + v);
            assertAlmostEquals(expectedWeight, actual.getEdgeWeight(actualEdge), message + ": wrong weight for " + u + "--" + v);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertAlmostEquals(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > EPS) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }
}