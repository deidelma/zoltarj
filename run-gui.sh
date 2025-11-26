#!/bin/bash
# Launcher script for Zoltar GUI

cd "$(dirname "$0")"

# Build if needed
if [ ! -d "zoltar-gui/target/classes" ]; then
    echo "Building project..."
    mvn clean package -DskipTests -q
fi

# Run using Maven JavaFX plugin
cd zoltar-gui
mvn javafx:run
