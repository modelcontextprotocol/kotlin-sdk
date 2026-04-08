---
name: kdoc
description: "Add KDoc documentation to Kotlin public API. Use whenever the user asks to document Kotlin code, add KDoc, generate API docs, mentions undocumented public declarations, or wants to improve existing documentation. Also trigger when the user says 'add docs', 'document this class/file/module', 'write KDoc', or asks about missing documentation in Kotlin code."
---

# KDoc Generator

Add KDoc comments to public Kotlin API declarations.

## What to document

All public declarations (no explicit `private`/`internal`/`protected`):
- Classes, interfaces, objects, sealed classes/interfaces
- Functions, extension functions
- Properties
- Enum classes and entries
- Type aliases, annotation classes

Include `@Deprecated` elements.

## Context

Read the implementation, not just the signature, to write accurate descriptions. Understanding what the code actually does prevents superficial or misleading documentation.

## KDoc format

**Class/interface example:**

````kotlin
/**
 * Manages active client sessions and their lifecycle.
 *
 * Sessions are created on first connection and cleaned up
 * when the transport closes.
 *
 * @property maxSessions upper limit on concurrent sessions
 * @property timeout idle timeout before a session is evicted
 */
````

**Function example:**

````kotlin
/**
 * Registers a new tool with the given handler.
 *
 * Example:
 * ```kotlin
 * server.addTool(
 *     name = "echo",
 *     description = "Echoes input back",
 * ) { request ->
 *     CallToolResult(content = listOf(TextContent("Echo: ${request.arguments}")))
 * }
 * ```
 *
 * @param name unique tool identifier
 * @param description human-readable tool description
 * @param handler suspend function invoked when the tool is called
 * @return the registered tool definition
 */
````

## Rules

- Summary: concise first sentence starting with a third-person verb ("Creates", "Returns", "Represents"). Expand to 2-3 sentences only when genuinely complex
- **@property** in class-level KDoc for all public properties (never as individual KDoc on the property); **@param** for function parameters
- **@return** for non-Unit return types
- **Example block**: add for DSL builders, complex functions, extension functions with non-obvious usage. Skip for trivial one-liners and simple getters
- **DSL builders**: always include an Example showing full usage with the receiver scope — this is critical for discoverability
- KDoc links (`[ClassName]`, `[methodName]`): only where it adds clear navigational value
- No **@throws** — don't document exceptions
- No **suspend** notes — coroutine nature is visible from the signature
- **Existing KDoc**: rewrite if incomplete (missing @param/@return/@property) or low quality