package io.modelcontextprotocol.kotlin.sdk.client

import dev.mokksy.mokksy.BuildingStep
import dev.mokksy.mokksy.Mokksy
import dev.mokksy.mokksy.StubConfiguration
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.sse.ServerSentEvent
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonObject

const val MCP_SESSION_ID_HEADER = "mcp-session-id"

internal class MockMcp(verbose: Boolean = false) {

    private val mokksy: Mokksy = Mokksy(verbose = verbose)

    fun checkForUnmatchedRequests() {
        mokksy.checkForUnmatchedRequests()
    }

    val url = "${mokksy.baseUrl()}/mcp"

    fun onInitialize(
        clientName: String? = null,
        sessionId: String,
        protocolVersion: String = "2025-03-26",
        serverName: String = "Mock MCP Server",
        serverVersion: String = "1.0.0",
        capabilities: JsonObject = buildJsonObject {
            putJsonObject("tools") {
                put("listChanged", JsonPrimitive(false))
            }
        },
    ) {
        val predicates = if (clientName != null) {
            arrayOf<(JSONRPCRequest?) -> Boolean>({
                it?.params?.jsonObject
                    ?.get("clientInfo")?.jsonObject
                    ?.get("name")?.jsonPrimitive
                    ?.contentOrNull == clientName
            })
        } else {
            emptyArray()
        }

        handleWithResult(
            jsonRpcMethod = "initialize",
            sessionId = sessionId,
            bodyPredicates = predicates,
            // language=json
            result = """
            {
                "capabilities": $capabilities,
                "protocolVersion": "$protocolVersion",
                "serverInfo": {
                  "name": "$serverName",
                  "version": "$serverVersion"
                },
                "_meta": {
                  "foo": "bar"
                }
            }
            """.trimIndent(),
        )
    }

    fun onJSONRPCRequest(
        httpMethod: HttpMethod = HttpMethod.Post,
        jsonRpcMethod: String,
        expectedSessionId: String? = null,
        vararg bodyPredicates: (JSONRPCRequest) -> Boolean,
    ): BuildingStep<JSONRPCRequest> = mokksy.method(
        configuration = StubConfiguration(removeAfterMatch = true),
        httpMethod = httpMethod,
        requestType = JSONRPCRequest::class,
    ) {
        path("/mcp")
        expectedSessionId?.let {
            containsHeader(MCP_SESSION_ID_HEADER, it)
        }
        bodyMatchesPredicate(
            description = "JSON-RPC version is '2.0'",
            predicate =
            {
                it!!.jsonrpc == "2.0"
            },
        )
        bodyMatchesPredicate(
            description = "JSON-RPC Method should be '$jsonRpcMethod'",
            predicate =
            {
                it!!.method == jsonRpcMethod
            },
        )
        bodyPredicates.forEach { predicate ->
            bodyMatchesPredicate(predicate = { predicate.invoke(it!!) })
        }
    }

    fun handleWithResult(
        httpMethod: HttpMethod = HttpMethod.Post,
        jsonRpcMethod: String,
        expectedSessionId: String? = null,
        sessionId: String,
        contentType: ContentType = ContentType.Application.Json,
        statusCode: HttpStatusCode = HttpStatusCode.OK,
        vararg bodyPredicates: (JSONRPCRequest) -> Boolean,
        result: () -> JsonObject,
    ) {
        onJSONRPCRequest(
            httpMethod = httpMethod,
            jsonRpcMethod = jsonRpcMethod,
            expectedSessionId = expectedSessionId,
            bodyPredicates = bodyPredicates,
        ) respondsWith {
            val requestId = when (request.body.id) {
                is RequestId.NumberId -> (request.body.id as RequestId.NumberId).value.toString()
                is RequestId.StringId -> "\"${(request.body.id as RequestId.StringId).value}\""
            }
            val resultObject = result.invoke()
            // language=json
            body = """
             {
              "jsonrpc": "2.0",
              "id": $requestId,
              "result": $resultObject
            }
            """.trimIndent()
            this.contentType = contentType
            headers += MCP_SESSION_ID_HEADER to sessionId
            httpStatus = statusCode
        }
    }

    fun handleWithResult(
        httpMethod: HttpMethod = HttpMethod.Post,
        jsonRpcMethod: String,
        expectedSessionId: String? = null,
        sessionId: String,
        contentType: ContentType = ContentType.Application.Json,
        statusCode: HttpStatusCode = HttpStatusCode.OK,
        vararg bodyPredicates: (JSONRPCRequest) -> Boolean,
        result: String,
    ) {
        handleWithResult(
            httpMethod = httpMethod,
            jsonRpcMethod = jsonRpcMethod,
            expectedSessionId = expectedSessionId,
            sessionId = sessionId,
            contentType = contentType,
            statusCode = statusCode,
            bodyPredicates = bodyPredicates,
            result = {
                Json.parseToJsonElement(result).jsonObject
            },
        )
    }

    fun handleJSONRPCRequest(
        httpMethod: HttpMethod = HttpMethod.Post,
        jsonRpcMethod: String,
        expectedSessionId: String? = null,
        sessionId: String,
        contentType: ContentType = ContentType.Application.Json,
        statusCode: HttpStatusCode = HttpStatusCode.OK,
        vararg bodyPredicates: (JSONRPCRequest?) -> Boolean,
        bodyBuilder: () -> String = { "" },
    ) {
        onJSONRPCRequest(
            httpMethod = httpMethod,
            jsonRpcMethod = jsonRpcMethod,
            expectedSessionId = expectedSessionId,
            bodyPredicates = bodyPredicates,
        ) respondsWith {
            body = bodyBuilder.invoke()
            this.contentType = contentType
            headers += MCP_SESSION_ID_HEADER to sessionId
            httpStatus = statusCode
        }
    }

    fun onSubscribe(httpMethod: HttpMethod = HttpMethod.Post, sessionId: String): BuildingStep<Any> = mokksy.method(
        httpMethod = httpMethod,
        name = "MCP GETs",
        requestType = Any::class,
    ) {
        path("/mcp")
        containsHeader(MCP_SESSION_ID_HEADER, sessionId)
        containsHeader("Accept", "application/json,text/event-stream")
        containsHeader("Cache-Control", "no-store")
    }

    fun handleSubscribeWithGet(sessionId: String, block: () -> Flow<ServerSentEvent>) {
        onSubscribe(
            httpMethod = HttpMethod.Get,
            sessionId = sessionId,
        ) respondsWithSseStream {
            headers += MCP_SESSION_ID_HEADER to sessionId
            this.flow = block.invoke()
        }
    }

    fun mockUnsubscribeRequest(sessionId: String) {
        mokksy.delete(
            configuration = StubConfiguration(removeAfterMatch = true),
            requestType = JSONRPCRequest::class,
        ) {
            path("/mcp")
            containsHeader(MCP_SESSION_ID_HEADER, sessionId)
        } respondsWith {
            body = null
        }
    }
}
