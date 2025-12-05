package io.modelcontextprotocol.kotlin.sdk.server

import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage

/**
 * Interface for resumability support via event storage
 */
public interface EventStore {
    /**
     * Stores an event for later retrieval
     * @param streamId ID of the stream the event belongs to
     * @param message The JSON-RPC message to store
     * @returns The generated event ID for the stored event
     */
    public suspend fun storeEvent(streamId: String, message: JSONRPCMessage): String

    /**
     * Replays events after the specified event ID
     * @param lastEventId The last event ID that was received
     * @param sender Function to send events
     * @return The stream ID for the replayed events
     */
    public suspend fun replayEventsAfter(
        lastEventId: String,
        sender: suspend (eventId: String, message: JSONRPCMessage) -> Unit,
    ): String

    /**
     * Returns the stream ID associated with [eventId], or null if the event is unknown.
     * Default implementation is a no-op which disables extra validation during replay.
     */
    public suspend fun getStreamIdForEventId(eventId: String): String?
}
