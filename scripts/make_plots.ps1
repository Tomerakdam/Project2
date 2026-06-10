param(
    [ValidateSet("structural", "large")]
    [string]$PlotMode = "structural",

    [string]$InputCsv = "",

    [string]$OutputDir = ""
)

$ErrorActionPreference = "Stop"

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Push-Location $RepoRoot

try {
    if ([string]::IsNullOrWhiteSpace($InputCsv)) {
        switch ($PlotMode) {
            "structural" {
                $InputCsv = "results\structural_results.csv"
            }
            "large" {
                $InputCsv = "results\large_results.csv"
            }
        }
    }

    if ([string]::IsNullOrWhiteSpace($OutputDir)) {
        switch ($PlotMode) {
            "structural" {
                $OutputDir = "results\plots_structural"
            }
            "large" {
                $OutputDir = "results\plots_large"
            }
        }
    }

    # Compile first because visual examples are exported by the Java implementation.
    & (Join-Path $PSScriptRoot "compile.ps1")

    $RequiredJars = @(
        "lib/jgrapht-core-1.5.2.jar",
        "lib/jheaps-0.14.jar"
    )
    $Classpath = (@($RequiredJars) + @("src")) -join [System.IO.Path]::PathSeparator

    if ($PlotMode -eq "structural") {
        $VisualExamplesDir = Join-Path $OutputDir "visual_examples"
        New-Item -ItemType Directory -Force -Path $VisualExamplesDir | Out-Null

        Write-Host "Exporting visual-example data from Java..."
        java -cp $Classpath VisualExampleExporter $VisualExamplesDir
    } else {
        Write-Host "Skipping visual examples for large mode."
    }

    $WindowsVenvPython = Join-Path $RepoRoot ".venv\Scripts\python.exe"
    $UnixVenvPython = Join-Path $RepoRoot ".venv/bin/python"

    if (Test-Path $WindowsVenvPython) {
        & $WindowsVenvPython ".\scripts\make_plots.py" $InputCsv $OutputDir $PlotMode
    } elseif (Test-Path $UnixVenvPython) {
        & $UnixVenvPython ".\scripts\make_plots.py" $InputCsv $OutputDir $PlotMode
    } elseif (Get-Command py -ErrorAction SilentlyContinue) {
        py -3 ".\scripts\make_plots.py" $InputCsv $OutputDir $PlotMode
    } elseif (Get-Command python -ErrorAction SilentlyContinue) {
        python ".\scripts\make_plots.py" $InputCsv $OutputDir $PlotMode
    } else {
        throw "Python was not found. Install Python or add it to PATH, then install pandas, matplotlib, and tabulate."
    }
}
finally {
    Pop-Location
}
