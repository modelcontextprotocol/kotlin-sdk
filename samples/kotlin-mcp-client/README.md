# Kotlin MCP Client

This project demonstrates how to build a Model Context Protocol (MCP) client in Kotlin that interacts with an MCP server
via a STDIO transport layer while leveraging Anthropic's API for natural language processing. The client uses the MCP
Kotlin SDK to communicate with an MCP server that exposes various tools, and it uses Anthropic's API to process user
queries and integrate tool responses into the conversation.

For more information about the MCP SDK and protocol, please refer to
the [MCP documentation](https://modelcontextprotocol.io/introduction).

## Prerequisites

- **Java 17 or later**
- **Gradle** (or the Gradle wrapper provided with the project)
- An Anthropic API key set in your environment variable `ANTHROPIC_API_KEY`
- Basic understanding of MCP concepts and Kotlin programming

## Overview

The client application performs the following tasks:

- **Connecting to an MCP server** —
  launches an MCP server process (implemented in JavaScript, Python, or Java) using STDIO transport.
  It connects to the server, retrieves available tools, and converts them to Anthropic’s tool format.
- **Processing queries** — 
  accepts user queries, sends them to Anthropic’s API along with the registered tools, and handles responses.
  If the response indicates a tool should be called, it invokes the corresponding MCP tool and continues the
  conversation based on the tool’s result.
- **Interactive chat loop** —
  runs an interactive command-line loop, allowing users to continuously submit queries and receive responses.

## Building and Running

Use the Gradle wrapper to build the application. In a terminal, run:

```shell
./gradlew clean build -x test
```

To run the client, execute the jar file and provide the path to your MCP server script.

To run the client with any MCP server:

```shell
java -jar build/libs/<your-jar-name>.jar path/to/server.jar # jvm server
java -jar build/libs/<your-jar-name>.jar path/to/server.py # python server
java -jar build/libs/<your-jar-name>.jar path/to/build/index.js # node server
```

> [!NOTE]
> The client uses STDIO transport, so it launches the MCP server as a separate process.
> Ensure the server script is executable and is a valid `.js`, `.py`, or `.jar` file.

## Configuration for Anthropic

Ensure your Anthropic API key is available in your environment:

```shell
export ANTHROPIC_API_KEY=your_anthropic_api_key_here
```

The client uses `AnthropicOkHttpClient.fromEnv()` to automatically load the API key from `ANTHROPIC_API_KEY` and
`ANTHROPIC_AUTH_TOKEN` environment variables.

## Additional Resources

- [MCP Specification](https://spec.modelcontextprotocol.io/)
- [Kotlin MCP SDK](https://github.com/modelcontextprotocol/kotlin-sdk)
- [Anthropic Java SDK](https://github.com/anthropics/anthropic-sdk-java/tree/main)
