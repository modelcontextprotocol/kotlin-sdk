# MCP Conformance Tests

Conformance tests for the Kotlin MCP SDK. Uses the external
[`@modelcontextprotocol/conformance`](https://www.npmjs.com/package/@modelcontextprotocol/conformance)
runner (pinned to **0.1.15**) to validate compliance with the MCP specification.

## Prerequisites

- **JDK 17+**
- **Node.js 18+** and `npx` (for the conformance runner)
- **curl** (used to poll server readiness)

## Quick Start

Run **all** suites (server, client core, client auth) from the project root:

```bash
./conformance-test/run-conformance.sh all
```

## Commands

```
./conformance-test/run-conformance.sh <command> [extra-args...]
```

| Command       | What it does                                                                        |
|---------------|-------------------------------------------------------------------------------------|
| `server`      | Starts the Ktor conformance server, runs the server test suite against it           |
| `client`      | Runs the client test suite (`initialize`, `tools_call`, `elicitation`, `sse-retry`) |
| `client-auth` | Runs the client auth test suite (17 OAuth scenarios)                                |
| `all`         | Runs all three suites sequentially                                                  |

Any `[extra-args]` are forwarded to the conformance runner (e.g. `--verbose`).

## What the Script Does

1. **Builds** the module via `./gradlew :conformance-test:installDist`
2. For `server` — starts the conformance server on `localhost:3001`, polls until ready
3. Invokes `npx @modelcontextprotocol/conformance@0.1.10` with the appropriate arguments
4. Saves results to `conformance-test/results/<command>/`
5. Cleans up the server process on exit
6. Exits non-zero if any suite fails

## Environment Variables

| Variable   | Default | Description                     |
|------------|---------|---------------------------------|
| `MCP_PORT` | `3001`  | Port for the conformance server |

## Project Structure

```
conformance-test/
├── build.gradle.kts            # Build config (no test deps — only compilation + installDist)
├── run-conformance.sh          # Single entry point script
├── .gitignore                  # Ignores /results/
├── SPEC.md                     # Design decisions and full specification
├── README.md                   # This file
└── src/main/kotlin/.../conformance/
    ├── ConformanceServer.kt    # Ktor server entry point (StreamableHTTP, DNS rebinding, EventStore)
    ├── ConformanceClient.kt    # Scenario-based client entry point (MCP_CONFORMANCE_SCENARIO routing)
    ├── ConformanceTools.kt     # 18 tool registrations
    ├── ConformanceResources.kt # 5 resource registrations (static, binary, template, watched, dynamic)
    ├── ConformancePrompts.kt   # 5 prompt registrations (simple, args, image, embedded, dynamic)
    ├── ConformanceCompletions.kt # completion/complete handler
    ├── ConformanceAuth.kt      # OAuth client for 17 auth scenarios (authz code + client credentials)
    └── InMemoryEventStore.kt   # EventStore impl for SSE resumability (SEP-1699)
```

## Test Suites

### Server Suite

Tests the conformance server against all server scenarios:

- Lifecycle — initialize, ping
- Tools — text, image, audio, embedded, multiple, progress, logging, error, sampling, elicitation, dynamic,
  reconnection, JSON Schema 2020-12
- Resources — list, read-text, read-binary, templates, subscribe, dynamic
- Prompts — simple, with-args, with-image, with-embedded-resource, dynamic
- Completions — complete
- Security — DNS rebinding protection

### Client Core Suite

| Scenario                              | Description                                   |
|---------------------------------------|-----------------------------------------------|
| `initialize`                          | Connect, list tools, close                    |
| `tools_call`                          | Connect, call `add_numbers(a=5, b=3)`, close  |
| `elicitation-sep1034-client-defaults` | Elicitation with `applyDefaults` capability   |
| `sse-retry`                           | Call `test_reconnection`, verify reconnection |

### Client Auth Suite

15 OAuth Authorization Code scenarios + 2 Client Credentials scenarios (`jwt`, `basic`).

## Known SDK Limitations

Some tests are expected to fail due to current SDK limitations:

- **`test_reconnection` / `sse-retry`** — cannot access JSONRPC request ID from tool handler to close SSE stream
- **Resource templates** — SDK may not fully support template URI matching
- **Tool logging/progress notifications** — StreamableHTTP may not route notifications to the correct SSE stream

These failures reveal SDK gaps and are intentionally not fixed in this module.
