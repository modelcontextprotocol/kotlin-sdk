# MCP Kotlin SDK

Kotlin SDK for the Model Context Protocol (MCP).
This is a Kotlin Multiplatform library that helps you build MCP clients and servers that speak the same protocol and
share the same types.
The SDK focuses on clarity, small building blocks, and first‑class coroutine support.

Use the umbrella `kotlin-sdk` artifact when you want a single dependency that brings the core types plus both client and
server toolkits. If you only need one side, depend on `kotlin-sdk-client` or `kotlin-sdk-server` directly.

Gradle (Kotlin DSL):

```kotlin
dependencies {
    // Convenience bundle with everything you need to start
    implementation("io.modelcontextprotocol:kotlin-sdk:<version>")

    // Or pick modules explicitly
    implementation("io.modelcontextprotocol:kotlin-sdk-client:<version>")
    implementation("io.modelcontextprotocol:kotlin-sdk-server:<version>")
}
```

---

## Module kotlin-sdk-core

Foundational, platform‑agnostic pieces:

- Protocol data model and JSON serialization (kotlinx.serialization)
- Request/response and notification types used by both sides of MCP
- Coroutine‑friendly protocol engine and utilities
- Transport abstractions shared by client and server

You typically do not use `core` directly in application code; it is pulled in by the client/server modules. Use it
explicitly if you only need the protocol types or plan to implement a custom transport.

---

## Module kotlin-sdk-client

High‑level client API for connecting to an MCP server and invoking its tools, prompts, and resources. Ships with several
transports:

- WebSocketClientTransport – low latency, full‑duplex
- SSEClientTransport – Server‑Sent Events over HTTP
- StdioClientTransport – CLI‑friendly stdio bridge
- StreamableHttpClientTransport – simple HTTP streaming

A minimal client:

```kotlin
val client = Client(
    clientInfo = Implementation(name = "sample-client", version = "1.0.0")
)

client.connect(WebSocketClientTransport("ws://localhost:8080/mcp"))

val tools = client.listTools()
val result = client.callTool(
    name = "echo",
    arguments = mapOf("text" to "Hello, MCP!")
)
```

---

## Module kotlin-sdk-server

Lightweight server toolkit for hosting MCP tools, prompts, and resources. It provides a small, composable API and
ready‑to‑use transports:

- StdioServerTransport – integrates well with CLIs and editors
- SSE/WebSocket helpers for Ktor – easy HTTP deployment

Register tools and run over stdio:

```kotlin

val server = Server(
    serverInfo = Implementation(name = "sample-server", version = "1.0.0"),
    options = ServerOptions(ServerCapabilities())
)

server.addTool(
    name = "echo",
    description = "Echoes the provided text"
) { request ->
    // Build and return a CallToolResult from request.arguments
    // (see CallToolResult and related types in kotlin-sdk-core)
    /* ... */
}

// Bridge the protocol over stdio
val transport = StdioServerTransport(
    inputStream = kotlinx.io.files.Path("/dev/stdin").source(),
    outputStream = kotlinx.io.files.Path("/dev/stdout").sink()
)
// Start transport and wire it with the server using provided helpers in the SDK.
```

For HTTP deployments, use the Ktor extensions included in the module to expose an MCP WebSocket or SSE endpoint with a
few lines of code.
