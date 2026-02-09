#!/usr/bin/env bash
set -euo pipefail

readonly ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly OUT="$ROOT/out"
readonly LIBS=(
  "$ROOT/lib/pdfbox-2.0.29.jar"
  "$ROOT/lib/fontbox-2.0.29.jar"
  "$ROOT/lib/commons-logging-1.2.jar"
  "$ROOT/lib/pdfbox-graphics2d-0.6.0.jar"
  "$ROOT/lib/graphics2d-3.0.3.jar"
)

classpath_separator=":"
case "$(uname -s)" in
  CYGWIN*|MINGW*|MSYS*)
    classpath_separator=";"
    ;;
esac

echo "Cleaning $OUT"
rm -rf "$OUT"
mkdir -p "$OUT"

CLASSPATH=$(IFS="$classpath_separator"; echo "${LIBS[*]}")

echo "Compiling sources to $OUT"
javac -d "$OUT" -cp "$CLASSPATH" -sourcepath src $(find src -name "*.java")

echo "Build completed. Classes are in $OUT"
