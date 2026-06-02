# Visual Graph Examples

Each image compares the original graph with the greedy weighted spanner on the same vertex layout.
The graph and spanner edges are exported by the Java implementation; Python only renders the already-computed data.
Blue edges are one chosen MST contained in the spanner; red edges are additional spanner edges; pale gray edges are removed edges shown for context.

| example                              |   k |   n |   original_edges |   spanner_edges |   edge_reduction_percent |   max_stretch |   allowed_stretch |   spanner_weight |   mst_weight |   weight_over_mst |
|:-------------------------------------|----:|----:|-----------------:|----------------:|-------------------------:|--------------:|------------------:|-----------------:|-------------:|------------------:|
| complete_n10_seed102                 |   2 |  10 |               45 |              10 |                   77.778 |         2.091 |             3.000 |           51.000 |       42.000 |             1.214 |
| random_connected_n14_extra22_seed104 |   3 |  14 |               35 |              13 |                   62.857 |         3.000 |             5.000 |          103.000 |      103.000 |             1.000 |
| grid_5x5_weighted_seed601            |   3 |  25 |               40 |              24 |                   40.000 |         4.500 |             5.000 |          184.000 |      184.000 |             1.000 |
| theta_lower_bound_targetK3_chains6   |   3 |  20 |               24 |              24 |                    0.000 |         1.000 |             5.000 |           24.000 |       19.000 |             1.263 |
| cycle_threshold_n7                   |   4 |   7 |                7 |               6 |                   14.286 |         6.000 |             7.000 |            6.000 |        6.000 |             1.000 |
| complete_unit_n20                    |   2 |  20 |              190 |              19 |                   90.000 |         2.000 |             3.000 |           19.000 |       19.000 |             1.000 |

Recommended report use:
- use `complete_n10_seed102_k2.png` to show strong sparsification on dense weighted graphs;
- use `complete_unit_n20_k2.png` to show the equal-weight complete-graph family collapsing to a tree-like spanner;
- use `cycle_threshold_n7_k4.png` to illustrate the small cycle-threshold family where the last cycle edge becomes skippable;
- use `grid_5x5_weighted_seed601_k3.png` to show a structured planar weighted example;
- use `theta_lower_bound_targetK3_chains6_k3.png` to connect to lower-bound intuition;
- use `random_connected_n14_extra22_seed104_k3.png` as a generic non-complete example.
