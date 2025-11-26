# Build Runtime Script Fix

## Problem

The original `build-runtime.sh` script was failing with the error:
```
Error: Module javafx.controls not found
```

And later:
```
Error: Module org.slf4j not found, required by ca.zoltar.gui
```

## Root Cause

The script was attempting to use `jlink` to create a custom JRE runtime. However, `jlink` has strict requirements:
1. **All modules must be modular** - jlink only works with JPMS modules (jmod files or modular JARs)
2. **JavaFX JARs vs JMODs** - Maven downloads JavaFX as JARs, but jlink requires JavaFX JMODs
3. **Non-modular dependencies** - Libraries like SLF4J, Logback, Lucene, etc. are not modular, so jlink cannot include them

## Solution

Changed the approach from `jlink` to `jpackage`:

### Why jpackage?

- **Works with any JARs** - Handles both modular and non-modular dependencies
- **Simpler** - No need to download separate JavaFX JMODs
- **More appropriate** - jpackage is designed for creating application bundles, jlink is for creating custom JRE runtimes
- **Better for JavaFX apps** - Recommended tool for packaging JavaFX applications

### What Changed

**Before (jlink approach):**
```bash
1. Build project with Maven
2. Download JavaFX JMODs separately
3. Construct complex module path
4. Run jlink to create custom JRE
5. Run jpackage later to create installer
```

**After (jpackage approach):**
```bash
1. Build project with Maven
2. Copy all dependencies to target/lib/
3. Run jpackage directly to create app bundle
```

### New Script Behavior

The updated `build-runtime.sh`:

1. Builds the project: `mvn clean package -DskipTests`
2. Copies all module JARs to `target/lib/`
3. Copies all runtime dependencies via Maven: `mvn dependency:copy-dependencies`
4. Runs jpackage with:
   - Input directory: `target/lib/` (all JARs)
   - Main JAR: `zoltar-gui-1.0.0-SNAPSHOT.jar`
   - Main class: `ca.zoltar.gui.MainApp`
   - Type: `app-image` (creates runnable application bundle)

Output: `target/installer/Zoltar.app` (~130 MB) - a complete macOS application bundle

## Benefits

1. **Simpler** - One-step process, no intermediate runtime creation
2. **More reliable** - Works with all dependencies regardless of modularity
3. **Faster** - No need to download JavaFX JMODs
4. **Better UX** - Creates a directly runnable application
5. **Industry standard** - This is how modern Java desktop apps are packaged

## Testing

The packaged application can be launched:
```bash
open target/installer/Zoltar.app
```

Or run from command line:
```bash
target/installer/Zoltar.app/Contents/MacOS/Zoltar
```

## Next Steps

The app bundle can be:
- Distributed as-is (users copy to Applications)
- Packaged into DMG: `./package-macos.sh dmg`
- Packaged into PKG: `./package-macos.sh pkg`

## Technical Details

### Dependencies Included

The final package includes (~130 MB total):
- All Zoltar modules (app, gui, core, db, search, pubmed, util)
- JavaFX runtime (controls, fxml, graphics, base)
- All non-modular dependencies (SLF4J, Logback, Lucene, PDFBox, Jackson, SQLite)
- Embedded JRE (bundled by jpackage)

### Module Configuration

- Module path: All JARs in `target/lib/`
- Main module: `ca.zoltar.gui` (automatic module from JAR)
- Main class: `ca.zoltar.gui.MainApp`
- Java options: `-Xmx2g` (2GB max heap)
