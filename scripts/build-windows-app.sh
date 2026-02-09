#!/usr/bin/env bash
set -euo pipefail

readonly ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly DIST="$ROOT/dist"
readonly APP_NAME="PWSEditor"
readonly MAIN_JAR="PWSEditor.jar"
readonly MAIN_CLASS="pws.editor.PWSEditor"
readonly VERSION="3.0"

package_type="app-image"

print_usage() {
  cat <<EOF
Usage: $(basename "$0") [--installer] [--help]

Builds a Windows package with jpackage.
Default output type is app-image (contains ${APP_NAME}.exe).

Options:
  --installer  Build an .exe installer instead of an app-image
  --help       Show this help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --installer)
      package_type="exe"
      shift
      ;;
    --help|-h)
      print_usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1"
      print_usage
      exit 1
      ;;
  esac
done

case "$(uname -s)" in
  CYGWIN*|MINGW*|MSYS*)
    ;;
  *)
    echo "This script must be run on Windows (Git Bash, MSYS2 or Cygwin)."
    exit 1
    ;;
esac

if ! command -v jpackage >/dev/null 2>&1; then
  echo "jpackage not found. Install a JDK that includes jpackage (JDK 17+ recommended)."
  exit 1
fi

"$ROOT/scripts/build-jar.sh"

echo "Creating Windows package in $DIST (type=$package_type)"
jpackage \
  --type "$package_type" \
  --name "$APP_NAME" \
  --dest "$DIST" \
  --input "$DIST" \
  --main-jar "$MAIN_JAR" \
  --main-class "$MAIN_CLASS" \
  --app-version "$VERSION" \
  --vendor "PWSEditor"

if [[ "$package_type" == "app-image" ]]; then
  echo "Windows app image created at: $DIST/$APP_NAME"
  echo "Launcher executable: $DIST/$APP_NAME/$APP_NAME.exe"
else
  echo "Windows installer created in: $DIST"
fi
