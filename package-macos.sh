#!/bin/bash
# Package script for creating macOS installer (.dmg or .pkg)

set -e

echo "=== Packaging Zoltar for macOS ==="

# Ensure we're in the project root
cd "$(dirname "$0")"

# Check if runtime exists
if [ ! -d "target/zoltar-runtime" ]; then
    echo "Runtime not found. Running build-runtime.sh first..."
    ./build-runtime.sh
fi

# Application metadata
APP_NAME="Zoltar"
APP_VERSION="1.0.0"
APP_VENDOR="Zoltar Research Tools"
APP_DESCRIPTION="PubMed Novelty Evaluator"
MAIN_CLASS="ca.zoltar.gui.MainApp"
MAIN_JAR="zoltar-gui/target/zoltar-gui-1.0.0-SNAPSHOT.jar"

# Output directory
OUTPUT_DIR="target/installer"
mkdir -p "$OUTPUT_DIR"

# Detect installer type (default to dmg)
TYPE="${1:-dmg}"

echo "Creating $TYPE installer..."

# Find an icon if available
ICON_PATH=""
if [ -f "src/main/resources/icon.icns" ]; then
    ICON_PATH="--icon src/main/resources/icon.icns"
fi

# Build the installer
jpackage \
    --name "$APP_NAME" \
    --app-version "$APP_VERSION" \
    --description "$APP_DESCRIPTION" \
    --vendor "$APP_VENDOR" \
    --runtime-image target/zoltar-runtime \
    --module ca.zoltar.app/ca.zoltar.app.MainApp \
    --type "$TYPE" \
    --dest "$OUTPUT_DIR" \
    $ICON_PATH \
    --mac-package-identifier "ca.zoltar.app" \
    --mac-package-name "$APP_NAME"

echo "=== Package created in $OUTPUT_DIR ==="
ls -lh "$OUTPUT_DIR"

if [ "$TYPE" = "dmg" ]; then
    echo ""
    echo "To install:"
    echo "  1. Open $OUTPUT_DIR/$APP_NAME-$APP_VERSION.dmg"
    echo "  2. Drag Zoltar.app to Applications folder"
fi
