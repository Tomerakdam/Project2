# Greedy Weighted Graph Spanner

Implementation and experimental evaluation of the greedy weighted graph spanner algorithm from:

> I. Althöfer, G. Das, D. Dobkin, D. Joseph, and J. Soares, **On Sparse Spanners of Weighted Graphs**, Discrete & Computational Geometry, 9, 81–100, 1993.

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

- the output graph keeps the original vertex set;
- every selected edge comes from the original graph;
- connected inputs produce connected spanners;
- the empirical stretch respects the `2k - 1` bound;
- `k = 1` preserves exact shortest-path distances;
- the spanner contains at least one minimum spanning tree of the original graph.

MST note: when edge weights have ties, there may be several valid MSTs. The code and visualizations use a tie-safe interpretation: the spanner is checked/colored as containing **some** MST, not necessarily the exact arbitrary MST that a library might choose on the original graph.

## Implementation Details

- Language: Java
- Graph library: JGraphT 1.5.2
- Tested with Java 21
- Shortest paths during construction: Dijkstra on the current spanner
- Plotting: Python with `pandas`, `matplotlib`, and `tabulate`

The implementation prioritizes clarity over performance. Dijkstra is recomputed for each candidate edge, which is correct for this project scale but not optimized for very large graphs.

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
    GraphFactory.java             deterministic graph generators
    SanityTests.java              correctness smoke tests
    ExperimentRunner.java         quick/full experiment runner
    VisualExampleExporter.java    exports Java-computed visual-example data for Python plots
    GraphDotExporter.java         optional DOT overlay exporter
    DotExampleExporter.java       optional DOT example generator

  scripts/
    java21.ps1                    checks Java visible on PATH
    compile.ps1                   compiles all Java source files
    test.ps1                      compiles and runs sanity tests
    run_experiments.ps1           compiles and runs quick/full experiments with logs
    make_plots.ps1                compiles, exports visual data, and runs Python plotting
    make_plots.py                 generates report plots and visual examples

  results/
    results.csv                   full experiment output
    quick_results.csv             quick experiment output
    logs/                         run logs
    plots/                        generated figures and summary CSV files
    dot/                          optional Graphviz DOT examples

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

Quick mode:

```powershell
.\scripts\run_experiments.ps1 quick results\quick_results.csv
```

Full mode:

```powershell
.\scripts\run_experiments.ps1 full results\results.csv
```

Default run, equivalent to full mode with `results\results.csv`:

```powershell
.\scripts\run_experiments.ps1
```

The experiment script writes a log under `results\logs\`. If an existing log has the same name, it is archived under `results\logs\archive\` before the new run starts.

## Experiment Coverage

The full mode currently uses `k = 1, 2, 3, 4, 5` and includes:

- paths;
- cycles;
- cycle-threshold examples;
- random trees;
- complete weighted graphs;
- equal-weight complete graphs;
- sparse, medium, and dense random connected graphs;
- repeated random seeds for averaging;
- grid planar graphs;
- theta-style lower-bound examples;
- wide-weight-range graphs.

These families are used to compare sparsity, stretch, lightness, runtime, dense-vs-sparse behavior, planar behavior, and lower-bound intuition.

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
| `general_size_bound`       | report-friendly `n^(1+1/k)` size scale                                       |
| `general_size_bound_ratio` | `spanner_edges / general_size_bound`                                         |
| `planar_size_bound`        | planar theorem size bound for known planar benchmark families, when applicable |
| `planar_size_bound_ratio`  | `spanner_edges / planar_size_bound`                                          |

The `general_size_bound` column is a normalization scale for the common `(2k-1)`-spanner discussion. The Althöfer paper uses a different parameter notation in the theorem statement, so explain the mapping in the report instead of quoting the column as the theorem verbatim.

## Generate Plots and Visual Examples

Run:

```powershell
.\scripts\make_plots.ps1 results\results.csv results\plots
```

This performs three steps:

1. compiles the Java sources;
2. runs `VisualExampleExporter` to export exact Java-computed visual-example CSV files;
3. runs `scripts\make_plots.py` to generate plots, visual graph examples, and summary tables.

Main outputs:

```text
results/plots/avg_spanner_edges_by_k.png
results/plots/avg_edge_reduction_by_k.png
results/plots/avg_weight_over_mst_by_k.png
results/plots/max_observed_stretch_by_k.png
results/plots/avg_general_size_bound_ratio_by_k.png
results/plots/avg_stretch_utilization_by_k.png
results/plots/family_edge_reduction_by_k.png
results/plots/family_weight_over_mst_by_k.png
results/plots/family_general_size_bound_ratio_by_k.png
results/plots/runtime_vs_original_edges.png
results/plots/summary.md
results/plots/summary_by_k.csv
results/plots/summary_by_family_k.csv
results/plots/summary_planar_bounds.csv
results/plots/results_with_derived_metrics.csv
results/plots/visual_examples/
```

The visual examples compare the original graph and the greedy spanner on the same layout. Blue edges are one chosen MST contained in the spanner, red edges are additional spanner edges, and pale gray edges are original edges removed by the spanner.

## Optional DOT Examples

DOT generation is secondary. The report-ready visual examples are produced by `VisualExampleExporter` and `make_plots.py`.

To generate DOT files manually:

```powershell
.\scripts\compile.ps1; java -cp "lib\jgrapht-core-1.5.2.jar;lib\jheaps-0.14.jar;src" DotExampleExporter results\dot
```

To render DOT files with Graphviz:

```powershell
Get-ChildItem results\dot\*.dot | ForEach-Object { dot -Tpng $_.FullName -o ($_.FullName -replace '\.dot$', '.png') }
```

## Interpreting Results

Expected behavior:

- trees should remain unchanged because removing any tree edge disconnects the graph;
- dense graphs, especially complete graphs, should lose many edges;
- increasing `k` usually reduces edge count and lightness, but this is empirical behavior, not a strict nesting guarantee for every graph;
- all valid runs should have `stretch_violations = 0`;
- `weight_over_mst` measures how far the spanner is from the lightest possible connected subgraph;
- planar benchmark families should be compared separately using the planar bound columns.

## Final Submission Checklist

Before submission:

```powershell
.\scripts\test.ps1; .\scripts\run_experiments.ps1 full results\results.csv; .\scripts\make_plots.ps1 results\results.csv results\plots
```

Then verify:

- sanity tests pass;
- full experiment log exists under `results\logs\full-run.log`;
- the log reports zero stretch violations;
- plots and visual examples exist under `results\plots\`;
- report PDF is in `report\`;
- presentation file is in `presentation\`.

Remove generated `.class` files before final submission if required:

```powershell
del src\*.class
```

Do not submit unnecessary temporary files, old unused DOT/TXT files, or `.git/` internals unless specifically required.
