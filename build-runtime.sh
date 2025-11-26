#!/bin/bash
# Build script for creating a distributable package using jpackage

set -e

echo "=== Building Zoltar Package with jpackage ==="

# Ensure we're in the project root
cd "$(dirname "$0")"

# Build the project
echo "Building project..."
mvn clean package -DskipTests

# Copy all dependencies to a lib folder
echo "Preparing dependencies..."
LIB_DIR="target/lib"
rm -rf "$LIB_DIR"
mkdir -p "$LIB_DIR"

# Copy all module JARs
cp zoltar-app/target/zoltar-app-1.0.0-SNAPSHOT.jar "$LIB_DIR/"
cp zoltar-gui/target/zoltar-gui-1.0.0-SNAPSHOT.jar "$LIB_DIR/"
cp zoltar-core/target/zoltar-core-1.0.0-SNAPSHOT.jar "$LIB_DIR/"
cp zoltar-db/target/zoltar-db-1.0.0-SNAPSHOT.jar "$LIB_DIR/"
cp zoltar-search/target/zoltar-search-1.0.0-SNAPSHOT.jar "$LIB_DIR/"
cp zoltar-pubmed/target/zoltar-pubmed-1.0.0-SNAPSHOT.jar "$LIB_DIR/"
cp zoltar-util/target/zoltar-util-1.0.0-SNAPSHOT.jar "$LIB_DIR/"

# Copy all runtime dependencies from maven
mvn dependency:copy-dependencies -DoutputDirectory="../target/lib" -pl zoltar-gui -DincludeScope=runtime

echo "✓ Dependencies copied to $LIB_DIR"
echo "  JAR count: $(ls -1 $LIB_DIR/*.jar | wc -l)"

# Create runtime image with jpackage
echo "Creating native package with jpackage..."
OUTPUT_DIR="target/installer"
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

jpackage \
    --type app-image \
    --input "$LIB_DIR" \
    --name "Zoltar" \
    --main-jar zoltar-gui-1.0.0-SNAPSHOT.jar \
    --main-class ca.zoltar.gui.MainApp \
    --dest "$OUTPUT_DIR" \
    --java-options '-Xmx2g' \
    --java-options '--module-path $APPDIR' \
    --java-options '--add-modules javafx.controls,javafx.fxml' \
    --java-options '--enable-native-access=javafx.graphics,org.xerial.sqlitejdbc' \
    --app-version "1.0.0" \
    --description "Zoltar - Intelligent Literature Analysis Tool" \
    --vendor "Zoltar Project"

echo "=== Package created at $OUTPUT_DIR ==="
echo "Size: $(du -sh $OUTPUT_DIR | cut -f1)"
echo ""
echo "To run the application:"
if [[ "$OSTYPE" == "darwin"* ]]; then
    echo "  $OUTPUT_DIR/Zoltar.app/Contents/MacOS/Zoltar"
else
    echo "  $OUTPUT_DIR/Zoltar/bin/Zoltar"
fi
