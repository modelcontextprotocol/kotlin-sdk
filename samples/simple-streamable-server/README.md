# Simple Streamable HTTP Server

A minimal MCP server using the recommended Streamable HTTP transport with optional Bearer token authentication.

## Overview

This sample demonstrates a Streamable HTTP MCP server built with Ktor. It exposes tools, a prompt
template, and a resource over HTTP, and optionally supports Bearer token authentication. The
`multi-greet` tool showcases server-to-client logging notifications with streaming delays.

## Prerequisites

- JDK 17+

## Build & Run

### Without authentication

```shell
./gradlew :samples:simple-streamable-server:run
```

The server starts on `http://localhost:3001/mcp` by default.

Connect with the [MCP Inspector](https://modelcontextprotocol.io/docs/tools/inspector):

```shell
npx @modelcontextprotocol/inspector
```

In the Inspector UI, select **Streamable HTTP** transport and enter `http://localhost:3001/mcp`.

Pass a port number as an argument to change it:

```shell
./gradlew :samples:simple-streamable-server:run --args="8080"
```

### With authentication

```shell
MCP_AUTH_TOKEN=my-secret ./gradlew :samples:simple-streamable-server:run --args="--auth"
```

When `--auth` is passed, clients must include an `Authorization: Bearer <token>` header.
The token is read from the `MCP_AUTH_TOKEN` environment variable (falls back to a hardcoded
`demo-token-12345` if unset).

### Authentication caveats

This sample is intended **for demonstration only**. In production you should:

- Use a proper identity provider instead of a hardcoded fallback token.
- Restrict CORS origins — the sample uses `anyHost()`.
- Serve the endpoint over HTTPS.

## MCP Capabilities

### Tools

| Name | Description |
|------|-------------|
| `greet` | Returns a greeting for a given `name`. |
| `multi-greet` | Sends logging notifications between delayed greetings, demonstrating streaming. |

### Prompts

| Name | Description |
|------|-------------|
| `greeting-template` | Generates a friendly greeting message for a given `name`. |

### Resources

| Name | URI | Description |
|------|-----|-------------|
| `Default Greeting` | `https://example.com/greetings/default` | Returns a static "Hello, world!" text. |