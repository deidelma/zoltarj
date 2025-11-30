param(
    [ValidateSet("app-image", "exe", "msi")]
    [string]$InstallerType = "app-image",
    [switch]$WinConsole
)

$ErrorActionPreference = "Stop"

Write-Host "=== Zoltar Windows Build & Package ===" -ForegroundColor Cyan

# Resolve repository root (script directory)
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $repoRoot

# Helper to run commands and surface failures clearly
function Invoke-Step {
    param(
        [Parameter(Mandatory)] [string]$Description,
        [Parameter(Mandatory)] [scriptblock]$Action
    )

    Write-Host "-- $Description" -ForegroundColor Yellow
    & $Action
}

Invoke-Step "Cleaning previous build artifacts" {
    if (Test-Path "target") {
        Remove-Item "target" -Recurse -Force
    }
}

Invoke-Step "Building Maven modules" {
    mvn clean install -DskipTests | Write-Host
}

$libDir = Join-Path $repoRoot "target\lib"
Invoke-Step "Preparing target/lib" {
    if (Test-Path $libDir) {
        Remove-Item $libDir -Recurse -Force
    }
    New-Item $libDir -ItemType Directory | Out-Null
}

$modules = @(
    "zoltar-app",
    "zoltar-gui",
    "zoltar-core",
    "zoltar-db",
    "zoltar-search",
    "zoltar-pubmed",
    "zoltar-util"
)

Invoke-Step "Copying module jars" {
    foreach ($module in $modules) {
        $jar = "{0}\target\{0}-1.0.0-SNAPSHOT.jar" -f $module
        if (-not (Test-Path $jar)) {
            throw "Expected artifact '$jar' not found."
        }
        Copy-Item $jar $libDir -Force
    }
}

Invoke-Step "Copying runtime dependencies" {
    pushd "$repoRoot\zoltar-gui"
    try {
        mvn dependency:copy-dependencies -DincludeScope=runtime `
            -DoutputDirectory="../target/lib" | Write-Host
    }
    finally {
        popd
    }
}

$installerDir = Join-Path $repoRoot "target\installer"
Invoke-Step "Preparing installer directory" {
    if (Test-Path $installerDir) {
        Remove-Item $installerDir -Recurse -Force
    }
    New-Item $installerDir -ItemType Directory | Out-Null
}

$iconPath = Join-Path $repoRoot "icons\zoltar.ico"
if (-not (Test-Path $iconPath)) {
    Write-Warning "Icon '$iconPath' not found. The executable will use the default icon."
}

# Build module path for JavaFX
$javafxJars = Get-ChildItem -Path $libDir -Filter "javafx-*.jar" | 
    ForEach-Object { $_.FullName }
$modulePath = $javafxJars -join [System.IO.Path]::PathSeparator

$jpackageArgs = @(
    "--type", $InstallerType,
    "--input", "target\lib",
    "--name", "Zoltar",
    "--main-jar", "zoltar-gui-1.0.0-SNAPSHOT.jar",
    "--main-class", "ca.zoltar.gui.Launcher",
    "--dest", "target\installer",
    "--module-path", $modulePath,
    "--add-modules", "javafx.controls,javafx.fxml,java.naming,java.sql",
    "--java-options", "-Xmx2g",
    "--java-options", "--enable-native-access=ALL-UNNAMED",
    "--app-version", "1.0.0",
    "--description", "Zoltar - PubMed Novelty Evaluator",
    "--vendor", "Zoltar Project"
)

if (Test-Path $iconPath) {
    $jpackageArgs += @("--icon", $iconPath)
}

if ($WinConsole.IsPresent) {
    $jpackageArgs += "--win-console"
}

Invoke-Step "Running jpackage ($InstallerType)" {
    jpackage @jpackageArgs | Write-Host
}

Write-Host "=== Build complete ===" -ForegroundColor Green
Write-Host "Output: $installerDir" -ForegroundColor Green
