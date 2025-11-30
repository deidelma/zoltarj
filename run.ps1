#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Run Zoltar application in debug mode
.DESCRIPTION
    This script runs the Zoltar application using Maven's JavaFX plugin.
    Useful for development and debugging.
.PARAMETER Clean
    Clean and rebuild before running
.EXAMPLE
    .\run.ps1
    Run the application
.EXAMPLE
    .\run.ps1 -Clean
    Clean, rebuild, and run the application
#>

param(
    [switch]$Clean
)

$ErrorActionPreference = "Stop"

Write-Host "=== Running Zoltar in Debug Mode ===" -ForegroundColor Cyan

# Resolve repository root (script directory)
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $repoRoot

if ($Clean.IsPresent) {
    Write-Host "-- Cleaning and building project" -ForegroundColor Yellow
    mvn clean install -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Build failed"
        exit $LASTEXITCODE
    }
} else {
    Write-Host "-- Building project (use -Clean to force clean build)" -ForegroundColor Yellow
    mvn install -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Build failed"
        exit $LASTEXITCODE
    }
}

Write-Host "-- Starting Zoltar application" -ForegroundColor Yellow
Set-Location "$repoRoot\zoltar-gui"

# Run the JavaFX application using the javafx-maven-plugin
mvn javafx:run

if ($LASTEXITCODE -ne 0) {
    Write-Error "Application failed to start"
    exit $LASTEXITCODE
}

Write-Host "=== Application closed ===" -ForegroundColor Green
