---
name: mcp-docs
description: >-
  Look up the live MCP specification instead of answering from memory: the spec moves faster
  than training data (the 2026-07-28 revision removed initialize, sessions and
  server-to-client requests). Covers spec pages for every revision, changelogs, SEPs,
  schema.ts, conformance-test scenarios, and how the reference TypeScript/Python SDKs read
  the spec. Use whenever a task depends on what MCP requires: implementing, reviewing or
  debugging protocol behavior (tools, resources, prompts, sampling, elicitation, completion,
  caching, MRTR, subscriptions, server/discover), transports (stdio, Streamable HTTP, SSE,
  headers, cancellation), version and era negotiation, authorization/OAuth, error codes,
  message schemas, or a failing conformance check. Skip only for work with no protocol
  dependency (pure Kotlin refactoring, build, dependency bumps). When in doubt, use it.
argument-hint: "[topic | SEP number | spec page]"
allowed-tools:
  - WebFetch(domain:modelcontextprotocol.io)
  - WebFetch(domain:raw.githubusercontent.com)
  - mcp__mcp-docs__search_model_context_protocol
  - mcp__mcp-docs__query_docs_filesystem_model_context_protocol
---

# MCP docs

The MCP specification changes faster than training data. The 2026-07-28 revision, the newest when this skill was written, was a breaking rewrite: no `initialize` handshake, no protocol sessions, no GET stream, server-to-client requests replaced by multi round-trip results. Details recalled from memory are therefore often from the wrong revision. Look them up, quote what the spec says, and link the page.

## Pick the revision

Use the newest released revision unless the question is tied to another one: the user names a revision ("по 2025-11-25", "what does the draft say"), or the thing being asked about cites one (a conformance check's `specReferences`, a SEP). `latest` in a spec URL redirects to the newest revision (`curl -sI https://modelcontextprotocol.io/specification/latest` names it), so you never need to guess its date.

Give the verdict from that revision, even when the code at hand implements an older one: the SDK is being brought up to the newest revision, so "is this allowed by the spec" means allowed by the newest one. The SDK implements `LATEST_PROTOCOL_VERSION` from `kotlin-sdk-core/src/commonMain/kotlin/io/modelcontextprotocol/kotlin/sdk/types/common.kt`. When that revision says something different on the point in question, add a short block after the main answer, labelled with its revision and carrying its key quote, so the user sees why the code behaves as it does today. Put the newest revision first in a summary too, and go deeper into the older text only if the user asks.

Revisions also remove things, 2026-07-28 more than any before it, and that changes how to read what you find:

- If the topic does not exist in the revision you answer from (in 2026-07-28: sessions, `initialize`, `ping`, `resources/subscribe`, `logging/setLevel`, SSE resumability, ...), the answer is that it was removed. Say what replaced it, cite `changelog.md`, and name the last revision that defined it so the user can ask for that one. Don't fill the gap from memory.
- Pages of removed features redirect without warning: `specification/2026-07-28/basic/lifecycle.md` lands on `basic/versioning.md`. If the page you got is not about what you asked for, the feature probably moved or was removed.

## Where things live

Every page on modelcontextprotocol.io is served as raw markdown when you append `.md`: verbatim, and about a twentieth of the HTML size.

| What | Where |
|---|---|
| Spec page | `https://modelcontextprotocol.io/specification/<rev>/<page>.md`, where `<rev>` is `latest`, `2026-07-28`, `2025-11-25`, `2025-06-18`, `2025-03-26`, `2024-11-05` or `draft` |
| What a revision changed | `specification/<rev>/changelog.md`, removals included; from 2026-07-28 on, `specification/<rev>/deprecated.md` lists deprecated features, which are still in the spec |
| SEP | `https://modelcontextprotocol.io/seps/<number>.md` (redirects to the full name). Some merged SEPs have no page: on a 404, check `gh pr view <number> -R modelcontextprotocol/modelcontextprotocol --json state,labels` rather than describing it from memory |
| Extensions (outside the core spec) | `extensions/overview.md`; Tasks `extensions/tasks/overview.md`, MCP Apps `extensions/apps/overview.md`, auth `extensions/auth/*.md` |
| Schema | `https://raw.githubusercontent.com/modelcontextprotocol/modelcontextprotocol/main/schema/<rev>/schema.ts`, see below |
| Every page of every revision | `https://modelcontextprotocol.io/llms.txt` (~50 KB) |

Pages of the 2026-07-28 revision, so you can usually go straight to the right one:

- `basic/index`: the `_meta` request envelope, `resultType`, error codes
- `basic/versioning`: version negotiation, legacy and modern eras, compatibility matrix
- `basic/patterns/`: `mrtr`, `subscriptions`, `cancellation`, `progress`
- `basic/transports/`: `index`, `stdio`, `streamable-http`
- `basic/authorization/`: `index`, `authorization-server-discovery`, `client-registration`, `security-considerations`
- `server/`: `index`, `discover`, `tools`, `resources`, `prompts`; `server/utilities/`: `caching`, `completion`, `logging`, `pagination`
- `client/`: `roots`, `sampling`, `elicitation`

Older revisions are laid out differently (`basic/lifecycle`, a single `basic/transports` page, `basic/utilities/*`), and the next revision may reshuffle again. When a guessed path 404s, look it up in `llms.txt`.

## How to fetch

Use the best tool you have:

1. **Docs MCP server**, if configured: `search_model_context_protocol` to find pages and `query_docs_filesystem_model_context_protocol` to read them and match exactly, e.g. `rg -n "MUST" /specification/2026-07-28/basic/transports/streamable-http.mdx` or `head -150 <page>.mdx`. Paths there end in `.mdx`, and output is capped at 30 KB per call. In Claude Code the tools are named `mcp__<server>__<tool>` (`mcp__plugin_<plugin>_<server>__<tool>` when a plugin provides them) and are usually deferred: load them with ToolSearch, e.g. the query `model_context_protocol`. Leave the server's `submit_feedback` tool alone unless the user asks to report a docs problem.
2. **WebFetch on `.md` URLs.** WebFetch is lossy by design: for most pages it returns a small model's answer to your prompt, not the page. Spec `.md` pages often do come back verbatim, which you can tell from the raw markdown (headings, code fences, `<Note>` tags). Anything that reads like a summary is one: don't quote it, and don't conclude from it that a page doesn't mention something; fetch again with a narrower prompt, or use curl. Text past 100,000 characters is cut off; the result says so, and `offset` continues.
3. **`curl -sL <url>`** in Bash, when you need exact text and Bash has network access. Pipe it through `grep -n` or `sed -n` to pull out only the lines you need from a large file.

Don't load `llms-full.txt` (2.5 MB) or the rendered `schema.md` (450-700 KB) whole.

## Beyond the spec pages

**Schema.** For field names, types, optionality and allowed values, the revision's `schema.ts` is the source of truth; prose pages paraphrase and leave things out. Grep for the declaration (`grep -n "export interface CallToolResult" -A 40`) and read its doc comment as well, because normative constraints often live only there. Follow `extends` to the parent types: inherited fields count. `schema.json` sits next to it if you need JSON Schema.

**Conformance scenarios.** When a conformance check fails, read the check: it shows exactly what is sent and what is asserted, and lists the spec sections it enforces. The runner version is pinned as `CONFORMANCE_VERSION` in `conformance-test/run-conformance.sh`. Scenario sources are in `modelcontextprotocol/conformance` at tag `v<version>`, under `src/scenarios/server/` and `src/scenarios/client/` (auth in `src/scenarios/client/auth/`). Read the pinned tag, not `main`, which runs ahead of what the SDK is tested against. Scenario ids don't always match file names (`elicitation-sep1034-client-defaults` lives in `elicitation-defaults.ts`), so list the directory or grep for the id:

```bash
gh api "repos/modelcontextprotocol/conformance/contents/src/scenarios/server?ref=v<version>" --jq '.[].name'
curl -sL "https://raw.githubusercontent.com/modelcontextprotocol/conformance/v<version>/src/scenarios/server/<file>.ts"
```

**Reference SDKs.** Where the spec leaves a choice open (MAY-level behavior, values it doesn't specify, how to detect which era a peer speaks), check what `modelcontextprotocol/typescript-sdk` and `modelcontextprotocol/python-sdk` do. Both describe their dual-era behavior in `docs/protocol-versions.md`; for code, `gh search code '<term>' --repo modelcontextprotocol/typescript-sdk`. Present it as that SDK's choice, not as a spec requirement.

## Answering

- For a question, open with the short answer in a few lines, then give the quotes and links behind it.
- Link every page you relied on, with the concrete revision in the URL (`/specification/2026-07-28/...`, not `latest`), so the link keeps saying the same thing after the next release.
- Quote normative sentences verbatim and keep the RFC 2119 keyword as written: whether something is MUST, SHOULD or MAY is usually the point of the question.
- Keep what the spec requires, what an SDK chose to do, and what you recommend visibly apart.
- When more than one revision is in play, say which one each statement comes from.
- Extract what the task needs; don't paste whole pages.
