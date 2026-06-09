package io.modelcontextprotocol.kotlin.sdk.utils

/**
 * Internal parser for RFC 6570 URI Templates.
 *
 * Parses a template string into a structured [Part] list and provides helpers
 * for PCT-encoding values during expansion.
 */
@Suppress("TooManyFunctions")
internal object UriTemplateParser {

    //region Model types ────────────────────────────────────────────────────────

    internal data class OpInfo(
        val char: Char?,
        val first: String,
        val sep: String,
        val named: Boolean,
        val ifemp: String,
        val allowReserved: Boolean,
    )

    internal sealed interface Modifier {
        data object None : Modifier
        data object Explode : Modifier
        data class Prefix(val length: Int) : Modifier
    }

    internal data class VarSpec(val name: String, val modifier: Modifier)

    /** A parsed segment of a URI template. */
    internal sealed interface Part {
        /** Literal text, already pct-encoded for direct output. */
        data class Literal(val text: String) : Part

        /** An expression `{…}` with its operator metadata and variable list. */
        data class Expression(val op: OpInfo, val varSpecs: List<VarSpec>) : Part
    }

    //endregion
    //region Operator constants ─────────────────────────────────────────────────
    // Pre-allocated singletons — one per object, shared across all parse calls.

    private const val HEX_CHARS = "0123456789ABCDEF"

    internal val NUL_OP = OpInfo(null, "", ",", named = false, ifemp = "", allowReserved = false)
    private val PLUS_OP = OpInfo('+', "", ",", named = false, ifemp = "", allowReserved = true)
    private val HASH_OP = OpInfo('#', "#", ",", named = false, ifemp = "", allowReserved = true)
    private val DOT_OP = OpInfo('.', ".", ".", named = false, ifemp = "", allowReserved = false)
    private val SLASH_OP = OpInfo('/', "/", "/", named = false, ifemp = "", allowReserved = false)
    private val SEMI_OP = OpInfo(';', ";", ";", named = true, ifemp = "", allowReserved = false)
    private val QUERY_OP = OpInfo('?', "?", "&", named = true, ifemp = "=", allowReserved = false)
    private val AMP_OP = OpInfo('&', "&", "&", named = true, ifemp = "=", allowReserved = false)

    //endregion
    //region parsing entry point ─────────────────────────────────────────

    /**
     * Parses [template] into an ordered list of [Part]s.
     *
     * @throws IllegalArgumentException if the template contains an unclosed brace.
     */
    internal fun parse(template: String): List<Part> {
        val result = mutableListOf<Part>()
        val literalBuf = StringBuilder()
        var i = 0

        fun flushLiteral() {
            if (literalBuf.isNotEmpty()) {
                result.add(Part.Literal(literalBuf.toString()))
                literalBuf.clear()
            }
        }

        @Suppress("LoopWithTooManyJumpStatements")
        while (i < template.length) {
            if (template[i] != '{') {
                literalBuf.append(encodeLiteralChar(template[i]))
                i++
                continue
            }
            val end = template.indexOf('}', i + 1)
            require(end != -1) {
                "Unclosed brace at index $i in URI template: \"$template\""
            }
            flushLiteral()
            result.add(parseExpression(template.substring(i + 1, end)))
            i = end + 1
        }
        flushLiteral()
        return result
    }

    //endregion
    //region PCT-encoding helpers (used by UriTemplate expand logic) ───────────

    /** PCT-encodes [s], passing reserved characters through when [allowReserved] is true. */
    internal fun pctEncode(s: String, allowReserved: Boolean): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                isUnreserved(c) -> sb.append(c)

                allowReserved && isReserved(c) -> sb.append(c)

                // Preserve existing pct-encoded triplets in reserved/fragment mode.
                allowReserved &&
                    c == '%' &&
                    i + 2 < s.length &&
                    isHexDigit(s[i + 1]) &&
                    isHexDigit(s[i + 2]) -> {
                    sb.append(c).append(s[i + 1]).append(s[i + 2])
                    i += 2
                }

                // Surrogate pair — encode the full code point as UTF-8.
                c.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate() -> {
                    pctEncodeBytes(sb, s.substring(i, i + 2).encodeToByteArray())
                    i++ // skip low surrogate; outer increment follows
                }

                else -> pctEncodeBytes(sb, c.toString().encodeToByteArray())
            }
            i++
        }
        return sb.toString()
    }

    internal fun pctEncodeUnreserved(s: String): String = pctEncode(s, allowReserved = false)

    /**
     * Truncates [s] to at most [n] Unicode code points.
     * Surrogate pairs count as a single code point.
     */
    internal fun truncateCodePoints(s: String, n: Int): String {
        var count = 0
        var i = 0
        while (i < s.length && count < n) {
            if (s[i].isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate()) i++
            i++
            count++
        }
        return s.substring(0, i)
    }

    //endregion
    //region Private parsing helpers ────────────────────────────────────────────

    private fun parseExpression(expression: String): Part.Expression {
        if (expression.isEmpty()) return Part.Expression(NUL_OP, emptyList())
        val (opInfo, varListStr) = detectOperator(expression)
        val varSpecs = if (varListStr.isEmpty()) emptyList() else parseVarList(varListStr)
        return Part.Expression(opInfo, varSpecs)
    }

    private fun detectOperator(expression: String): Pair<OpInfo, String> {
        val op = when (expression.firstOrNull()) {
            '+' -> PLUS_OP
            '#' -> HASH_OP
            '.' -> DOT_OP
            '/' -> SLASH_OP
            ';' -> SEMI_OP
            '?' -> QUERY_OP
            '&' -> AMP_OP
            else -> return Pair(NUL_OP, expression)
        }
        return Pair(op, expression.substring(1))
    }

    private fun parseVarList(varListStr: String): List<VarSpec> =
        varListStr.split(',').mapNotNull { parseVarSpec(it.trim()) }

    @Suppress("ReturnCount")
    private fun parseVarSpec(raw: String): VarSpec? {
        if (raw.isEmpty()) return null
        if (raw.endsWith('*')) return VarSpec(raw.dropLast(1), Modifier.Explode)
        val colon = raw.indexOf(':')
        if (colon > 0) {
            val len = raw.substring(colon + 1).toIntOrNull()
            if (len != null && len > 0) return VarSpec(raw.substring(0, colon), Modifier.Prefix(len))
        }
        return VarSpec(raw, Modifier.None)
    }

    //endregion
    //region Private encoding helpers ───────────────────────────────────────────

    /**
     * Encodes a single literal template character.
     * Unreserved, reserved, and `%` characters are passed through unchanged;
     * everything else is PCT-encoded as UTF-8.
     */
    private fun encodeLiteralChar(c: Char): String = if (isUnreserved(c) || isReserved(c) || c == '%') {
        c.toString()
    } else {
        buildString { pctEncodeBytes(this, c.toString().encodeToByteArray()) }
    }

    @Suppress("MagicNumber")
    private fun pctEncodeBytes(sb: StringBuilder, bytes: ByteArray) {
        for (b in bytes) {
            val n = b.toInt() and 0xFF
            sb.append('%')
            sb.append(HEX_CHARS[n ushr 4])
            sb.append(HEX_CHARS[n and 0xF])
        }
    }

    private fun isUnreserved(c: Char): Boolean =
        c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '.' || c == '_' || c == '~'

    /**
     * Returns `true` for reserved characters (gen-delims | sub-delims) per RFC 3986.
     *
     * gen-delims: `:/?#[]@`
     * sub-delims: `!$&'()*+,;=`
     */
    private fun isReserved(c: Char): Boolean = c in ":/?#[]@!$&'()*+,;="

    private fun isHexDigit(c: Char): Boolean = c in '0'..'9' || c in 'A'..'F' || c in 'a'..'f'

    //endregion
}
