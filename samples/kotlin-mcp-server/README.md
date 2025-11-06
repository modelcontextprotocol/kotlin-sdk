# MCP Kotlin Server Sample

A sample implementation of an MCP (Model Context Protocol) server in Kotlin that demonstrates different server
configurations and transport methods.

## Features

- Multiple server operation modes:
    - Standard I/O server
    - SSE (Server-Sent Events) server with plain configuration
    - SSE server using Ktor plugin
- Built-in capabilities for:
    - Prompts management
    - Resources handling
    - Tools integration

## Getting Started

### Running the Server

The server defaults [STDIO transport](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports#stdio). 

You can customize the behavior using command-line arguments.
Logs are printed to [./build/stdout.log](./build/stdout.log)

#### Standard I/O mode (STDIO):

```bash
./gradlew clean build
```
Use the [MCP inspector](https://modelcontextprotocol.io/docs/tools/inspector) 
to connect to MCP via STDIO (Click the "▶️ Connect" button):

```shell
npx @modelcontextprotocol/inspector --config mcp-inspector-config.json --server stdio-server
```

#### SSE with plain configuration:

**NB!: 🐞 This configuration may not work ATM**

```bash
./gradlew run --args="--sse-server 3001"
```
or
```shell
./gradlew clean build
java -jar ./build/libs/kotlin-mcp-server-0.1.0-all.jar --sse-server 3001
```

Use the [MCP inspector](https://modelcontextprotocol.io/docs/tools/inspector) 
to connect to `http://localhost:3002/` via SSE Transport (Click the "▶️ Connect" button):
```shell
npx @modelcontextprotocol/inspector --config mcp-inspector-config.json --server sse-server
```

#### SSE with Ktor plugin:

```bash
./gradlew run --args="--sse-server-ktor 3002"
```
or
```shell
./gradlew clean build
java -jar ./build/libs/kotlin-mcp-server-0.1.0-all.jar --sse-server-ktor 3002
```

Use the [MCP inspector](https://modelcontextprotocol.io/docs/tools/inspector) 
to connect to `http://localhost:3002/` via SSE transport (Click the "▶️ Connect" button):
```shell
npx @modelcontextprotocol/inspector --config mcp-inspector-config.json --server sse-ktor-server
```

## Server Capabilities

- **Prompts**: Supports prompt management with list change notifications
- **Resources**: Includes subscription support and list change notifications
- **Tools**: Supports tool management with list change notifications

## Implementation Details

The server is implemented using:
- Ktor for HTTP server functionality (SSE modes)
- Kotlin coroutines for asynchronous operations
- SSE for real-time communication in web contexts
- Standard I/O for command-line interface and process-based communication

## Example Capabilities

The sample server demonstrates:
- **Prompt**: "Kotlin Developer" - helps develop small Kotlin applications with a configurable project name
- **Tool**: "kotlin-sdk-tool" - a simple test tool that returns a greeting
- **Resource**: "Web Search" - a placeholder resource demonstrating resource handling
