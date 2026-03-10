# Kotlin MCP Client

An interactive CLI client that connects to any MCP server over STDIO and pipes queries through
Anthropic's Claude API.

## Overview

This sample demonstrates a complete MCP client workflow: launching an MCP server as a subprocess,
discovering its tools, converting them to Anthropic's tool format, and running an interactive chat
loop where Claude can call server tools on behalf of the user.

## Prerequisites

- JDK 17+
- An `ANTHROPIC_API_KEY` environment variable set with a valid Anthropic API key
- An MCP server script to connect to (`.js`, `.py`, or `.jar`)

## Build & Run

Run the client, passing the path to an MCP server:

```shell
# Connect to a JVM server
./gradlew run --args="path/to/server.jar"

# Connect to a Python server
./gradlew run --args="path/to/server.py"

# Connect to a Node.js server
./gradlew run --args="path/to/build/index.js"
```

> [!NOTE]
> The client uses STDIO transport, so it launches the MCP server as a subprocess.
> Ensure the server script is executable and is a valid `.js`, `.py`, or `.jar` file.

## MCP Capabilities

From the **client** perspective, this sample demonstrates:

- **Tool discovery** — lists tools from the connected server and converts them to Anthropic's tool
  format.
- **Tool invocation** — when Claude's response requests a tool call, the client invokes the
  corresponding MCP tool and feeds the result back into the conversation.

## Additional Resources

- [MCP Specification](https://modelcontextprotocol.io/specification/latest)
- [Kotlin MCP SDK](https://github.com/modelcontextprotocol/kotlin-sdk)
- [Anthropic Java SDK](https://github.com/anthropics/anthropic-sdk-java)
