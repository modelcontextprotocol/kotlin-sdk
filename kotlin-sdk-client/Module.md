# Module kotlin-sdk-client

`kotlin-sdk-client` is the multiplatform MCP client for Kotlin. It handles the MCP handshake, enforces capabilities, and
gives you typed APIs to call tools, prompts, resources, completions, and logging endpoints on MCP servers. It reuses
protocol types from `kotlin-sdk-core` and ships with transports for CLI and Ktor-based networking.

## What the module provides

- **Client runtime**: The `Client` class (or `mcpClient` helper) wraps initialization, capability checks, and
  request/notification plumbing. After connecting, it exposes `serverCapabilities`, `serverVersion`, and
  `serverInstructions`.
- **Typed operations**: Convenience functions like `listTools`, `callTool`, `listResources`, `readResource`,
  `listPrompts`, `complete`, `setLoggingLevel`, and subscription helpers for resources.
- **Capability enforcement**: Methods fail fast if the server does not advertise the needed capability; optional
  strictness mirrors the protocol options used by the server SDK.
- **Transports**: Ready-to-use implementations for STDIO (CLI interoperability), Ktor SSE (`SseClientTransport` with
  POST back-channel), Ktor WebSocket, and Streamable HTTP for environments that prefer request/response streaming.
- **Ktor client integration**: Extension helpers wire MCP over Ktor client engines with minimal setup for SSE,
  Streamable HTTP, or WebSocket.

## Typical client setup

1. Declare client capabilities with `ClientOptions` (e.g., sampling, roots, elicitation, experimental).
2. Create a transport suitable for your environment.
3. Connect using `mcpClient` or instantiate `Client` and call `connect(transport)`.
4. Use typed APIs to interact with the server; results and errors use the shared MCP types.

### Minimal STDIO client

```kotlin
val client = mcpClient(
    clientInfo = Implementation(name = "demo-client", version = "1.0.0"),
    clientOptions = ClientOptions(
        capabilities = ClientCapabilities(
            tools = ClientCapabilities.Tools(),
            resources = ClientCapabilities.Resources(subscribe = true),
        )
    ),
    transport = StdioClientTransport(
        System.`in`.asSource().buffered(),
        System.out.asSink().buffered()
    )
)

val tools = client.listTools()
val result = client.callTool("hello", mapOf("name" to "Kotlin"))
println(result.content)
```

### Ktor-based transports

- **SSE** (streaming GET + POST back-channel): create `SseClientTransport(httpClient, url)` (or
  `HttpClient.mcpSseTransport(url)`); sessions are keyed by `sessionId` managed by the SDK.
- **WebSocket**: use `WebSocketClientTransport(httpClient, url)` (or `HttpClient.mcpWebSocketTransport(url)`) to get
  bidirectional messaging in one connection.
- **Streamable HTTP**: `StreamableHttpClientTransport(httpClient, url)` enables MCP over streaming HTTP
  (or `HttpClient.mcpStreamableHttpTransport(url)`).

## Feature usage highlights

- **Tools**: `callTool` accepts raw maps or `CallToolRequest`; metadata keys are validated per MCP rules.
- **Prompts & completion**: `getPrompt`, `listPrompts`, and `complete` wrap common prompt flows.
- **Resources**: `listResources`, `listResourceTemplates`, `readResource`, and `subscribeResource` cover discovery,
  templating, and updates (when the server allows subscriptions).
- **Roots**: If enabled, register local roots with `addRoot`/`addRoots`; the client responds to `RootsList` requests
  from the server.
- **Notifications**: Built-in handlers cover initialization, cancellation, progress, and roots list changes; you can
  register more handlers via the underlying `Protocol` API.

## Diagnostics and lifecycle

- Capability assertions prevent calling endpoints the server does not expose; errors surface with clear messages.
- Logging uses `KotlinLogging`; enable DEBUG to inspect transport and handshake behavior.
- `close()` tears down the transport and cancels in-flight requests.

Use this module whenever you need an MCP-aware Kotlin client—whether embedding in a CLI over STDIO or talking to remote
servers via Ktor SSE/WebSocket/streamable HTTP transports. Public APIs are explicit so downstream users can rely on
stable, well-typed surface areas across platforms.
