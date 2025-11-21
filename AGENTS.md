# kotlin-mcp-sdk

MCP Kotlin SDK — Kotlin Multiplatform implementation of the Model Context Protocol.

## Repository Layout

- `kotlin-sdk-core`: Core protocol types, transport abstractions, WebSocket implementation
- `kotlin-sdk-client`: Client transports (STDIO, SSE, StreamableHttp, WebSocket)
- `kotlin-sdk-server`: Server transports + Ktor integration (STDIO, SSE, WebSocket)
- `kotlin-sdk`: Umbrella module that aggregates all three above
- `kotlin-sdk-test`: Shared test utilities
- `samples/`: Three sample projects (composite builds)

## General Guidance

- Avoid changing public API signatures. Run `./gradlew apiCheck` before every commit.
- **Explicit API mode** is strict: all public APIs must have explicit visibility modifiers and return types.
- Anything under an `internal` directory is not part of the public API and may change freely.
- Package structure follows: `io.modelcontextprotocol.kotlin.sdk.*`
- The SDK targets Kotlin 2.2+ and JVM 1.8+ as minimum.

## Building and Testing

1. Build the project:
   ```bash
   ./gradlew build
   ```
2. Run tests. To save time, run only the module you changed:
   ```bash
   ./gradlew :kotlin-sdk-core:test
   ./gradlew :kotlin-sdk-client:test
   ./gradlew :kotlin-sdk-server:test
   ```
3. For platform-specific tests:
   ```bash
   ./gradlew jsTest
   ./gradlew wasmJsTest
   ```
4. Check binary compatibility (required before commit):
   ```bash
   ./gradlew apiCheck
   ```
5. If you intentionally changed public APIs, update the API dump:
   ```bash
   ./gradlew apiDump
   ```

## Tests

- Write comprehensive tests for new features
- **Prioritize test readability**
- Avoid creating too many test methods. If multiple parameters can be tested in one scenario, go for it.
- In case of similar scenarios/use-cases, consider using parametrized tests.
  But never make a parametrized test for only one use-case
- Use function `Names with backticks` for test methods in Kotlin, e.g. "fun `should return 200 OK`()"
- Avoid writing KDocs for tests, keep code self-documenting
- When running tests on a Kotlin Multiplatform project, run only JVM tests,
  if not asked to run tests on another platform either.
- Common tests for each module are located in `src/commonTest/kotlin/io/modelcontextprotocol/kotlin/sdk/`
- Platform-specific tests go in `src/jvmTest/`, `src/jsTest/`, etc.
- Use Kotest assertions (`shouldBe`, `shouldContain`, etc.) for readable test failures.
- Use `shouldMatchJson` from Kotest for JSON validation.
- Mock Ktor HTTP clients using `MockEngine` from `io.ktor:ktor-client-mock`.
- Always add tests for new features or bug fixes, even if not explicitly requested.

## Code Conventions

### Multiplatform Patterns

- Use `expect`/`actual` pattern for platform-specific implementations in `utils.*` files.
- Test changes on JVM first, then verify platform-specific behavior if needed.
- Supported targets: JVM (1.8+), JS/Wasm, iOS, watchOS, tvOS.

### Serialization

- Use Kotlinx Serialization with explicit `@Serializable` annotations.
- Custom serializers should be registered in the companion object.
- JSON config is defined in `McpJson.kt` — use it consistently.

### Concurrency and State

- Use `kotlinx.atomicfu` for thread-safe operations across platforms.
- Prefer coroutines over callbacks where possible.
- Transport implementations extend `AbstractTransport` for consistent callback management.

### Error Handling

- Use sealed classes for representing different result states.
- Map errors to JSON-RPC error codes in protocol handlers.
- Log errors using `io.github.oshai.kotlinlogging.KotlinLogging`.

### Logging

- Use `KotlinLogging.logger {}` for structured logging.
- Never log sensitive data (credentials, tokens).

## Pull Requests

- Base all PRs on the `main` branch.
- PR title format: Brief description of the change
- Include in PR description:
    - **What changed?**
    - **Why? Motivation and context**
    - **Breaking changes?** (if any)
- Run `./gradlew apiCheck` before submitting.
- Link PR to related issue (except for minor docs/typo fixes).
- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/reference/coding-conventions.html).

## Review Checklist

- `./gradlew apiCheck` must pass.
- `./gradlew test` must succeed for affected modules.
- New tests added for any new feature or bug fix.
- Documentation updated for user-facing changes.
- No breaking changes to public APIs without discussion.
