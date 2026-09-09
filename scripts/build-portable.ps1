param(
    [string]$AppVersion = "0.1.0",
    [string]$MavenExecutable = "mvn"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$portableInput = Join-Path $projectRoot "target\portable-input"
$portableOutput = Join-Path $projectRoot "dist\oneofx"
$dataDirectory = Join-Path $portableOutput "data"
$backupRoot = Join-Path ([IO.Path]::GetTempPath()) `
    ("oneofx-portable-data-" + [Guid]::NewGuid().ToString("N"))
$dataBackup = Join-Path $backupRoot "data"

$maven = Get-Command $MavenExecutable -ErrorAction Stop
$jpackage = Get-Command jpackage -ErrorAction SilentlyContinue
if ($null -eq $jpackage -and $env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
    if (Test-Path -LiteralPath $candidate) {
        $jpackage = Get-Item -LiteralPath $candidate
    }
}
if ($null -eq $jpackage) {
    throw "jpackage wurde nicht gefunden. Bitte ein vollständiges JDK ab Version 17 installieren."
}
$jpackageExecutable = if ($jpackage.Path) {
    $jpackage.Path
} elseif ($jpackage.Source) {
    $jpackage.Source
} else {
    $jpackage.FullName
}

Push-Location $projectRoot
try {
    & $maven.Source clean test package dependency:copy-dependencies "-DoutputDirectory=$portableInput"
    if ($LASTEXITCODE -ne 0) {
        throw "Der Maven-Build ist fehlgeschlagen."
    }

    Copy-Item -LiteralPath (Join-Path $projectRoot "target\oneofx-fusion-1.0.0-SNAPSHOT.jar") `
        -Destination (Join-Path $portableInput "oneofx-fusion.jar") -Force

    if (Test-Path -LiteralPath $dataDirectory) {
        New-Item -ItemType Directory -Path $backupRoot | Out-Null
        Copy-Item -LiteralPath $dataDirectory -Destination $dataBackup -Recurse
    }

    if (Test-Path -LiteralPath $portableOutput) {
        Remove-Item -LiteralPath $portableOutput -Recurse -Force
    }

    & $jpackageExecutable `
        --type app-image `
        --name oneofx `
        --app-version $AppVersion `
        --vendor OneOfX `
        --input $portableInput `
        --main-jar oneofx-fusion.jar `
        --main-class com.oneofx.fusion.tradingbot.desktop.DesktopLauncher `
        --dest (Join-Path $projectRoot "dist") `
        --java-options '-DONEOFX_HOME=$APPDIR/..'

    if ($LASTEXITCODE -ne 0) {
        throw "Das portable App-Image konnte nicht erzeugt werden."
    }

    if (Test-Path -LiteralPath $dataBackup) {
        Copy-Item -LiteralPath $dataBackup -Destination $dataDirectory -Recurse -Force
    }

    Write-Host "Portable Version erstellt: $portableOutput\oneofx.exe"
} finally {
    Pop-Location
    if (Test-Path -LiteralPath $backupRoot) {
        if ((Test-Path -LiteralPath $dataBackup) `
                -and -not (Test-Path -LiteralPath $dataDirectory)) {
            New-Item -ItemType Directory -Path $portableOutput -Force | Out-Null
            Copy-Item -LiteralPath $dataBackup -Destination $dataDirectory -Recurse -Force
        }
        $resolvedBackup = (Resolve-Path -LiteralPath $backupRoot).Path
        $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
        if (-not $resolvedBackup.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Temporäres Backup liegt außerhalb des erwarteten Temp-Ordners."
        }
        Remove-Item -LiteralPath $resolvedBackup -Recurse -Force
    }
}
