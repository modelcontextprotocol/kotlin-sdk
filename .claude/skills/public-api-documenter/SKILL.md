---
name: public-api-documenter
description: >
  Audit public API for missing KDoc. Use when asked to check documentation
  coverage, find undocumented public members, or before a release to ensure
  all public API has KDoc. Trigger for: "check kdoc", "missing docs",
  "audit documentation", "public api docs". Skip for: test code, internal
  modules, non-Kotlin files.
allowed-tools: Bash(python3 ${CLAUDE_SKILL_DIR}/scripts/find-missing-kdoc.py*)
---

# Public API Documenter

Audit the public API surface for missing KDoc documentation.

## How to run

```bash
python3 ${CLAUDE_SKILL_DIR}/scripts/find-missing-kdoc.py
python3 ${CLAUDE_SKILL_DIR}/scripts/find-missing-kdoc.py --verbose
```

## When to use

- Before a release, to verify all public API members are documented
- During code review, when new public API is introduced
- When asked to "check docs", "audit kdoc", or "find missing documentation"

## When to skip

- Test code or internal modules — only public ABI matters
- Non-Kotlin files or documentation-only changes
- When the user is asking about code behavior, not documentation coverage

## Exclusions

The script automatically skips members that should not have KDoc:

- **`override` methods/properties** — KDoc belongs on the interface or superclass
- Auto-generated members: `equals`, `hashCode`, `toString`, `copy`, `componentN`, `serializer`, getters/setters
- Synthetic and mangled members (inline class name-mangled signatures)
- `Companion`, `DefaultImpls`, `$$serializer` inner classes

## KDoc guidelines

- Wrap KDoc to keep lines under 100 chars; wrap to multi-line if longer
- Keep docs concise — focus on "what" and "why", not implementation details
- Do NOT add KDoc to `override` members; document the contract on the declaring interface/superclass
- For DSL builder internal `build()` methods (`@PublishedApi`), a one-liner is sufficient
- For transport `start()`/`close()`/`send()`, document the lifecycle contract on the `Transport` interface
- Don't add KDoc to data class auto-generated members

## Modules checked

- `kotlin-sdk-core` — Protocol types, JSON-RPC, Transport abstraction
- `kotlin-sdk-client` — MCP client implementation
- `kotlin-sdk-server` — MCP server implementation
- `kotlin-sdk-testing` — In-memory test transports
