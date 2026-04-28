package io.modelcontextprotocol.kotlin.sdk.server.utils

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger("ToolNameValidation")

private const val MAX_TOOL_NAME_LENGTH = 128

private const val SEP_986_URL = "https://github.com/modelcontextprotocol/modelcontextprotocol/issues/986"

/**
 * Validates a tool name against the MCP tool naming standard (SEP-986).
 * Returns a list of warnings. An empty list means the name is valid.
 */
internal fun validateToolName(name: String): List<String> {
    if (name.isEmpty()) return listOf("Tool name cannot be empty")

    val invalidChars = name.toSet().filterNot { it.isAsciiAllowed() }
    val remainingInvalidChars = invalidChars - setOf(' ', ',')

    return listOfNotNull(
        "Tool name exceeds maximum length of $MAX_TOOL_NAME_LENGTH characters (current: ${name.length})"
            .takeIf { name.length > MAX_TOOL_NAME_LENGTH },
        "Tool name contains spaces, which may cause parsing issues"
            .takeIf { ' ' in invalidChars },
        "Tool name contains commas, which may cause parsing issues"
            .takeIf { ',' in invalidChars },
        "Tool name contains invalid characters: ${remainingInvalidChars.joinToString(", ") { "\"$it\"" }}"
            .takeIf { remainingInvalidChars.isNotEmpty() },
        "Allowed characters are: A-Z, a-z, 0-9, underscore (_), dash (-), and dot (.)"
            .takeIf { invalidChars.isNotEmpty() },
        "Tool name starts or ends with a dash, which may cause parsing issues in some contexts"
            .takeIf { name.startsWith('-') || name.endsWith('-') },
        "Tool name starts or ends with a dot, which may cause parsing issues in some contexts"
            .takeIf { name.startsWith('.') || name.endsWith('.') },
    )
}

/**
 * Validates the tool [name] and logs a warning if it does not conform to SEP-986.
 * Does not block registration — tools with non-conforming names are still accepted.
 */
internal fun warnIfInvalidToolName(name: String) {
    val warnings = validateToolName(name)
    if (warnings.isEmpty()) return

    val warningLines = warnings.joinToString("\n") { "  - $it" }
    logger.warn {
        """
        |Tool name validation warning for "$name":
        |$warningLines
        |Tool registration will proceed, but this may cause compatibility issues.
        |Consider updating the tool name to conform to the MCP tool naming standard.
        |See SEP-986: $SEP_986_URL
        """.trimMargin()
    }
}

private fun Char.isAsciiAllowed(): Boolean =
    this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this == '_' || this == '-' || this == '.'
