# AGENTS: Development Guidelines for AI Contributors

This document is for autonomous agents and AI copilots contributing code to this repository.
Follow these rules to keep changes safe, comprehensible, and easy to maintain.

## Repository Layout

- `kotlin-sdk-core`: Core protocol types, transport abstractions, serialization utilities
- `kotlin-sdk-client`: Client transports (STDIO, SSE, StreamableHttp, WebSocket)
- `kotlin-sdk-server`: Server transports + Ktor integration (STDIO, SSE, WebSocket)
- `kotlin-sdk`: Umbrella module that aggregates all three above
- `test-utils`: Shared test utilities
- `integration-test`: Integration tests with TypeScript/other implementations
- `conformance-test`: Protocol conformance tests
- `samples/`: Sample projects (composite builds)
- Package structure: `io.modelcontextprotocol.kotlin.sdk.*`
- JVM 11+, Kotlin 2.2+

## Prime Directives

1. **Tests first, always.**
    - Before changing code, identify or add tests that express the desired behavior.
    - Prefer readable, minimal tests over clever ones. Tests are documentation.
2. **Keep tests simple and explicit.**
    - Arrange/Act/Assert structure; avoid hidden magic and overuse of helpers.
    - Prefer concrete inputs/outputs; avoid randomness and time dependence.
3. **Uphold SOLID principles** in production code:
    - Single Responsibility: each class/function should do one thing well.
    - Open/Closed: extend via new code, avoid risky edits to stable code paths.
    - Liskov Substitution: honor contracts; keep types substitutable.
    - Interface Segregation: keep abstractions small and focused.
    - Dependency Inversion: depend on abstractions, not concretions.
4. **Make the minimal change** that satisfies the tests and the issue.
   Stay focused on a single concern and avoid "drive-by" changes.
5. **Never commit changes!** It's human who should verify and commit to git.
6. **Keep the build green.** Do not merge changes that break existing tests.
7. **Prefer clarity** over micro-optimizations and cleverness.
8. **Ask when uncertain.** If requirements are ambiguous, request clarification with a concise question.
9. **Write code with the quality of a Kotlin Champion.**
10. **Use MCP servers** like `jetbrains` and `intellij-index` to explore the codebase, for code navigation, analysis,
    refactoring, and running tests.
11. **Use the Bash tool for terminal commands** instead of MCP terminal execution features.
12. **Suggest updates** to AGENTS.md/CLAUDE.md with best practices and guidelines.

## Code Style

### Kotlin

- Avoid changing public API signatures. Run `./gradlew apiCheck` before every commit.
- **Explicit API mode** is strict: all public APIs must have explicit visibility modifiers and return types.
- Anything under an `internal` directory is not part of the public API and may change freely.
- Package structure follows: `io.modelcontextprotocol.kotlin.sdk.*`
- Follow Kotlin coding conventions
- Use the provided `.editorconfig` for consistent formatting
- Use Kotlin typesafe DSL builders where possible; prioritize fluent builder style over standard builder methods
- Prefer DSL builder style (method with lambda blocks) over constructors, if possible
- Use `val` for immutable properties and `var` for mutable; consider `lateinit var` instead of nullable types when
  appropriate
- Use multi-dollar interpolation prefix for strings where applicable
- Use fully qualified imports instead of star imports
- Ensure backward compatibility when making changes

## Testing Guidance

### Test Organization

- **Location**: `src/commonTest/kotlin/io/modelcontextprotocol/kotlin/sdk/`
- **Platform-specific**: `src/jvmTest/`, `src/jsTest/`, etc.
- **Naming**: Use function `Names with backticks`, e.g., `fun \`should return 200 OK\`()`
- **Structure**: Arrange/Act/Assert pattern
- **Documentation**: Keep tests self-documenting; don't add KDocs to tests.

### Test Principles

- Write comprehensive tests for new features. Always add tests for new features or bug fixes, even if not explicitly
  requested.
- **Prioritize test readability**
- Avoid creating too many test methods; use parametrized tests when testing multiple similar scenarios
- When running tests on Kotlin Multiplatform projects, run JVM tests only unless asked for other platforms

### Test Framework Stack

- **kotlin-test**: Core test framework
- **Kotest assertions**: Use infix style (`shouldBe`, `shouldContain`) instead of `assertEquals`.
    - Use `shouldMatchJson` from kotest-assertions-json for JSON validation.
    - For nullable objects with nested properties, prefer `shouldNotBeNull { ... }` blocks:
      ```kotlin
      content.annotations shouldNotBeNull {
          priority shouldBe 1.0
      }
      ```
- **mockk**: Mocking library
- **Ktor MockEngine**: For HTTP client mocking (`io.ktor:ktor-client-mock`)
- **Java tests**: Use JUnit5, Mockito, AssertJ core

### Kotest Patterns

- Basic assertions: Use infix style (`shouldBe`, `shouldContain`) instead of `assertEquals`.
    ```kotlin
    result shouldBe expected
    list shouldContain item
    ```
- Nullable Assertions
    ```kotlin
    // Preferred: lambda-style for multiple assertions
    content.annotations shouldNotBeNull {
        priority shouldBe 1.0
    }
    ```

- JSON Assertions (kotest-assertions-json):
    ```kotlin
    // language=json
    val expected = """
    {
      "id": 123
    }
    """.trimIndent()
    actual shouldEqualJson expected
    ```

- Soft Assertions
    ```kotlin
    // Use for multiple assertions on SAME subject
    assertSoftly(subject) {
        name shouldBe "test"
        value shouldBe 42
    }
    // DON'T use for different subjects or single assertions
    ```
- Clues (Use Sparingly)
    ```kotlin
    // Only when assertion is NOT obvious
    withClue("Failed to parse response") {
        result shouldNotBe null
    }
    ```

#### Avoiding `this.` in Lambda Blocks

```kotlin
// Avoid explicit `this.` unless necessary for disambiguation
prop.shouldNotBeNull {
    enum shouldHaveSize 2 // Preferred
    this.enum shouldHaveSize 2 // Avoid
}
```

### Multiplatform Patterns

- Use `expect`/`actual` pattern for platform-specific implementations in `utils.*` files.
- Test changes on JVM first, then verify platform-specific behavior if needed.
- Supported targets: JVM (11+), JS/Wasm, iOS, watchOS, tvOS.

## Coding Guidance

### Module Boundaries

- Respect separation of concerns between modules
- Keep abstractions framework-agnostic
- Avoid leaking implementation details across module boundaries

### Serialization

- Use Kotlinx Serialization with explicit `@Serializable` annotations
- JSON config is defined in `jsonUtils.kt` as `McpJson` — use it consistently
- Register custom serializers in companion objects

### Error Handling

- Use sealed classes for result states
- Map errors to JSON-RPC error codes in protocol handlers
- Log errors using `io.github.oshai.kotlinlogging.KotlinLogging`
- Never log sensitive data (credentials, tokens)

### Code Quality

- Handle nullability and references as established patterns
- Keep changes small and reversible; prefer adding functions over editing many call sites
- Add focused comments **only** where intent is non-obvious
- Use `git mv` command when moving version-controlled files
- Never commit or push changes. This is a Human responsibility.

## Workflow for AI Agents

1. **Understand the issue**
    - Summarize requirement in 1-2 sentences
    - Identify affected files and tests; ask if unclear

2. **Plan minimal changes**
    - Use TaskCreate/TaskUpdate to track work, or EnterPlanMode for complex changes
    - Document affected files and approach

3. **Work with code**
    - View, edit, and run tests through MCP tools

4. **Start with tests**
    - Update existing or add new tests in relevant modules

5. **Implement the change**
    - Prefer composition and small functions
    - Avoid breaking public APIs without tests and discussion

6. **Run tests locally**
    ```bash
    ./gradlew test
    ./gradlew :kotlin-sdk-core:test       # Specific module
    ./gradlew :integration-test:test      # Integration tests
    ```

7. **Verify results**
    - Use Kotest JSON matchers for schema validation
    - Double-check the readability of code and tests

8. **Document key decisions**
    - Brief notes in markdown document, if requested

## Build and Run Commands

```bash
# Build all modules
./gradlew build

# Run all tests
./gradlew test

# Run specific module tests
./gradlew :kotlin-sdk-core:test
./gradlew :kotlin-sdk-client:test
./gradlew :kotlin-sdk-server:test

# Check API compatibility (required before commit)
./gradlew apiCheck

# Update API dump (after intentional API changes)
./gradlew apiDump

# Platform-specific tests
./gradlew jsTest
./gradlew wasmJsTest
```

## Definition of done

- Tests for affected functionality exist
- All tests pass across all targets
- Changes are focused on a single concern.
- Code respects module boundaries
- `./gradlew apiCheck` passes
- Documentation updated for user-facing changes

## Documentation

### General Guidelines

- **Never make up documentation/facts**
- Update README files when adding new features
- Document API changes in the appropriate module
- **Keep documentation concise and straight to the point**
- Always verify documentation matches code; fix discrepancies
- Write tutorials in Markdown format in README.md

### KDoc Guidelines

- Properly document interfaces and abstract classes in production code
- Avoid KDocs on override functions to reduce verbosity
- Update KDocs when API changes
- Use references: `[SendMessageRequest]` instead of `SendMessageRequest`
- Add brief code examples to KDocs
- Add links to specifications with verified URLs; never add broken links

### Module.md Files for Dokka

Each module must have a `Module.md` file at the module root for Dokka-generated API documentation.

**Format Requirements** (per [Dokka specification](https://kotlinlang.org/docs/dokka-module-and-package-docs.html)):

- Must start with `# Module module-name` (level 1 header)
- Package documentation uses `# Package package.name` (level 1 header)
- All other content uses level 2+ headers (`##`, `###`, etc.)

**Content Guidelines**:

- **Purpose**: Provide module-level overview in generated API docs
- **Detail level**: API documentation style (concise, focused on classes/interfaces)
- **Module section**:
    - Brief module description (1-2 sentences)
    - Platform support (format: "Multiplatform • Kotlin 2.2+" or "JVM only • Kotlin 2.2+")
    - Key classes/components (use Dokka references like `[ClassName]`)
    - Example (minimal, demonstrating primary usage)
- **Package sections**: Use `# Package package.name` headers with brief description
- **Optional sections**: Features, Limitations, Related Specifications
- **Style**:
    - Use bullet points over prose
    - Keep examples short (5-15 lines)
    - Reference related modules using Dokka links
    - Avoid duplicating README content

**Example structure (markdown)**:

    # Module module-name
    
    Brief description.
    
    **Platform Support:** Multiplatform • Kotlin 2.2+
    
    ## Key Classes
    
    - [MainClass] - what it does
      - [HelperClass] - what it does
    
    ## Example
    
    ```kotlin
    val example = MainClass()
    ```
    
    # Package package.name
    
    Package description.
    
    # Package another.package
    
    Another package description.

## When to Ask for Help

- Requirements conflict with existing tests or documentation
- The smallest change still requires altering core abstractions
- Unclear expected schema shape for a new feature—ask for a concrete, readable test case to anchor implementation
