# Roots Demo

A minimal sample demonstrating the MCP **Roots** capability using in-memory
[ChannelTransport](../../kotlin-sdk-testing/src/commonMain/kotlin/io/modelcontextprotocol/kotlin/sdk/testing/ChannelTransport.kt).

## Overview

[Roots](https://modelcontextprotocol.io/docs/concepts/roots) allow an MCP client to
expose filesystem locations to a server. The server can request the list of roots and
receive notifications when that list changes.

This sample shows the full roots lifecycle:

1. **Client declares roots capability** — the client advertises `roots` with
   `listChanged = true` during initialization.
2. **Client registers roots** — `addRoot(uri, name)` adds roots that the server can
   discover.
3. **Server queries roots** — `serverSession.listRoots()` sends a `roots/list`
   request to the client.
4. **Client sends change notification** — after dynamically adding or removing roots,
   the client calls `sendRootsListChanged()` so the server knows to re-fetch.
5. **Server reacts to changes** — the server listens for
   `notifications/roots/list_changed` and re-queries the updated root list.

## Prerequisites

- JDK 17+

## Build & Run

```shell
./gradlew run
```

The program connects a client and server in-process using `ChannelTransport` and
prints the roots exchange to the console. No external server or network is required.

## MCP Capabilities

### Client

| Capability | Details |
|-----------|---------|
| `roots` | Advertised with `listChanged = true`. Registers project directories and notifies the server on changes. |

### Server

Demonstrates consuming the roots capability by calling `listRoots()` and listening for
`notifications/roots/list_changed`.

## Additional Resources

- [MCP Roots Specification](https://modelcontextprotocol.io/docs/concepts/roots)
- [Kotlin MCP SDK](https://github.com/modelcontextprotocol/kotlin-sdk)