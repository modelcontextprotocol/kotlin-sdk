package io.modelcontextprotocol.kotlin.sdk.client.sse

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.sse.SSECapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.ResponseAdapterAttributeKey
import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

@OptIn(InternalAPI::class)
internal open class MockSseClientEngine(
    private val endpoint: String,
    private val onPostRequest: (postRequest: HttpRequestData) -> Unit,
    override val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : HttpClientEngine {

    override val config = HttpClientEngineConfig()
    override val supportedCapabilities = setOf(SSECapability)

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = dispatcher + job

    private val channel by lazy { ByteChannel(true) }

    override suspend fun execute(data: HttpRequestData): HttpResponseData = when (data.method) {
        HttpMethod.Get -> respondWithSseEndpointEvent(data)

        HttpMethod.Post -> {
            onPostRequest(data)
            respondWithAccepted()
        }

        else -> error("Unsupported method: ${data.method}")
    }

    override fun close() {
        channel.close()
        job.cancel()
    }

    private fun responseContext(): CoroutineContext = dispatcher + Job()

    private suspend fun respondWithSseEndpointEvent(data: HttpRequestData): HttpResponseData {
        val sseResponseAdapter = requireNotNull(data.attributes.getOrNull(ResponseAdapterAttributeKey)) {
            "Missing SSE response adapter."
        }
        channel.writeStringUtf8(endpointEvent())
        channel.flush()
        val responseContext = responseContext()
        val responseBody = sseResponseAdapter.adapt(
            data,
            HttpStatusCode.OK,
            eventStreamHeader,
            channel,
            data.body,
            responseContext,
        ) ?: error("Failed to adapt SSE response.")
        return HttpResponseData(
            statusCode = HttpStatusCode.OK,
            requestTime = GMTDate(),
            headers = eventStreamHeader,
            version = HttpProtocolVersion.HTTP_1_1,
            body = responseBody,
            callContext = responseContext,
        )
    }

    private val eventStreamHeader by lazy { headersOf("Content-Type", "text/event-stream") }

    private fun endpointEvent(): String = buildString {
        appendLine("event: endpoint")
        appendLine("data: $endpoint")
        appendLine()
    }

    private fun respondWithAccepted(): HttpResponseData = HttpResponseData(
        statusCode = HttpStatusCode.Accepted,
        requestTime = GMTDate(),
        headers = Headers.Empty,
        version = HttpProtocolVersion.HTTP_1_1,
        body = ByteReadChannel.Empty,
        callContext = responseContext(),
    )
}
