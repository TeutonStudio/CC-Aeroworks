#!/bin/sh
set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
JAVA_CMD="${JAVA_HOME:+$JAVA_HOME/bin/}java"

if ! command -v "$JAVA_CMD" >/dev/null 2>&1; then
  echo "ERROR: Java 21 was not found. Set JAVA_HOME or add java to PATH." >&2
  exit 1
fi

export CC_AEROWORKS_PROJECT_DIR="$APP_HOME"
exec "$JAVA_CMD" "$APP_HOME/gradle/wrapper/GradleBootstrap.java" "$@"
