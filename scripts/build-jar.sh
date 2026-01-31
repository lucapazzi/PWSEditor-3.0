#!/usr/bin/env bash
set -euo pipefail

readonly ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly OUT="$ROOT/out"
readonly DIST="$ROOT/dist"
readonly JAR_NAME="PWSEditor.jar"
readonly MAIN_CLASS="pws.editor.PWSEditor"
readonly LIBS=(
  "$ROOT/lib/pdfbox-2.0.29.jar"
  "$ROOT/lib/fontbox-2.0.29.jar"
  "$ROOT/lib/commons-logging-1.2.jar"
  "$ROOT/lib/pdfbox-graphics2d-0.6.0.jar"
  "$ROOT/lib/graphics2d-3.0.3.jar"
)

wrap_manifest_attr() {
  local name="$1"
  shift
  local value="$*"
  local line="${name}: ${value}"
  echo "$line" | fold -s -w 70 | awk 'NR==1{print; next}{print " " $0}'
}

"$ROOT/scripts/build.sh"

echo "Preparing dist directory at $DIST"
rm -rf "$DIST"
mkdir -p "$DIST/lib"

echo "Copying runtime libraries"
cp -f "${LIBS[@]}" "$DIST/lib/"

manifest="$DIST/manifest.mf"
classpath_entries=()
for jar in "${LIBS[@]}"; do
  classpath_entries+=("lib/$(basename "$jar")")
done

{
  echo "Manifest-Version: 1.0"
  echo "Main-Class: $MAIN_CLASS"
  wrap_manifest_attr "Class-Path" "${classpath_entries[*]}"
  echo
} > "$manifest"

echo "Creating executable jar"
jar cfm "$DIST/$JAR_NAME" "$manifest" -C "$OUT" .

rm -f "$manifest"
echo "Executable jar created at $DIST/$JAR_NAME"
echo "Run with: java -jar $DIST/$JAR_NAME"
