# Kotlinlang MCP Server

A Streamable HTTP MCP server that exposes the official
[Kotlin documentation](https://kotlinlang.org/docs/) to LLM clients — full-text search via
[Algolia](https://www.algolia.com/) plus page retrieval in markdown through kotlinlang.org's
`_llms` endpoints.

## Overview

This sample demonstrates an MCP server that wraps a real external documentation source. It uses the
recommended Streamable HTTP transport, supports multiple concurrent client sessions, and caches
responses in memory to reduce upstream load. The server is intentionally read-only — it exposes two
tools, no prompts, no resources.

## Prerequisites

- JDK 21+
- Algolia credentials for `kotlinlang.org` (exported as environment variables, see
  [Configuration](#configuration))

## Build & Run

```shell
export ALGOLIA_APP_ID=...
export ALGOLIA_API_KEY=...
export ALGOLIA_INDEX_NAME=...

./gradlew run
```

The server starts on `http://localhost:8080/mcp` by default.

Connect with the [MCP Inspector](https://modelcontextprotocol.io/docs/tools/inspector):

```shell
npx @modelcontextprotocol/inspector
```

In the Inspector UI, select **Streamable HTTP** transport and enter `http://localhost:8080/mcp`.

## MCP Capabilities

### Tools

| Name                  | Description                                                                                                                            |
|-----------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| `search_kotlinlang`   | Full-text search across Kotlin documentation. Returns up to 5 results filtered to `/docs/` pages. Responses cached for 10 minutes.     |
| `get_kotlinlang_page` | Fetches a documentation page as markdown via kotlinlang.org's `_llms` endpoint. Accepts a path relative to `/docs/`. Cached for 1 hour. |

Example paths accepted by `get_kotlinlang_page`: `coroutines-overview`,
`multiplatform/compose-multiplatform-and-jetpack-compose`. Leading/trailing slashes and an optional
`.html` suffix are normalized before resolution.

## Configuration

| Variable             | Required | Default   | Description              |
|----------------------|----------|-----------|--------------------------|
| `ALGOLIA_APP_ID`     | yes      | —         | Algolia application ID   |
| `ALGOLIA_API_KEY`    | yes      | —         | Algolia search API key   |
| `ALGOLIA_INDEX_NAME` | yes      | —         | Algolia index name       |
| `SERVER_PORT`        | no       | `8080`    | HTTP server port         |
| `SERVER_HOST`        | no       | `0.0.0.0` | HTTP server bind address |

The server does not provide fallback values for `ALGOLIA_*`. Startup fails if any of those
variables are missing.

## Docker

Multi-stage build with Alpine-based images. Runs as a non-root user.

```shell
docker build -t kotlinlang-mcp-server .

docker run \
  -p 8080:8080 \
  -e ALGOLIA_APP_ID=... \
  -e ALGOLIA_API_KEY=... \
  -e ALGOLIA_INDEX_NAME=... \
  kotlinlang-mcp-server
```

## Connecting an MCP client

Any MCP client that supports Streamable HTTP transport can connect:

```json
{
  "mcpServers": {
    "kotlinlang": {
      "url": "http://localhost:8080/mcp"
    }
  }
}
```

## Limitations

This sample is intended **for demonstration only**. Before production use, consider:

- **In-memory cache only** — cached data is lost on server restart.
- **No authentication or authorization** — run behind a trusted proxy or restrict network access.
- **No rate limiting** on outgoing requests to Algolia and kotlinlang.org.
- **Permissive CORS** (`anyHost()`) — restrict to specific origins for any non-local deployment.
- **Streamable HTTP only** — no STDIO transport for local-only usage.
- **Tools only** — no MCP resources or prompts are exposed.
