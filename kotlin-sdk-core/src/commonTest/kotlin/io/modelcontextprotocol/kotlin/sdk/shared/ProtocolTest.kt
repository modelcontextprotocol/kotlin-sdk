package io.modelcontextprotocol.kotlin.sdk.shared

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.modelcontextprotocol.kotlin.sdk.types.CustomRequest
import io.modelcontextprotocol.kotlin.sdk.types.EmptyResult
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.PingRequest
import io.modelcontextprotocol.kotlin.sdk.types.ProgressNotification
import io.modelcontextprotocol.kotlin.sdk.types.ProgressNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.ProgressToken
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.Request
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.RequestMeta
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class ProtocolTest {

    /** Sends [request] through a freshly connected protocol, answers it, and returns what went on the wire. */
    private suspend fun TestScope.sendAndAnswer(request: Request, options: RequestOptions? = null): JSONRPCRequest {
        val (protocol, transport) = connectedProtocol()
        val inFlight = async { protocol.request<EmptyResult>(request, options) }
        val sent = transport.awaitRequest()
        transport.deliver(JSONRPCResponse(sent.id, EmptyResult()))
        inFlight.await()
        return sent
    }

    @Test
    fun `should preserve existing meta when adding progress token`() = runTest {
        val meta = buildJsonObject {
            put("customField", "customValue")
            put("anotherField", 123)
        }

        val sent = sendAndAnswer(
            ReadResourceRequest(ReadResourceRequestParams(uri = "test://resource", meta = RequestMeta(meta))),
            RequestOptions(onProgress = {}),
        )

        val params = sent.params?.jsonObject.shouldNotBeNull()
        params["uri"]?.jsonPrimitive?.content shouldBe "test://resource"
        params["_meta"] shouldBe JsonObject(meta + ("progressToken" to McpJson.encodeToJsonElement(sent.id)))
    }

    @Test
    fun `should not modify meta when onProgress is absent`() = runTest {
        val meta = buildJsonObject { put("customField", "customValue") }

        val sent = sendAndAnswer(
            ReadResourceRequest(ReadResourceRequestParams(uri = "test://resource", meta = RequestMeta(meta))),
        )

        val params = sent.params?.jsonObject.shouldNotBeNull()
        params["uri"]?.jsonPrimitive?.content shouldBe "test://resource"
        params["_meta"] shouldBe meta
    }

    @Test
    fun `should create params object when request params are null`() = runTest {
        val sent = sendAndAnswer(
            CustomRequest(method = Method.Custom("example"), params = null),
            RequestOptions(onProgress = {}),
        )

        sent.params shouldBe buildJsonObject {
            putJsonObject("_meta") { put("progressToken", McpJson.encodeToJsonElement(sent.id)) }
        }
    }

    @Test
    fun `progress reaches the request callback and progress for an unknown token is reported`() = runTest {
        val (protocol, transport) = connectedProtocol()
        val received = mutableListOf<Double>()
        val inFlight = async {
            protocol.request<EmptyResult>(PingRequest(), RequestOptions(onProgress = { received += it.progress }))
        }
        val sent = transport.awaitRequest()

        transport.deliver(ProgressNotification(ProgressNotificationParams(sent.id, 0.5)).toJSON())
        transport.deliver(JSONRPCResponse(sent.id, EmptyResult()))
        inFlight.await()
        transport.deliver(ProgressNotification(ProgressNotificationParams(sent.id, 1.0)).toJSON()) // token retired

        received shouldBe listOf(0.5)
        protocol.errors shouldHaveSize 1
    }

    @Test
    fun `should propagate CancellationException from notification handler without calling onError`() = runTest {
        val (protocol, transport) = connectedProtocol()
        protocol.fallbackNotificationHandler = { throw CancellationException("test cancellation") }

        shouldThrow<CancellationException> { transport.deliver(JSONRPCNotification(method = "test/notification")) }

        protocol.errors shouldBe emptyList()
    }

    @Test
    fun `should report non-cancellation exception from notification handler via onError`() = runTest {
        val (protocol, transport) = connectedProtocol()
        protocol.fallbackNotificationHandler = { throw IllegalStateException("handler failed") }

        transport.deliver(JSONRPCNotification(method = "test/notification"))

        protocol.errors.single().message shouldBe "handler failed"
    }

    @Test
    fun `request handler receives enriched extra and ambient context element`() = runTest {
        val (protocol, transport) = connectedProtocol()
        val seen = mutableListOf<Pair<RequestHandlerExtra, RequestHandlerExtra?>>()
        protocol.fallbackRequestHandler = { _, extra ->
            seen += extra to currentRequestHandlerExtra()
            EmptyResult()
        }

        transport.deliver(JSONRPCRequest(id = RequestId(7L), method = "custom/echo"))
        transport.deliver(JSONRPCRequest(id = RequestId(8L), method = Method.Defined.ToolsList.value))

        seen.map { (extra, _) -> extra.requestId to extra.method } shouldBe listOf(
            RequestId(7L) to Method.Custom("custom/echo"),
            RequestId(8L) to Method.Defined.ToolsList,
        )
        // the SAME instance flows through both delivery paths
        seen.forEach { (extra, ambient) -> ambient shouldBeSameInstanceAs extra }
    }

    @Test
    fun `extra sendNotification stamps relatedRequestId`() = runTest {
        val (protocol, transport) = connectedProtocol()
        protocol.fallbackRequestHandler = { _, extra ->
            extra.sendNotification(ProgressNotification(ProgressNotificationParams(ProgressToken(1L), 0.5)))
            EmptyResult()
        }

        transport.deliver(JSONRPCRequest(id = RequestId(42L), method = "custom/progressing"))

        val sent = transport.sentWithOptions.first { it.first is JSONRPCNotification }
        sent.second?.relatedRequestId shouldBe RequestId(42L)
    }

    @Test
    fun `extra sendRequest stamps relatedRequestId and preserves caller options`() = runTest {
        val (protocol, transport) = connectedProtocol()
        val onProgress: ProgressCallback = {}
        protocol.fallbackRequestHandler = { _, extra ->
            extra.sendRequest<EmptyResult>(
                PingRequest(),
                RequestOptions(resumptionToken = "tok", onProgress = onProgress, timeout = 30.seconds),
            )
            EmptyResult()
        }

        // The serial phase runs the handler inline inside deliver(), and the handler suspends
        // awaiting the nested request's response — drive delivery from a child coroutine.
        val delivery = launch { transport.deliver(JSONRPCRequest(id = RequestId(42L), method = "custom/nested")) }

        val outbound = transport.awaitRequest()
        val options = transport.sentWithOptions.single { it.first == outbound }.second
            .shouldBeInstanceOf<RequestOptions>()
        options.relatedRequestId shouldBe RequestId(42L)
        options.resumptionToken shouldBe "tok"
        options.timeout shouldBe 30.seconds
        options.onProgress shouldBeSameInstanceAs onProgress

        transport.deliver(JSONRPCResponse(id = outbound.id, result = EmptyResult()))
        delivery.join()
    }

    @Test
    fun `connect while already connected throws IllegalStateException`() = runTest {
        val (protocol, _) = connectedProtocol()

        shouldThrow<IllegalStateException> { protocol.connect(RecordingTransport()) }
    }

    @Test
    fun `stale onClose from a previous transport does not tear down the successor connection`() = runTest {
        val (protocol, first) = connectedProtocol()
        val staleCloseCallback = first.closeCallback.shouldNotBeNull()

        protocol.close() // disconnect the first transport (fires its own doClose)
        val second = RecordingTransport()
        protocol.connect(second)
        staleCloseCallback() // late duplicate close signal from the first transport

        protocol.transport shouldBe second
    }

    @Test
    fun `failed transport start rolls back the connection and allows reconnect`() = runTest {
        val protocol = TestProtocol()

        shouldThrow<IllegalStateException> {
            protocol.connect(RecordingTransport(startFailure = IllegalStateException("boom")))
        }
        protocol.transport shouldBe null // rolled back

        val transport = RecordingTransport()
        protocol.connect(transport) // no "already connected"
        protocol.transport shouldBe transport
    }

    @Test
    fun `request cleans up its handlers when the transport send fails`() = runTest {
        // Send fails without closing the connection, so a leak is not masked by doClose().
        val protocol = TestProtocol()
        protocol.connect(RecordingTransport(sendFailure = IllegalStateException("send failed")))

        shouldThrow<IllegalStateException> {
            protocol.request<EmptyResult>(PingRequest(), RequestOptions(onProgress = {}))
        }

        protocol.responseHandlers shouldBe emptyMap()
        protocol.progressHandlers shouldBe emptyMap()
    }

    @Test
    fun `connect rejects non-positive maxConcurrentHandlers`() = runTest {
        val error = shouldThrow<IllegalArgumentException> {
            TestProtocol(ProtocolOptions(maxConcurrentHandlers = 0)).connect(RecordingTransport())
        }
        error.message.shouldNotBeNull() shouldContain "maxConcurrentHandlers"
    }

    @Test
    fun `connect rejects maxInFlightHandlers below maxConcurrentHandlers`() = runTest {
        val error = shouldThrow<IllegalArgumentException> {
            TestProtocol(ProtocolOptions(maxConcurrentHandlers = 4, maxInFlightHandlers = 0))
                .connect(RecordingTransport())
        }
        error.message.shouldNotBeNull() shouldContain "maxInFlightHandlers"
    }
}
