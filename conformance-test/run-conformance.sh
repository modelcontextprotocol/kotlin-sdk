#!/bin/bash
# Script to run MCP conformance tests for the Kotlin SDK.
#
# Usage: ./conformance-test/run-conformance.sh <command> [extra-args...]
# Commands: server | client | client-auth | all

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

CONFORMANCE_VERSION="0.1.15"
PORT="${MCP_PORT:-3001}"
SERVER_URL="http://localhost:${PORT}/mcp"
RESULTS_DIR="$SCRIPT_DIR/results"
SERVER_DIST="$SCRIPT_DIR/build/install/conformance-test/bin/conformance-test"
CLIENT_DIST="$SCRIPT_DIR/build/install/conformance-test/bin/conformance-client"

SERVER_PID=""

cleanup() {
    if [ -n "$SERVER_PID" ] && kill -0 "$SERVER_PID" 2>/dev/null; then
        echo "Stopping server (PID: $SERVER_PID)..."
        kill "$SERVER_PID" 2>/dev/null || true
        wait "$SERVER_PID" 2>/dev/null || true
    fi
}
trap cleanup EXIT

build() {
    echo "Building conformance-test distributions..."
    cd "$PROJECT_ROOT"
    ./gradlew :conformance-test:installDist --quiet
    cd "$SCRIPT_DIR"
    echo "Build complete."
}

start_server() {
    echo "Starting conformance server on port $PORT..."
    MCP_PORT="$PORT" "$SERVER_DIST" &
    SERVER_PID=$!

    echo "Waiting for server to be ready..."
    local retries=0
    local max_retries=30
    while ! curl -sf "$SERVER_URL" > /dev/null 2>&1; do
        retries=$((retries + 1))
        if [ "$retries" -ge "$max_retries" ]; then
            echo "ERROR: Server failed to start after $max_retries attempts"
            return 1
        fi
        sleep 0.5
    done
    echo "Server is ready (PID: $SERVER_PID)."
}

stop_server() {
    if [ -n "$SERVER_PID" ] && kill -0 "$SERVER_PID" 2>/dev/null; then
        echo "Stopping server (PID: $SERVER_PID)..."
        kill "$SERVER_PID" 2>/dev/null || true
        wait "$SERVER_PID" 2>/dev/null || true
        SERVER_PID=""
    fi
}

run_server_suite() {
    local output_dir="$RESULTS_DIR/server"
    mkdir -p "$output_dir"
    echo ""
    echo "=========================================="
    echo "  Running SERVER conformance tests"
    echo "=========================================="
    start_server || return 1
    local rc=0
    npx "@modelcontextprotocol/conformance@$CONFORMANCE_VERSION" server \
        --url "$SERVER_URL" \
        --output-dir "$output_dir" \
        "$@" || rc=$?
    stop_server
    return $rc
}

run_client_suite() {
    local output_dir="$RESULTS_DIR/client"
    mkdir -p "$output_dir"
    echo ""
    echo "=========================================="
    echo "  Running CLIENT conformance tests"
    echo "=========================================="
    local scenarios=("initialize" "tools_call" "elicitation-sep1034-client-defaults" "sse-retry")
    local rc=0
    for scenario in "${scenarios[@]}"; do
        npx "@modelcontextprotocol/conformance@$CONFORMANCE_VERSION" client \
            --command "$CLIENT_DIST" \
            --scenario "$scenario" \
            --output-dir "$output_dir" \
            "$@" || rc=$?
    done
    return $rc
}

run_client_auth_suite() {
    local output_dir="$RESULTS_DIR/client-auth"
    mkdir -p "$output_dir"
    echo ""
    echo "=========================================="
    echo "  Running CLIENT (auth) conformance tests"
    echo "=========================================="
    npx "@modelcontextprotocol/conformance@$CONFORMANCE_VERSION" client \
        --command "$CLIENT_DIST" \
        --suite auth \
        --output-dir "$output_dir" \
        "$@" || return 1
}

# ============================================================================
# Main
# ============================================================================

COMMAND="${1:-}"
shift 2>/dev/null || true

if [ -z "$COMMAND" ]; then
    echo "Usage: $0 <command> [extra-args...]"
    echo "Commands: server | client | client-auth | all"
    exit 1
fi

build

EXIT_CODE=0

case "$COMMAND" in
    server)
        run_server_suite "$@" || EXIT_CODE=1
        ;;
    client)
        run_client_suite "$@" || EXIT_CODE=1
        ;;
    client-auth)
        run_client_auth_suite "$@" || EXIT_CODE=1
        ;;
    all)
        run_server_suite "$@" || EXIT_CODE=1
        run_client_suite "$@" || EXIT_CODE=1
        run_client_auth_suite "$@" || EXIT_CODE=1
        ;;
    *)
        echo "Unknown command: $COMMAND"
        echo "Commands: server | client | client-auth | all"
        exit 1
        ;;
esac

echo ""
echo "=========================================="
echo "  Results saved to: $RESULTS_DIR"
echo "=========================================="

exit $EXIT_CODE
