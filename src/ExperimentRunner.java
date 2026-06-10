import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Runs reproducible experiments for the greedy weighted spanner implementation.
 *
 * Modes:
 * - quick: small smoke-test experiment set for development.
 * - full: expanded report-quality experiment set.
 *
 * Output: results/results.csv by default.
 */
public final class ExperimentRunner {
    private static final int[] K_VALUES = {1, 2, 3, 4, 5};

    private ExperimentRunner() {
        // Utility class; do not instantiate.
    }

    public static void main(String[] args) throws IOException {
        String mode = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : "quick";
        Path outputPath = args.length >= 2 ? Paths.get(args[1]) : Paths.get("results", "results.csv");

        List<Scenario> scenarios;
        switch (mode) {
            case "quick":
                scenarios = quickScenarios();
                break;
            case "full":
                scenarios = fullScenarios();
                break;
            case "large":
                scenarios = largeScenarios();
                break;
            default:
                throw new IllegalArgumentException("Unknown mode: " + mode + ". Expected quick, full, or large.");
        }

        runExperiments(mode, scenarios, outputPath);
    }

    private static void runExperiments(String mode, List<Scenario> scenarios, Path outputPath) throws IOException {
        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        System.out.println("Mode: " + mode);
        System.out.println("Writing results to: " + outputPath.toAbsolutePath());
        System.out.println("Scenarios: " + scenarios.size());
        System.out.println("k values: " + Arrays.toString(K_VALUES));
        System.out.println("Expected rows: " + (scenarios.size() * K_VALUES.length));
        System.out.println();

        int rows = 0;
        long startAll = System.nanoTime();

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write(SpannerResult.csvHeader());
            writer.newLine();

            for (Scenario scenario : scenarios) {
                for (int k : K_VALUES) {
                    SpannerResult result = runSingleExperiment(scenario, k);
                    writer.write(result.toCsvRow());
                    writer.newLine();
                    rows++;
                    System.out.println(result);
                }
            }
        }

        long totalMs = (System.nanoTime() - startAll) / 1_000_000L;
        System.out.println();
        System.out.println("Done. Wrote " + rows + " rows in " + totalMs + "ms.");
    }

    private static SpannerResult runSingleExperiment(Scenario scenario, int k) {
        long start = System.nanoTime();
        SimpleWeightedGraph<Integer, DefaultWeightedEdge> spanner =
                GreedyWeightedSpanner.buildSpanner(scenario.graph, k);
        long runtimeMs = (System.nanoTime() - start) / 1_000_000L;

        return GraphMetrics.summarize(
                scenario.name,
                scenario.graph,
                spanner,
                k,
                runtimeMs
        );
    }

    /**
     * Fast experiments for development and debugging.
     */
    private static List<Scenario> quickScenarios() {
        List<Scenario> scenarios = new ArrayList<>();

        scenarios.add(new Scenario("small_triangle", GraphFactory.smallTriangle()));
        scenarios.add(new Scenario("path_n30", GraphFactory.pathGraph(30)));
        scenarios.add(new Scenario("cycle_n30", GraphFactory.cycleGraph(30)));
        scenarios.add(new Scenario("random_tree_n30_seed101", GraphFactory.randomTree(30, 1, 100, 101L)));
        scenarios.add(new Scenario("complete_n12_seed102", GraphFactory.completeGraph(12, 1, 100, 102L)));
        scenarios.add(new Scenario("complete_n20_seed103", GraphFactory.completeGraph(20, 1, 100, 103L)));
        scenarios.add(new Scenario("random_connected_n30_extra30_seed104", GraphFactory.randomConnectedGraph(30, 30, 1, 100, 104L)));
        scenarios.add(new Scenario("random_connected_n40_extra120_seed105", GraphFactory.randomConnectedGraph(40, 120, 1, 100, 105L)));

        return scenarios;
    }

    /**
     * Expanded experiments for the final report.
     *
     * The set is intentionally balanced:
     * - sparse baseline graphs: paths, cycles, trees
     * - dense graphs: complete graphs
     * - random connected graphs at several densities
     * - repeated random seeds for averaging
     * - wide weight ranges to test sensitivity to edge weights
     */
    private static List<Scenario> fullScenarios() {
        List<Scenario> scenarios = new ArrayList<>();

        scenarios.addAll(quickScenarios());

        addPathScenarios(scenarios, 60, 100, 150);
        addCycleScenarios(scenarios, 60, 100, 150);

        // Small threshold cycles show exactly when the final cycle edge becomes skippable.
        // For a unit cycle C_n, after n-1 edges were added, the last edge is skipped only when
        // the alternate path of length n-1 is within the allowed stretch 2k-1.
        addCycleThresholdScenarios(scenarios, 6, 7, 8, 10);

        addRandomTreeScenarios(scenarios, 1, 100, 210L, 60, 100, 150);

        // Complete graphs show the strongest sparsification, but are expensive, so keep n moderate.
        scenarios.add(new Scenario("complete_n30_seed220", GraphFactory.completeGraph(30, 1, 100, 220L)));
        scenarios.add(new Scenario("complete_n40_seed221", GraphFactory.completeGraph(40, 1, 100, 221L)));
        scenarios.add(new Scenario("complete_n50_seed222", GraphFactory.completeGraph(50, 1, 100, 222L)));

        // Equal-weight complete graphs are useful for the report because they cleanly demonstrate
        // the transition toward MST-like output as k grows.
        addEqualWeightCompleteScenarios(scenarios, 1000L, 20, 40);

        // Sparse random connected graphs: tree plus roughly n extra edges.
        addRandomConnectedScenarios(scenarios, "sparse", 1, 100, 300L,
                new int[][]{
                        {60, 60},
                        {100, 100},
                        {150, 150}
                });

        // Medium density: tree plus roughly 3n extra edges.
        addRandomConnectedScenarios(scenarios, "medium", 1, 100, 400L,
                new int[][]{
                        {60, 180},
                        {100, 300},
                        {150, 450}
                });

        // Dense-ish random connected graphs. Kept below complete graph density to avoid excessive runtime.
        addRandomConnectedScenarios(scenarios, "dense", 1, 100, 500L,
                new int[][]{
                        {40, 240},
                        {60, 360},
                        {80, 480},
                        {100, 600}
                });

        // Planar structured graphs. These connect directly to the paper's planar-spanner guarantees.
        scenarios.add(new Scenario("grid_8x8_unit_seed600",
                GraphFactory.gridGraph(8, 8, 1, 1, 600L)));
        scenarios.add(new Scenario("grid_10x10_weighted_seed601",
                GraphFactory.gridGraph(10, 10, 1, 100, 601L)));
        scenarios.add(new Scenario("grid_12x12_weighted_seed602",
                GraphFactory.gridGraph(12, 12, 1, 100, 602L)));

        // Theta-style lower-bound examples: planar graphs that are intentionally hard to sparsify
        // at the matching stretch value. They are useful report examples, not random benchmarks.
        scenarios.add(new Scenario("theta_lower_bound_targetK2_chains20",
                GraphFactory.thetaLowerBoundGraph(2, 20)));
        scenarios.add(new Scenario("theta_lower_bound_targetK3_chains20",
                GraphFactory.thetaLowerBoundGraph(3, 20)));
        scenarios.add(new Scenario("theta_lower_bound_targetK4_chains20",
                GraphFactory.thetaLowerBoundGraph(4, 20)));
        scenarios.add(new Scenario("theta_lower_bound_targetK5_chains20",
                GraphFactory.thetaLowerBoundGraph(5, 20)));

        // Wide weight range experiments: tests whether heavy edges are filtered aggressively.
        scenarios.add(new Scenario("random_connected_n80_extra240_wide_weights_seed700",
                GraphFactory.randomConnectedGraph(80, 240, 1, 1000, 700L)));
        scenarios.add(new Scenario("random_connected_n100_extra300_wide_weights_seed701",
                GraphFactory.randomConnectedGraph(100, 300, 1, 1000, 701L)));
        scenarios.add(new Scenario("complete_n40_wide_weights_seed702",
                GraphFactory.completeGraph(40, 1, 1000, 702L)));

        // Repeated seeds for stable averages on random graphs.
        addRepeatedRandomConnectedScenarios(scenarios, "repeat_medium_n80_extra240", 80, 240, 1, 100, 800L, 5);
        addRepeatedRandomConnectedScenarios(scenarios, "repeat_medium_n100_extra300", 100, 300, 1, 100, 900L, 3);

        return scenarios;
    }

    /**
     * Larger report experiments focused on scaling and explicit edge probability.
     *
     * These scenarios are intentionally separated from fullScenarios() because they
     * can take longer. They answer the report question: how does the algorithm behave
     * when n and density increase?
     */
    private static List<Scenario> largeScenarios() {
        List<Scenario> scenarios = new ArrayList<>();

        addErdosRenyiScenarios(scenarios, "er_p002", 0.02, 1, 100, 2000L, 3, 200, 400, 800);
        addErdosRenyiScenarios(scenarios, "er_p005", 0.05, 1, 100, 3000L, 3, 200, 400, 800);
        addErdosRenyiScenarios(scenarios, "er_p010", 0.10, 1, 100, 4000L, 3, 200, 400);
        addErdosRenyiScenarios(scenarios, "er_p020", 0.20, 1, 100, 5000L, 3, 200, 400);

        scenarios.add(new Scenario("grid_20x20_weighted_seed610",
                GraphFactory.gridGraph(20, 20, 1, 100, 610L)));
        scenarios.add(new Scenario("grid_30x30_weighted_seed611",
                GraphFactory.gridGraph(30, 30, 1, 100, 611L)));

        scenarios.add(new Scenario("complete_n75_seed620",
                GraphFactory.completeGraph(75, 1, 100, 620L)));
        scenarios.add(new Scenario("complete_n100_seed621",
                GraphFactory.completeGraph(100, 1, 100, 621L)));

        return scenarios;
    }

    private static void addErdosRenyiScenarios(
            List<Scenario> scenarios,
            String label,
            double p,
            int minWeight,
            int maxWeight,
            long baseSeed,
            int repetitions,
            int... sizes
    ) {
        for (int n : sizes) {
            for (int rep = 0; rep < repetitions; rep++) {
                long seed = baseSeed + 100L * n + rep;
                scenarios.add(new Scenario(
                        "random_connected_" + label + "_n" + n + "_p" + String.format(Locale.US, "%.3f", p) + "_rep" + (rep + 1) + "_seed" + seed,
                        GraphFactory.randomConnectedErdosRenyiGraph(n, p, minWeight, maxWeight, seed)
                ));
            }
        }
    }

    private static void addPathScenarios(List<Scenario> scenarios, int... sizes) {
        for (int n : sizes) {
            scenarios.add(new Scenario("path_n" + n, GraphFactory.pathGraph(n)));
        }
    }

    private static void addCycleScenarios(List<Scenario> scenarios, int... sizes) {
        for (int n : sizes) {
            scenarios.add(new Scenario("cycle_n" + n, GraphFactory.cycleGraph(n)));
        }
    }

    private static void addCycleThresholdScenarios(List<Scenario> scenarios, int... sizes) {
        for (int n : sizes) {
            scenarios.add(new Scenario("cycle_threshold_n" + n, GraphFactory.cycleGraph(n)));
        }
    }

    private static void addEqualWeightCompleteScenarios(List<Scenario> scenarios, long baseSeed, int... sizes) {
        for (int i = 0; i < sizes.length; i++) {
            int n = sizes[i];
            long seed = baseSeed + i;
            scenarios.add(new Scenario(
                    "complete_unit_n" + n,
                    GraphFactory.completeGraph(n, 1, 1, seed)
            ));
        }
    }

    private static void addRandomTreeScenarios(
            List<Scenario> scenarios,
            int minWeight,
            int maxWeight,
            long baseSeed,
            int... sizes
    ) {
        for (int i = 0; i < sizes.length; i++) {
            int n = sizes[i];
            long seed = baseSeed + i;
            scenarios.add(new Scenario(
                    "random_tree_n" + n + "_seed" + seed,
                    GraphFactory.randomTree(n, minWeight, maxWeight, seed)
            ));
        }
    }

    private static void addRandomConnectedScenarios(
            List<Scenario> scenarios,
            String densityLabel,
            int minWeight,
            int maxWeight,
            long baseSeed,
            int[][] nAndExtraEdges
    ) {
        for (int i = 0; i < nAndExtraEdges.length; i++) {
            int n = nAndExtraEdges[i][0];
            int extraEdges = nAndExtraEdges[i][1];
            long seed = baseSeed + i;
            scenarios.add(new Scenario(
                    "random_connected_" + densityLabel + "_n" + n + "_extra" + extraEdges + "_seed" + seed,
                    GraphFactory.randomConnectedGraph(n, extraEdges, minWeight, maxWeight, seed)
            ));
        }
    }

    private static void addRepeatedRandomConnectedScenarios(
            List<Scenario> scenarios,
            String label,
            int n,
            int extraEdges,
            int minWeight,
            int maxWeight,
            long baseSeed,
            int repetitions
    ) {
        for (int i = 0; i < repetitions; i++) {
            long seed = baseSeed + i;
            scenarios.add(new Scenario(
                    label + "_rep" + (i + 1) + "_seed" + seed,
                    GraphFactory.randomConnectedGraph(n, extraEdges, minWeight, maxWeight, seed)
            ));
        }
    }

    private static final class Scenario {
        private final String name;
        private final SimpleWeightedGraph<Integer, DefaultWeightedEdge> graph;

        private Scenario(String name, SimpleWeightedGraph<Integer, DefaultWeightedEdge> graph) {
            this.name = sanitizeCsvName(name);
            this.graph = graph;
        }

        private static String sanitizeCsvName(String name) {
            return name.replace(',', '_').replace('\n', '_').replace('\r', '_').trim();
        }
    }
}
