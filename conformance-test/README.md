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

| Command       | What it does                                                                         |
|---------------|--------------------------------------------------------------------------------------|
| `list`        | [List scenarios available in MCP Conformance Test Framework][list-scenarios-command] |
| `server`      | Starts the Ktor conformance server, runs the server test suite against it            |
| `client`      | Runs the client test suite (`initialize`, `tools_call`, `elicitation`, `sse-retry`)  |
| `client-auth` | Runs the client auth test suite (20 OAuth scenarios)                                 |
| `all`         | Runs all three suites sequentially                                                   |

Any `[extra-args]` are forwarded to the conformance runner (e.g. `--verbose`).

## What the Script Does

1. **Builds** the module via `./gradlew :conformance-test:installDist`
2. For `server` — starts the conformance server on `localhost:3001`, polls until ready
3. Invokes `npx @modelcontextprotocol/conformance@0.1.15` with the appropriate arguments
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
├── run-conformance.sh            # Single entry point script
├── conformance-baseline.yml      # Expected failures for known SDK limitations
└── src/main/kotlin/.../conformance/
    ├── ConformanceServer.kt      # Ktor server entry point (StreamableHTTP, DNS rebinding, EventStore)
    ├── ConformanceClient.kt      # Scenario-based client entry point (MCP_CONFORMANCE_SCENARIO routing)
    ├── ConformanceTools.kt       # 18 tool registrations
    ├── ConformanceResources.kt   # 5 resource registrations (static, binary, template, watched, dynamic)
    ├── ConformancePrompts.kt     # 5 prompt registrations (simple, args, image, embedded, dynamic)
    ├── ConformanceCompletions.kt # completion/complete handler
    ├── InMemoryEventStore.kt     # EventStore impl for SSE resumability (SEP-1699)
    └── auth/                     # OAuth client for 20 auth scenarios
        ├── registration.kt       # Scenario handler registration
        ├── utils.kt              # Shared utilities: JSON instance, constants, extractOrigin()
        ├── discovery.kt          # Protected Resource Metadata + AS Metadata discovery
        ├── pkce.kt               # PKCE code verifier/challenge generation + AS capability check
        ├── tokenExchange.kt      # Token endpoint interaction (exchange code, error handling)
        ├── authCodeFlow.kt       # Main Authorization Code flow handler (runAuthClient + interceptor)
        ├── scopeHandling.kt      # Scope selection strategy + step-up 403 handling
        ├── clientRegistration.kt # Client registration logic (pre-reg, CIMD, dynamic)
        ├── JWTScenario.kt              # Client Credentials JWT scenario
        ├── basicScenario.kt            # Client Credentials Basic scenario
        └── crossAppAccessScenario.kt   # Cross-App Access (SEP-990) scenario
```

## Test Suites

### Server Suite

Tests the conformance server against all server scenarios:

| Category    | Scenarios                                                                                                                           |
|-------------|-------------------------------------------------------------------------------------------------------------------------------------|
| Lifecycle   | initialize, ping                                                                                                                    |
| Tools       | text, image, audio, embedded, multiple, progress, logging, error, sampling, elicitation, dynamic, reconnection, JSON Schema 2020-12 |
| Resources   | list, read-text, read-binary, templates, subscribe, dynamic                                                                         |
| Prompts     | simple, with-args, with-image, with-embedded-resource, dynamic                                                                      |
| Completions | complete                                                                                                                            |
| Security    | DNS rebinding protection                                                                                                            |

### Client Core Suite

| Scenario                              | Description                                   |
|---------------------------------------|-----------------------------------------------|
| `initialize`                          | Connect, list tools, close                    |
| `tools_call`                          | Connect, call `add_numbers(a=5, b=3)`, close  |
| `elicitation-sep1034-client-defaults` | Elicitation with `applyDefaults` capability   |
| `sse-retry`                           | Call `test_reconnection`, verify reconnection |

### Client Auth Suite

17 OAuth Authorization Code scenarios + 2 Client Credentials scenarios (`jwt`, `basic`) + 1 Cross-App Access scenario = 20 total.

> [!NOTE]
> Auth scenarios are implemented using Ktor's `HttpClient` plugins (`HttpSend` interceptor,
> `ktor-client-auth`) as a standalone OAuth client. They do not use the SDK's built-in auth support.

## Known SDK Limitations

9 scenarios are expected to fail due to current SDK limitations (tracked in [
`conformance-baseline.yml`](conformance-baseline.yml).

| Scenario                              | Suite  | Root Cause                                                                                                                                             |
|---------------------------------------|--------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| `tools-call-with-logging`             | server | Notifications from tool handlers have no `relatedRequestId`; transport routes them to the standalone SSE stream instead of the request-specific stream |
| `tools-call-with-progress`            | server | *(same as above)*                                                                                                                                      |
| `tools-call-sampling`                 | server | *(same as above)*                                                                                                                                      |
| `tools-call-elicitation`              | server | *(same as above)*                                                                                                                                      |
| `elicitation-sep1034-defaults`        | server | *(same as above)*                                                                                                                                      |
| `elicitation-sep1330-enums`           | server | *(same as above)*                                                                                                                                      |
| `resources-templates-read`            | server | SDK does not implement `addResourceTemplate()` with URI pattern matching; resources are looked up by exact URI                                         |
| `elicitation-sep1034-client-defaults` | client | SDK does not fill in `default` values from the elicitation request schema before sending the response                                                  |
| `sse-retry`                           | client | Transport does not respect the SSE `retry` field timing or send `Last-Event-ID` on reconnection                                                        |

These failures reveal SDK gaps and are intentionally not fixed in this module.

[list-scenarios-command]: https://github.com/modelcontextprotocol/conformance/tree/main?tab=readme-ov-file#list-available-scenarios
