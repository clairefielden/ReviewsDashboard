#!/bin/bash
set -euo pipefail

# Set environment variables
export HOSTAWAY_ACCOUNT_ID=61148
export HOSTAWAY_CLIENT_SECRET='f94377ebbbb479490bb3ec364649168dc443dda2e4830facaf5de2e74ccc9152'
export GOOGLE_PLACES_API_KEY='AIzaSyArd9iXspU548ndQJcD4KdhJAjzVspOBAg'
export HOSTAWAY_BASE_URL="${HOSTAWAY_BASE_URL:-https://api.hostaway.com}"

# --- CONFIG ---------------------------------------------------------------
# Paths (edit if your layout differs)
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$ROOT_DIR/ReviewsDashboard/"
STATIC_DIR="$ROOT_DIR/ReviewsDashboard/src/main/resources/static"
JAR_OUT="$ROOT_DIR/ReviewsDashboard/target"
JAR_NAME_PATTERN="*-SNAPSHOT.jar"
UI_DIR="$ROOT_DIR/ReviewsDashboard/ui"

# Default ports
SPRING_PORT="${SPRING_PORT:-8080}"
VITE_PORT="${VITE_PORT:-5173}"

# --- ENV LOADING ---------------------------------------------------------
# Load variables from .env if present
ENV_FILE="$ROOT_DIR/.env"
if [[ -f "$ENV_FILE" ]]; then
  # shellcheck disable=SC2046
  export $(grep -E '^[A-Za-z_][A-Za-z0-9_]*=' "$ENV_FILE" | xargs)
fi

# Fail fast if critical keys are missing (only for prod build/run)
require_keys() {
  local missing=0
  for k in HOSTAWAY_ACCOUNT_ID HOSTAWAY_CLIENT_SECRET GOOGLE_PLACES_API_KEY; do
    if [[ -z "${!k:-}" ]]; then
      echo "❌ Missing env: $k (set it or add to .env)" >&2
      missing=1
    fi
  done
  [[ "$missing" -eq 0 ]]
}

# --- HELPERS -------------------------------------------------------------
header() { echo; echo "=== $* ==="; }
die() { echo "❌ $*" >&2; exit 1; }
exists() { command -v "$1" >/dev/null 2>&1; }

# --- TASKS ---------------------------------------------------------------

dev() {
  header "Starting DEV (backend on :$SPRING_PORT, frontend on :$VITE_PORT)"

  exists mvn    || die "mvn not found"
  exists node   || die "node not found"
  exists npm    || die "npm not found"

  # Start Spring Boot (background)
  pushd "$BACKEND_DIR" >/dev/null
  SPRING_OUTPUT="$ROOT_DIR/target/dev-spring.log"
  echo "→ Spring Boot starting… (logs: $SPRING_OUTPUT)"
  # Use spring-boot:run to pick up changes
  HOSTAWAY_ACCOUNT_ID="$HOSTAWAY_ACCOUNT_ID" \
  HOSTAWAY_CLIENT_SECRET="$HOSTAWAY_CLIENT_SECRET" \
  GOOGLE_PLACES_API_KEY="$GOOGLE_PLACES_API_KEY" \
  HOSTAWAY_BASE_URL="$HOSTAWAY_BASE_URL" \
  mvn -q -DskipTests spring-boot:run >"$SPRING_OUTPUT" 2>&1 &
  SPRING_PID=$!
  popd >/dev/null

  # Start Vite dev server (background)
  pushd "$UI_DIR" >/dev/null
  if [[ ! -d node_modules ]]; then
    header "Installing UI deps"
    npm ci
  fi
  VITE_OUTPUT="$ROOT_DIR/target/dev-vite.log"
  echo "→ Vite starting… (logs: $VITE_OUTPUT)"
  npm run dev -- --port "$VITE_PORT" >"$VITE_OUTPUT" 2>&1 &
  VITE_PID=$!
  popd >/dev/null

  # Cleanup on exit
  trap 'echo; echo "Stopping…"; kill -TERM ${SPRING_PID:-0} ${VITE_PID:-0} 2>/dev/null || true; wait ${SPRING_PID:-0} ${VITE_PID:-0} 2>/dev/null || true' INT TERM EXIT

  echo
  echo "✅ Dev is up:"
  echo "  Backend:  http://localhost:${SPRING_PORT}"
  echo "  Frontend: http://localhost:${VITE_PORT}"
  echo
  echo "Press Ctrl+C to stop."
  wait
}

build_ui() {
  header "Building UI"
  exists npm || die "npm not found"

  pushd "$UI_DIR" >/dev/null
  npm ci
  npm run build
  popd >/dev/null

  header "Copying UI dist → Spring static"
  rm -rf "$STATIC_DIR"
  mkdir -p "$STATIC_DIR"
  cp -R "$UI_DIR/dist/"* "$STATIC_DIR/"
}

package_prod() {
  header "Packaging backend (prod)"
  exists mvn || die "mvn not found"
  # Build the jar with the static assets included
  mvn -q -DskipTests clean package
  echo "✅ JAR built:"
  ls -1 "$JAR_OUT"/*.jar
}

run_jar() {
  header "Running packaged JAR"
  local jar
  jar="$(ls -1 "$JAR_OUT"/$JAR_NAME_PATTERN 2>/dev/null | head -n1 || true)"
  [[ -n "$jar" ]] || die "Jar not found in $JAR_OUT"

  HOSTAWAY_ACCOUNT_ID="$HOSTAWAY_ACCOUNT_ID" \
  HOSTAWAY_CLIENT_SECRET="$HOSTAWAY_CLIENT_SECRET" \
  GOOGLE_PLACES_API_KEY="$GOOGLE_PLACES_API_KEY" \
  HOSTAWAY_BASE_URL="$HOSTAWAY_BASE_URL" \
  java -jar "$jar"
}

prod() {
  require_keys || die "Set required envs before prod build/run."

  build_ui
  package_prod
  run_jar
}

package_only() {
  require_keys || die "Set required envs before packaging."
  build_ui
  package_prod
}

usage() {
  cat <<EOF

Usage: $(basename "$0") <command>

Commands
  dev           Run backend + frontend in watch mode (two processes)
  package       Build UI and Spring Boot JAR (production)
  prod          Build UI, package JAR, then run the JAR
  help          Show this help

Environment (.env or exported beforehand)
  HOSTAWAY_ACCOUNT_ID=...
  HOSTAWAY_CLIENT_SECRET=...
  GOOGLE_PLACES_API_KEY=...
  HOSTAWAY_BASE_URL=https://api.hostaway.com (default)
  SPRING_PORT=8080 (optional)
  VITE_PORT=5173  (optional)

Examples
  # One-time dev
  ./scripts/dev.sh dev

  # Production package
  export HOSTAWAY_ACCOUNT_ID=12345
  export HOSTAWAY_CLIENT_SECRET=xxxx
  export GOOGLE_PLACES_API_KEY=yyyy
  ./scripts/dev.sh package

  # Build + run JAR
  ./scripts/dev.sh prod

EOF
}

# --- ENTRY ---------------------------------------------------------------
cmd="${1:-help}"
case "$cmd" in
  dev) dev ;;
  package) package_only ;;
  prod) prod ;;
  help|--help|-h) usage ;;
  *) echo "Unknown command: $cmd"; usage; exit 1 ;;
esac

