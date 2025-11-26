#!/bin/bash
# Build script for Windows runtime (to be run on Windows with Git Bash)

set -e

echo "=== Building Zoltar Custom Runtime with jlink (Windows) ==="

# Ensure we're in the project root
cd "$(dirname "$0")"

# Build the project
echo "Building project..."
mvn clean package -DskipTests

# Prepare module path (Windows paths)
MODULE_PATH="zoltar-app\\target\\zoltar-app-1.0.0-SNAPSHOT.jar"
MODULE_PATH="${MODULE_PATH};zoltar-gui\\target\\zoltar-gui-1.0.0-SNAPSHOT.jar"
MODULE_PATH="${MODULE_PATH};zoltar-core\\target\\zoltar-core-1.0.0-SNAPSHOT.jar"
MODULE_PATH="${MODULE_PATH};zoltar-db\\target\\zoltar-db-1.0.0-SNAPSHOT.jar"
MODULE_PATH="${MODULE_PATH};zoltar-search\\target\\zoltar-search-1.0.0-SNAPSHOT.jar"
MODULE_PATH="${MODULE_PATH};zoltar-pubmed\\target\\zoltar-pubmed-1.0.0-SNAPSHOT.jar"
MODULE_PATH="${MODULE_PATH};zoltar-util\\target\\zoltar-util-1.0.0-SNAPSHOT.jar"

# Add dependencies
for jar in zoltar-gui/target/lib/*.jar; do
    if [ -f "$jar" ]; then
        # Convert to Windows path
        winjar=$(echo "$jar" | sed 's/\//\\/g')
        MODULE_PATH="${MODULE_PATH};${winjar}"
    fi
done

# Add JavaFX from Maven repo
JAVAFX_VERSION="21.0.1"
MAVEN_REPO="$USERPROFILE\\.m2\\repository"
JAVAFX_BASE="${MAVEN_REPO}\\org\\openjfx"

MODULE_PATH="${MODULE_PATH};${JAVAFX_BASE}\\javafx-base\\${JAVAFX_VERSION}\\javafx-base-${JAVAFX_VERSION}-win.jar"
MODULE_PATH="${MODULE_PATH};${JAVAFX_BASE}\\javafx-controls\\${JAVAFX_VERSION}\\javafx-controls-${JAVAFX_VERSION}-win.jar"
MODULE_PATH="${MODULE_PATH};${JAVAFX_BASE}\\javafx-fxml\\${JAVAFX_VERSION}\\javafx-fxml-${JAVAFX_VERSION}-win.jar"
MODULE_PATH="${MODULE_PATH};${JAVAFX_BASE}\\javafx-graphics\\${JAVAFX_VERSION}\\javafx-graphics-${JAVAFX_VERSION}-win.jar"

# Add JDK modules
MODULE_PATH="${MODULE_PATH};${JAVA_HOME}\\jmods"

echo "Module path: $MODULE_PATH"

# Create custom runtime image
echo "Creating custom runtime with jlink..."
OUTPUT_DIR="target\\zoltar-runtime"

rm -rf "$OUTPUT_DIR"

jlink \
    --module-path "$MODULE_PATH" \
    --add-modules ca.zoltar.app,javafx.controls,javafx.fxml,javafx.graphics \
    --strip-debug \
    --no-header-files \
    --no-man-pages \
    --compress=2 \
    --output "$OUTPUT_DIR"

echo "=== Runtime image created at $OUTPUT_DIR ==="
echo ""
echo "To test the runtime:"
echo "  $OUTPUT_DIR\\bin\\java -m ca.zoltar.app/ca.zoltar.app.MainApp"
