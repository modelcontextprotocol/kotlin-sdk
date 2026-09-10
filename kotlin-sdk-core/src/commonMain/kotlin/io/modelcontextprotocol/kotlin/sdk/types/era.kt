package io.modelcontextprotocol.kotlin.sdk.types

/**
 * Which lifecycle a message is being handled under.
 *
 * Revision `2026-07-28` replaced the `initialize` handshake with a per-request `_meta` envelope.
 * The two cannot interoperate, so a server decides this per request and a client per connection.
 *
 * Not public: callers observe the lifecycle through facts they can act on — whether a request
 * carried an envelope, which protocol version governs it — rather than through this enum.
 */
internal enum class ProtocolEra {
    /** The connection-scoped lifecycle established by `initialize`. */
    Legacy,

    /** The request-scoped lifecycle carried by the `_meta` envelope. */
    Modern,

    ;

    internal companion object {
        /** A method that survived the transition and is reachable under either lifecycle. */
        val BOTH: Set<ProtocolEra> = setOf(Legacy, Modern)

        /** A method `2026-07-28` removed, still served for peers that predate the removal. */
        val LEGACY_ONLY: Set<ProtocolEra> = setOf(Legacy)

        /** A method `2026-07-28` introduced, which no handshake-era peer knows. */
        val MODERN_ONLY: Set<ProtocolEra> = setOf(Modern)
    }
}

/**
 * Whether this method exists in [era].
 *
 * A method absent from the era being served is answered `-32601` even where a handler is
 * registered. [Method.Custom] is era-blind: the protocol says nothing about methods it does not
 * define, so they always route.
 */
internal fun Method.isAvailableIn(era: ProtocolEra): Boolean = when (this) {
    is Method.Defined -> era in eras
    is Method.Custom -> true
}
