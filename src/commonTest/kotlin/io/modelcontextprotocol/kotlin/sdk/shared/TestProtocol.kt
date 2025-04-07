package io.modelcontextprotocol.kotlin.sdk.shared

import io.modelcontextprotocol.kotlin.sdk.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.Method

internal class TestProtocol : Protocol(options = null) {
    var receivedNotification: JSONRPCNotification? = null
    var errorHandlerCalled = false
    var lastError: Throwable? = null

    init {
        notificationHandlers["test/notification"] = { notification ->
            receivedNotification = notification
        }

        fallbackNotificationHandler = { notification ->
            receivedNotification = notification
        }
    }

    override fun assertCapabilityForMethod(method: Method) {}
    override fun assertNotificationCapability(method: Method) {}
    override fun assertRequestHandlerCapability(method: Method) {}

    override fun onError(cause: Throwable) {
        errorHandlerCalled = true
        lastError = cause
    }
}
