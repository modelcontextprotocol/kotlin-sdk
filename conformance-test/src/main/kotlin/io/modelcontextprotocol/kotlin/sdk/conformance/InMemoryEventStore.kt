package io.modelcontextprotocol.kotlin.sdk.conformance

import io.modelcontextprotocol.kotlin.sdk.server.EventStore
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class InMemoryEventStore : EventStore {

    private val events = ConcurrentHashMap<String, Pair<JSONRPCMessage, String>>()
    private val streamEvents = ConcurrentHashMap<String, MutableList<String>>()

    override suspend fun storeEvent(streamId: String, message: JSONRPCMessage): String {
        val eventId = "$streamId::${System.currentTimeMillis()}_${Uuid.random()}"
        events[eventId] = message to streamId
        streamEvents.getOrPut(streamId) { mutableListOf() }.add(eventId)
        return eventId
    }

    override suspend fun replayEventsAfter(
        lastEventId: String,
        sender: suspend (eventId: String, message: JSONRPCMessage) -> Unit,
    ): String {
        val streamId = getStreamIdForEventId(lastEventId)
            ?: error("Unknown event ID: $lastEventId")
        val eventIds = streamEvents[streamId] ?: return streamId

        var found = false
        for (eventId in eventIds) {
            if (!found) {
                if (eventId == lastEventId) found = true
                continue
            }
            val (message, _) = events[eventId] ?: continue
            sender(eventId, message)
        }
        return streamId
    }

    override suspend fun getStreamIdForEventId(eventId: String): String? {
        val idx = eventId.indexOf("::")
        return if (idx >= 0) eventId.substring(0, idx) else null
    }
}
