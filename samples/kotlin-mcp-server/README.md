# MCP Kotlin Server Sample

A sample implementation of an MCP (Model Communication Protocol) server in Kotlin that demonstrates different server
configurations and transport methods for both JVM and WASM targets.

## Features

- Multiple server operation modes:
    - Standard I/O server (JVM only)
      - SSE (Server-Sent Events) server with plain configuration (JVM, WASM)
      - SSE server using Ktor plugin (JVM, WASM)
- Multiplatform support
- Built-in capabilities for:
    - Prompts management
    - Resources handling
    - Tools integration

## Getting Started

### Running the Server

You can run the server on the JVM or using Kotlin/WASM on Node.js.


#### JVM:

To run the server on the JVM (defaults to SSE mode with Ktor plugin on port 3001):

```bash
./gradlew runJvm
```

#### WASM:

To run the server using Kotlin/WASM on Node.js (defaults to SSE mode with Ktor plugin on port 3001):

```bash
./gradlew wasmJsNodeDevelopmentRun
```

### Connecting to the Server

For servers on JVM or WASM:
1. Start the server
2. Use the [MCP inspector](https://modelcontextprotocol.io/docs/tools/inspector) to connect to `http://localhost:<port>/sse`

## Server Capabilities

- **Prompts**: Supports prompt management with list change notifications
- **Resources**: Includes subscription support and list change notifications
- **Tools**: Supports tool management with list change notifications

## Implementation Details

The server is implemented using:
- Ktor for HTTP server functionality
- Kotlin coroutines for asynchronous operations
- SSE for real-time communication
- Standard I/O for command-line interface
- Common Kotlin code shared between JVM and WASM targets
