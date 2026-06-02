$ErrorActionPreference = "Stop"

# This script only checks the Java toolchain that is already configured on this machine.
# It intentionally does not set JAVA_HOME or modify PATH. Configure those once per
# computer, then use the normal project scripts.

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Push-Location $RepoRoot

try {
    $java = Get-Command java -ErrorAction Stop
    $javac = Get-Command javac -ErrorAction Stop

    if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        Write-Host "JAVA_HOME is not set. This is allowed if java/javac are already on PATH."
    } else {
        Write-Host "JAVA_HOME=$env:JAVA_HOME"
    }

    Write-Host "Using java: $($java.Source)"
    & $java.Source -version

    Write-Host "Using javac: $($javac.Source)"
    & $javac.Source -version
}
finally {
    Pop-Location
}
