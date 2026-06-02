import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Generates exact DOT visual examples from the Java graph generators and Java greedy spanner.
 */
public final class DotExampleExporter {
    private DotExampleExporter() {
        // Utility class; do not instantiate.
    }

    public static void main(String[] args) throws IOException {
        Path outputDir = args.length >= 1 ? Paths.get(args[0]) : Paths.get("results", "dot");

        List<Example> examples = new ArrayList<>();
        examples.add(new Example("small_triangle", GraphFactory.smallTriangle(), 2));
        examples.add(new Example("cycle_n12", GraphFactory.cycleGraph(12), 2));
        examples.add(new Example("complete_n10_seed102", GraphFactory.completeGraph(10, 1, 30, 102L), 2));
        examples.add(new Example("random_connected_n14_extra22_seed104", GraphFactory.randomConnectedGraph(14, 22, 1, 30, 104L), 3));
        examples.add(new Example("grid_5x5_weighted_seed601", GraphFactory.gridGraph(5, 5, 1, 20, 601L), 3));
        examples.add(new Example("theta_lower_bound_targetK3_chains6", GraphFactory.thetaLowerBoundGraph(3, 6), 3));
        examples.add(new Example("cycle_threshold_n7", GraphFactory.cycleGraph(7), 4));
        examples.add(new Example("complete_unit_n20", GraphFactory.completeGraph(20, 1, 1, 1000L), 2));

        for (Example example : examples) {
            SimpleWeightedGraph<Integer, DefaultWeightedEdge> spanner = GreedyWeightedSpanner.buildSpanner(example.graph, example.k);
            String fileName = example.name + "_k" + example.k + ".dot";
            Path path = outputDir.resolve(fileName);
            String title = String.format(
                    Locale.US,
                    "%s, k=%d, stretch=%d, original edges=%d, spanner edges=%d",
                    example.name,
                    example.k,
                    2 * example.k - 1,
                    example.graph.edgeSet().size(),
                    spanner.edgeSet().size()
            );
            GraphDotExporter.writeOverlay(example.graph, spanner, path, title);
            System.out.println("Wrote " + path.toAbsolutePath());
        }

        System.out.println();
        System.out.println("Render PNGs with Graphviz:");
        System.out.println("Get-ChildItem " + outputDir + "\\*.dot | ForEach-Object { dot -Tpng $_.FullName -o ($_.FullName -replace '\\.dot$', '.png') }");
    }

    private static final class Example {
        private final String name;
        private final SimpleWeightedGraph<Integer, DefaultWeightedEdge> graph;
        private final int k;

        private Example(String name, SimpleWeightedGraph<Integer, DefaultWeightedEdge> graph, int k) {
            this.name = name;
            this.graph = graph;
            this.k = k;
        }
    }
}
