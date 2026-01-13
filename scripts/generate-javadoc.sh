#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$ROOT_DIR/docs/javadoc"
SRC_DIR="$ROOT_DIR/src"
LIB_DIR="$ROOT_DIR/lib"

mkdir -p "$OUT_DIR"

CLASSPATH=""
if [[ -d "$LIB_DIR" ]]; then
  jars=()
  for jar in "$LIB_DIR"/*.jar; do
    [[ -e "$jar" ]] || break
    jars+=("$jar")
  done
  if (( ${#jars[@]} )); then
    CLASSPATH=$(IFS=:; echo "${jars[*]}")
  fi
fi

cmd=(javadoc -Xdoclint:all,-missing -d "$OUT_DIR" -sourcepath "$SRC_DIR" -subpackages assembly:machinery:editor:pws:serializer:smalgebra:utility)
if [[ -n "$CLASSPATH" ]]; then
  cmd+=(-classpath "$CLASSPATH")
fi

"${cmd[@]}"
