# MCP Kotlin SDK

[![Maven Central](https://img.shields.io/maven-central/v/io.modelcontextprotocol/kotlin-sdk.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.modelcontextprotocol/kotlin-sdk)
[![Build](https://github.com/modelcontextprotocol/kotlin-sdk/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/modelcontextprotocol/kotlin-sdk/actions/workflows/build.yml)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2+-blueviolet.svg?logo=kotlin)](http://kotlinlang.org)
[![Kotlin Multiplatform](https://img.shields.io/badge/Platforms-%20JVM%20%7C%20Wasm%2FJS%20%7C%20Native%20-blueviolet?logo=kotlin)](https://kotlinlang.org/docs/multiplatform.html)
[![JVM](https://img.shields.io/badge/JVM-11+-red.svg?logo=jvm)](http://java.com)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Kotlin Multiplatform implementation of the [Model Context Protocol](https://modelcontextprotocol.io) (MCP), providing both client and server capabilities for integrating with LLM applications across JVM, JavaScript, and Native platforms.

---

## Table of Contents

- [What is MCP?](#what-is-mcp)
- [Why Use This SDK?](#why-use-this-sdk)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
  - [Creating a Client](#creating-a-client)
  - [Creating a Server](#creating-a-server)
- [Architecture](#architecture)
- [Transports](#transports)
- [Deploying to Claude Desktop](#deploying-to-claude-desktop)
- [API Reference](#api-reference)
- [Testing](#testing)
- [Production Deployment](#production-deployment)
- [Security](#security)
- [Troubleshooting](#troubleshooting)
- [Examples](#examples)
- [Contributing](#contributing)
- [License](#license)

---

## What is MCP?

The **Model Context Protocol (MCP)** is an open standard that enables AI applications to securely access data sources, 
tools, and context in a standardized way.

### Key Benefits

- 🔄 **Interoperability**: Write tools once, use with Claude, GPT-4, Gemini, or any LLM
- 🎯 **Discovery**: Tools and resources are auto-discovered, not hard-coded
- 🔐 **Security**: Sandboxed execution with permission controls
- 📦 **Ecosystem**: Share servers with community, use 100+ existing integrations
- 🏢 **Enterprise**: Standardize AI context across your organization

### MCP Concepts

| Concept | Description | Example |
|---------|-------------|---------|
| **Server** | Provides tools, resources, or prompts | Database connector, file system access |
| **Client** | Consumes MCP capabilities | Claude Desktop, custom AI app |
| **Tool** | Function LLM can call | `get_weather(location)`, `send_email(to, body)` |
| **Resource** | Data LLM can read | File contents, database records, API responses |
| **Prompt** | Reusable prompt templates | Code review template, analysis framework |
| **Transport** | Communication channel | stdio, SSE, WebSocket |

---

## Why Use This SDK?

### Comparison with Other MCP SDKs

| Feature | Kotlin SDK | TypeScript | Python |
|---------|------------|------------|---------|
| **Platforms** | JVM, JS, Native, Android, iOS | Node.js, Browser | CPython only |
| **Type Safety** | ✅ Compile-time | ✅ Compile-time | ⚠️ Runtime |
| **Async Model** | Coroutines | Promises | asyncio |
| **Mobile Support** | ✅ Full | ❌ No | ❌ No |
| **Interop** | Java, Kotlin, Scala | JavaScript, TypeScript | Python |

**Choose Kotlin SDK when:**
- ✅ You have existing Kotlin/JVM infrastructure
- ✅ You need multiplatform deployment (Android, iOS, Desktop, Server)
- ✅ Type safety and compile-time checks are important
- ✅ You're building mobile AI applications
- ✅ You want coroutine-based async programming

**Other official SDKs:**
- **TypeScript**: [@modelcontextprotocol/sdk](https://github.com/modelcontextprotocol/typescript-sdk)
- **Python**: [mcp](https://github.com/modelcontextprotocol/python-sdk)

---

## Requirements

### Minimum Versions

- **Kotlin**: 2.0 or later
- **JVM**: 11 or later (for JVM targets)
- **Gradle**: 7.0+ or Maven 3.6+

### Supported Platforms

| Platform | Support | Architectures | Notes |
|----------|---------|---------------|-------|
| **JVM** | ✅| All | Java 11+ required |
| **Android** | ✅| All | API 21+ (Lollipop) |
| **JavaScript** | ✅| - | Node.js & Browser |
| **Wasm** | ✅| wasm32 | Experimental |
| **iOS** | ✅| arm64, x64 | iOS 14+ |
| **macOS** | ✅| arm64, x64 | macOS 11+ |
| **Linux** | ✅| x64, arm64 | glibc 2.27+ |
| **Windows** | ✅| x64 | Windows 10+ |

---

## Installation

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    // Core SDK (includes client + server)
    implementation("io.modelcontextprotocol:kotlin-sdk:0.8.1")

    // Required: Choose a Ktor client engine
    implementation("io.ktor:ktor-client-cio:3.0.0")
    // OR implementation("io.ktor:ktor-client-okhttp:3.0.0")
    // OR implementation("io.ktor:ktor-client-java:3.0.0")

    // Required for servers: Choose a Ktor server engine
    implementation("io.ktor:ktor-server-netty:3.0.0")
    // OR implementation("io.ktor:ktor-server-cio:3.0.0")
}
```

<details>
<summary><b>Why separate Ktor engine dependencies?</b></summary>

The SDK is engine-agnostic to give you flexibility:

- **CIO**: Pure Kotlin, good default choice
- **OkHttp**: Battle-tested, extensive ecosystem
- **Java**: Uses Java's HttpClient (JDK 11+)
- **Netty**: High performance, production-grade

See [Ktor engines documentation](https://ktor.io/docs/engines.html) for guidance.
</details>

### Gradle (Version Catalogs)

<details>
<summary><b>Click to expand version catalog example</b></summary>

In `gradle/libs.versions.toml`:
```properties
# Version definitions
mcp = "0.8.1"
ktor = "3.0.0"

# Library definitions
mcp-sdk = { module = "io.modelcontextprotocol:kotlin-sdk", version.ref = "mcp" }
ktor-client-cio = { module = "io.ktor:ktor-client-cio", version.ref = "ktor" }
ktor-server-netty = { module = "io.ktor:ktor-server-netty", version.ref = "ktor" }
```

In `build.gradle.kts`:
```kotlin
dependencies {
    implementation(libs.mcp.sdk)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.server.netty)
}
```

</details>

### Maven

```xml
<dependencies>
    <dependency>
        <groupId>io.modelcontextprotocol</groupId>
        <artifactId>kotlin-sdk-jvm</artifactId>
        <version>0.8.1</version>
    </dependency>
    <dependency>
        <groupId>io.ktor</groupId>
        <artifactId>ktor-client-cio-jvm</artifactId>
        <version>3.0.0</version>
    </dependency>
</dependencies>
```

### Multiplatform Projects

```kotlin
kotlin {
    jvm()
    js(IR) { nodejs() }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            dependencies {
                implementation("io.modelcontextprotocol:kotlin-sdk:0.8.1")
                implementation("io.ktor:ktor-client-core:3.0.0")
            }
        }

        jvmMain {
            dependencies {
                implementation("io.ktor:ktor-client-cio:3.0.0")
            }
        }

        iosMain {
            dependencies {
                implementation("io.ktor:ktor-client-darwin:3.0.0")
            }
        }
    }
}
```

---

<!--- CLEAR -->

## Quick Start

### Creating a Client

A client connects to MCP servers to discover and use tools, resources, and prompts.

<!--- TEST_NAME ClientExampleTest -->
<!--- INCLUDE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
-->
```kotlin
fun main() = runBlocking {
    // 1. Create client
    val client = Client(
        clientInfo = Implementation(
            name = "my-ai-app",
            version = "1.0.0",
        ),
    )

    // 2. Start MCP server process
    val serverProcess = ProcessBuilder(
        "npx",
        "-y",
        "@modelcontextprotocol/server-everything",
    ).start()

    // 3. Connect via stdio transport
    val transport = StdioClientTransport(
        input = serverProcess.inputStream.asSource().buffered(),
        output = serverProcess.outputStream.asSink().buffered(),
    )

    client.connect(transport)

    // 4. Discover available tools
    val tools = client.listTools()
    println("Available tools: ${tools.tools.map { it.name }}")

    // 5. Call a tool
    val result = client.callTool(
        name = "get_weather",
        arguments = mapOf("location" to "Tokyo"),
    )

    println("Weather: ${result.content}")
}
```

<!--- KNIT example-client-01.kt -->

### Creating a Server

A server exposes tools, resources, or prompts to any MCP client.

<!--- TEST_NAME ServerExampleTest -->
<!--- INCLUDE
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
-->
```kotlin
fun main() = runBlocking {
    // 1. Create server with capabilities
    val server = Server(
        serverInfo = Implementation(
            name = "my-tools-server",
            version = "1.0.0",
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true),
                resources = ServerCapabilities.Resources(
                    subscribe = true,
                    listChanged = true,
                ),
            ),
        ),
    ) {
        "My MCP server providing useful tools and resources"
    }

    // 2. Add a tool
    server.addTool(
        name = "calculate",
        description = "Performs basic calculations",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("operation") {
                    put("type", "string")
                    put(
                        "enum",
                        buildJsonArray {
                            add("add")
                            add("subtract")
                            add("multiply")
                            add("divide")
                        },
                    )
                }
                putJsonObject("a") { put("type", "number") }
                putJsonObject("b") { put("type", "number") }
            },
            required = listOf("operation", "a", "b"),
        ),
    ) { request ->
        val args = request.arguments
            ?: throw McpException(RPCError.ErrorCode.INVALID_PARAMS, "Missing arguments")
        val op = args["operation"]?.jsonPrimitive?.content
        val a = args["a"]?.jsonPrimitive?.double ?: 0.0
        val b = args["b"]?.jsonPrimitive?.double ?: 0.0

        val result = when (op) {
            "add" -> a + b

            "subtract" -> a - b

            "multiply" -> a * b

            "divide" -> if (b != 0.0) {
                a / b
            } else {
                throw McpException(RPCError.ErrorCode.INVALID_PARAMS, "Division by zero")
            }

            else -> throw McpException(RPCError.ErrorCode.INVALID_PARAMS, "Unknown operation: $op")
        }

        CallToolResult(content = listOf(TextContent(result.toString())))
    }

    // 3. Add a resource
    server.addResource(
        uri = "config://app/settings",
        name = "Application Settings",
        description = "Current application configuration",
        mimeType = "application/json",
    ) { request ->
        ReadResourceResult(
            contents = listOf(
                TextResourceContents(
                    text = """{"theme": "dark", "language": "en"}""",
                    uri = request.uri,
                    mimeType = "application/json",
                ),
            ),
        )
    }

    // 4. Start server with stdio transport
    val transport = StdioServerTransport(
        inputStream = System.`in`.asSource().buffered(),
        outputStream = System.out.asSink().buffered(),
    )

    server.createSession(transport)

    // Keep server running
    kotlinx.coroutines.delay(Long.MAX_VALUE)
}
```

<!--- KNIT example-server-01.kt -->

---

## Architecture

### Module Structure

```
kotlin-sdk (umbrella artifact)
├── kotlin-sdk-core      # Protocol types, base transports, JSON-RPC
├── kotlin-sdk-client    # Client implementation, client-specific transports
└── kotlin-sdk-server    # Server implementation, server-specific transports
```

### When to Use Each Module

| Module | Use When | Dependency |
|--------|----------|------------|
| `kotlin-sdk` | Building apps with both client + server (most common) | All modules |
| `kotlin-sdk-client` | Building pure MCP clients (AI apps) | Core + Client |
| `kotlin-sdk-server` | Building pure MCP servers (tool providers) | Core + Server |
| `kotlin-sdk-core` | Building custom transports or protocol extensions | Core only |

### Architecture Patterns

<details>
<summary><b>Pattern 1: Desktop Integration</b> (Claude Desktop, Cursor, Zed)</summary>

```
┌─────────────────┐
│  AI Desktop App │ (e.g., Claude Desktop)
│   (MCP Client)  │
└────────┬────────┘
         │ stdio (subprocess)
         ▼
┌─────────────────┐
│  Your Server    │ (Kotlin JAR)
│     (Stdio)     │
└────────┬────────┘
         │
         ▼
   Your Services
  (DB, APIs, etc)
```

**Use Case**: Personal productivity tools, local file access, IDE integrations

**Deployment**: Package as executable JAR, configured in Claude Desktop settings
</details>

<details>
<summary><b>Pattern 2: Microservice Architecture</b></summary>

```
┌──────────────┐    HTTP/SSE     ┌─────────────┐
│  AI Service  │ ◄─────────────► │ MCP Server  │
│ (MCP Client) │   or WebSocket  │   (Ktor)    │
└──────────────┘                 └──────┬──────┘
                                        │
                                        ▼
                                 Backend Services
                                (PostgreSQL, Redis, S3)
```

**Use Case**: Enterprise AI applications, multi-tenant SaaS, high availability

**Deployment**: Kubernetes deployment with Ingress, Docker containers
</details>

<details>
<summary><b>Pattern 3: Embedded (Mobile)</b></summary>

```
┌───────────────────────────┐
│      Mobile App           │
│  ┌─────────────────────┐  │
│  │   MCP Client        │  │
│  │  (in-process)       │  │
│  └──────────┬──────────┘  │
│             │              │
│  ┌──────────▼──────────┐  │
│  │   MCP Server        │  │
│  │   (embedded)        │  │
│  └─────────────────────┘  │
│             │              │
│    Local Storage/APIs     │
└───────────────────────────┘
```

**Use Case**: Offline-first mobile apps, on-device AI

**Deployment**: Kotlin Multiplatform shared library
</details>

---

## Transports

MCP supports multiple transport mechanisms for different deployment scenarios.

### Stdio Transport

**Best for**: Desktop integration (Claude Desktop, Cursor, Zed), CLI tools

```kotlin
// Server
fun main() = runBlocking {
    val server = Server(...)
    val transport = StdioServerTransport(
        inputStream = System.`in`.asSource().buffered(),
        outputStream = System.out.asSink().buffered()
    )
    server.createSession(transport)
    kotlinx.coroutines.delay(Long.MAX_VALUE)
}

// Client
val process = ProcessBuilder("java", "-jar", "server.jar").start()
val transport = StdioClientTransport(
    input = process.inputStream.asSource().buffered(),
    output = process.outputStream.asSink().buffered()
)
client.connect(transport)
```

### SSE (Server-Sent Events) Transport

**Best for**: Web-based servers, unidirectional streaming from server

<details>
<summary><b>Simple SSE Server with Ktor Plugin</b></summary>

```kotlin
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.modelcontextprotocol.kotlin.sdk.server.mcp

fun main() {
    embeddedServer(Netty, port = 8080) {
        mcp {
            Server(
                serverInfo = Implementation("sse-server", "1.0.0"),
                options = ServerOptions(
                    capabilities = ServerCapabilities(
                        tools = ServerCapabilities.Tools(listChanged = true)
                    )
                )
            ) { "SSE MCP Server" }
        }
    }.start(wait = true)
}
```

Server will be available at `http://localhost:8080`
- SSE endpoint: `GET http://localhost:8080/sse`
- Message endpoint: `POST http://localhost:8080/message?sessionId={id}`
</details>

<details>
<summary><b>SSE Client</b></summary>

```kotlin
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport

val httpClient = HttpClient(CIO)
val transport = SseClientTransport(
    client = httpClient,
    urlString = "http://localhost:8080"
)
client.connect(transport)
```
</details>

<details>
<summary><b>Custom SSE Routing</b></summary>

```kotlin
import io.ktor.server.routing.*
import io.ktor.server.sse.SSE

fun Application.module() {
    install(SSE)

    routing {
        route("/custom-mcp") {
            mcp {
                Server(
                    serverInfo = Implementation("custom-sse", "1.0.0"),
                    options = ServerOptions(
                        capabilities = ServerCapabilities(
                            tools = ServerCapabilities.Tools(listChanged = true)
                        )
                    )
                ) { "Custom path SSE server" }
            }
        }
    }
}
```
</details>

### WebSocket Transport

**Best for**: Bidirectional streaming, real-time applications

```kotlin
// Server
import io.ktor.server.websocket.*

fun Application.module() {
    install(WebSockets)

    routing {
        mcpWebSocket("/mcp") {
            Server(
                serverInfo = Implementation("ws-server", "1.0.0"),
                options = ServerOptions(
                    capabilities = ServerCapabilities(
                        tools = ServerCapabilities.Tools(listChanged = true)
                    )
                )
            ) { "WebSocket MCP Server" }
        }
    }
}

// Client
import io.ktor.client.plugins.websocket.*

val httpClient = HttpClient(CIO) {
    install(WebSockets)
}

val transport = WebSocketClientTransport(
    client = httpClient,
    urlString = "ws://localhost:8080/mcp"
)
client.connect(transport)
```

### Custom Transports

Implement `Transport` interface for custom communication channels:

```kotlin
class CustomTransport : Transport {
    override suspend fun start() {
        // Initialize connection
    }

    override suspend fun send(message: JSONRPCMessage) {
        // Send message over your protocol
    }

    override suspend fun close() {
        // Cleanup resources
    }
}
```

---

## Deploying to Claude Desktop

[Claude Desktop](https://claude.ai/download) has built-in MCP support, making it the fastest way to use your server.

### Step 1: Build Executable JAR

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "2.0.0"
    application
}

application {
    mainClass.set("com.example.MyServerKt")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.example.MyServerKt"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })
}
```

Build the JAR:
```bash
./gradlew jar
# Output: build/libs/my-server.jar
```

### Step 2: Configure Claude Desktop

Edit the configuration file:
- **macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
- **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`
- **Linux**: `~/.config/Claude/claude_desktop_config.json`

```json
{
  "mcpServers": {
    "my-kotlin-server": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/build/libs/my-server.jar"
      ],
      "env": {
        "API_KEY": "your-api-key-here",
        "LOG_LEVEL": "INFO"
      }
    }
  }
}
```

**Important**: Use absolute paths, not relative paths.

### Step 3: Restart Claude Desktop

Claude will automatically:
1. Start your server as a subprocess
2. Connect via stdio transport
3. Discover tools/resources/prompts
4. Make them available in conversations

### Testing

Ask Claude:
```
What tools do you have available?
```

Claude should list your MCP tools. Try using one:
```
Use my calculator tool to add 42 and 58
```

### Troubleshooting Claude Desktop

<details>
<summary><b>Server not starting?</b></summary>

**Check server logs:**
- **macOS**: `~/Library/Logs/Claude/mcp-server-my-kotlin-server.log`
- **Windows**: `%APPDATA%\Claude\logs\mcp-server-my-kotlin-server.log`

**Common issues:**
- JAR path must be absolute, not relative
- Java must be in PATH: `which java` (macOS/Linux) or `where java` (Windows)
- Verify JAR runs standalone: `java -jar /path/to/server.jar`
- Check JSON syntax in config file
</details>

<details>
<summary><b>Tools not appearing?</b></summary>

- Server must write to stdout (not stderr) for stdio transport
- Verify capabilities are configured: `tools = ServerCapabilities.Tools(listChanged = true)`
- Check server logs for initialization errors
- Ensure `server.addTool(...)` is called before `createSession()`
</details>

<details>
<summary><b>Need to debug server?</b></summary>

Add debug output to your server:
```kotlin
fun main() = runBlocking {
    println("MCP Server starting...") // Visible in Claude logs
    System.err.println("Debug: Server initialized") // Also visible

    val server = Server(...)
    server.onRequest { request ->
        System.err.println("Received: ${request.method}")
    }

    // Rest of server setup
}
```

Or run server standalone to see output directly:
```bash
java -jar build/libs/server.jar
# Type JSON-RPC messages manually to test
```
</details>

---

## API Reference

### Client API

The `Client` class provides methods to interact with MCP servers.

#### Connection Management

| Method | Description |
|--------|-------------|
| `Client(clientInfo: Implementation, options: ClientOptions = ClientOptions())` | Create a client instance |
| `suspend fun connect(transport: Transport)` | Connect to MCP server via transport |
| `suspend fun close()` | Close connection and cleanup resources |

#### Resources

| Method | Return Type | Description |
|--------|-------------|-------------|
| `listResources()` | `ListResourcesResult` | List all available resources |
| `readResource(request: ReadResourceRequest)` | `ReadResourceResult` | Read content of a specific resource |
| `subscribeResource(request: SubscribeRequest)` | `EmptyResult` | Subscribe to resource change notifications |
| `unsubscribeResource(request: UnsubscribeRequest)` | `EmptyResult` | Unsubscribe from resource notifications |

#### Tools

| Method | Return Type | Description |
|--------|-------------|-------------|
| `listTools()` | `ListToolsResult` | List all available tools |
| `callTool(name: String, arguments: Map<String, Any?>, meta: Map<String, Any?> = emptyMap())` | `CallToolResult` | Call a tool by name with arguments |

#### Prompts

| Method | Return Type | Description |
|--------|-------------|-------------|
| `listPrompts()` | `ListPromptsResult` | List all available prompts |
| `getPrompt(request: GetPromptRequest)` | `GetPromptResult` | Get a specific prompt with arguments |

#### Other Operations

| Method | Return Type | Description |
|--------|-------------|-------------|
| `complete(request: CompleteRequest)` | `CompleteResult` | Request argument/URI completions |
| `setLoggingLevel(level: LoggingLevel)` | `EmptyResult` | Set server logging level |
| `ping()` | `EmptyResult` | Ping server to check connectivity |

### Server API

The `Server` class provides methods to expose MCP capabilities.

#### Initialization

```kotlin
Server(
    serverInfo: Implementation,
    options: ServerOptions,
    instructionsProvider: (() -> String)? = null,
    block: Server.() -> Unit = {}
)
```

#### Resource Management

| Method | Description |
|--------|-------------|
| `addResource(uri, name, description, mimeType, handler)` | Add a resource with fixed URI |
| `addResourceTemplate(uriTemplate, name, description, mimeType, handler)` | Add resource template with URI pattern |
| `suspend fun sendResourceUpdated(uri: String)` | Notify clients that resource changed |
| `suspend fun sendResourceListChanged()` | Notify clients that resource list changed |

#### Tool Management

| Method | Description |
|--------|-------------|
| `addTool(name, description, inputSchema, handler)` | Add a tool handler |
| `suspend fun sendToolListChanged()` | Notify clients that tool list changed |

#### Prompt Management

| Method | Description |
|--------|-------------|
| `addPrompt(name, description, arguments, handler)` | Add a prompt template handler |
| `suspend fun sendPromptListChanged()` | Notify clients that prompt list changed |

#### Session Management

| Method | Description |
|--------|-------------|
| `suspend fun createSession(transport: Transport): ServerSession` | Create new session with transport |
| `fun onClose(handler: () -> Unit)` | Register close handler |

### Capabilities Configuration

#### Client Capabilities

```kotlin
val client = Client(
    clientInfo = Implementation("my-client", "1.0.0"),
    options = ClientOptions(
        capabilities = ClientCapabilities(
            roots = ClientCapabilities.Roots(listChanged = true),
            sampling = ClientCapabilities.Sampling()
        )
    )
)
```

#### Server Capabilities

```kotlin
val server = Server(
    serverInfo = Implementation("my-server", "1.0.0"),
    options = ServerOptions(
        capabilities = ServerCapabilities(
            resources = ServerCapabilities.Resources(
                subscribe = true,
                listChanged = true
            ),
            tools = ServerCapabilities.Tools(
                listChanged = true
            ),
            prompts = ServerCapabilities.Prompts(
                listChanged = true
            ),
            logging = ServerCapabilities.Logging()
        )
    )
)
```

---

## Testing

### Unit Testing Servers

```kotlin
import io.modelcontextprotocol.kotlin.sdk.test.InMemoryTransport
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MyServerTest {
    @Test
    fun `test calculator tool`() = runTest {
        // Arrange
        val server = Server(
            serverInfo = Implementation("test-server", "1.0.0"),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = true)
                )
            )
        )

        server.addTool("add", "Adds two numbers") { request ->
            val a = request.arguments?.get("a")?.jsonPrimitive?.double ?: 0.0
            val b = request.arguments?.get("b")?.jsonPrimitive?.double ?: 0.0
            CallToolResult(content = listOf(TextContent((a + b).toString())))
        }

        val transport = InMemoryTransport()
        server.createSession(transport.serverEnd)

        val client = Client(Implementation("test-client", "1.0.0"))
        client.connect(transport.clientEnd)

        // Act
        val result = client.callTool("add", mapOf("a" to 5, "b" to 3))

        // Assert
        val content = result.content.first() as TextContent
        assertEquals("8.0", content.text)
    }
}
```

### Test Utilities

```kotlin
dependencies {
    testImplementation("io.modelcontextprotocol:kotlin-sdk-test:0.8.1")
}
```

Available utilities:
- `InMemoryTransport` - Fast in-process transport for unit tests

---

## Production Deployment

### Error Handling

```kotlin
server.addTool("divide", "Divides two numbers") { request ->
    try {
        val a = request.arguments?.get("a")?.jsonPrimitive?.double
            ?: throw McpException.InvalidParams("Missing parameter 'a'")
        val b = request.arguments?.get("b")?.jsonPrimitive?.double
            ?: throw McpException.InvalidParams("Missing parameter 'b'")

        if (b == 0.0) {
            throw McpException.InvalidParams("Cannot divide by zero")
        }

        CallToolResult(content = listOf(TextContent((a / b).toString())))

    } catch (e: McpException) {
        throw e // Re-throw MCP protocol errors
    } catch (e: Exception) {
        logger.error(e) { "Tool execution failed" }
        throw McpException.InternalError("Calculation failed: ${e.message}")
    }
}
```

### Observability

<details>
<summary><b>Structured Logging</b></summary>

```kotlin
import io.github.oshai.kotlinlogging.KotlinLogging

val logger = KotlinLogging.logger {}

server.onInitialized {
    logger.info { "Server initialized and ready" }
}

server.addTool("example", "Example tool") { request ->
    logger.info { "Tool called with args: ${request.arguments}" }
    // Tool implementation
    CallToolResult(content = listOf(TextContent("Result")))
}
```
</details>

<details>
<summary><b>Metrics with Micrometer</b></summary>

```kotlin
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlin.system.measureTimeMillis

val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

server.addTool("example", "Example tool") { request ->
    val duration = measureTimeMillis {
        // Tool implementation
    }

    registry.counter("mcp.tools.calls", "tool", "example").increment()
    registry.timer("mcp.tools.duration", "tool", "example")
        .record(java.time.Duration.ofMillis(duration))

    CallToolResult(content = listOf(TextContent("Result")))
}
```
</details>

<details>
<summary><b>Distributed Tracing</b></summary>

```kotlin
import io.opentelemetry.api.GlobalOpenTelemetry

val tracer = GlobalOpenTelemetry.getTracer("mcp-server")

server.addTool("example", "Example tool") { request ->
    val span = tracer.spanBuilder("mcp.tool.example")
        .setAttribute("tool.name", "example")
        .startSpan()

    try {
        // Tool implementation
        CallToolResult(content = listOf(TextContent("Result")))
    } finally {
        span.end()
    }
}
```
</details>

### Deployment Patterns

<details>
<summary><b>Docker Container</b></summary>

```dockerfile
FROM eclipse-temurin:11-jre-alpine

WORKDIR /app
COPY build/libs/server.jar server.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "server.jar"]
```

```bash
docker build -t my-mcp-server .
docker run -p 8080:8080 -e API_KEY=secret my-mcp-server
```
</details>

<details>
<summary><b>Kubernetes Deployment</b></summary>

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mcp-server
spec:
  replicas: 3
  selector:
    matchLabels:
      app: mcp-server
  template:
    metadata:
      labels:
        app: mcp-server
    spec:
      containers:
      - name: mcp-server
        image: myorg/mcp-server:1.0.0
        ports:
        - containerPort: 8080
        env:
        - name: API_KEY
          valueFrom:
            secretKeyRef:
              name: mcp-secrets
              key: api-key
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
---
apiVersion: v1
kind: Service
metadata:
  name: mcp-server
spec:
  selector:
    app: mcp-server
  ports:
  - port: 80
    targetPort: 8080
  type: LoadBalancer
```
</details>

---

## Security

### Authentication

```kotlin
// Custom authentication middleware
class AuthenticatedServer(
    serverInfo: Implementation,
    options: ServerOptions,
    private val validateToken: (String) -> Boolean
) : Server(serverInfo, options) {

    init {
        onRequest { request ->
            val token = request.meta?.get("authorization")?.jsonPrimitive?.content
                ?: throw McpException.InvalidParams("Missing authorization")

            if (!validateToken(token)) {
                throw McpException.InvalidParams("Invalid authorization")
            }
        }
    }
}

// Client with authentication
val client = Client(Implementation("secure-client", "1.0.0"))
client.setDefaultMeta(mapOf(
    "authorization" to System.getenv("MCP_TOKEN")
))
```

### Tool Sandboxing

```kotlin
// Restrict dangerous operations
val ALLOWED_COMMANDS = setOf("ls", "cat", "grep")

server.addTool("execute", "Executes shell command") { request ->
    val command = request.arguments?.get("command")?.jsonPrimitive?.content
        ?: throw McpException.InvalidParams("Missing command")

    val parts = command.split(" ")
    if (parts.first() !in ALLOWED_COMMANDS) {
        throw McpException.InvalidParams("Command not allowed: ${parts.first()}")
    }

    // Execute in restricted environment
    val result = ProcessBuilder(parts).start().inputStream.bufferedReader().readText()
    CallToolResult(content = listOf(TextContent(result)))
}
```

### TLS Configuration

```kotlin
import io.ktor.network.tls.certificates.*
import java.io.File

embeddedServer(Netty, port = 8080) {
    // Production TLS
    sslConnector(
        keyStore = generateCertificate(
            file = File("keystore.jks"),
            keyAlias = "mcp-server",
            keyPassword = "changeit",
            jksPassword = "changeit"
        ),
        keyAlias = "mcp-server",
        keyStorePassword = { "changeit".toCharArray() },
        privateKeyPassword = { "changeit".toCharArray() }
    ) {
        port = 8443
    }

    mcp { /* server config */ }
}.start(wait = true)
```

---

## Troubleshooting

### Common Issues

<details>
<summary><b>"Could not find Ktor engine" Error</b></summary>

**Error:**
```
java.lang.ClassNotFoundException: io.ktor.client.engine.cio.CIOEngine
```

**Solution:**
Add a Ktor client engine dependency:
```kotlin
dependencies {
    implementation("io.ktor:ktor-client-cio:3.0.0")
}
```
</details>

<details>
<summary><b>Transport Connection Failures</b></summary>

**Symptoms:** `TimeoutException`, `ConnectionRefusedException`

**Checklist:**
- ✅ Server process is running before client connects
- ✅ Correct transport type (stdio, SSE, WebSocket)
- ✅ Firewall allows connections (for network transports)
- ✅ Correct host/port in client configuration
- ✅ Server logs show successful startup

**Debug stdio transport:**
```kotlin
// Server: Add debug output
fun main() = runBlocking {
    System.err.println("Server starting...")
    val server = Server(...)
    System.err.println("Server ready")
    // ... rest of setup
}
```

**Debug network transports:**
```bash
# Test SSE endpoint
curl http://localhost:8080/sse

# Test WebSocket endpoint
wscat -c ws://localhost:8080/mcp
```
</details>

<details>
<summary><b>Tools Not Discovered</b></summary>

**Issue:** `client.listTools()` returns empty list

**Solutions:**
1. Verify capabilities are configured:
```kotlin
ServerCapabilities(
    tools = ServerCapabilities.Tools(listChanged = true) // Required
)
```

2. Ensure tools are added before creating session:
```kotlin
server.addTool(...) // BEFORE this:
server.createSession(transport)
```

3. Check tool registration:
```kotlin
server.addTool("my-tool", ...) { request ->
    println("Tool called!") // Debug output
    CallToolResult(...)
}
```
</details>

<details>
<summary><b>Serialization Errors</b></summary>

**Error:**
```
kotlinx.serialization.SerializationException: Unexpected JSON token
```

**Common causes:**
- Invalid JSON in tool arguments
- Incorrect `inputSchema` type definitions
- Missing required fields in response objects

**Solution:**
Validate input schema matches arguments:
```kotlin
server.addTool(
    name = "example",
    inputSchema = ToolSchema(
        properties = buildJsonObject {
            putJsonObject("name") {
                put("type", "string") // Must match argument type
            }
        },
        required = listOf("name") // Mark required fields
    )
) { request ->
    // Validate manually if needed
    val name = request.arguments?.get("name")?.jsonPrimitive?.content
        ?: throw McpException.InvalidParams("Missing required field: name")

    CallToolResult(...)
}
```
</details>

### Getting Help

- 📚 **Specification**: [spec.modelcontextprotocol.io](https://spec.modelcontextprotocol.io)
- 💬 **Discord**: [MCP Community](https://discord.gg/modelcontextprotocol)
- 🐛 **Issues**: [GitHub Issues](https://github.com/modelcontextprotocol/kotlin-sdk/issues)
- 🔍 **Inspector**: [MCP Inspector](https://github.com/modelcontextprotocol/inspector) - Debug tool for testing servers

---

## Migration Guides

### From Direct LLM APIs

<details>
<summary><b>Migrating from Anthropic SDK</b></summary>

**Before (Direct API):**
```kotlin
val anthropic = Anthropic(apiKey)
val response = anthropic.messages.create(
    model = "claude-3-5-sonnet-20241022",
    maxTokens = 1024,
    tools = listOf(
        Tool.builder()
            .name("get_weather")
            .description("Get weather")
            .inputSchema(/* manual schema */)
            .build()
    ),
    messages = listOf(/* messages */)
)

// Manual tool execution
if (response.stopReason == StopReason.TOOL_USE) {
    val toolCall = response.content.first { it.isToolUse() }.toolUse()
    when (toolCall.name) {
        "get_weather" -> {
            val result = WeatherAPI.fetch(/* hard-coded */)
            // Send back to API...
        }
    }
}
```

**After (MCP):**
```kotlin
// 1. Create reusable MCP server (once)
val mcpServer = Server(...)
mcpServer.addTool("get_weather", ...) { request ->
    CallToolResult(content = listOf(TextContent(WeatherAPI.fetch(...))))
}

// 2. Connect MCP client
val mcpClient = Client(...)
mcpClient.connect(transport)

// 3. Tools auto-discovered
val tools = mcpClient.listTools() // Discovers get_weather

// 4. Use with Anthropic (or any LLM)
val anthropic = Anthropic(apiKey)
val response = anthropic.messages.create(
    tools = tools.toAnthropicFormat(), // Auto-converted
    // ... rest
)

// 5. Protocol-handled execution
if (response.stopReason == StopReason.TOOL_USE) {
    val toolCall = response.content.first { it.isToolUse() }.toolUse()
    val result = mcpClient.callTool(toolCall.name, toolCall.input)
}
```

**Benefits:**
- ✅ Tools work with any LLM (Claude, GPT-4, Gemini)
- ✅ Reusable across applications
- ✅ Shareable with team/community
- ✅ Standardized discovery and execution
</details>

### From TypeScript SDK

<details>
<summary><b>Key Differences</b></summary>

| Concept | TypeScript | Kotlin |
|---------|-----------|--------|
| **Async Model** | `async/await` | `suspend fun` + coroutines |
| **Error Handling** | `try/catch` + Promises | `try/catch` + structured concurrency |
| **Types** | Interfaces | Data classes |
| **Null Safety** | `Type \| undefined` | `Type?` with null safety |
| **Imports** | `import { Client } from '@modelcontextprotocol/sdk'` | `import io.modelcontextprotocol.kotlin.sdk.client.Client` |

**TypeScript:**
```typescript
import { Client } from '@modelcontextprotocol/sdk/client/index.js';

const client = new Client({
  name: "my-client",
  version: "1.0.0"
});

await client.connect(transport);
const tools = await client.listTools();
```

**Kotlin:**
```kotlin
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.Implementation

val client = Client(
    clientInfo = Implementation(
        name = "my-client",
        version = "1.0.0"
    )
)

client.connect(transport)
val tools = client.listTools()
```
</details>

---

## Examples

### Complete Examples

| Example | Description | Location |
|---------|-------------|----------|
| **Multiplatform Server** | MCP server with multiple transports (JVM, Wasm) | [kotlin-mcp-server](./samples/kotlin-mcp-server) |
| **Weather Server** | Real-world API integration with stdio transport | [weather-stdio-server](./samples/weather-stdio-server) |
| **AI Client** | Interactive client with Anthropic API integration | [kotlin-mcp-client](./samples/kotlin-mcp-client) |

### Community Servers

Explore pre-built MCP servers at [mcp.run](https://mcp.run):
- 📁 **File systems**: Google Drive, Dropbox, S3
- 🗄️ **Databases**: PostgreSQL, MongoDB, Supabase
- 🔍 **Search**: Brave, Perplexity, Exa
- 💻 **Development**: GitHub, Linear, Sentry
- 📊 **Data**: Snowflake, BigQuery, Airtable

> **Note:** Community servers may be in TypeScript or Python. This SDK can connect to any MCP server regardless of implementation language.

---

## Contributing

We welcome contributions! Please see:

- [Contributing Guide](CONTRIBUTING.md) - Development setup, testing, PR process
- [Code of Conduct](CODE_OF_CONDUCT.md) - Community guidelines
- [Architecture](ARCHITECTURE.md) - Codebase structure and design decisions

### Quick Start for Contributors

```bash
# Clone repository
git clone https://github.com/modelcontextprotocol/kotlin-sdk.git
cd kotlin-sdk

# Build
./gradlew build

# Run tests
./gradlew test

# Run samples
./gradlew :samples:weather-stdio-server:run
```

---

## License

This project is licensed under the MIT License—see the [LICENSE](LICENSE) file for details.

### Third-Party Dependencies

This SDK depends on:
- **Kotlin** (Apache 2.0)
- **Ktor** (Apache 2.0)
- **kotlinx-coroutines** (Apache 2.0)
- **kotlinx-serialization** (Apache 2.0)

---

## Additional Resources

- 📖 **MCP Specification**: [spec.modelcontextprotocol.io](https://spec.modelcontextprotocol.io)
- 🌐 **Official Website**: [modelcontextprotocol.io](https://modelcontextprotocol.io)
- 📦 **TypeScript SDK**: [@modelcontextprotocol/sdk](https://github.com/modelcontextprotocol/typescript-sdk)
- 🐍 **Python SDK**: [mcp](https://github.com/modelcontextprotocol/python-sdk)
- 🔍 **MCP Inspector**: [Debug tool](https://github.com/modelcontextprotocol/inspector)
- 💬 **Community Discord**: [Join here](https://discord.gg/modelcontextprotocol)

---

<p align="center">
  <strong>Built with ❤️ by the MCP community</strong>
</p>
