#!/usr/bin/env python3
"""
Generate report figures from ExperimentRunner CSV output.

This script creates two kinds of report material:
1. quantitative plots from results/results.csv
2. selected visual graph examples showing original graph vs greedy spanner

Usage:
    python scripts/make_plots.py results/results.csv results/plots
"""

from __future__ import annotations

import sys
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Tuple

import matplotlib.pyplot as plt
import pandas as pd


REQUIRED_COLUMNS = {
    "graph", "n", "original_edges", "spanner_edges", "k", "stretch",
    "edge_reduction_percent", "original_weight", "spanner_weight",
    "mst_weight", "max_stretch", "stretch_violations", "runtime_ms",
}

EPS = 1e-9


def configure_matplotlib() -> None:
    """Use a clean report/presentation style with Matplotlib mathtext.

    This does not require an external LaTeX installation. Matplotlib's built-in
    math renderer is enough for labels such as $H_k$, $G$, $|E_H|$, and $2k-1$.
    """
    plt.rcParams.update({
        "figure.dpi": 140,
        "savefig.dpi": 240,
        "font.family": "DejaVu Sans",
        "mathtext.fontset": "stix",
        "axes.titlesize": 15,
        "axes.titleweight": "bold",
        "axes.labelsize": 12,
        "xtick.labelsize": 10,
        "ytick.labelsize": 10,
        "legend.fontsize": 8,
        "axes.spines.top": False,
        "axes.spines.right": False,
        "axes.grid": True,
        "grid.alpha": 0.28,
    })


def style_axes(ax) -> None:
    ax.set_facecolor("#fbfbfd")
    ax.grid(True, alpha=0.28, linewidth=0.8)
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)


def set_report_title(ax, title: str, subtitle: Optional[str] = None) -> None:
    if subtitle:
        ax.set_title(f"{title}\n{subtitle}", pad=14, linespacing=1.35)
    else:
        ax.set_title(title, pad=14)


def add_header_box(fig, text: str, y: float = 0.965) -> None:
    fig.text(
        0.5,
        y,
        text,
        ha="center",
        va="top",
        fontsize=11,
        bbox=dict(
            facecolor="#f7f7fb",
            edgecolor="#d5d5e3",
            boxstyle="round,pad=0.45",
            linewidth=0.9,
        ),
    )


def pretty_family_name(name: str) -> str:
    return name.replace("_", " ").title()


# ---------------------------------------------------------------------------
# Quantitative plots from the Java experiment CSV
# ---------------------------------------------------------------------------


def graph_family(name: str) -> str:
    if name.startswith("path"):
        return "path"
    if name.startswith("cycle_threshold"):
        return "cycle_threshold"
    if name.startswith("cycle"):
        return "cycle"
    if name.startswith("random_tree"):
        return "tree"
    if name.startswith("complete_unit"):
        return "complete_unit"
    if name.startswith("complete"):
        return "complete"
    if name.startswith("grid"):
        return "grid_planar"
    if name.startswith("theta_lower_bound"):
        return "theta_lower_bound"
    if "random_connected_sparse" in name:
        return "random_sparse"
    if "random_connected_medium" in name or "repeat_medium" in name:
        return "random_medium"
    if "random_connected_dense" in name:
        return "random_dense"
    if "wide_weights" in name:
        return "wide_weights"
    if name.startswith("random_connected"):
        return "random_connected"
    return "other"


def validate(df: pd.DataFrame) -> None:
    missing = REQUIRED_COLUMNS - set(df.columns)
    if missing:
        raise ValueError(f"Missing required CSV columns: {sorted(missing)}")
    if (df["stretch_violations"] > 0).any():
        bad = df[df["stretch_violations"] > 0][["graph", "k", "stretch_violations"]]
        print("WARNING: stretch violations found:")
        print(bad.to_string(index=False))


def save_line_plot(
    data: pd.DataFrame,
    x: str,
    ys: List[str],
    title: str,
    ylabel: str,
    path: Path,
    *,
    subtitle: Optional[str] = None,
    labels: Optional[Dict[str, str]] = None,
) -> None:
    fig, ax = plt.subplots(figsize=(8.8, 5.2))
    style_axes(ax)
    for y in ys:
        ax.plot(
            data[x],
            data[y],
            marker="o",
            linewidth=2.1,
            markersize=5.0,
            label=(labels or {}).get(y, y),
        )
    set_report_title(ax, title, subtitle)
    ax.set_xlabel(r"Spanner parameter $k$")
    ax.set_ylabel(ylabel)
    ax.set_xticks(sorted(data[x].unique()))
    if len(ys) > 1:
        ax.legend(frameon=True, framealpha=0.92)
    fig.tight_layout()
    fig.savefig(path)
    plt.close(fig)


def save_family_line_plot(
    df: pd.DataFrame,
    metric: str,
    title: str,
    ylabel: str,
    path: Path,
    *,
    subtitle: Optional[str] = None,
) -> None:
    grouped = df.groupby(["family", "k"], as_index=False)[metric].mean()
    fig, ax = plt.subplots(figsize=(10.2, 5.8))
    style_axes(ax)
    for family, part in grouped.groupby("family"):
        part = part.sort_values("k")
        ax.plot(part["k"], part[metric], marker="o", linewidth=2.0, markersize=4.6, label=pretty_family_name(family))
    set_report_title(ax, title, subtitle)
    ax.set_xlabel(r"Spanner parameter $k$")
    ax.set_ylabel(ylabel)
    ax.legend(fontsize=8, ncols=2, frameon=True, framealpha=0.92)
    fig.tight_layout()
    fig.savefig(path)
    plt.close(fig)


def save_runtime_scatter(df: pd.DataFrame, path: Path) -> None:
    fig, ax = plt.subplots(figsize=(9.4, 5.4))
    style_axes(ax)
    for family, part in df.groupby("family"):
        ax.scatter(part["original_edges"], part["runtime_ms"], label=pretty_family_name(family), alpha=0.75, s=34)
    set_report_title(
        ax,
        r"Runtime Scaling of Greedy Construction",
        r"empirical cost as a function of input density $|E_G|$",
    )
    ax.set_xlabel(r"Original edge count $|E_G|$")
    ax.set_ylabel(r"Runtime $\mathrm{ms}$")
    ax.legend(fontsize=8, ncols=2, frameon=True, framealpha=0.92)
    fig.tight_layout()
    fig.savefig(path)
    plt.close(fig)


def save_summary_markdown(df: pd.DataFrame, by_k: pd.DataFrame, by_family_k: pd.DataFrame, output_dir: Path) -> None:
    total_rows = len(df)
    violations = int(df["stretch_violations"].sum())
    max_stretch = df["max_stretch"].max()
    max_reduction = df["edge_reduction_percent"].max()

    lines = [
        "# Experiment Summary",
        "",
        f"Rows: {total_rows}",
        f"Distinct scenarios: {df['graph'].nunique()}",
        f"k values: {', '.join(map(str, sorted(df['k'].unique())))}",
        f"Total stretch violations: {violations}",
        f"Maximum observed stretch: {max_stretch:.6f}",
        f"Maximum edge reduction: {max_reduction:.2f}%",
        "",
        "## Generated visual examples",
        "",
        "The folder `visual_examples/` contains side-by-side drawings of original graphs and their greedy spanners.",
        "Blue edges are one chosen MST contained in the spanner; red edges are additional spanner edges.",
        "",
        "## Summary by k",
        "",
        by_k.to_markdown(index=False, floatfmt=".4f"),
        "",
        "## Summary by family and k",
        "",
        by_family_k.to_markdown(index=False, floatfmt=".4f"),
        "",
    ]
    (output_dir / "summary.md").write_text("\n".join(lines), encoding="utf-8")


def create_quantitative_plots(input_csv: Path, output_dir: Path) -> None:
    df = pd.read_csv(input_csv)
    validate(df)

    df["family"] = df["graph"].map(graph_family)

    # New Java CSV files already include the report-ready derived metrics below.
    # Keep fallback formulas so old CSV files can still be plotted.
    if "weight_over_mst" not in df.columns:
        df["weight_over_mst"] = df["spanner_weight"] / df["mst_weight"]
    if "allowed_stretch" not in df.columns:
        df["allowed_stretch"] = 2 * df["k"] - 1
    if "stretch_utilization" not in df.columns:
        df["stretch_utilization"] = df["max_stretch"] / df["allowed_stretch"]
    if "general_size_bound" not in df.columns:
        df["general_size_bound"] = df.apply(lambda row: row["n"] ** (1.0 + 1.0 / row["k"]), axis=1)
    if "general_size_bound_ratio" not in df.columns:
        df["general_size_bound_ratio"] = df["spanner_edges"] / df["general_size_bound"]

    df["spanner_edges_over_original"] = df["spanner_edges"] / df["original_edges"]

    planar_mask = df["family"].isin(["grid_planar", "theta_lower_bound", "path", "cycle", "cycle_threshold", "tree"]) & (df["k"] >= 2)
    if "planar_size_bound" not in df.columns:
        df["planar_size_bound"] = pd.NA
        df.loc[planar_mask, "planar_size_bound"] = (
            (df.loc[planar_mask, "n"] - 1) * (1.0 + 1.0 / (df.loc[planar_mask, "k"] - 1))
        )
    if "planar_size_bound_ratio" not in df.columns:
        df["planar_size_bound_ratio"] = pd.NA
        df.loc[planar_mask, "planar_size_bound_ratio"] = (
            df.loc[planar_mask, "spanner_edges"] / df.loc[planar_mask, "planar_size_bound"]
        )

    # Backwards-compatible aliases for any older notebooks or report notes.
    df["theoretical_general_size_bound"] = df["general_size_bound"]
    df["spanner_edges_over_general_bound"] = df["general_size_bound_ratio"]
    df["spanner_edges_over_planar_bound"] = df["planar_size_bound_ratio"]

    planar = df[planar_mask].copy()

    by_k = df.groupby("k", as_index=False).agg(
        avg_spanner_edges=("spanner_edges", "mean"),
        avg_edge_reduction_percent=("edge_reduction_percent", "mean"),
        avg_weight_over_mst=("weight_over_mst", "mean"),
        max_observed_stretch=("max_stretch", "max"),
        avg_runtime_ms=("runtime_ms", "mean"),
        avg_general_bound_ratio=("general_size_bound_ratio", "mean"),
        avg_stretch_utilization=("stretch_utilization", "mean"),
    ).sort_values("k")

    by_family_k = df.groupby(["family", "k"], as_index=False).agg(
        scenarios=("graph", "nunique"),
        avg_original_edges=("original_edges", "mean"),
        avg_spanner_edges=("spanner_edges", "mean"),
        avg_edge_reduction_percent=("edge_reduction_percent", "mean"),
        avg_weight_over_mst=("weight_over_mst", "mean"),
        max_observed_stretch=("max_stretch", "max"),
        avg_runtime_ms=("runtime_ms", "mean"),
        avg_general_bound_ratio=("general_size_bound_ratio", "mean"),
    ).sort_values(["family", "k"])

    by_k.to_csv(output_dir / "summary_by_k.csv", index=False)
    by_family_k.to_csv(output_dir / "summary_by_family_k.csv", index=False)
    df.to_csv(output_dir / "results_with_derived_metrics.csv", index=False)
    if not planar.empty:
        planar.groupby(["family", "k"], as_index=False).agg(
            avg_planar_bound_ratio=("planar_size_bound_ratio", "mean"),
            max_planar_bound_ratio=("planar_size_bound_ratio", "max"),
        ).to_csv(output_dir / "summary_planar_bounds.csv", index=False)

    save_line_plot(
        by_k, "k", ["avg_spanner_edges"],
        r"Average Spanner Size $|E_H|$",
        r"Average edge count $\mathbb{E}[|E_H|]$",
        output_dir / "avg_spanner_edges_by_k.png",
        subtitle=r"Greedy weighted spanner $H_k \subseteq G$ with stretch bound $2k-1$",
        labels={"avg_spanner_edges": r"$\mathbb{E}[|E_H|]$"},
    )
    save_line_plot(
        by_k, "k", ["avg_edge_reduction_percent"],
        r"Average Edge Reduction",
        r"Reduction $100\cdot(1-|E_H|/|E_G|)$",
        output_dir / "avg_edge_reduction_by_k.png",
        subtitle=r"higher values mean stronger sparsification",
        labels={"avg_edge_reduction_percent": r"$100(1-|E_H|/|E_G|)$"},
    )
    save_line_plot(
        by_k, "k", ["avg_weight_over_mst"],
        r"Average Spanner Lightness",
        r"Average lightness $w(H)/w(\mathrm{MST})$",
        output_dir / "avg_weight_over_mst_by_k.png",
        subtitle=r"total spanner weight relative to the minimum spanning tree",
        labels={"avg_weight_over_mst": r"$w(H)/w(\mathrm{MST})$"},
    )
    save_line_plot(
        by_k, "k", ["max_observed_stretch"],
        r"Maximum Observed Stretch",
        r"$\max_{u,v} d_H(u,v)/d_G(u,v)$",
        output_dir / "max_observed_stretch_by_k.png",
        subtitle=r"empirical verification against the theoretical bound $2k-1$",
        labels={"max_observed_stretch": r"$\max\, d_H/d_G$"},
    )
    save_line_plot(
        by_k, "k", ["avg_general_bound_ratio"],
        r"General Size-Bound Ratio",
        r"Average ratio $|E_H|/n^{1+1/k}$",
        output_dir / "avg_general_size_bound_ratio_by_k.png",
        subtitle=r"normalizes measured size by the standard $n^{1+1/k}$ spanner size scale",
        labels={"avg_general_bound_ratio": r"$|E_H|/n^{1+1/k}$"},
    )
    save_line_plot(
        by_k, "k", ["avg_stretch_utilization"],
        r"Stretch-Bound Utilization",
        r"Average utilization $(\max d_H/d_G)/(2k-1)$",
        output_dir / "avg_stretch_utilization_by_k.png",
        subtitle=r"values below $1$ mean the measured stretch satisfies the guarantee",
        labels={"avg_stretch_utilization": r"$(\max d_H/d_G)/(2k-1)$"},
    )
    save_family_line_plot(
        df, "edge_reduction_percent",
        r"Edge Reduction by Graph Family",
        r"Average reduction $100\cdot(1-|E_H|/|E_G|)$",
        output_dir / "family_edge_reduction_by_k.png",
        subtitle=r"comparison across paths, grids, complete graphs, random graphs, and theta examples",
    )
    save_family_line_plot(
        df, "weight_over_mst",
        r"Lightness by Graph Family",
        r"Average lightness $w(H)/w(\mathrm{MST})$",
        output_dir / "family_weight_over_mst_by_k.png",
        subtitle=r"shows which graph families force heavier spanners",
    )
    save_family_line_plot(
        df, "general_size_bound_ratio",
        r"General Size-Bound Ratio by Family",
        r"Average ratio $|E_H|/n^{1+1/k}$",
        output_dir / "family_general_size_bound_ratio_by_k.png",
        subtitle=r"measured sparsity relative to the standard $n^{1+1/k}$ size scale",
    )
    save_runtime_scatter(df, output_dir / "runtime_vs_original_edges.png")

    save_summary_markdown(df, by_k, by_family_k, output_dir)


# ---------------------------------------------------------------------------
# Representative graph visualizations from Java-exported data
# ---------------------------------------------------------------------------


def parse_bool(value) -> bool:
    if isinstance(value, bool):
        return value
    return str(value).strip().lower() in {"true", "1", "yes", "y"}


def format_weight_label(w: float) -> str:
    if abs(w - round(w)) < 1e-9:
        return str(int(round(w)))
    return f"{w:.2f}".rstrip("0").rstrip(".")


def edge_rows_to_vertices(rows: pd.DataFrame) -> Dict[int, Tuple[float, float]]:
    coords: Dict[int, Tuple[float, float]] = {}
    for row in rows.itertuples(index=False):
        coords[int(row.u)] = (float(row.x_u), float(row.y_u))
        coords[int(row.v)] = (float(row.x_v), float(row.y_v))
    return coords


def draw_edges(
    ax,
    rows: Iterable,
    *,
    color: str,
    linewidth: float,
    alpha: float,
    zorder: int = 1,
    label_weights: bool = False,
    label_color: str = "black",
) -> None:
    row_list = list(rows)
    for row in row_list:
        ax.plot(
            [float(row.x_u), float(row.x_v)],
            [float(row.y_u), float(row.y_v)],
            color=color,
            linewidth=linewidth,
            alpha=alpha,
            zorder=zorder,
        )

    if label_weights:
        for row in row_list:
            mx = (float(row.x_u) + float(row.x_v)) / 2.0
            my = (float(row.y_u) + float(row.y_v)) / 2.0
            ax.text(
                mx,
                my,
                format_weight_label(float(row.weight)),
                fontsize=6,
                color=label_color,
                ha="center",
                va="center",
                zorder=zorder + 10,
                bbox=dict(facecolor="white", edgecolor="none", alpha=0.72, boxstyle="round,pad=0.12"),
            )


def draw_nodes(ax, coords: Dict[int, Tuple[float, float]], label_nodes: bool) -> None:
    vertices = sorted(coords)
    xs = [coords[v][0] for v in vertices]
    ys = [coords[v][1] for v in vertices]
    ax.scatter(xs, ys, s=80, facecolor="white", edgecolor="black", linewidth=1.1, zorder=5)
    if label_nodes:
        for v in vertices:
            x, y = coords[v]
            ax.text(x, y, str(v), fontsize=7, ha="center", va="center", zorder=6)


def draw_comparison_from_java_data(example_name: str, rows: pd.DataFrame, summary_row: pd.Series, output_path: Path) -> None:
    k = int(summary_row["k"])
    n = int(summary_row["n"])
    original_edges = int(summary_row["original_edges"])
    spanner_edges = int(summary_row["spanner_edges"])
    reduction = float(summary_row["edge_reduction_percent"])
    stretch = float(summary_row["max_stretch"])
    allowed_stretch = float(summary_row["allowed_stretch"])
    weight_ratio = float(summary_row["weight_over_mst"])

    coords = edge_rows_to_vertices(rows)
    row_objects = list(rows.itertuples(index=False))
    spanner = [row for row in row_objects if parse_bool(row.in_spanner)]
    mst_in_spanner = [row for row in spanner if parse_bool(row.in_mst)]
    extra_in_spanner = [row for row in spanner if not parse_bool(row.in_mst)]
    removed = [row for row in row_objects if not parse_bool(row.in_spanner)]

    show_original_weights = parse_bool(rows["show_original_weights"].iloc[0])
    show_spanner_weights = parse_bool(rows["show_spanner_weights"].iloc[0])
    label_nodes = parse_bool(rows["label_nodes"].iloc[0])

    fig, axes = plt.subplots(1, 2, figsize=(13.2, 6.2))
    fig.patch.set_facecolor("white")

    title_name = example_name.replace("_", " ")
    fig.suptitle(
        rf"Greedy Weighted Spanner Visualization from Java Output: $H_k \subseteq G$  ($k={k}$, $2k-1={int(allowed_stretch)}$)"
        + "\n"
        + title_name,
        fontsize=15,
        fontweight="bold",
        y=0.985,
    )
    add_header_box(
        fig,
        rf"$|V|={n}$   $|E_G|={original_edges}$   $|E_H|={spanner_edges}$   "
        rf"$\max\, d_H/d_G={stretch:.2f}$   $w(H)/w(\mathrm{{MST}})={weight_ratio:.2f}$   "
        rf"edge reduction $={reduction:.1f}\%$",
        y=0.905,
    )

    ax = axes[0]
    ax.set_facecolor("#fbfbfd")
    draw_edges(
        ax,
        row_objects,
        color="#999999",
        linewidth=1.0,
        alpha=0.55,
        label_weights=show_original_weights,
        label_color="#444444",
    )
    draw_nodes(ax, coords, label_nodes)
    ax.set_title(rf"Original graph $G$: $|E_G|={original_edges}$", fontsize=12, pad=10)
    ax.set_aspect("equal")
    ax.axis("off")

    ax = axes[1]
    ax.set_facecolor("#fbfbfd")
    draw_edges(ax, removed, color="#dddddd", linewidth=0.8, alpha=0.35)
    draw_edges(
        ax,
        mst_in_spanner,
        color="#1f77b4",
        linewidth=2.2,
        alpha=0.95,
        zorder=3,
        label_weights=show_spanner_weights,
        label_color="#1f77b4",
    )
    draw_edges(
        ax,
        extra_in_spanner,
        color="#d62728",
        linewidth=2.0,
        alpha=0.90,
        zorder=4,
        label_weights=show_spanner_weights,
        label_color="#d62728",
    )
    draw_nodes(ax, coords, label_nodes)
    ax.set_title(rf"Greedy spanner $H_k$: $|E_H|={spanner_edges}$, $\max\, d_H/d_G={stretch:.2f}$", fontsize=12, pad=10)
    ax.set_aspect("equal")
    ax.axis("off")

    legend_text = (
        r"blue = chosen MST contained in $H_k$    "
        r"red = extra spanner edges    "
        r"pale gray = removed edges"
    )
    fig.text(0.5, 0.035, legend_text, ha="center", fontsize=10)
    fig.tight_layout(rect=[0, 0.06, 1, 0.84])
    fig.savefig(output_path, dpi=240)
    plt.close(fig)


def create_visual_examples(output_dir: Path) -> None:
    visual_dir = output_dir / "visual_examples"
    visual_dir.mkdir(parents=True, exist_ok=True)

    edges_path = visual_dir / "visual_examples_edges.csv"
    summary_path = visual_dir / "visual_examples_summary.csv"

    if not edges_path.exists() or not summary_path.exists():
        print(
            "WARNING: Java visual-example CSV files were not found. "
            "Run scripts/make_plots.ps1, or run VisualExampleExporter before calling make_plots.py directly. "
            "Skipping visual example PNG generation."
        )
        return

    edges = pd.read_csv(edges_path)
    summary = pd.read_csv(summary_path)

    required_edge_columns = {
        "example", "k", "n", "u", "v", "weight", "in_spanner", "in_mst",
        "x_u", "y_u", "x_v", "y_v", "show_original_weights", "show_spanner_weights", "label_nodes",
    }
    missing_edges = required_edge_columns - set(edges.columns)
    if missing_edges:
        raise ValueError(f"Missing required Java visual edge columns: {sorted(missing_edges)}")

    required_summary_columns = {
        "example", "k", "n", "original_edges", "spanner_edges", "edge_reduction_percent",
        "max_stretch", "allowed_stretch", "spanner_weight", "mst_weight", "weight_over_mst",
    }
    missing_summary = required_summary_columns - set(summary.columns)
    if missing_summary:
        raise ValueError(f"Missing required Java visual summary columns: {sorted(missing_summary)}")

    for summary_row in summary.itertuples(index=False):
        example_name = str(summary_row.example)
        rows = edges[edges["example"] == example_name].copy()
        if rows.empty:
            raise ValueError(f"No edge rows found for visual example: {example_name}")
        out = visual_dir / f"{example_name}_k{int(summary_row.k)}.png"
        draw_comparison_from_java_data(example_name, rows, pd.Series(summary_row._asdict()), out)

    lines = [
        "# Visual Graph Examples",
        "",
        "Each image compares the original graph with the greedy weighted spanner on the same vertex layout.",
        "The graph and spanner edges are exported by the Java implementation; Python only renders the already-computed data.",
        "Blue edges are one chosen MST contained in the spanner; red edges are additional spanner edges; pale gray edges are removed edges shown for context.",
        "",
        summary.to_markdown(index=False, floatfmt=".3f"),
        "",
        "Recommended report use:",
        "- use `complete_n10_seed102_k2.png` to show strong sparsification on dense weighted graphs;",
        "- use `complete_unit_n20_k2.png` to show the equal-weight complete-graph family collapsing to a tree-like spanner;",
        "- use `cycle_threshold_n7_k4.png` to illustrate the small cycle-threshold family where the last cycle edge becomes skippable;",
        "- use `grid_5x5_weighted_seed601_k3.png` to show a structured planar weighted example;",
        "- use `theta_lower_bound_targetK3_chains6_k3.png` to connect to lower-bound intuition;",
        "- use `random_connected_n14_extra22_seed104_k3.png` as a generic non-complete example.",
        "",
    ]
    (visual_dir / "README_visual_examples.md").write_text("\n".join(lines), encoding="utf-8")

# ---------------------------------------------------------------------------
# Entrypoint
# ---------------------------------------------------------------------------


def main() -> int:
    input_csv = Path(sys.argv[1]) if len(sys.argv) >= 2 else Path("results/results.csv")
    output_dir = Path(sys.argv[2]) if len(sys.argv) >= 3 else Path("results/plots")
    output_dir.mkdir(parents=True, exist_ok=True)

    configure_matplotlib()
    create_quantitative_plots(input_csv, output_dir)
    create_visual_examples(output_dir)

    print(f"Wrote quantitative plots, summaries, and visual examples to: {output_dir.resolve()}")
    print(f"Visual examples are in: {(output_dir / 'visual_examples').resolve()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
