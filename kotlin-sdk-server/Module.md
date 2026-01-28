# Module kotlin-sdk-server

`kotlin-sdk-server` contains the server-side building blocks for Model Context Protocol (MCP) applications written in
Kotlin Multiplatform. It pairs the protocol types from `kotlin-sdk-core` with transport implementations and utilities
that make it straightforward to expose tools, prompts, and resources to MCP clients.

## What the module provides

- **Server runtime**: The `Server` class coordinates sessions, initialization flow, and capability enforcement. It
  tracks active sessions and cleans them up when transports close.
- **Feature registries**: Helpers to register and remove tools, prompts, resources, and resource templates. Registry
  listeners emit list-changed notifications when the capability is enabled.
- **Transports**: Ready-to-use transports for STDIO (CLI-style hosting), Ktor Server-Sent Events (SSE) with POST
  back-channel, and Ktor WebSocket. All share the common `Transport` abstraction from the SDK.
- **Ktor integration**: Extension functions (`Routing.mcp`, `Routing.mcpWebSocket`, and `Application` variants) that
  plug MCP into an existing Ktor server with minimal boilerplate.
- **Notifications**: Built-in support for resource subscription, resource-updated events, and feature list change
  notifications when capabilities are set accordingly.

## Typical server setup

1. Declare capabilities your server supports using `ServerOptions`. Capabilities drive which handlers are installed and
   whether list-change notifications are emitted.
2. Register features: tools, prompts, resources, and resource templates. Handlers run in suspending context and can be
   removed or replaced at runtime.
3. Attach a transport and open a session via `createSession(transport)`. Multiple sessions can be active simultaneously.
4. Close the server to tear down transports and unsubscribe sessions.

### Minimal STDIO server

```kotlin
val server = Server(
    serverInfo = Implementation(name = "demo-server", version = "1.0.0"),
    options = ServerOptions(
        capabilities = ServerCapabilities(
            tools = ServerCapabilities.Tools(listChanged = true)
        )
    )
) {
    addTool(
        name = "hello",
        description = "Greets the caller by name"
    ) { request ->
        val name = request.params.arguments["name"]?.jsonPrimitive?.content ?: "there"
        CallToolResult(
            content = listOf(TextContent("Hello, $name!")),
            isError = false,
        )
    }
}

val transport = StdioServerTransport(System.`in`.source(), System.out.sink())
server.createSession(transport)
```

### Hosting with Ktor (SSE)

```kotlin
fun Application.module() {
    mcp {
        Server(
            serverInfo = Implementation("ktor-sse", "1.0.0"),
            options = ServerOptions(
                ServerCapabilities(
                    prompts = ServerCapabilities.Prompts(listChanged = true),
                    resources = ServerCapabilities.Resources(listChanged = true, subscribe = true),
                )
            )
        ) {
            addPrompt(
                name = "welcome",
                description = "Explain how to use the server"
            ) {
                GetPromptResult(
                    description = "Welcome to the MCP server",
                    messages = listOf(CreateMessageResult.Message(TextContent("Try listing tools first")))
                )
            }
        }
    }
}
```

The SSE route exposes a streaming GET endpoint and a POST endpoint for client messages. Session affinity is maintained
by a `sessionId` query parameter managed by the SDK.

### Hosting with Ktor (WebSocket)

```kotlin
fun Application.module() {
    mcpWebSocket {
        Server(
            serverInfo = Implementation("ktor-ws", "1.0.0"),
            options = ServerOptions(ServerCapabilities(tools = ServerCapabilities.Tools()))
        )
    }
}
```

`mcpWebSocket` installs Ktor WebSockets and wires message handling over a single bidirectional connection.

## Feature registration details

- **Tools**: `addTool` accepts a `Tool` descriptor or inline parameters plus a suspend handler. `removeTool` and
  `addTools/removeTools` support dynamic updates.
- **Prompts**: `addPrompt` wires a provider that returns `GetPromptResult` and optional arguments metadata.
- **Resources**: `addResource` exposes static or generated content via `ReadResourceResult`; `addResources`
  batch-registers. Templates and roots are also supported for structured discovery.
- **Instructions**: Provide static text or a function via `instructionsProvider` to send per-session usage guidance
  during initialization.

## Lifecycle and diagnostics

- Sessions emit callbacks via `onConnect` and `onClose`. Deprecated `onInitialized` exists for backward compatibility;
  prefer session-level hooks.
- The server enforces declared capabilities; attempting to register an unsupported feature throws with a descriptive
  message.
- Logging uses `KotlinLogging`; avoid logging secrets. To inspect traffic, run the server with DEBUG level enabled.

Use this module whenever you need to expose MCP features from a Kotlin service—whether embedding in a CLI via STDIO,
serving from Ktor over SSE/WebSocket, or composing transports for custom environments. Public APIs are explicit to keep
server-facing contracts stable for consumers.
