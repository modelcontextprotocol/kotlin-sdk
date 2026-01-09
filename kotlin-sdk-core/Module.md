# kotlin-sdk-core

`kotlin-sdk-core` is the foundation of the MCP Kotlin SDK. It contains protocol message types, JSON handling, and
transport abstractions used by both client and server modules. No platform-specific code lives here; everything is
designed for Kotlin Multiplatform with explicit API mode enabled.

## What the module provides

- **Protocol model**: Complete MCP data classes for requests, results, notifications, capabilities, tools, prompts,
  resources, logging, completion, sampling, elicitation, and roots. DSL helpers (`*.dsl.kt`) let you build messages
  fluently without repeating boilerplate.
- **JSON utilities**: A shared `McpJson` configuration (kotlinx.serialization) with MCP-friendly settings (ignore
  unknown keys, no class discriminator). Helpers for converting maps to `JsonElement`, `EmptyJsonObject`, and
  encoding/decoding JSON-RPC envelopes.
- **Transport abstractions**: `Transport` and `AbstractTransport` define the message pipeline, callbacks, and error
  handling. `WebSocketMcpTransport` adds a shared WebSocket implementation for both client and server sides, and
  `ReadBuffer` handles streaming JSON-RPC framing.
- **Protocol engine**: The `Protocol` base class manages request/response correlation, notifications, progress tokens,
  and capability assertions. Higher-level modules extend it to become `Client` and `Server`.
- **Errors and safety**: Common exception types (`McpException`, parsing errors) plus capability enforcement hooks
  ensure callers cannot use endpoints the peer does not advertise.

## Typical usage

- Import MCP types to define capabilities and message payloads for your own transports or custom integrations.
- Extend `Protocol` if you need a bespoke peer role while reusing correlation logic and JSON-RPC helpers.
- Use the DSL builders to construct well-typed requests (tools, prompts, resources, logging, completion) instead of
  crafting raw JSON.

### Example: building a tool call request

```kotlin
val request = CallToolRequest {
    name = "summarize"
    arguments = mapOf("text" to "Hello MCP")
}
```

### Example: parsing a JSON-RPC message

```kotlin
val message: JSONRPCMessage = McpJson.decodeFromString(jsonString)
```

Use this module when you need the raw building blocks of MCP—types, JSON config, and transport base classes—whether to
embed in another runtime, author new transports, or contribute higher-level features in the client/server modules. The
APIs are explicit to keep the shared surface stable for downstream users.
