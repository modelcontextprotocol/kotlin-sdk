# MCP Tier 1 Readiness Roadmap

This document tracks the work required to move this Kotlin SDK fork toward MCP
Tier 1. It is intentionally evidence-based: every implementation task should be
backed by the official specification, conformance results, or behavior already
proven in a Tier 1 SDK.

## Current Target

- Current stable protocol target: `2025-11-25`.
- Kotlin SDK status in official SDK listing: Tier 3.
- Tier 1 model SDKs in the official listing: TypeScript, Python, C#, and Go.
- Track future release-candidate work separately from the stable Tier 1 target
  until the MCP project publishes a newer stable spec.

Official references:

- SDK tier requirements: https://modelcontextprotocol.io/community/sdk-tiers
- SDK listing: https://modelcontextprotocol.io/docs/sdk
- Latest stable schema reference: https://modelcontextprotocol.io/specification/2025-11-25/schema
- Latest stable transports: https://modelcontextprotocol.io/specification/2025-11-25/basic/transports

## Tier 1 Acceptance Criteria

The SDK can be considered ready to request Tier 1 only when all of these are
true:

- Applicable conformance tests pass at 100%, excluding only tests the official
  criteria exclude: experimental features, skipped/pending tests, disputed tests,
  and legacy tests the SDK does not claim to support.
- All non-experimental protocol features for the target stable spec are
  implemented on the appropriate side of the SDK.
- Optional but Tier 1-relevant capabilities are supported, including sampling
  and elicitation.
- New protocol features have an implementation plan before each new spec
  release, with scope adjusted to feature complexity.
- Documentation includes examples for all supported features.
- A dependency update policy and a published roadmap exist.
- Stable versioning and release notes make compatibility expectations clear.
- Issue triage happens within two business days.
- Critical bugs, including P0 security or core MCP operation failures, are
  resolved within seven days.
- Issue labels or GitHub issue types support the standardized Type, Status, and
  Priority reporting expected by the tiering system.

## Tier 1 SDK Capability Model

The Tier 1 SDKs use different language idioms, but they share these product
properties:

- Client and server APIs are both first-class.
- Local and remote transports are supported, especially stdio and Streamable
  HTTP; SSE remains useful for compatibility.
- Protocol types are generated or maintained with close schema parity.
- Auth/OAuth support is represented as reusable SDK surface, not only example
  code.
- Docs include guided server/client examples plus feature-specific guides.
- Conformance or integration suites are easy to run and visible to maintainers.

Observed model SDK signals:

- TypeScript publishes split client/server packages, Streamable HTTP, stdio,
  auth helpers, framework middleware, examples, troubleshooting, and API docs.
- Python documents server and client APIs, stdio, Streamable HTTP, SSE, and a
  transport-free in-memory client flow.
- C# splits core, main, and ASP.NET Core packages and documents cross-application
  access support.
- Go publishes `mcp`, `jsonrpc`, `auth`, and `oauthex` packages with a version
  compatibility table and feature documentation.

## Kotlin SDK Readiness Snapshot

Strong signals already present:

- `LATEST_PROTOCOL_VERSION` is `2025-11-25`.
- Protocol support includes JSON-RPC, lifecycle, ping, progress, cancellation,
  pagination, metadata, tools, resources, prompts, completion, logging, roots,
  sampling, elicitation, and task extension types.
- Newer schema concepts exist in core types, including icons, titles, audio
  content, resource links, tool output schemas, sampling tools, URL-mode
  elicitation, and JSON Schema 2020-12 fields.
- Transport surface includes stdio, Streamable HTTP, SSE, WebSocket, and
  in-memory channel transport for tests.
- Streamable HTTP has session handling, protocol-version headers, resumability,
  DNS rebinding hooks, request-size limits, and SSE reconnection tests.
- `conformance-test` includes server, client, and client-auth entry points with
  an empty expected-failure baseline.

Known gaps and risks:

- The latest recorded conformance run is tracked in
  `docs/conformance-status.md`; it must be refreshed after protocol, transport,
  auth, or conformance-runner changes.
- Client auth now has initial reusable discovery and bearer-header primitives,
  but the full SDK OAuth flow still needs PKCE, token exchange, refresh, and
  transport integration.
- Feature docs are not yet comprehensive for auth, URL-mode elicitation,
  sampling with tools, transport resumability, and security constraints.
- Tasks are experimental/extension work and should not block Tier 1 unless the
  SDK explicitly claims production support for the extension.
- The dependency update policy was not previously published.
- The README contains mojibake in several prose sections and should be cleaned
  before a Tier request.
- The conformance README must stay synchronized with
  `conformance-test/run-conformance.sh`.

## Work Plan

### Phase 1: Establish Evidence

- Run `./conformance-test/run-conformance.sh list` and save the available
  scenario inventory when the runner version changes.
- Run `./conformance-test/run-conformance.sh all` and refresh
  `docs/conformance-status.md` after protocol, transport, auth, or runner
  changes.
- Keep `conformance-baseline.yml` empty unless a known limitation is explicitly
  approved and tracked.

### Phase 2: Close Protocol and SDK Gaps

- Fix every non-experimental conformance failure for `2025-11-25`.
- Promote auth/OAuth conformance logic into reusable client/server SDK APIs
  where the official spec expects SDK support; continue from client metadata
  discovery toward complete PKCE and token lifecycle support.
- Audit protocol type parity against the official schema reference.
- Harden Streamable HTTP against conformance and production edge cases:
  resumability, polling disconnects, standalone GET stream behavior, session
  lifecycle, protocol-version negotiation, and JSON/SSE content negotiation.
- Audit structured concurrency and cancellation paths in transports and session
  registries.

### Phase 3: Documentation and Release Readiness

- Add feature examples for elicitation, URL-mode elicitation, sampling with
  tools, auth/OAuth, Streamable HTTP resumability, and security-sensitive host
  validation.
- Keep Tasks documentation as extension documentation, separate from the Tier 1
  blocking checklist.
- Publish compatibility notes for supported MCP spec versions.
- Keep public API dumps current with intentional API changes.
- Run before each commit when relevant:
  - `./gradlew :kotlin-sdk-core:jvmTest`
  - `./gradlew :kotlin-sdk-client:jvmTest`
  - `./gradlew :kotlin-sdk-server:jvmTest`
  - `./gradlew apiCheck`
  - targeted conformance suite for changed behavior

### Phase 4: Tier Request

- Have a subagent review the evidence and code changes before each commit.
- Request final Tier 1 readiness review from a subagent only after conformance is
  100% and docs/release evidence is complete.
- Prepare Tier advancement evidence for the MCP SDK Working Group:
  conformance output, feature matrix, docs links, dependency policy, release
  policy, and maintenance commitments.
