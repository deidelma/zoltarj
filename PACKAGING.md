# Zoltar Packaging Guide

This directory contains scripts for packaging Zoltar as native installers for macOS and Windows.

## Prerequisites

- **Java 21+** with `jlink` and `jpackage` tools
- **Maven 3.9+** 
- **macOS** (for building macOS installers) or **Windows** (for building Windows installers)

## Build Steps

### 1. Build the Project

```bash
mvn clean package -DskipTests
```

### 2. Create Custom Runtime (jlink)

**macOS/Linux:**
```bash
chmod +x build-runtime.sh
./build-runtime.sh
```

**Windows:**
```cmd
build-runtime.bat
```

This creates a trimmed JRE in `target/zoltar-runtime` containing only the modules needed by Zoltar.

### 3. Package the Application (jpackage)

**macOS - DMG:**
```bash
chmod +x package-macos.sh
./package-macos.sh dmg
```

**macOS - PKG:**
```bash
./package-macos.sh pkg
```

**Windows - MSI:**
```cmd
package-windows.bat msi
```

**Windows - EXE:**
```cmd
package-windows.bat exe
```

The installers will be created in `target/installer/`.

## Installer Features

### macOS (.dmg / .pkg)
- Native macOS application bundle
- Installable via drag-and-drop (DMG) or wizard (PKG)
- No JDK installation required
- App appears in `/Applications`

### Windows (.msi / .exe)
- Native Windows installer
- Start Menu entry
- Desktop shortcut (optional)
- Add/Remove Programs integration
- No JDK installation required

## Runtime Size

The custom runtime is optimized using:
- `--strip-debug` - Removes debug symbols
- `--no-header-files` - Excludes C header files
- `--no-man-pages` - Excludes manual pages
- `--compress=2` - Maximum compression

Expected sizes:
- Runtime: ~80-120 MB (varies by platform)
- Installer: ~100-150 MB (includes application and dependencies)

## Troubleshooting

### "Module not found" errors
- Ensure all modules are built: `mvn clean package`
- Check that `module-info.java` exports are correct

### JavaFX modules not found
- Set `JAVAFX_MODS` environment variable to JavaFX jmods directory
- Or ensure JavaFX is in Maven local repository

### jpackage command not found
- Ensure using JDK 21+ (not JRE)
- Check `java -version` shows correct JDK path

### Installer won't launch
- Check application logs (macOS: Console.app, Windows: Event Viewer)
- Verify `~/.zoltar-java/` directory permissions
- Ensure OpenAI API key is configured in `~/.zoltar-java/config.json`

## Code Signing (Optional)

### macOS
For distribution outside the App Store, sign with Apple Developer certificate:

```bash
codesign --deep --force --verify --verbose \
  --sign "Developer ID Application: Your Name" \
  target/installer/Zoltar.app
```

Then notarize:
```bash
xcrun notarytool submit target/installer/Zoltar-1.0.0.dmg \
  --apple-id your@email.com \
  --password @keychain:AC_PASSWORD \
  --team-id TEAMID
```

### Windows
For distribution, sign with a code signing certificate:

```cmd
signtool sign /f certificate.pfx /p password /t http://timestamp.digicert.com target\installer\Zoltar-1.0.0.msi
```

## CI/CD Integration

See `.github/workflows/release.yml` for automated building across platforms using GitHub Actions.

## Version Updates

When releasing a new version:

1. Update version in `pom.xml` files
2. Update `APP_VERSION` in packaging scripts
3. Tag the release: `git tag v1.0.0`
4. Run packaging scripts
5. Upload installers to GitHub Releases
