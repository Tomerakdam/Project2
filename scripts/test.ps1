$ErrorActionPreference = "Stop"

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Push-Location $RepoRoot

try {
    & (Join-Path $PSScriptRoot "compile.ps1")

    $java = Get-Command java -ErrorAction Stop
    $Classpath = @(
        "lib/jgrapht-core-1.5.2.jar",
        "lib/jheaps-0.14.jar",
        "src"
    ) -join [System.IO.Path]::PathSeparator

    & $java.Source -cp $Classpath SanityTests
}
finally {
    Pop-Location
}
