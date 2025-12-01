package io.modelcontextprotocol.kotlin.sdk.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.types.Notification
import io.modelcontextprotocol.kotlin.sdk.types.PromptListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ResourceListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.ToolListChangedNotification
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.getAndUpdate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Represents an event for the notification service.
 *
 * @property timestamp A timestamp for the event.
 */
private sealed class NotificationEvent(open val timestamp: Long)

/**
 * Represents an event for a notification.
 *
 * @property notification The notification associated with the event.
 */
private class SendEvent(override val timestamp: Long, val notification: Notification) : NotificationEvent(timestamp)

/** Represents an event marking the end of notification processing. */
private class EndEvent(override val timestamp: Long) : NotificationEvent(timestamp)

/**
 * Represents a job that handles session-specific notifications, processing events
 * and delivering relevant notifications to the associated session.
 *
 * This class listens to a stream of notification events and processes them
 * based on the event type and the resource subscriptions associated with the session.
 * It allows subscribing to or unsubscribing from specific resource keys for granular
 * notification handling. The job can also be canceled to stop processing further events.
 * Notification with timestamps older than the starting timestamp are skipped.
 */
private class SessionNotificationJob {
    private val job: Job
    private val resourceSubscriptions = atomic(persistentMapOf<FeatureKey, Long>())
    private val logger = KotlinLogging.logger {}

    constructor(
        session: ServerSession,
        scope: CoroutineScope,
        events: SharedFlow<NotificationEvent>,
        fromTimestamp: Long,
    ) {
        logger.info { "Starting notification job from timestamp $fromTimestamp for sessionId: ${session.sessionId} " }
        job = scope.launch {
            events.takeWhile { it !is EndEvent }.collect { event ->
                when (event) {
                    is SendEvent -> {
                        if (event.timestamp >= fromTimestamp) {
                            when (val notification = event.notification) {
                                is PromptListChangedNotification -> {
                                    logger.info {
                                        "Sending prompt list changed notification for sessionId: ${session.sessionId}"
                                    }
                                    session.notification(notification)
                                }

                                is ResourceListChangedNotification -> {
                                    logger.info {
                                        "Sending resourse list changed notification for sessionId: ${session.sessionId}"
                                    }
                                    session.notification(notification)
                                }

                                is ToolListChangedNotification -> {
                                    logger.info {
                                        "Sending tool list changed notification for sessionId: ${session.sessionId}"
                                    }
                                    session.notification(notification)
                                }

                                is ResourceUpdatedNotification -> {
                                    resourceSubscriptions.value[notification.params.uri]?.let { resourceFromTimestamp ->
                                        if (event.timestamp >= resourceFromTimestamp) {
                                            logger.info {
                                                "Sending resource updated notification for resource " +
                                                    "${notification.params.uri} " +
                                                    "to sessionId: ${session.sessionId}"
                                            }
                                            session.notification(notification)
                                        } else {
                                            logger.info {
                                                "Skipping resource updated notification for resource " +
                                                    "${notification.params.uri} " +
                                                    "as it is older than subscription timestamp $resourceFromTimestamp"
                                            }
                                        }
                                    } ?: run {
                                        logger.info {
                                            "No subscription for resource ${notification.params.uri}. " +
                                                "Skipping notification: $notification"
                                        }
                                    }
                                }

                                else -> {
                                    logger.warn { "Skipping notification: $notification" }
                                }
                            }
                        } else {
                            logger.info {
                                "Skipping event with id: ${event.timestamp} " +
                                    "as it is older than startingEventId $fromTimestamp: $event"
                            }
                        }
                    }

                    else -> {
                        logger.warn { "Skipping event: $event" }
                    }
                }
            }
        }
    }

    /**
     * Subscribes to a resource identified by the given feature key.
     *
     * @param resourceKey The key representing the resource to subscribe to.
     * @param timestamp The timestamp of the subscription.
     */
    fun subscribe(resourceKey: FeatureKey, timestamp: Long) {
        resourceSubscriptions.getAndUpdate { it.put(resourceKey, timestamp) }
    }

    /**
     * Unsubscribes from a resource identified by the given feature key.
     *
     * @param resourceKey The key representing the resource to unsubscribe from.
     */
    fun unsubscribe(resourceKey: FeatureKey) {
        resourceSubscriptions.getAndUpdate { it.remove(resourceKey) }
    }

    suspend fun join() {
        job.join()
    }

    fun cancel() {
        job.cancel()
    }
}

/**
 * Service responsible for managing and emitting notifications related to feature changes.
 *
 * This service facilitates notification subscriptions for different sessions and supports managing
 * listeners for feature-related events. Notifications include changes in tool lists, prompt lists,
 * resource lists, and updates to specific resources.
 *
 * This class operates on a background coroutine scope to handle notifications asynchronously.
 * It maintains jobs associated with sessions and features for controlling active subscriptions.
 *
 * Key Responsibilities:
 * - Emit notifications for various feature-related events.
 * - Provide listeners for handling feature change events.
 * - Allow clients to subscribe or unsubscribe from specific notifications.
 *
 * Notifications managed:
 * - Tool list change notifications.
 * - Prompt list change notifications.
 * - Resource list change notifications.
 * - Resource updates pertaining to specific resources.
 */
internal class FeatureNotificationService(
    @OptIn(ExperimentalTime::class)
    private val clock: Clock = Clock.System,
) {
    /** Coroutine scope used to handle asynchronous notifications. */
    private val notificationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Flag indicating whether the service is closing. */
    private val closingService = atomic(false)

    /** Shared flow used to emit events within the feature notification service. */
    private val notificationEvents = MutableSharedFlow<NotificationEvent>(
        extraBufferCapacity = 100,
        replay = 0,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )

    /** Active emit jobs. */
    private val activeEmitJobs = atomic(persistentSetOf<Job>())

    /** Notification jobs associated with sessions. */
    private val sessionNotificationJobs = atomic(persistentMapOf<ServerSessionKey, SessionNotificationJob>())

    private val logger = KotlinLogging.logger {}

    /** Listener for tool feature events. */
    private val toolListChangedListener: FeatureListener by lazy {
        object : FeatureListener {
            override fun onFeatureUpdated(featureKey: FeatureKey) {
                logger.info { "Emitting tool list changed notification" }
                emit(ToolListChangedNotification())
            }
        }
    }

    /** Listener for prompt feature events. */
    private val promptListChangeListener: FeatureListener by lazy {
        object : FeatureListener {
            override fun onFeatureUpdated(featureKey: FeatureKey) {
                logger.info { "Emitting prompt list changed notification" }
                emit(PromptListChangedNotification())
            }
        }
    }

    /** Listener for resource feature events. */
    private val resourceListChangedListener: FeatureListener by lazy {
        object : FeatureListener {
            override fun onFeatureUpdated(featureKey: FeatureKey) {
                logger.info { "Emitting resource list changed notification" }
                emit(ResourceListChangedNotification())
            }
        }
    }

    /** Listener for resource update events. */
    private val resourceUpdatedListener: FeatureListener by lazy {
        object : FeatureListener {
            override fun onFeatureUpdated(featureKey: FeatureKey) {
                logger.info { "Emitting resource updated notification for feature key: $featureKey" }
                emit(ResourceUpdatedNotification(ResourceUpdatedNotificationParams(uri = featureKey)))
            }
        }
    }

    /** Listener for the tool list changed events. */
    internal fun getToolListChangedListener(): FeatureListener = toolListChangedListener

    /** Listener for the prompt list changed events. */
    internal fun getPromptListChangedListener(): FeatureListener = promptListChangeListener

    /** Listener for the resource list changed events. */
    internal fun getResourceListChangedListener(): FeatureListener = resourceListChangedListener

    /** Listener for resource update events. */
    internal fun getResourceUpdateListener(): FeatureListener = resourceUpdatedListener

    /**
     * Subscribes session to list changed notifications for all features and resource update notifications.
     * For each session the job is created and stored until the [unsubscribeSession] method is called.
     * In case of session already subscribed to list changed notifications, the method will skip the subscription and
     * continue to send notification using the existing job.
     *
     * @param session The session to subscribe.
     */
    internal fun subscribeSession(session: ServerSession) {
        logger.info { "Subscribing session for notifications sessionId: ${session.sessionId}" }

        val timestamp = getCurrentTimestamp()
        if (closingService.value) {
            logger.warn { "Skipping subscription notification as service is closing: ${session.sessionId}" }
            return
        }

        sessionNotificationJobs.getAndUpdate {
            if (it.containsKey(session.sessionId)) {
                logger.info { "Session already subscribed: ${session.sessionId}" }
                return@getAndUpdate it
            } else {
                it.put(
                    session.sessionId,
                    SessionNotificationJob(
                        session = session,
                        scope = notificationScope,
                        events = notificationEvents,
                        // Save the first event id to process, as notification can be emitted after the subscription
                        fromTimestamp = timestamp,
                    ),
                )
            }
        }

        logger.info { "Subscribed session for notifications sessionId: ${session.sessionId}" }
    }

    /**
     * Unsubscribes a session from list changed notifications for all features.
     * Cancels and removes the job associated with the given session's notifications.
     *
     * @param session The session to unsubscribe from list changed notifications.
     */
    internal fun unsubscribeSession(session: ServerSession) {
        logger.info { "Unsubscribing from list changed notifications for sessionId: ${session.sessionId}" }
        sessionNotificationJobs.getAndUpdate {
            it[session.sessionId]?.cancel()
            it.remove(session.sessionId)
        }
        logger.info { "Unsubscribed from list changed notifications for sessionId: ${session.sessionId}" }
    }

    /**
     * Subscribes a session to notifications for resource updates pertaining to the given resource key.
     *
     * @param session The session to subscribe.
     * @param resourceKey The resource key to subscribe to.
     */
    internal fun subscribeToResourceUpdate(session: ServerSession, resourceKey: FeatureKey) {
        logger.info { "Subscribing to resource $resourceKey update notifications for sessionId: ${session.sessionId}" }
        // Set starting event id for resources notifications to skip events emitted before the subscription
        sessionNotificationJobs.value[session.sessionId]?.subscribe(resourceKey, getCurrentTimestamp())
        logger.info { "Subscribed to resource $resourceKey update notifications for sessionId: ${session.sessionId}" }
    }

    /**
     * Unsubscribes a session from notifications for resource updates pertaining to the given resource key.
     *
     * @param session The session to unsubscribe from.
     * @param resourceKey The resource key to unsubscribe from.
     */
    internal fun unsubscribeFromResourceUpdate(session: ServerSession, resourceKey: FeatureKey) {
        logger.info {
            "Unsubscribing from resource $resourceKey update notifications for sessionId: ${session.sessionId}"
        }
        sessionNotificationJobs.value[session.sessionId]?.unsubscribe(resourceKey)
        logger.info {
            "Unsubscribed from resource $resourceKey update notifications for sessionId: ${session.sessionId}"
        }
    }

    /** Emits a notification to all active sessions. */
    private fun emit(notification: Notification) {
        // Create a timestamp before emit to ensure notifications are processed in order
        val timestamp = getCurrentTimestamp()
        if (closingService.value) {
            logger.warn { "Skipping emitting notification as service is closing: $notification" }
            return
        }

        logger.info { "Emitting notification $timestamp: $notification" }

        // Launching emit lazily to put it to the jobs queue before the completion
        val job = notificationScope.launch(start = CoroutineStart.LAZY) {
            logger.info { "Actually emitting notification $timestamp: $notification" }
            notificationEvents.emit(SendEvent(timestamp, notification))
            logger.info { "Notification emitted $timestamp: $notification" }
        }

        // Add job to set before starting
        activeEmitJobs.getAndUpdate { it.add(job) }

        // Register completion
        job.invokeOnCompletion {
            activeEmitJobs.getAndUpdate { it.remove(job) }
        }

        // Start the job after it's safely added
        job.start()
    }

    /** Returns the current timestamp in milliseconds. */
    @OptIn(ExperimentalTime::class)
    private fun getCurrentTimestamp(): Long = clock.now().toEpochMilliseconds()

    suspend fun close() {
        logger.info { "Closing feature notification service" }
        closingService.compareAndSet(false, update = true)

        // Making sure all emit jobs are completed
        activeEmitJobs.value.joinAll()

        // Emitting end event to complete all session notification jobs
        notificationScope.launch {
            logger.info { "Emitting end event" }
            notificationEvents.emit(EndEvent(getCurrentTimestamp()))
            logger.info { "End event emitted" }
        }.join()

        // Making sure all session notification jobs are completed (after receiving end event)
        sessionNotificationJobs.value.values.forEach { it.join() }
        // Cancelling notification scope to stop processing further events
        notificationScope.cancel()
    }
}
