package io.modelcontextprotocol.kotlin.sdk.client

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Options for controlling SSE reconnection behavior.
 *
 * @property initialReconnectionDelay The initial delay before the first reconnection attempt.
 * @property maxReconnectionDelay The maximum delay between reconnection attempts.
 * @property reconnectionDelayGrowFactor The factor by which the delay grows on each attempt.
 * @property maxRetries The maximum number of reconnection attempts per disconnect.
 */
public class ReconnectionOptions(
    public val initialReconnectionDelay: Duration = 1.seconds,
    public val maxReconnectionDelay: Duration = 30.seconds,
    public val reconnectionDelayGrowFactor: Double = 1.5,
    public val maxRetries: Int = 2,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ReconnectionOptions

        if (reconnectionDelayGrowFactor != other.reconnectionDelayGrowFactor) return false
        if (maxRetries != other.maxRetries) return false
        if (initialReconnectionDelay != other.initialReconnectionDelay) return false
        if (maxReconnectionDelay != other.maxReconnectionDelay) return false

        return true
    }

    override fun hashCode(): Int {
        var result = reconnectionDelayGrowFactor.hashCode()
        result = 31 * result + maxRetries
        result = 31 * result + initialReconnectionDelay.hashCode()
        result = 31 * result + maxReconnectionDelay.hashCode()
        return result
    }

    override fun toString(): String =
        "ReconnectionOptions(initialReconnectionDelay=$initialReconnectionDelay, maxReconnectionDelay=$maxReconnectionDelay, reconnectionDelayGrowFactor=$reconnectionDelayGrowFactor, maxRetries=$maxRetries)"
}
