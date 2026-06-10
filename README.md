# Greedy Weighted Graph Spanner

Implementation and experimental evaluation of the greedy weighted graph spanner algorithm from:

> I. Althöfer, G. Das, D. Dobkin, D. Joseph, and J. Soares,  **On Sparse Spanners of Weighted Graphs** , Discrete & Computational Geometry, 9, 81–100, 1993.

The project constructs sparse weighted subgraphs that approximately preserve all-pairs shortest-path distances.

## Goal

Given a connected undirected graph `G = (V, E)` with positive edge weights, construct a subgraph `H = (V, E_H)` such that:

```text
dist_H(u, v) <= r * dist_G(u, v)
```

for every pair of vertices `u, v`.

This implementation uses an integer parameter `k >= 1` and sets:

```text
r = 2k - 1
```

| k | Stretch bound |
| - | ------------- |
| 1 | 1-spanner     |
| 2 | 3-spanner     |
| 3 | 5-spanner     |
| 4 | 7-spanner     |
| 5 | 9-spanner     |

Larger `k` allows more stretch and usually produces a sparser/lighter spanner. The result is not guaranteed to be nested as `k` changes, so do not claim that weight or edge count is strictly monotone for every single graph.

## Algorithm

```text
SPANNER(G, r):
    sort all edges by nondecreasing weight
    H = graph with all vertices of G and no edges

    for each edge e = (u, v) in sorted order:
        compute the current shortest-path distance dist_H(u, v)

        if dist_H(u, v) > r * weight(e):
            add e to H

    return H
```

The code checks the main correctness properties needed for the report:

* the output graph keeps the original vertex set;
* every selected edge comes from the original graph;
* connected inputs produce connected spanners;
* the empirical stretch respects the `2k - 1` bound;
* `k = 1` preserves exact shortest-path distances;
* the spanner contains at least one minimum spanning tree of the original graph.

MST note: when edge weights have ties, there may be several valid MSTs. The code and visualizations use a tie-safe interpretation: the spanner is checked/colored as containing **some** MST, not necessarily the exact arbitrary MST that a library might choose on the original graph.

## Implementation Details

* Language: Java
* Graph library: JGraphT 1.5.2
* Tested with Java 21
* Shortest paths during construction: Dijkstra on the current spanner
* Plotting: Python with `pandas`, `matplotlib`, and `tabulate`

The implementation prioritizes clarity over performance. Dijkstra is recomputed for each candidate edge, which is correct for this project scale but not optimized for very large graphs.

Runtime note: `runtime_ms` measures greedy spanner construction time only. It does not include plotting, CSV post-processing, or report generation.

## Project Structure

```text
Project2/
  lib/
    jgrapht-core-1.5.2.jar
    jheaps-0.14.jar

  src/
    GreedyWeightedSpanner.java    core greedy spanner implementation
    SpannerResult.java            one experiment row and derived CSV metrics
    GraphMetrics.java             stretch, weight, MST, and validation metrics
    GraphFactory.java             deterministic and random weighted graph generators
    SanityTests.java              correctness smoke tests
    ExperimentRunner.java         quick/structural/large experiment runner
    VisualExampleExporter.java    exports Java-computed visual-example data for Python plots

  scripts/
    java21.ps1                    checks Java visible on PATH
    compile.ps1                   compiles all Java source files
    test.ps1                      compiles and runs sanity tests
    run_experiments.ps1           compiles and runs quick/structural/large experiments with logs
    make_plots.ps1                compiles and generates mode-specific report plots
    make_plots.py                 generates quantitative plots and structural visual examples

  results/
    structural_results.csv        structural experiment output
    large_results.csv             large weighted random/scaling experiment output
    logs/                         run logs
    plots_structural/             figures, summaries, and visual examples from structural results
    plots_large/                  figures and summaries from large results

  docs/
    althofer.new.pdf              source paper
```

## Setup on a New Computer

Clone the repository and open PowerShell from the project root:

```powershell
git clone https://github.com/Tomerakdam/Project2.git; cd Project2
```

The project includes the required Java `.jar` dependencies under `lib/`, so no Maven or Gradle setup is required.

Allow local PowerShell scripts to run and remove Windows' downloaded-file block from the project scripts:

```powershell
Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy RemoteSigned -Force; Get-ChildItem .\scripts -Recurse -File -Filter *.ps1 | Unblock-File
```

Install Java 21, then verify that both `java` and `javac` are available from PowerShell:

```powershell
java -version; javac -version; where.exe java; where.exe javac
```

If Java is installed but not found, set `JAVA_HOME` to the local JDK folder and add its `bin` directory to the user PATH. Replace the path below with the actual Java 21 JDK path on the computer:

```powershell
$JdkHome = "C:\Path\To\Your\JDK"; [Environment]::SetEnvironmentVariable("JAVA_HOME", $JdkHome, "User"); [Environment]::SetEnvironmentVariable("Path", "$JdkHome\bin;" + [Environment]::GetEnvironmentVariable("Path", "User"), "User")
```

Close PowerShell, open it again from the project root, and verify Java again:

```powershell
java -version; javac -version; where.exe java; where.exe javac
```

Create the local Python environment used by the plotting script:

```powershell
py -3 -m venv .venv; .\.venv\Scripts\python.exe -m pip install --upgrade pip; .\.venv\Scripts\python.exe -m pip install pandas matplotlib tabulate
```

Check the Java toolchain seen by the project scripts:

```powershell
.\scripts\java21.ps1
```

## Compile

```powershell
.\scripts\compile.ps1
```

## Run Sanity Tests

```powershell
.\scripts\test.ps1
```

Expected final line:

```text
PASS all sanity tests (6/6)
```

## Run Experiments

Quick mode is a small development run:

```powershell
.\scripts\run_experiments.ps1 quick
```

Structural mode is the graph-family benchmark used for the structural part of the report:

```powershell
.\scripts\run_experiments.ps1 structural
```

Large mode is the weighted large-graph benchmark used for density and scaling analysis:

```powershell
.\scripts\run_experiments.ps1 large
```

Default run is equivalent to structural mode:

```powershell
.\scripts\run_experiments.ps1
```

Default output files:

```text
quick       -> results\quick_results.csv
structural  -> results\structural_results.csv
large       -> results\large_results.csv
```

The experiment script writes a log under `results\logs\`. If an existing log has the same name, it is archived under `results\logs\archive\` before the new run starts.

## Experiment Coverage

The project separates report experiments into two categories.

### Structural experiments

Structural mode uses `k = 1, 2, 3, 4, 5` and includes:

* paths;
* cycles;
* cycle-threshold examples;
* random trees;
* complete weighted graphs;
* equal-weight complete graphs;
* sparse, medium, and dense random connected graphs;
* repeated random seeds for averaging;
* grid planar graphs;
* theta-style lower-bound examples;
* wide-weight-range graphs.

These families are used to compare sparsity, stretch, lightness, dense-vs-sparse behavior, planar behavior, and lower-bound intuition.

### Large weighted density/scaling experiments

Large mode also uses `k = 1, 2, 3, 4, 5`, but its purpose is different. It focuses on larger weighted graphs and controlled density experiments:

* weighted connected Erdős–Rényi-style random graphs with explicit edge probability `p`;
* larger weighted planar grid graphs;
* larger complete weighted graphs.

The connected Erdős–Rényi-style generator first adds a random spanning tree to guarantee connectivity. Then, for every remaining possible edge, it adds the edge independently with probability `p`. Every edge receives a positive integer weight sampled from the configured weight range.

Current large random graph probabilities:

```text
p = 0.02, 0.05, 0.10, 0.20, 0.30
```

The lower probabilities are used with larger `n` values, while higher probabilities are kept to moderate sizes to avoid excessive runtime. The large mode is intended for density and scaling analysis: how edge reduction, lightness, stretch, and runtime change as the number of vertices, number of edges, and edge probability increase.

## CSV Output Columns

The experiment CSV contains one row per graph scenario and `k` value.

| Column                       | Meaning                                                                        |
| ---------------------------- | ------------------------------------------------------------------------------ |
| `graph`                    | graph scenario name                                                            |
| `n`                        | number of vertices                                                             |
| `original_edges`           | number of edges in the input graph                                             |
| `spanner_edges`            | number of edges selected by the spanner                                        |
| `k`                        | spanner parameter                                                              |
| `stretch`                  | theoretical stretch bound, equal to `2k - 1`                                 |
| `edge_reduction_percent`   | percent of original edges removed                                              |
| `original_weight`          | total edge weight of the input graph                                           |
| `spanner_weight`           | total edge weight of the spanner                                               |
| `mst_weight`               | total weight of a minimum spanning tree                                        |
| `max_stretch`              | maximum observed pairwise stretch                                              |
| `stretch_violations`       | number of violated stretch constraints                                         |
| `runtime_ms`               | construction runtime in milliseconds                                           |
| `weight_over_mst`          | `spanner_weight / mst_weight`                                                |
| `allowed_stretch`          | duplicate/report-friendly alias for `2k - 1`                                 |
| `stretch_utilization`      | `max_stretch / allowed_stretch`                                              |
| `general_size_bound`       | report-friendly `n^(1+1/k)`size scale                                        |
| `general_size_bound_ratio` | `spanner_edges / general_size_bound`                                         |
| `planar_size_bound`        | planar theorem size bound for known planar benchmark families, when applicable |
| `planar_size_bound_ratio`  | `spanner_edges / planar_size_bound`                                          |

The `general_size_bound` column is a normalization scale for the common `(2k-1)`-spanner discussion. The Althöfer paper uses a different parameter notation in the theorem statement, so explain the mapping in the report instead of quoting the column as the theorem verbatim.

## Generate Plots and Visual Examples

Generate structural plots and visual examples:

```powershell
.\scripts\make_plots.ps1 structural
```

Generate large density/scaling plots:

```powershell
.\scripts\make_plots.ps1 large
```

Default input/output locations:

```text
structural:
  input  = results\structural_results.csv
  output = results\plots_structural

large:
  input  = results\large_results.csv
  output = results\plots_large
```

Structural plotting performs three steps:

1. compiles the Java sources;
2. runs `VisualExampleExporter` to export exact Java-computed visual-example CSV files;
3. runs `scripts\make_plots.py` to generate plots, visual graph examples, and summary tables.

Large plotting skips visual examples and generates only quantitative plots and summary tables.

## Structural Plot Outputs

```text
results/plots_structural/avg_edge_reduction_by_k.png
results/plots_structural/avg_weight_over_mst_by_k.png
results/plots_structural/max_observed_stretch_by_k.png
results/plots_structural/family_edge_reduction_by_k.png
results/plots_structural/family_weight_over_mst_by_k.png
results/plots_structural/summary.md
results/plots_structural/summary_by_k.csv
results/plots_structural/summary_by_family_k.csv
results/plots_structural/summary_planar_bounds.csv
results/plots_structural/results_with_derived_metrics.csv
results/plots_structural/visual_examples/
```

The visual examples compare the original graph and the greedy spanner on the same layout. Blue edges are one chosen MST contained in the spanner, red edges are additional spanner edges, and pale gray edges are original edges removed by the spanner.

## Large Plot Outputs

```text
results/plots_large/avg_weight_over_mst_by_k.png
results/plots_large/max_observed_stretch_by_k.png
results/plots_large/edge_reduction_vs_density.png
results/plots_large/edge_reduction_heatmap_k_density.png
results/plots_large/runtime_vs_original_edges_large.png
results/plots_large/spanner_edges_vs_n_by_density_k3.png
results/plots_large/summary.md
results/plots_large/summary_by_k.csv
results/plots_large/summary_by_family_k.csv
results/plots_large/results_with_derived_metrics.csv
```

The large plots should be used for the report discussion about controlled edge probability, density, graph size, and construction runtime.

## Interpreting Results

Expected behavior:

* trees should remain unchanged because removing any tree edge disconnects the graph;
* dense graphs, especially complete graphs, should lose many edges;
* weighted connected random graphs should usually show higher edge reduction as density increases;
* increasing `k` usually reduces edge count and lightness, but this is empirical behavior, not a strict nesting guarantee for every graph;
* all valid runs should have `stretch_violations = 0`;
* `weight_over_mst` measures how far the spanner is from the lightest possible connected subgraph;
* planar benchmark families should be compared separately using the planar bound columns;
* large-mode runtime plots should be used for scaling discussion, not the small structural-mode runtime averages.

The structural plots are best used for explaining behavior across graph families: paths, trees, cycles, complete graphs, random graphs, grids, and theta-style hard examples.

The large plots are best used for explaining scaling and density behavior: how the algorithm behaves on larger weighted graphs where edge density is controlled by probability `p`.

## Reproduce Structural Results

```powershell
.\scripts\test.ps1; .\scripts\run_experiments.ps1 structural; .\scripts\make_plots.ps1 structural
```

## Reproduce Large Results

```powershell
.\scripts\test.ps1; .\scripts\run_experiments.ps1 large; .\scripts\make_plots.ps1 large
```

## Reproduce All Report Results

```powershell
.\scripts\test.ps1; .\scripts\run_experiments.ps1 structural; .\scripts\make_plots.ps1 structural; .\scripts\run_experiments.ps1 large; .\scripts\make_plots.ps1 large
```
