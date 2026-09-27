#!/bin/bash
# Runs the :web-app tests under plain Node.js. The cloud sandbox cannot fetch Kotlin's Karma fork
# from GitHub, so the browser test task fails there; locally prefer `./gradlew :web-app:jsBrowserTest`.
set -euo pipefail

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
NODE_DEPS="$PROJECT_DIR/.gradle-user-home/node-test-deps"

if [ ! -d "$NODE_DEPS/node_modules/@js-joda/core" ]; then
  mkdir -p "$NODE_DEPS"
  npm install --silent --prefix "$NODE_DEPS" @js-joda/core >/dev/null
fi

cd "$PROJECT_DIR"
bash ./gradlew --quiet :web-app:compileTestDevelopmentExecutableKotlinJs
cd build/js/packages/Logica-web-app-test/kotlin
NODE_PATH="$NODE_DEPS/node_modules" node -r "$SCRIPT_DIR/node-test-shim.js" Logica-web-app-test.js
