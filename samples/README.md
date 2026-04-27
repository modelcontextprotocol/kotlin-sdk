# Kotlin MCP SDK Samples

Runnable projects demonstrating MCP server and client implementations with the
[Kotlin MCP SDK](https://github.com/modelcontextprotocol/kotlin-sdk).
For background on the protocol itself, see the [MCP documentation](https://modelcontextprotocol.io/introduction).

## Overview

| Sample                                                 | Type              | Transport       | MCP Features                       |
|--------------------------------------------------------|-------------------|-----------------|------------------------------------|
| [simple-streamable-server](./simple-streamable-server) | Server            | Streamable HTTP | Tools, Resources, Prompts, Logging |
| [kotlinlang-mcp-server](./kotlinlang-mcp-server)       | Server            | Streamable HTTP | Tools                              |
| [kotlin-mcp-server](./kotlin-mcp-server)               | Server            | STDIO, SSE      | Tools, Resources, Prompts          |
| [weather-stdio-server](./weather-stdio-server)         | Server            | STDIO           | Tools                              |
| [kotlin-mcp-client](./kotlin-mcp-client)               | Client            | STDIO           | Tool discovery & invocation        |
| [notebooks](./notebooks)                               | Client (Notebook) | Streamable HTTP | Tool discovery & invocation        |

## Getting Started

- **Building a server?** Start with [simple-streamable-server](./simple-streamable-server) — it
  uses the recommended Streamable HTTP transport and covers tools, resources, prompts, and logging.
- **Building a client?** Open the [notebooks](./notebooks) sample for a step-by-step walkthrough,
  or see [kotlin-mcp-client](./kotlin-mcp-client) for a full CLI client with Anthropic API
  integration.

## Samples

### Simple Streamable HTTP Server

A minimal Streamable HTTP server with optional Bearer token authentication. Demonstrates tools
(`greet`, `multi-greet`), a prompt template, a resource, and server-to-client logging notifications.
[Read more →](./simple-streamable-server)

### Kotlinlang MCP Server

A Streamable HTTP server that exposes the official Kotlin documentation (kotlinlang.org) to LLM
clients — full-text search via Algolia and page retrieval in markdown. Demonstrates wrapping a real
external API in an MCP server with in-memory caching.
[Read more →](./kotlinlang-mcp-server)

### Kotlin MCP Server

A multi-transport server supporting STDIO, SSE (plain), and SSE (Ktor plugin). Useful for exploring
different transport modes side by side.
[Read more →](./kotlin-mcp-server)

### Weather STDIO Server

A focused STDIO server that exposes weather forecast and alert tools backed by the weather.gov API.
Includes Claude Desktop integration instructions.
[Read more →](./weather-stdio-server)

### Kotlin MCP Client

An interactive CLI client that connects to any MCP server over STDIO and routes queries through
Anthropic's Claude API, bridging MCP tools with LLM conversations.
[Read more →](./kotlin-mcp-client)

### MCP Client Notebook

A Kotlin notebook that connects to a remote MCP server via Streamable HTTP and demonstrates ping,
tool listing, and tool invocation — all in an interactive cell-by-cell format.
[Read more →](./notebooks)
