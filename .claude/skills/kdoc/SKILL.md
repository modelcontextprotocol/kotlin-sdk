---
name: kdoc
description: "Add KDoc documentation to Kotlin public API. Use whenever the user asks to document Kotlin code, add KDoc, generate API docs, mentions undocumented public declarations, or wants to improve existing documentation. Also trigger when the user says 'add docs', 'document this class/file/module', 'write KDoc', or asks about missing documentation in Kotlin code."
---

# KDoc Generator

Add KDoc to all public Kotlin declarations (classes, interfaces, objects, functions, properties, enums, type aliases, annotations — including `@Deprecated`). Read the implementation, not just the signature, to write accurate descriptions.

## Tag placement

| Where the declaration lives | Tag |
|---|---|
| Primary-ctor `val` / `var` (public, internal, or protected) | `@property` in class KDoc |
| Primary-ctor `name: Type` (no `val`/`var`) | `@param` in class KDoc |
| Primary-ctor `private val` / `private var` | `@param` in class KDoc |
| Body of class/object/interface/sealed interface | inline `/** */` above the declaration |
| Function parameter | `@param` |
| Non-Unit return type | `@return` |

## Style

- Summary: one concise sentence, third-person verb ("Creates", "Returns", "Represents"). Expand to 2-3 sentences only when genuinely complex.
- **Example block**: mandatory for DSL builders (show full receiver-scope usage); add for complex functions or non-obvious extension functions; skip for one-liners and simple getters.
- KDoc links (`[ClassName]`, `[methodName]`) only where they add navigational value.
- Don't write `@throws` or "suspend" notes.
- Rewrite existing KDoc if incomplete or low quality.

## Examples

**Class with mixed primary-constructor parameters:**

````kotlin
/**
 * Manages active client sessions and their lifecycle.
 *
 * @property maxSessions upper limit on concurrent sessions
 * @param scope coroutine scope for session lifecycle tasks
 * @param secret used internally for session token signing
 */
public class SessionManager(
    public val maxSessions: Int,         // public val  → @property
    scope: CoroutineScope,                // no val/var → @param
    private val secret: ByteArray,        // private val → @param
)
````

**Body and interface properties (inline `/** */`):**

````kotlin
/** Represents an entity that includes additional metadata. */
public interface WithMeta {
    /** Optional metadata attached to this entity. */
    public val meta: JsonObject?
}

public class Connection(public val id: String) {
    /** Capabilities negotiated during the handshake, or `null` before initialize completes. */
    public val capabilities: ServerCapabilities? get() = ...
}
````

**Function with Example block:**

````kotlin
/**
 * Registers a new tool with the given handler.
 *
 * Example:
 * ```kotlin
 * server.addTool(name = "echo", description = "Echoes input back") { request ->
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
