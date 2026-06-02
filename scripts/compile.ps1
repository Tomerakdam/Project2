$ErrorActionPreference = "Stop"

# Compile from the project root, regardless of the caller's current directory.
$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Push-Location $RepoRoot

try {
    $javac = Get-Command javac -ErrorAction Stop

    Write-Host "Using javac:"
    & $javac.Source -version

    $RequiredJars = @(
        "lib/jgrapht-core-1.5.2.jar",
        "lib/jheaps-0.14.jar"
    )

    foreach ($Jar in $RequiredJars) {
        if (-not (Test-Path $Jar)) {
            throw "Missing dependency: $Jar"
        }
    }

    Get-ChildItem -Path "src" -Filter "*.class" -File -ErrorAction SilentlyContinue | Remove-Item -Force

    $Sources = @(Get-ChildItem -Path "src" -Filter "*.java" -File | Sort-Object Name | ForEach-Object { Join-Path "src" $_.Name })

    if ($Sources.Count -eq 0) {
        throw "No Java source files found in src."
    }

    $Classpath = (@($RequiredJars) + @("src")) -join [System.IO.Path]::PathSeparator

    & $javac.Source -encoding UTF-8 -cp $Classpath @Sources
}
finally {
    Pop-Location
}
