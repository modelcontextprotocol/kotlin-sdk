package io.modelcontextprotocol.kotlin.sdk.shared

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.modelcontextprotocol.kotlin.sdk.types.CustomRequest
import io.modelcontextprotocol.kotlin.sdk.types.EmptyResult
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
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
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.RequestMeta
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.BeforeTest
import kotlin.test.Test

class ProtocolTest {
    private lateinit var protocol: TestProtocol
    private lateinit var transport: RecordingTransport

    @BeforeTest
    fun setUp() {
        protocol = TestProtocol()
        transport = RecordingTransport()
    }

    @Test
    fun `should preserve existing meta when adding progress token`() = runTest {
        protocol.connect(transport)
        val request = ReadResourceRequest(
            ReadResourceRequestParams(
                uri = "test://resource",
                meta = metaOf {
                    put("customField", JsonPrimitive("customValue"))
                    put("anotherField", JsonPrimitive(123))
                },
            ),
        )

        val inFlight = async {
            protocol.request<EmptyResult>(
                request = request,
                options = RequestOptions(onProgress = {}),
            )
        }

        val sent = transport.awaitRequest()
        val params = sent.params?.jsonObject.shouldNotBeNull()
        val meta = params["_meta"]?.jsonObject.shouldNotBeNull()

        params["uri"]?.jsonPrimitive?.content shouldBe "test://resource"
        meta["customField"]?.jsonPrimitive?.content shouldBe "customValue"
        meta["anotherField"]?.jsonPrimitive?.int shouldBe 123
        meta["progressToken"] shouldBe McpJson.encodeToJsonElement(sent.id)

        transport.deliver(JSONRPCResponse(sent.id, EmptyResult()))
        inFlight.await()
    }

    @Test
    fun `should create meta with progress token when none exists`() = runTest {
        protocol.connect(transport)
        val request = ReadResourceRequest(
            ReadResourceRequestParams(
                uri = "test://resource",
                meta = null,
            ),
        )

        val inFlight = async {
            protocol.request<EmptyResult>(
                request = request,
                options = RequestOptions(onProgress = {}),
            )
        }

        val sent = transport.awaitRequest()
        val params = sent.params?.jsonObject.shouldNotBeNull()
        val meta = params["_meta"]?.jsonObject.shouldNotBeNull()

        params["uri"]?.jsonPrimitive?.content shouldBe "test://resource"
        meta["progressToken"] shouldBe McpJson.encodeToJsonElement(sent.id)

        transport.deliver(JSONRPCResponse(sent.id, EmptyResult()))
        inFlight.await()
    }

    @Test
    fun `should not modify meta when onProgress is absent`() = runTest {
        protocol.connect(transport)
        val originalMeta = metaJson {
            put("customField", JsonPrimitive("customValue"))
        }
        val request = ReadResourceRequest(
            ReadResourceRequestParams(
                uri = "test://resource",
                meta = RequestMeta(originalMeta),
            ),
        )

        val inFlight = async {
            protocol.request<EmptyResult>(request)
        }

        val sent = transport.awaitRequest()
        val params = sent.params?.jsonObject.shouldNotBeNull()
        val meta = params["_meta"]?.jsonObject.shouldNotBeNull()

        meta shouldBe originalMeta
        params["uri"]?.jsonPrimitive?.content shouldBe "test://resource"

        transport.deliver(JSONRPCResponse(sent.id, EmptyResult()))
        inFlight.await()
    }

    @Test
    fun `should propagate CancellationException from notification handler without calling onError`() = runTest {
        protocol.connect(transport)

        protocol.fallbackNotificationHandler = {
            throw CancellationException("test cancellation")
        }

        shouldThrow<CancellationException> {
            transport.deliver(JSONRPCNotification(method = "test/notification"))
        }

        protocol.errors shouldHaveSize 0
    }

    @Test
    fun `should report non-cancellation exception from notification handler via onError`() = runTest {
        protocol.connect(transport)

        protocol.fallbackNotificationHandler = {
            throw IllegalStateException("handler failed")
        }

        // Non-CE exceptions are caught and reported, not propagated
        transport.deliver(JSONRPCNotification(method = "test/notification"))

        protocol.errors shouldHaveSize 1
        protocol.errors[0].message shouldBe "handler failed"
    }

    @Test
    fun `should create params object when request params are null`() = runTest {
        protocol.connect(transport)
        val request = CustomRequest(
            method = Method.Custom("example"),
            params = null,
        )

        val inFlight = async {
            protocol.request<EmptyResult>(
                request = request,
                options = RequestOptions(onProgress = {}),
            )
        }

        val sent = transport.awaitRequest()
        val params = sent.params?.jsonObject.shouldNotBeNull()
        val meta = params["_meta"]?.jsonObject.shouldNotBeNull()

        params.keys shouldContainExactly setOf("_meta")
        meta["progressToken"] shouldBe McpJson.encodeToJsonElement(sent.id)

        transport.deliver(JSONRPCResponse(sent.id, EmptyResult()))
        inFlight.await()
    }

    @Test
    fun `request handler receives enriched extra and ambient context element`() = runTest {
        protocol.connect(transport)

        var seenByParameter: RequestHandlerExtra? = null
        var seenAmbient: RequestHandlerExtra? = null
        protocol.fallbackRequestHandler = { _, extra ->
            seenByParameter = extra
            seenAmbient = currentRequestHandlerExtra()
            EmptyResult()
        }

        transport.deliver(JSONRPCRequest(id = RequestId(7L), method = "custom/echo"))

        seenByParameter shouldNotBe null
        seenByParameter?.requestId shouldBe RequestId(7L)
        seenByParameter?.method shouldBe Method.Custom("custom/echo")
        // the SAME instance flows through both delivery paths
        seenAmbient shouldBe seenByParameter
    }

    @Test
    fun `extra method resolves defined methods to Defined entries`() = runTest {
        protocol.connect(transport)

        var seenMethod: Method? = null
        protocol.setRequestHandler<PingRequest>(Method.Defined.Ping) { _, extra ->
            seenMethod = extra.method
            EmptyResult()
        }
        transport.deliver(JSONRPCRequest(id = RequestId(1L), method = "ping"))

        seenMethod shouldBe Method.Defined.Ping
    }

    @Test
    fun `extra sendNotification stamps relatedRequestId`() = runTest {
        protocol.connect(transport)

        protocol.fallbackRequestHandler = { _, extra ->
            extra.sendNotification(
                ProgressNotification(
                    ProgressNotificationParams(progressToken = ProgressToken(1L), progress = 0.5),
                ),
            )
            EmptyResult()
        }

        transport.deliver(JSONRPCRequest(id = RequestId(42L), method = "custom/progressing"))

        val sent = transport.sentWithOptions.first { it.first is JSONRPCNotification }
        sent.second?.relatedRequestId shouldBe RequestId(42L)
    }

    @Test
    fun `currentRequestHandlerExtra returns null outside handlers`() = runTest {
        currentRequestHandlerExtra() shouldBe null
    }
}

private class TestProtocol : Protocol(null) {
    val errors = mutableListOf<Throwable>()

    override fun onError(error: Throwable) {
        errors.add(error)
    }

    override fun assertCapabilityForMethod(method: Method) {
        // noop
    }
    override fun assertNotificationCapability(method: Method) {
        // noop
    }
    override fun assertRequestHandlerCapability(method: Method) {
        // noop
    }
}

private class RecordingTransport : Transport {
    private val sentMessages = Channel<JSONRPCMessage>(Channel.UNLIMITED)
    private var onMessageCallback: (suspend (JSONRPCMessage) -> Unit)? = null
    private var onCloseCallback: (() -> Unit)? = null

    val sentWithOptions = mutableListOf<Pair<JSONRPCMessage, TransportSendOptions?>>()

    override suspend fun start() {
        // noop
    }

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        sentWithOptions.add(message to options)
        sentMessages.send(message)
    }

    override suspend fun close() {
        onCloseCallback?.invoke()
    }

    override fun onClose(block: () -> Unit) {
        onCloseCallback = block
    }

    override fun onError(block: (Throwable) -> Unit) {
        // noop
    }

    override fun onMessage(block: suspend (JSONRPCMessage) -> Unit) {
        onMessageCallback = block
    }

    suspend fun awaitRequest(): JSONRPCRequest {
        val message = sentMessages.receive()
        return message as? JSONRPCRequest
            ?: error("Expected JSONRPCRequest but received ${message::class.simpleName}")
    }

    suspend fun deliver(message: JSONRPCMessage) {
        val callback = onMessageCallback ?: error("onMessage callback not registered")
        callback(message)
    }
}

private fun metaOf(builderAction: JsonObjectBuilder.() -> Unit): RequestMeta = RequestMeta(metaJson(builderAction))

private fun metaJson(builderAction: JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject(builderAction)
