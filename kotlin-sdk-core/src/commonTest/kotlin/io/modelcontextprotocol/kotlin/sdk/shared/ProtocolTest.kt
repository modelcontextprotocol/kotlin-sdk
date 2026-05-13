package io.modelcontextprotocol.kotlin.sdk.shared

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.types.CustomRequest
import io.modelcontextprotocol.kotlin.sdk.types.EmptyResult
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.ProgressNotification
import io.modelcontextprotocol.kotlin.sdk.types.ProgressNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.RequestMeta
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
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
        protocol = TestProtocol(ProtocolOptions(dispatcher = Dispatchers.Unconfined))
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
    fun `should not report CancellationException from notification handler to onError`() = runTest {
        // With concurrent message dispatch, CancellationException in a handler
        // cancels the launched coroutine but does not propagate to the caller
        // and does not trigger onError, consistent with SupervisorJob semantics.
        protocol.connect(transport)

        protocol.fallbackNotificationHandler = {
            throw CancellationException("test cancellation")
        }

        // CancellationException is caught by the coroutine machinery and
        // cancels the individual launched coroutine; deliver() returns normally.
        transport.deliver(JSONRPCNotification(method = "test/notification"))

        // With Unconfined dispatcher, the handler executes immediately
        // onError should NOT be called for CancellationException
        protocol.errors shouldHaveSize 0
    }

    @Test
    fun `should report non-cancellation exception from notification handler via onError`() = runTest {
        protocol.connect(transport)

        protocol.fallbackNotificationHandler = {
            throw IllegalStateException("handler failed")
        }

        // Non-CE exceptions are caught and reported via onError
        transport.deliver(JSONRPCNotification(method = "test/notification"))

        // With Unconfined dispatcher, the handler executes immediately
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
    fun `should process response while request handler is suspended`() = runTest {
        // Core deadlock test: when a request handler is running (or suspended),
        // an incoming response must still be processable. Without concurrent
        // dispatch, the response would be stuck behind the running handler.
        protocol.connect(transport)

        // Register a request handler that blocks until a latch is released.
        // This simulates a handler that suspends (e.g., calling session.request()).
        val handlerCanFinish = CompletableDeferred<Unit>()
        val handlerStarted = CompletableDeferred<Unit>()
        protocol.setRequestHandler<CustomRequest>(Method.Custom("test/slow-handler")) { _, _ ->
            handlerStarted.complete(Unit)
            handlerCanFinish.await() // Suspend until we allow it
            EmptyResult()
        }

        // Start an outgoing request from the protocol.
        // This suspends waiting for a response with a specific request ID.
        val inFlight = async {
            protocol.request<EmptyResult>(
                request = CustomRequest(method = Method.Custom("test/outgoing"), params = null),
            )
        }

        // Get the outgoing request that the protocol sent
        val outgoingRequest = transport.awaitRequest()

        // Deliver an incoming request to the protocol.
        // This triggers the slow handler in a separate coroutine.
        transport.deliver(JSONRPCRequest(method = Method.Custom("test/slow-handler").value, id = 999))

        // Wait for the handler to start
        handlerStarted.await()

        // While the slow handler is still suspended, deliver the response for our
        // original outgoing request. This must be processable — if dispatch
        // were serial, this response would be blocked behind the slow handler.
        transport.deliver(JSONRPCResponse(outgoingRequest.id, EmptyResult()))

        // The outgoing request should complete even though the slow handler
        // is still running. This proves concurrent dispatch works.
        inFlight.await()

        // The slow handler has not finished yet (we haven't released it)
        handlerCanFinish.complete(Unit)
    }

    @Test
    fun `should process notifications concurrently with request handling`() = runTest {
        // Verify that a notification arriving while a request handler is running
        // gets processed immediately (not blocked behind the request handler).
        protocol.connect(transport)

        var notificationReceived = false
        val handlerStarted = CompletableDeferred<Unit>()
        val handlerCanFinish = CompletableDeferred<Unit>()

        // Register a request handler that suspends until we release it
        protocol.setRequestHandler<CustomRequest>(Method.Custom("test/slow-request")) { _, _ ->
            handlerStarted.complete(Unit)
            handlerCanFinish.await() // Suspend until we allow it
            EmptyResult()
        }

        protocol.fallbackNotificationHandler = {
            notificationReceived = true
        }

        // Deliver a request (handler will suspend)
        transport.deliver(JSONRPCRequest(method = Method.Custom("test/slow-request").value, id = 1))
        handlerStarted.await()

        // While the handler is suspended, deliver a notification
        // It should be processed immediately (concurrent dispatch)
        transport.deliver(JSONRPCNotification(method = "test/notification"))

        // Give the notification coroutine a chance to execute
        // With Unconfined dispatcher, it should have already executed
        notificationReceived shouldBe true

        // Clean up
        handlerCanFinish.complete(Unit)
    }

    @Test
    fun `should not cancel scope when single message handler throws`() = runTest {
        // SupervisorJob ensures one handler failure does not cancel the scope.
        // After a handler throws, subsequent messages must still be processed.
        protocol.connect(transport)

        var secondMessageProcessed = false
        val secondMessageReceived = CompletableDeferred<Unit>()

        protocol.fallbackNotificationHandler = {
            throw IllegalStateException("handler failed")
        }

        // Register a second handler to verify scope is still alive
        protocol.setNotificationHandler<ProgressNotification>(
            Method.Defined.NotificationsProgress,
        ) {
            secondMessageProcessed = true
            secondMessageReceived.complete(Unit)
            CompletableDeferred(Unit)
        }

        // First message: handler throws
        transport.deliver(JSONRPCNotification(method = "test/notification"))

        // Second message: should still be processed
        transport.deliver(
            ProgressNotification(
                ProgressNotificationParams(
                    progressToken = RequestId.NumberId(1),
                    progress = 1.0,
                ),
            ).toJSON(),
        )

        // Wait for second message to be processed
        secondMessageReceived.await()

        secondMessageProcessed shouldBe true
        // First handler's exception should have been reported via onError
        protocol.errors.shouldHaveSize(1)
        protocol.errors[0].message shouldBe "handler failed"
    }

    @Test
    fun `should close message scope on transport close`() = runTest {
        // After doClose(), the messageScope is cancelled and transport is cleared.
        protocol.connect(transport)

        var messageProcessed = false
        val messageReceived = CompletableDeferred<Unit>()
        protocol.fallbackNotificationHandler = {
            messageProcessed = true
            messageReceived.complete(Unit)
        }

        // Deliver a message before close
        transport.deliver(JSONRPCNotification(method = "test/notification"))
        messageReceived.await()
        messageProcessed shouldBe true

        // Close the transport
        transport.close()

        // After close, protocol's transport should be null (cleared by doClose)
        protocol.transport shouldBe null
    }
}

private class TestProtocol(options: ProtocolOptions? = null) : Protocol(options) {
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

    override suspend fun start() {
        // noop
    }

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
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
