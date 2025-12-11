# MCP Kotlin SDK

[![Maven Central](https://img.shields.io/maven-central/v/io.modelcontextprotocol/kotlin-sdk.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.modelcontextprotocol/kotlin-sdk)
[![Build](https://github.com/modelcontextprotocol/kotlin-sdk/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/modelcontextprotocol/kotlin-sdk/actions/workflows/build.yml)
[![Conformance Tests](https://github.com/modelcontextprotocol/kotlin-sdk/actions/workflows/conformance.yml/badge.svg)](https://github.com/modelcontextprotocol/kotlin-sdk/actions/workflows/conformance.yml)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2+-blueviolet.svg?logo=kotlin)](http://kotlinlang.org)
[![Kotlin Multiplatform](https://img.shields.io/badge/Platforms-%20JVM%20%7C%20Wasm%2FJS%20%7C%20Native%20-blueviolet?logo=kotlin)](https://kotlinlang.org/docs/multiplatform.html)
[![JVM](https://img.shields.io/badge/JVM-11+-red.svg?logo=jvm)](http://java.com)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

Kotlin Multiplatform SDK for the [Model Context Protocol](https://modelcontextprotocol.io).
It enables Kotlin applications targeting JVM, Native, JS, and Wasm to implement MCP clients and servers using a
standardized protocol interface.

## Table of Contents

<!--- TOC -->

* [Overview](#overview)
* [Installation](#installation)
    * [Artifacts](#artifacts)
    * [Gradle setup (JVM)](#gradle-setup-jvm)
    * [Multiplatform](#multiplatform)
    * [Ktor dependencies](#ktor-dependencies)
* [Quickstart](#quickstart)
    * [Creating a Client](#creating-a-client)
    * [Creating a Server](#creating-a-server)
* [Core Concepts](#core-concepts)
    * [MCP Primitives](#mcp-primitives)
    * [Capabilities](#capabilities)
        * [Server Capabilities](#server-capabilities)
        * [Client Capabilities](#client-capabilities)
    * [Server Features](#server-features)
        * [Prompts](#prompts)
        * [Resources](#resources)
        * [Tools](#tools)
        * [Completion](#completion)
        * [Logging](#logging)
        * [Pagination](#pagination)
    * [Client Features](#client-features)
        * [Roots](#roots)
        * [Sampling](#sampling)
* [Transports](#transports)
    * [STDIO Transport](#stdio-transport)
    * [Streamable HTTP Transport](#streamable-http-transport)
    * [SSE Transport](#sse-transport)
    * [WebSocket Transport](#websocket-transport)
* [Connecting your server](#connecting-your-server)
* [Examples](#examples)
* [Documentation](#documentation)
* [Contributing](#contributing)
* [License](#license)

<!--- END -->

## Overview

The Model Context Protocol allows applications to provide context for LLMs in a standardized way,
separating the concerns of providing context from the actual LLM interaction.
This Kotlin SDK implements the MCP specification, making it easy to:

- Build MCP **clients** that can connect to any MCP server
- Create MCP **servers** that expose resources, prompts, and tools
- Target **JVM, Native, JS, and Wasm** from a single codebase
- Use standard transports like **stdio**, **SSE**, **Streamable HTTP**, and **WebSocket**
- Handle MCP protocol messages and lifecycle events with coroutine-friendly APIs

## Installation

### Artifacts

- `io.modelcontextprotocol:kotlin-sdk` – umbrella SDK (client + server APIs)
- `io.modelcontextprotocol:kotlin-sdk-client` – client-only APIs
- `io.modelcontextprotocol:kotlin-sdk-server` – server-only APIs

### Gradle setup (JVM)

Add the Maven Central repository and the SDK dependency:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    // See the badge above for the latest version
    implementation("io.modelcontextprotocol:kotlin-sdk:$mcpVersion")
}
```

Use _kotlin-sdk-client_ or _kotlin-sdk-server_ if you only need one side of the API:

```kotlin
dependencies {
    implementation("io.modelcontextprotocol:kotlin-sdk-client:$mcpVersion")
    implementation("io.modelcontextprotocol:kotlin-sdk-server:$mcpVersion")
}
```

### Multiplatform

In a Kotlin Multiplatform project you can add the SDK to commonMain:

```kotlin
commonMain {
    dependencies {
        // Works as a common dependency as well as the platform one
        implementation("io.modelcontextprotocol:kotlin-sdk:$mcpVersion")
    }
}
```

### Ktor dependencies

The Kotlin MCP SDK uses [Ktor](https://ktor.io/), but it does not add Ktor engine dependencies transitively.
You need to declare
Ktor [client](https://ktor.io/docs/client-dependencies.html#engine-dependency)/[server](https://ktor.io/docs/server-dependencies.html)
dependencies yourself (or reuse the ones already used in your project),
for example:

```kotlin
dependencies {
    // MCP client with Ktor
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.modelcontextprotocol:kotlin-sdk-client:$mcpVersion")

    // MCP server with Ktor
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.modelcontextprotocol:kotlin-sdk-server:$mcpVersion")
}
```

## Quickstart

Let's create a simple MCP client and server to demonstrate the basic usage of the Kotlin SDK.

<!--- CLEAR -->

### Creating a Client

Create an MCP client that connects to a server via Streamable HTTP transport and lists available tools:

```kotlin
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) = runBlocking {
    val url = args.firstOrNull() ?: "http://localhost:3000/mcp"

    val httpClient = HttpClient { install(SSE) }

    val client = Client(
        clientInfo = Implementation(
            name = "example-client",
            version = "1.0.0"
        )
    )

    val transport = StreamableHttpClientTransport(
        client = httpClient,
        url = url
    )

    // Connect to server
    client.connect(transport)

    // List available tools
    val tools = client.listTools().tools

    println(tools)
}
```

<!--- KNIT example-quickstart-client-01.kt -->

### Creating a Server

Create an MCP server that exposes a simple tool and runs on an embedded Ktor server with SSE transport:

```kotlin
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put


fun main(args: Array<String>) {
    val port = args.firstOrNull()?.toIntOrNull() ?: 3000
    val mcpServer = Server(
        serverInfo = Implementation(
            name = "example-server",
            version = "1.0.0"
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true),
            ),
        )
    )

    mcpServer.addTool(
        name = "example-tool",
        description = "An example tool",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("input", buildJsonObject { put("type", "string") })
            }
        )
    ) { request ->
        CallToolResult(content = listOf(TextContent("Hello, world!")))
    }

    embeddedServer(CIO, host = "127.0.0.1", port = port) {
        mcp {
            mcpServer
        }
    }.start(wait = true)
}
```

<!--- KNIT example-quickstart-server-01.kt -->

You can run the server and then connect to it using the client or test with the MCP Inspector:

```bash
npx -y @modelcontextprotocol/inspector
```

In the inspector UI, connect to `http://localhost:3000`.

## Core Concepts

### MCP Primitives

The MCP protocol defines core primitives that enable communication between servers and clients:

| Primitive     | Server Role                                       | Client Role                            | Description                                       |
|---------------|---------------------------------------------------|----------------------------------------|---------------------------------------------------|
| **Prompts**   | Provides prompt templates with optional arguments | Requests and uses prompts              | Interactive templates for LLM interactions        |
| **Resources** | Exposes data sources (files, API responses, etc.) | Reads and subscribes to resources      | Contextual data for augmenting LLM context        |
| **Tools**     | Defines executable functions                      | Calls tools to perform actions         | Functions the LLM can invoke to take actions      |
| **Sampling**  | Requests LLM completions from client              | Executes LLM calls and returns results | Server-initiated LLM requests (reverse direction) |

### Capabilities

Capabilities define what features a server or client supports. They are declared during initialization and determine
what operations are available.

#### Server Capabilities

Servers declare their capabilities to inform clients what features they provide:

| Capability     | Feature Flags                 | Description                                                |
|----------------|-------------------------------|------------------------------------------------------------|
| `prompts`      | `listChanged`                 | Prompt template management and notifications               |
| `resources`    | `subscribe`<br/>`listChanged` | Resource exposure, subscriptions, and update notifications |
| `tools`        | `listChanged`                 | Tool discovery, execution, and list change notifications   |
| `logging`      | -                             | Server logging to client console                           |
| `completions`  | -                             | Argument autocompletion suggestions                        |
| `experimental` | Custom properties             | Non-standard experimental features                         |

#### Client Capabilities

Clients declare their capabilities to inform servers what features they support:

| Capability     | Feature Flags     | Description                                                 |
|----------------|-------------------|-------------------------------------------------------------|
| `sampling`     | -                 | Client can sample from an LLM (execute model requests)      |
| `roots`        | `listChanged`     | Client exposes root directories and can notify of changes   |
| `elicitation`  | -                 | Client can display schema/form dialogs for structured input |
| `experimental` | Custom properties | Non-standard experimental features                          |

### Server Features

The `Server` API lets you wire prompts, resources, and tools with only a few lines of Kotlin. Each feature is registered
up front and then resolved lazily when a client asks for it, so your handlers stay small and suspendable.

#### Prompts

Prompts are reusable templates that help users or clients start conversations in a consistent way.

<!--- INCLUDE
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.PromptArgument
import io.modelcontextprotocol.kotlin.sdk.types.PromptMessage
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent

fun main() {
-->

```kotlin
val server = Server(
    serverInfo = Implementation(
        name = "example-server",
        version = "1.0.0"
    ),
    options = ServerOptions(
        capabilities = ServerCapabilities(
            prompts = ServerCapabilities.Prompts(listChanged = true),
        ),
    )
)

server.addPrompt(
    name = "code-review",
    description = "Ask the model to review a diff",
    arguments = listOf(
        PromptArgument(name = "diff", description = "Unified diff", required = true),
    ),
) { request ->
    GetPromptResult(
        description = "Quick code review helper",
        messages = listOf(
            PromptMessage(
                role = Role.User,
                content = TextContent(text = "Review this change:\n${request.arguments?.get("diff")}"),
            ),
        ),
    )
}
```

<!--- SUFFIX
}
-->

<!--- KNIT example-server-prompts-01.kt -->

Use prompts for anything that deserves a template: bug triage questions, onboarding checklists, or saved searches.

#### Resources

Resources expose read-only context. Register them with a stable URI and return a `ReadResourceResult` when the client
reads the resource.

<!--- INCLUDE
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents

fun main() {
-->

```kotlin
val server = Server(
    serverInfo = Implementation(
        name = "example-server",
        version = "1.0.0"
    ),
    options = ServerOptions(
        capabilities = ServerCapabilities(
            resources = ServerCapabilities.Resources(subscribe = true, listChanged = true),
        ),
    )
)

server.addResource(
    uri = "note://release/latest",
    name = "Release notes",
    description = "Last deployment summary",
    mimeType = "text/markdown",
) { request ->
    ReadResourceResult(
        contents = listOf(
            TextResourceContents(
                text = "Ship 42 reached production successfully.",
                uri = request.uri,
                mimeType = "text/markdown",
            ),
        ),
    )
}
```

<!--- SUFFIX
}
-->

<!--- KNIT example-server-resources-01.kt -->

Resources can be static text, generated JSON, or blobs—anything the client can surface to the user or inject into the
model context.

#### Tools

Tools are the imperative side of MCP. Each tool gets JSON arguments, can emit streaming logs or progress, and returns a
`CallToolResult`.

<!--- INCLUDE
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.jsonPrimitive

fun main() {
-->

```kotlin
val server = Server(
    serverInfo = Implementation(
        name = "example-server",
        version = "1.0.0"
    ),
    options = ServerOptions(
        capabilities = ServerCapabilities(
            tools = ServerCapabilities.Tools(listChanged = true),
        ),
    )
)

server.addTool(
    name = "echo",
    description = "Return whatever the user sent back to them",
) { request ->
    val text = request.arguments?.get("text")?.jsonPrimitive?.content ?: "(empty)"
    CallToolResult(content = listOf(TextContent(text = "Echo: $text")))
}
```

<!--- SUFFIX
}
-->

<!--- KNIT example-server-tools-01.kt -->

Register as many tools as you need—long-running jobs can report progress via the request context, and tools can also
trigger sampling (see below) when they need the client’s LLM.

#### Completion

#### Logging

#### Pagination

### Client Features

Clients advertise their capabilities (roots, sampling, elicitation, etc.) during initialization. After that they can
serve requests from the server while still initiating calls such as `listTools` or `callTool`.

#### Roots

Roots describe folders or logical mounts that the client is willing to expose. Servers can list them to understand what
paths are available before proposing file operations.

<!--- INCLUDE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Implementation

suspend fun main() {
-->

```kotlin
val client = Client(
    clientInfo = Implementation("demo-client", "1.0.0"),
    options = ClientOptions(
        capabilities = ClientCapabilities(roots = ClientCapabilities.Roots(listChanged = true)),
    ),
)

client.addRoot(
    uri = "file:///Users/demo/projects",
    name = "Projects",
)
client.sendRootsListChanged()
```

<!--- SUFFIX 
}    
-->

<!--- KNIT example-client-roots-01.kt -->

Call `addRoot`/`removeRoot` whenever your file system view changes, and use `sendRootsListChanged()` to notify the
server.

#### Sampling

Sampling lets a server ask the client to call its preferred LLM. Enable it by declaring the `sampling` capability and
handling the `sampling/createMessage` request.

<!--- INCLUDE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequest
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.TextContent

fun main() {
-->

```kotlin
val client = Client(
    clientInfo = Implementation("demo-client", "1.0.0"),
    options = ClientOptions(
        capabilities = ClientCapabilities(sampling = ClientCapabilities.sampling),
    ),
)

client.setRequestHandler<CreateMessageRequest>(Method.Defined.SamplingCreateMessage) { request, _ ->
    val content = request.messages.lastOrNull()?.content
    val prompt = if (content is TextContent) content.text else "your topic"
    CreateMessageResult(
        model = "gpt-4o-mini",
        role = Role.Assistant,
        content = TextContent(text = "Here is a short note about $prompt"),
    )
}
```

<!--- SUFFIX
}
-->

<!--- KNIT example-client-sampling-01.kt -->

Inside the handler you are free to choose any model/provider, collect extra approvals, or reject the request.

[//]: # (TODO: add elicitation section)

[//]: # (#### Elicitation)

## Transports

All transports share the same API surface, so you can change deployment style without touching business logic. Pick the
transport that best matches where the server runs.

### STDIO Transport

`StdioClientTransport` and `StdioServerTransport` tunnel MCP messages over stdin/stdout—perfect for editor plugins or
CLI tooling that spawns a helper process. No networking setup is required.

### Streamable HTTP Transport

`StreamableHttpClientTransport` and the Ktor `streamableHttpApp()` helpers expose MCP over a single HTTP endpoint with
optional JSON-only or SSE streaming responses. This is the recommended choice for remote deployments and integrates
nicely with proxies or service meshes.

### SSE Transport

Server-Sent Events remain available for backwards compatibility with older MCP clients. Use `SseServerTransport` or the
SSE Ktor plugin when you need drop-in compatibility, but prefer Streamable HTTP for new projects.

### WebSocket Transport

`WebSocketClientTransport` plus the matching server utilities provide full-duplex, low-latency connections—useful when
you expect lots of notifications or long-running sessions behind a reverse proxy that already terminates WebSockets.

## Connecting your server

1. Start a sample server:

   ```bash
   ./gradlew :samples:kotlin-mcp-server:run
   ```

2. Point the [MCP Inspector](https://github.com/modelcontextprotocol/inspector) or Claude Desktop/Code at the running
   endpoint:

   ```bash
   npx -y @modelcontextprotocol/inspector --connect http://localhost:3000/mcp
   # or
   claude mcp add --transport http kotlin-mcp http://localhost:3000/mcp
   ```

3. Watch the Inspector UI to verify prompts, tools, resources, and logs are all available, then iterate on your server
   locally until it’s ready for production hosting.

## Examples

- [kotlin-mcp-server](./samples/kotlin-mcp-server): demonstrates a MCP server setup with
  various features and transports.
- [weather-stdio-server](./samples/weather-stdio-server): shows how to build a Kotlin MCP server providing weather
  forecast and alerts using STDIO transport.
- [kotlin-mcp-client](./samples/kotlin-mcp-client): demonstrates building an interactive Kotlin MCP client that connects
  to an MCP server via STDIO and integrates with Anthropic’s API.

## Documentation

- [API Reference](https://modelcontextprotocol.github.io/kotlin-sdk/)
- [Model Context Protocol documentation](https://modelcontextprotocol.io)
- [MCP specification](https://modelcontextprotocol.io/specification/latest)

## Contributing

Please see the [contribution guide](CONTRIBUTING.md) and the [Code of conduct](CODE_OF_CONDUCT.md) before contributing.

## License

This project is licensed under Apache 2.0 for new contributions, with existing code under MIT—see the [LICENSE](LICENSE) file for details.
