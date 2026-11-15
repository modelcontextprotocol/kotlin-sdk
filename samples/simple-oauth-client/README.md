# Simple OAuth Client

A minimal MCP client that connects to an OAuth-protected server over Streamable HTTP.

## Overview

This sample shows how a command-line client signs in to a protected MCP server, following the
[MCP authorization specification](https://modelcontextprotocol.io/specification/2025-06-18/basic/authorization).
When the server answers `401 Unauthorized`, the client:

1. Discovers the server's protected resource metadata
   ([RFC 9728](https://datatracker.ietf.org/doc/html/rfc9728)), using the `resource_metadata` hint in the
   `WWW-Authenticate` header.
2. Discovers the authorization server metadata ([RFC 8414](https://datatracker.ietf.org/doc/html/rfc8414),
   with OpenID Connect discovery as a fallback).
3. Registers itself as a public client with dynamic client registration
   ([RFC 7591](https://datatracker.ietf.org/doc/html/rfc7591)), unless a client ID is configured.
4. Opens the browser for sign-in with PKCE (`S256`) and a `state` check, and receives the
   authorization code on a loopback redirect URI (`http://127.0.0.1:<port>/callback`).
5. Exchanges the code for an access token, bound to the server with the `resource` parameter
   ([RFC 8707](https://datatracker.ietf.org/doc/html/rfc8707)), and retries the request with it.

It then lists the server's tools and, optionally, calls one.

| File                          | What it does                                                    |
|-------------------------------|-----------------------------------------------------------------|
| `OAuthFlow.kt`                | Discovery, registration, PKCE and the token exchange            |
| `LoopbackCallbackServer.kt`   | Receives the authorization redirect on `127.0.0.1`              |
| `AuthorizedClient.kt`         | Ktor `HttpClient` that adds the token and signs in on `401`     |
| `main.kt`                     | Connects with `mcpStreamableHttp`, lists and calls tools        |

## Prerequisites

- JDK 17+
- An MCP server protected with OAuth that supports dynamic client registration (or a client
  registered ahead of time, see [Configuration](#configuration)).

## Build & Run

```shell
./gradlew run --args="http://localhost:3000/mcp"
```

The client prints the authorization URL and opens it in your browser. After you sign in, it lists the
server's tools. Pass a tool name as a second argument to call it without arguments:

```shell
./gradlew run --args="http://localhost:3000/mcp my-tool"
```

### Configuration

| Environment variable | Description                                                                 |
|----------------------|-----------------------------------------------------------------------------|
| `MCP_CLIENT_ID`      | Use a pre-registered client instead of dynamic client registration          |
| `MCP_CLIENT_SECRET`  | Secret of the pre-registered client, if it has one                          |
| `MCP_CALLBACK_PORT`  | Fixed port for the redirect URI (default: a free port), for registered URIs |

## Tests

`OAuthClientTest` runs the whole flow against an in-process MCP server guarded by a minimal
authorization server:

```shell
./gradlew test
```

## Caveats

This sample is intended **for demonstration only**. It keeps the access token in memory, does not
refresh tokens, and does not persist client registrations. A production client should store tokens
securely, use refresh tokens, and handle token expiry and scope step-up (`403 insufficient_scope`).
