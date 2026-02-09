#!/usr/bin/env bash
set -euo pipefail

readonly ROOT="$(cd "$(dirname "$0")/.." && pwd)"

print_usage() {
  cat <<EOF
Usage: $(basename "$0") [--javadoc] [--windows-installer] [--help]

Builds all artifacts available on the current operating system:
- macOS: jar + .app
- Windows: jar + app-image (.exe launcher)
- Linux/other: jar

Options:
  --javadoc            Also generate API docs (docs/javadoc)
  --windows-installer  On Windows, build installer (.exe) instead of app-image
  --help               Show this help
EOF
}

build_javadoc="false"
windows_installer="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --javadoc)
      build_javadoc="true"
      shift
      ;;
    --windows-installer)
      windows_installer="true"
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

os_name="$(uname -s)"

case "$os_name" in
  Darwin)
    echo "Detected macOS: building jar and .app bundle"
    "$ROOT/scripts/build-macos-app.sh"
    ;;
  CYGWIN*|MINGW*|MSYS*)
    echo "Detected Windows shell: building Windows package"
    if [[ "$windows_installer" == "true" ]]; then
      "$ROOT/scripts/build-windows-app.sh" --installer
    else
      "$ROOT/scripts/build-windows-app.sh"
    fi
    ;;
  *)
    echo "Detected $os_name: building executable jar"
    "$ROOT/scripts/build-jar.sh"
    ;;
esac

if [[ "$build_javadoc" == "true" ]]; then
  echo "Generating Javadoc"
  "$ROOT/scripts/generate-javadoc.sh"
fi

echo "Build-all completed."
