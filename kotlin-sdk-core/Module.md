# Module kotlin-sdk-core

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

## JSON Schema integration

The module integrates with [kotlinx-schema](https://kotlin.github.io/kotlinx-schema/) to enable type-safe schema
generation from Kotlin data classes. Extension functions convert between kotlinx-schema types and MCP's [ToolSchema],
allowing you to define tool schemas using `@Description` annotations and other schema metadata.

### Supported conversions

- [JsonObject.asToolSchema][asToolSchema] — converts a raw JSON Schema object
- [JsonSchema.asToolSchema][asToolSchema] — converts kotlinx-schema's [JsonSchema]
- [FunctionCallingSchema.asToolSchema][asToolSchema] — extracts parameters from [FunctionCallingSchema]

### Example: generating tool schema from a data class

```kotlin
import kotlinx.schema.Description
import kotlinx.schema.generator.core.SchemaGeneratorService
import kotlinx.schema.json.JsonSchema

data class SearchParams(
    @property:Description("Search query")
    val query: String,
    @property:Description("Maximum number of results")
    val limit: Int = 10,
)

// Generate schema from data class
val generator = SchemaGeneratorService.getGenerator(KClass::class, JsonSchema::class)
val schema = generator.generateSchema(SearchParams::class)

// Convert to MCP ToolSchema
val toolSchema = schema.asToolSchema()

// Use in Tool definition
val tool = Tool(
    name = "web-search",
    description = "Search the web",
    inputSchema = toolSchema,
)
```

### Example: using FunctionCallingSchema

```kotlin
import kotlinx.schema.json.FunctionCallingSchema
import kotlinx.schema.json.ObjectPropertyDefinition
import kotlinx.schema.json.StringPropertyDefinition

val functionSchema = FunctionCallingSchema(
    name = "calculate",
    description = "Perform a calculation",
    parameters = ObjectPropertyDefinition(
        properties = mapOf(
            "expression" to StringPropertyDefinition(
                description = "Mathematical expression to evaluate"
            ),
        ),
        required = listOf("expression"),
    ),
)

val toolSchema = functionSchema.asToolSchema()
```

### Schema validation

All conversion functions validate that the schema type is `"object"` (or omit validation if the type field is absent).
Only object schemas are supported for MCP tool input/output schemas. Attempting to convert array, string, or other
non-object schemas will throw [IllegalArgumentException].

---

Use this module when you need the raw building blocks of MCP—types, JSON config, and transport base classes—whether to
embed in another runtime, author new transports, or contribute higher-level features in the client/server modules. The
APIs are explicit to keep the shared surface stable for downstream users.
