@echo off
REM Package script for creating Windows installer (.msi or .exe)

echo === Packaging Zoltar for Windows ===

cd /d %~dp0

REM Check if runtime exists
if not exist "target\zoltar-runtime" (
    echo Runtime not found. Please run build-runtime.bat first
    exit /b 1
)

REM Application metadata
set APP_NAME=Zoltar
set APP_VERSION=1.0.0
set APP_VENDOR=Zoltar Research Tools
set APP_DESCRIPTION=PubMed Novelty Evaluator
set MAIN_CLASS=ca.zoltar.gui.MainApp

REM Output directory
set OUTPUT_DIR=target\installer
if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"

REM Detect installer type (default to msi)
set TYPE=%1
if "%TYPE%"=="" set TYPE=msi

echo Creating %TYPE% installer...

REM Find an icon if available
set ICON_PATH=
if exist "src\main\resources\icon.ico" (
    set ICON_PATH=--icon src\main\resources\icon.ico
)

REM Build the installer
jpackage ^
    --name "%APP_NAME%" ^
    --app-version "%APP_VERSION%" ^
    --description "%APP_DESCRIPTION%" ^
    --vendor "%APP_VENDOR%" ^
    --runtime-image target\zoltar-runtime ^
    --module ca.zoltar.app/ca.zoltar.app.MainApp ^
    --type "%TYPE%" ^
    --dest "%OUTPUT_DIR%" ^
    %ICON_PATH% ^
    --win-dir-chooser ^
    --win-menu ^
    --win-shortcut

echo === Package created in %OUTPUT_DIR% ===
dir "%OUTPUT_DIR%"

echo.
echo To install:
echo   1. Run %OUTPUT_DIR%\%APP_NAME%-%APP_VERSION%.%TYPE%
echo   2. Follow the installation wizard
