param(
    [ValidateSet("quick", "full")]
    [string]$Mode = "full",

    [string]$Output = "results\results.csv",

    # Default logs are written to results\logs\<mode>-run.log.
    # If the file already exists, the previous version is archived first.
    [string]$LogFile = ""
)

$ErrorActionPreference = "Stop"

# Run from the project root, no matter where the script is launched from.
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $ProjectRoot

try {
    $Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"

    if ([string]::IsNullOrWhiteSpace($LogFile)) {
        $LogFile = Join-Path "results\logs" "$Mode-run.log"
    }

    $ResolvedLogFile = if ([System.IO.Path]::IsPathRooted($LogFile)) {
        $LogFile
    } else {
        Join-Path $ProjectRoot $LogFile
    }

    $LogDir = Split-Path -Parent $ResolvedLogFile
    New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

    if (Test-Path $ResolvedLogFile) {
        $ArchiveDir = Join-Path $LogDir "archive"
        New-Item -ItemType Directory -Force -Path $ArchiveDir | Out-Null
        $BaseName = [System.IO.Path]::GetFileNameWithoutExtension($ResolvedLogFile)
        $Extension = [System.IO.Path]::GetExtension($ResolvedLogFile)
        if ([string]::IsNullOrWhiteSpace($Extension)) { $Extension = ".log" }
        $ArchiveFile = Join-Path $ArchiveDir "$BaseName-$Timestamp$Extension"
        Copy-Item -Path $ResolvedLogFile -Destination $ArchiveFile -Force
    }

    "" | Set-Content -Path $ResolvedLogFile -Encoding UTF8

    function Write-Log {
        param([string]$Message = "")

        if ([string]::IsNullOrEmpty($Message)) {
            Write-Host ""
            Add-Content -Path $ResolvedLogFile -Value "" -Encoding UTF8
            return
        }

        $Line = "[{0}] {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $Message
        Write-Host $Line
        Add-Content -Path $ResolvedLogFile -Value $Line -Encoding UTF8
    }

    function ConvertTo-CommandLineArgument {
        param([string]$Argument)

        if ($null -eq $Argument) {
            return '""'
        }

        if ($Argument -notmatch '[\s"]') {
            return $Argument
        }

        # Compatible with Windows PowerShell 5.1, where ProcessStartInfo.ArgumentList
        # is not available. Quote only when needed and escape embedded quotes.
        return '"' + ($Argument -replace '"', '\"') + '"'
    }

    function Invoke-LoggedProcess {
        param(
            [Parameter(Mandatory = $true)]
            [string]$Executable,

            [string[]]$Arguments = @(),

            [string]$FailureMessage = "Command failed."
        )

        $ArgumentText = ($Arguments | ForEach-Object { ConvertTo-CommandLineArgument $_ }) -join " "
        Write-Log ("> {0} {1}" -f $Executable, $ArgumentText)

        # Use System.Diagnostics.Process instead of PowerShell redirection.
        # Reason: commands like `java -version` write normal version text to STDERR.
        # With `$ErrorActionPreference = Stop`, PowerShell 5.1 may treat that STDERR text
        # as a NativeCommandError even when the native process exits with code 0.
        $Process = New-Object System.Diagnostics.Process
        $Process.StartInfo.FileName = $Executable
        $Process.StartInfo.Arguments = $ArgumentText
        $Process.StartInfo.UseShellExecute = $false
        $Process.StartInfo.RedirectStandardOutput = $true
        $Process.StartInfo.RedirectStandardError = $true
        $Process.StartInfo.CreateNoWindow = $true

        [void]$Process.Start()
        $StdOut = $Process.StandardOutput.ReadToEnd()
        $StdErr = $Process.StandardError.ReadToEnd()
        $Process.WaitForExit()
        $ExitCode = $Process.ExitCode

        if (-not [string]::IsNullOrWhiteSpace($StdOut)) {
            $StdOut -split "`r?`n" | Where-Object { $_ -ne "" } | ForEach-Object { Write-Log $_ }
        }
        if (-not [string]::IsNullOrWhiteSpace($StdErr)) {
            $StdErr -split "`r?`n" | Where-Object { $_ -ne "" } | ForEach-Object { Write-Log $_ }
        }

        Write-Log "Exit code: $ExitCode"
        if ($ExitCode -ne 0) {
            throw $FailureMessage
        }
    }

    function Write-ResultsSummary {
        param([string]$CsvPath)

        if (-not (Test-Path $CsvPath)) {
            Write-Log "CSV summary skipped: output file was not found at $CsvPath"
            return
        }

        $Rows = @(Import-Csv $CsvPath)
        $TotalViolations = 0
        $RowsWithViolations = 0

        foreach ($Row in $Rows) {
            if ($null -ne $Row.stretch_violations -and -not [string]::IsNullOrWhiteSpace($Row.stretch_violations)) {
                $Value = [int]$Row.stretch_violations
                $TotalViolations += $Value
                if ($Value -ne 0) {
                    $RowsWithViolations++
                }
            }
        }

        Write-Log "CSV summary:"
        Write-Log "  File: $CsvPath"
        Write-Log "  Rows: $($Rows.Count)"
        Write-Log "  Rows with stretch violations: $RowsWithViolations"
        Write-Log "  Total stretch violations: $TotalViolations"
    }

    # Do not set JAVA_HOME or modify PATH here. Configure Java once per computer,
    # then these scripts use whatever java/javac are available on PATH.
    $RequiredJars = @(
        "lib/jgrapht-core-1.5.2.jar",
        "lib/jheaps-0.14.jar"
    )

    foreach ($Jar in $RequiredJars) {
        if (-not (Test-Path $Jar)) {
            throw "Missing dependency: $Jar"
        }
    }

    $Classpath = (@($RequiredJars) + @("src")) -join [System.IO.Path]::PathSeparator

    New-Item -ItemType Directory -Force -Path "results" | Out-Null
    New-Item -ItemType Directory -Force -Path "results\logs" | Out-Null

    $OutputDir = Split-Path -Parent $Output
    if (-not [string]::IsNullOrWhiteSpace($OutputDir)) {
        New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
    }

    Write-Log "Project root: $ProjectRoot"
    Write-Log "Mode: $Mode"
    Write-Log "Output CSV: $Output"
    Write-Log "Log file: $ResolvedLogFile"
    Write-Log "PowerShell: $($PSVersionTable.PSVersion)"
    Write-Log "Started: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    Write-Log ""

    Write-Log "Using Java:"
    Invoke-LoggedProcess -Executable "java" -Arguments @("-version") -FailureMessage "java -version failed."

    Write-Log ""
    Write-Log "Cleaning old .class files..."
    Get-ChildItem -Path "src" -Filter "*.class" -File -ErrorAction SilentlyContinue | Remove-Item -Force

    Write-Log "Compiling project..."
    $Sources = @(Get-ChildItem -Path "src" -Filter "*.java" -File | Sort-Object Name | ForEach-Object { Join-Path "src" $_.Name })
    if ($Sources.Count -eq 0) {
        throw "No Java source files found in src."
    }

    $JavacArgs = @("-encoding", "UTF-8", "-cp", $Classpath) + $Sources
    Invoke-LoggedProcess -Executable "javac" -Arguments $JavacArgs -FailureMessage "Compilation failed."

    Write-Log ""
    Write-Log "Running experiments: mode=$Mode output=$Output"
    Invoke-LoggedProcess -Executable "java" -Arguments @("-cp", $Classpath, "ExperimentRunner", $Mode, $Output) -FailureMessage "ExperimentRunner failed."

    Write-Log ""
    Write-ResultsSummary -CsvPath $Output

    Write-Log ""
    Write-Log "SUCCESS. Results written to $Output"
    Write-Log "Finished: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
}
catch {
    if (Get-Command Write-Log -ErrorAction SilentlyContinue) {
        Write-Log ""
        Write-Log "FAILED: $($_.Exception.Message)"
        Write-Log "Finished: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    }
    throw
}
finally {
    Pop-Location
}
