# MCP Kotlin SDK

Kotlin Multiplatform implementation of the Model Context Protocol (MCP). The SDK focuses on clear, explicit APIs,
small building blocks, and first-class coroutine support so clients and servers share the same well-typed messages and
transports.

Use the umbrella `kotlin-sdk` artifact to bring in everything at once, or depend on the focused modules directly:

```kotlin
dependencies {
    // All-in-one bundle
    implementation("io.modelcontextprotocol:kotlin-sdk:<version>")

    // Or pick sides explicitly
    implementation("io.modelcontextprotocol:kotlin-sdk-client:<version>")
    implementation("io.modelcontextprotocol:kotlin-sdk-server:<version>")
}
```

---

## Module kotlin-sdk-core

Foundation shared by both sides:

- Protocol data model for MCP requests, results, notifications, capabilities, and content types.
- `McpJson` (kotlinx.serialization) with MCP-friendly defaults plus helpers for converting native values to JSON.
- Transport abstractions (`Transport`, `AbstractTransport`, `WebSocketMcpTransport`) and streaming `ReadBuffer`.
- `Protocol` base class that handles JSON-RPC framing, correlation, progress tokens, and capability assertions.

Use `core` when you need the raw types or want to author a custom transport. Public APIs are explicit to keep the shared
surface stable across platforms.

---

## Module kotlin-sdk-client

High-level client for connecting to MCP servers and invoking their features:

- `Client` runtime (or `mcpClient` helper) performs the MCP handshake and exposes `serverCapabilities`,
  `serverVersion`, and `serverInstructions`.
- Typed operations for tools, prompts, resources, completion, logging, roots, sampling, and elicitation with capability
  enforcement.
- Transports: `StdioClientTransport`, `SSEClientTransport`, `WebSocketClientTransport`, and `StreamableHttpClientTransport`,
  plus Ktor client extensions for quick wiring.

Minimal WebSocket client:

```kotlin
val client = mcpClient(
    clientInfo = Implementation("sample-client", "1.0.0"),
    clientOptions = ClientOptions(ClientCapabilities(tools = ClientCapabilities.Tools())),
    transport = WebSocketClientTransport("ws://localhost:8080/mcp")
)

val tools = client.listTools()
val result = client.callTool("echo", mapOf("text" to "Hello, MCP!"))
println(result.content)
```

---

## Module kotlin-sdk-server

Server toolkit for exposing MCP tools, prompts, and resources:

- `Server` runtime coordinates sessions, initialization flow, and capability enforcement with registries for tools,
  prompts, resources, and templates.
- Transports: `StdioServerTransport` for CLI/editor bridges; Ktor extensions (`mcp` for SSE + POST back-channel and
  `mcpWebSocket` for WebSocket) for HTTP hosting.
- Built-in notifications for list changes and resource subscriptions when capabilities enable them.

Minimal Ktor SSE server:

```kotlin
fun Application.module() {
    mcp {
        Server(
            serverInfo = Implementation("sample-server", "1.0.0"),
            options = ServerOptions(ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true),
                resources = ServerCapabilities.Resources(listChanged = true, subscribe = true),
            )),
        ) {
            addTool(name = "echo", description = "Echo text back") { request ->
                CallToolResult(content = listOf(TextContent("You said: ${request.params.arguments["text"]}")))
            }
        }
    }
}
```

Pick the module that matches your role, or use the umbrella artifact to get both sides with the shared core.
