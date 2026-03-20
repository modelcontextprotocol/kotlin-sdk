# AGENTS: Development Guidelines for AI Contributors

This file provides guidance to AI coding agents working with code in this repository.

## Project

Kotlin Multiplatform SDK for the [Model Context Protocol (MCP)](https://modelcontextprotocol.io). Targets JVM, Native (macOS/Linux/Windows), JS, and Wasm. Implements both MCP clients and servers with pluggable transports.

## Build Commands

```bash
./gradlew ktlintCheck                # Lint check
./gradlew detekt                     # Static analysis
./gradlew ktlintFormat               # Auto-fix lint issues
./gradlew apiCheck                   # Check public API compatibility (run before committing)
./gradlew apiDump                    # Update API dump after intentional API changes
./gradlew koverLog                   # Print coverage summary
./gradlew koverXmlReport            # Generate XML coverage report
```

Run a single test class:
```bash
./gradlew :kotlin-sdk-server:jvmTest --tests "io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransportTest"
```

Run a single test method:
```bash
./gradlew :kotlin-sdk-core:jvmTest --tests "*.ProtocolTest.should preserve existing meta when adding progress token"
```

Conformance tests (requires Node.js + npx):
```bash
./conformance-test/run-conformance.sh all           # All suites sequentially
./conformance-test/run-conformance.sh server         # Server suite only
./conformance-test/run-conformance.sh client         # Client core suite (initialize, tools_call, elicitation, sse-retry)
./conformance-test/run-conformance.sh client-auth    # Client auth suite (20 OAuth scenarios)
```

## Module Structure

```
kotlin-sdk-core      Core: protocol types, JSON-RPC, Transport abstraction, Protocol base class
kotlin-sdk-client    Client: MCP client implementation (tools, prompts, resources, sampling)
kotlin-sdk-server    Server: MCP server implementation (feature registries, sessions)
kotlin-sdk-testing   ChannelTransport for in-memory client-server testing
kotlin-sdk           Umbrella module aggregating client + server
test-utils           Shared test helpers (mockk, kotest, awaitility)
conformance-test     MCP specification compliance tests (external runner)
integration-test     End-to-end tests
samples/             Runnable examples (servers and clients)
```

## Architecture

Layered design:

```
Application (tools, prompts, resources)
  → Client / Server  (typed MCP operations, capability enforcement)
    → Protocol        (JSON-RPC framing, request correlation, timeouts)
      → Transport     (pluggable I/O: stdio, SSE, Streamable HTTP, WebSocket)
```

## Code Conventions

- **Explicit API mode (Strict)**: all public declarations must have explicit visibility modifiers and return types
- **DSL builders**: prefer DSL builder style (lambda blocks) over direct constructors
- **Multi-dollar interpolation**: use `$$"""..."""` for strings containing literal `$` (e.g., JSON with `$ref`, `$defs`)
- **`McpJson` has `explicitNulls = false`**: null properties must be absent from JSON, not `"field": null`
- **Logging**: use `io.github.oshai.kotlinlogging.KotlinLogging`
- **Structured concurrency**: every coroutine must have a bounded scope and lifetime; always re-throw `CancellationException`; never hold `Mutex` across suspension points

## Testing

- **JVM first**: run `jvmTest` instead of `test` — full multiplatform test runs are slow and rarely needed during development
- **Test naming**: use backtick syntax — `fun \`should return 200 OK\`()`
- **Frameworks**: JUnit 5 (runner) + Kotest (assertions) + MockK (mocking) + Awaitility (async waits)
- **In-memory testing**: `ChannelTransport.createLinkedPair()` connects client and server without network

## Skills

- `/mcp-docs` — fetches live MCP specification and docs; use when implementing or debugging MCP protocol features
