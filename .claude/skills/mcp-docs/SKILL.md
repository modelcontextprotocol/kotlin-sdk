---
name: mcp-docs
description: >
  Fetch live MCP specification and docs from modelcontextprotocol.io. Training data may be
  outdated — default to using this skill when the query touches MCP protocol in any way.
  Trigger for: implementing or debugging MCP features (sampling, elicitation, completion, tools,
  resources, prompts, transports); SEPs or spec requirements; MCP transport behavior (SSE,
  Streamable HTTP, reconnection, retry); authorization/OAuth; conformance test failures;
  capability negotiation; message schemas. Skip only for pure Kotlin, build system, or
  refactoring tasks with zero MCP protocol dependency. When in doubt, trigger.
user_invocable: true
---

# MCP Documentation Skill

Provides access to the official MCP specification and docs from modelcontextprotocol.io. The spec evolves faster than training data — always consult live documentation for protocol specifics like message schemas, required fields, capability negotiation, error handling, and transport requirements.

## When to use

**Proactively** (without being asked):
- Implementing a feature defined in the MCP spec
- Reviewing code that implements MCP protocol behavior
- Fixing conformance test failures

**On demand** (user asks or invokes `/mcp-docs`):
- Questions about how a specific MCP feature works
- Clarifying spec ambiguities
- Looking up SEPs (Specification Enhancement Proposals)

## How to fetch documentation

### Strategy: MCP tool first, llms.txt fallback

**Step 1 — Try the MCP search tool.** If `search_model_context_protocol` is available, use it:

```
search_model_context_protocol(query: "<your search query>")
```

One call, semantically ranked results. Fastest path.

**Step 2 — Fallback to llms.txt + WebFetch.** If the MCP tool is not available:

1. Fetch the documentation index:
   ```
   WebFetch("https://modelcontextprotocol.io/llms.txt", "Return the full content as-is")
   ```

2. Identify the relevant page URL(s) from the index. Key sections:
   - **Specification**: `specification/...` — protocol details, message formats, transports, lifecycle
   - **SEPs**: `seps/...` — specification enhancement proposals
   - **Learning**: `docs/learn/...` — architecture, concepts
   - **Development**: `docs/develop/...` — building clients/servers

3. Fetch the specific page:
   ```
   WebFetch("<page-url>", "Return the full content of this documentation page")
   ```

### Version handling

Always use the latest spec version by default. Only use a specific version or draft when the user explicitly requests it (e.g., "check the 2025-03-26 spec" or "what does the draft say").

### Search guidance

- Protocol message formats, required fields, capability negotiation → **Specification** section
- Feature motivation, design rationale → relevant **SEP**
- High-level concepts and architecture → **Learning** resources
- Use your judgment on query count. A field-level question may need one page; implementing a feature may need 2-3.

## Presenting information

- **Always cite sources.** Include links to spec pages used: `(source: <url>)`
- **Spec over training data** for specific protocol details (schemas, fields, error codes, capabilities). Training data is fine for general MCP concepts.
- **Preserve RFC 2119 language.** The spec uses MUST/SHOULD/MAY — keep this distinction when explaining requirements.
- **Be concise.** Focus on the details relevant to the user's task. Avoid dumping entire spec pages — extract what matters for the specific question or implementation at hand.
