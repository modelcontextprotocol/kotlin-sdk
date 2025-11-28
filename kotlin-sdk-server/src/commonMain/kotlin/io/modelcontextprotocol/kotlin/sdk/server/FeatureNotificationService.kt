package io.modelcontextprotocol.kotlin.sdk.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.types.Notification
import io.modelcontextprotocol.kotlin.sdk.types.PromptListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ResourceListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.ToolListChangedNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

internal class FeatureNotificationService {
    private val notifications = MutableSharedFlow<Notification>()
    private val notificationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val notificationSessionFeatureJobs: MutableMap<ServerSessionKey, Job> = mutableMapOf()
    private val notificationSessionResourceJobs: MutableMap<Pair<ServerSessionKey, FeatureKey>, Job> = mutableMapOf()

    private val logger = KotlinLogging.logger {}

    private val toolFeatureListener: FeatureListener by lazy {
        object : FeatureListener {
            override fun onListChanged() {
                logger.debug { "Emitting tool list changed notification" }
                emit(ToolListChangedNotification())
            }

            override fun onFeatureUpdated(featureKey: FeatureKey) {
                logger.debug { "Skipping update for tool feature key: $featureKey" }
            }
        }
    }

    private val promptFeatureListener: FeatureListener by lazy {
        object : FeatureListener {
            override fun onListChanged() {
                logger.debug { "Emitting prompt list changed notification" }
                emit(PromptListChangedNotification())
            }

            override fun onFeatureUpdated(featureKey: FeatureKey) {
                logger.debug { "Skipping update for prompt feature key: $featureKey" }
            }
        }
    }

    private val resourceFeatureListener: FeatureListener by lazy {
        object : FeatureListener {
            override fun onListChanged() {
                logger.debug { "Emitting resource list changed notification" }
                emit(ResourceListChangedNotification())
            }

            override fun onFeatureUpdated(featureKey: FeatureKey) {
                logger.debug { "Emitting resource updated notification for feature key: $featureKey" }
                emit(ResourceUpdatedNotification(ResourceUpdatedNotificationParams(uri = featureKey)))
            }
        }
    }

    internal fun getToolFeatureListener(): FeatureListener = toolFeatureListener
    internal fun getPromptFeatureListener(): FeatureListener = promptFeatureListener
    internal fun geResourceFeatureListener(): FeatureListener = resourceFeatureListener

    internal fun subscribeToListChangedNotification(session: ServerSession) {
        logger.debug { "Subscribing to list changed notifications for sessionId: ${session.sessionId}" }
        notificationSessionFeatureJobs[session.sessionId] = notificationScope.launch {
            notifications.collect { notification ->
                when (notification) {
                    is PromptListChangedNotification -> session.notification(notification)

                    is ResourceListChangedNotification -> session.notification(notification)

                    is ToolListChangedNotification -> session.notification(notification)

                    else -> logger.debug {
                        "Notification not handled for sessionId ${session.sessionId}: $notification"
                    }
                }
            }
        }
        logger.debug { "Subscribed to list changed notifications for sessionId: ${session.sessionId}" }
    }

    internal fun unsubscribeFromListChangedNotification(session: ServerSession) {
        logger.debug { "Unsubscribing from list changed notifications for sessionId: ${session.sessionId}" }
        notificationSessionFeatureJobs[session.sessionId]?.cancel()
        notificationSessionFeatureJobs.remove(session.sessionId)
        logger.debug { "Unsubscribed from list changed notifications for sessionId: ${session.sessionId}" }
    }

    internal fun subscribeToResourceUpdateNotifications(session: ServerSession, resourceKey: FeatureKey) {
        logger.debug { "Subscribing to resource update notifications for sessionId: ${session.sessionId}" }
        notificationSessionResourceJobs[session.sessionId to resourceKey] = notificationScope.launch {
            notifications.collect { notification ->
                when (notification) {
                    is ResourceUpdatedNotification -> {
                        if (notification.params.uri == resourceKey) {
                            session.notification(notification)
                        }
                    }

                    else -> logger.debug {
                        "Notification not handled for session for sessionId ${session.sessionId}: $notification"
                    }
                }
            }
        }
        logger.debug { "Subscribed to resource update notifications for sessionId: ${session.sessionId}" }
    }

    internal fun unsubscribeFromResourceUpdateNotifications(session: ServerSession, resourceKey: FeatureKey) {
        logger.debug { "Unsubscribing from resourcec update notifications for sessionId: ${session.sessionId}" }
        notificationSessionResourceJobs[session.sessionId to resourceKey]?.cancel()
        notificationSessionResourceJobs.remove(session.sessionId to resourceKey)
        logger.debug { "Unsubscribed from resourcec update notifications for sessionId: ${session.sessionId}" }
    }

    private fun emit(notification: Notification) {
        notificationScope.launch {
            notifications.emit(notification)
        }
    }
}
