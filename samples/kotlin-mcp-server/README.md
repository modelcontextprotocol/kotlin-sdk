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

The server defaults to SSE mode with Ktor plugin on port 3001. You can customize the behavior using command-line arguments.

#### Default (SSE with Ktor plugin):

```bash
./gradlew run
```

#### Standard I/O mode:

```bash
./gradlew run --args="--stdio"
```

#### SSE with plain configuration:

```bash
./gradlew run --args="--sse-server 3001"
```

#### SSE with Ktor plugin (custom port):

```bash
./gradlew run --args="--sse-server-ktor 3002"
```

### Connecting to the Server

For SSE servers:
1. Start the server
2. Use the [MCP inspector](https://modelcontextprotocol.io/docs/tools/inspector) to connect to `http://localhost:<port>/sse`

For STDIO servers:
- Connect using an MCP client that supports STDIO transport

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
