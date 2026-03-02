#!/usr/bin/env bash
set -e
cd "$(dirname "${BASH_SOURCE[0]}")/.."

echo "=== Building conformance server and client ==="
./gradlew :conformance-test:installDist

echo ""
echo "=== Starting conformance server ==="
rm -rf conformance-test/results

MCP_PORT=4001 conformance-test/build/install/conformance-test/bin/conformance-test &
SERVER_PID=$!
sleep 5

echo "=== Running server conformance tests ==="
npx @modelcontextprotocol/conformance server \
  --url http://localhost:4001/mcp \
  --output-dir conformance-test/results/server \
  || true

kill "$SERVER_PID" 2>/dev/null || true
wait "$SERVER_PID" 2>/dev/null || true

echo ""
echo "=== Running client conformance tests ==="
npx @modelcontextprotocol/conformance client \
  --command "conformance-test/build/install/conformance-test/bin/conformance-client" \
  --output-dir conformance-test/results/client \
  || true

echo ""
echo "=== Done ==="
echo "Results in conformance-test/results/"
