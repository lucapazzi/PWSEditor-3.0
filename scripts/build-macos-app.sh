#!/usr/bin/env bash
set -euo pipefail

readonly ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly DIST="$ROOT/dist"
readonly APP_NAME="PWSEditor"
readonly APP_DIR="$DIST/${APP_NAME}.app"
readonly CONTENTS_DIR="$APP_DIR/Contents"
readonly MACOS_DIR="$CONTENTS_DIR/MacOS"
readonly JAVA_DIR="$CONTENTS_DIR/Java"
readonly RESOURCES_DIR="$CONTENTS_DIR/Resources"
readonly IDENTIFIER="org.pws.editor"
readonly VERSION="3.0"

"$ROOT/scripts/build-jar.sh"

echo "Preparing app bundle at $APP_DIR"
rm -rf "$APP_DIR"
mkdir -p "$MACOS_DIR" "$JAVA_DIR" "$RESOURCES_DIR"

echo "Copying jar and runtime libraries"
cp -f "$DIST/PWSEditor.jar" "$JAVA_DIR/"
cp -R "$DIST/lib" "$JAVA_DIR/lib"

cat > "$CONTENTS_DIR/Info.plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleName</key>
  <string>${APP_NAME}</string>
  <key>CFBundleDisplayName</key>
  <string>${APP_NAME}</string>
  <key>CFBundleExecutable</key>
  <string>${APP_NAME}</string>
  <key>CFBundleIdentifier</key>
  <string>${IDENTIFIER}</string>
  <key>CFBundleVersion</key>
  <string>${VERSION}</string>
  <key>CFBundleShortVersionString</key>
  <string>${VERSION}</string>
  <key>CFBundlePackageType</key>
  <string>APPL</string>
</dict>
</plist>
EOF

cat > "$MACOS_DIR/$APP_NAME" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

APP_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_DIR="$APP_ROOT/Java"
JAR="$JAVA_DIR/PWSEditor.jar"

exec /usr/bin/java -jar "$JAR"
EOF

chmod +x "$MACOS_DIR/$APP_NAME"

echo "App bundle created at $APP_DIR"
echo "Drag it to Desktop or Applications, then double-click to launch."
