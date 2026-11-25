/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.notification

import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.exception.NotificationResolverException
import io.element.android.libraries.matrix.api.exception.isNetworkError
import io.element.android.libraries.matrix.api.notification.GetNotificationDataResult
import io.element.android.libraries.matrix.api.notification.NotificationContent
import io.element.android.libraries.matrix.api.notification.NotificationData
import io.element.android.libraries.matrix.api.notification.NotificationService
import io.element.android.libraries.matrix.impl.exception.mapClientException
import io.element.android.services.toolbox.api.systemclock.SystemClock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.matrix.rustcomponents.sdk.BatchNotificationResult
import org.matrix.rustcomponents.sdk.NotificationClient
import org.matrix.rustcomponents.sdk.NotificationItemsRequest
import org.matrix.rustcomponents.sdk.NotificationStatus
import org.matrix.rustcomponents.sdk.use
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

// 2ecure: how long a reaction waits for the message it reacts to, so it doesn't hold up the other notifications.
private val REACTED_EVENT_TIMEOUT = 10.seconds

class RustNotificationService(
    private val sessionId: SessionId,
    private val notificationClient: NotificationClient,
    private val dispatchers: CoroutineDispatchers,
    clock: SystemClock,
) : NotificationService {
    private val notificationMapper: NotificationMapper = NotificationMapper(clock)
    private val timelineEventMapper = TimelineEventToNotificationContentMapper()

    override suspend fun getNotifications(
        ids: Map<RoomId, List<EventId>>
    ): GetNotificationDataResult = withContext(dispatchers.io) {
        runCatchingExceptions {
            val requests = ids.map { (roomId, eventIds) ->
                NotificationItemsRequest(
                    roomId = roomId.value,
                    eventIds = eventIds.map { it.value }
                )
            }
            val items = notificationClient.getNotifications(requests)
            buildMap {
                val eventIds = requests.flatMap { it.eventIds }.distinct()
                for (rawEventId in eventIds) {
                    val roomId = RoomId(requests.find { it.eventIds.contains(rawEventId) }?.roomId!!)
                    val eventId = EventId(rawEventId)
                    items[rawEventId].use { result ->
                        when (result) {
                            is BatchNotificationResult.Ok -> {
                                when (val status = result.status) {
                                    is NotificationStatus.Event -> {
                                        val result = notificationMapper.map(sessionId, eventId, roomId, status.item)
                                            .fold(onSuccess = { it.withReactedEvent(roomId) }, onFailure = { Result.failure(it) })
                                        result.onFailure { Timber.e(it, "Could not map notification event $eventId") }
                                        put(eventId, result)
                                    }
                                    is NotificationStatus.EventNotFound -> {
                                        Timber.e("Could not retrieve event for notification with $eventId - event not found")
                                        put(eventId, Result.failure(NotificationResolverException.EventNotFound))
                                    }
                                    is NotificationStatus.EventFilteredOut -> {
                                        Timber.d("Could not retrieve event for notification with $eventId - event filtered out")
                                        put(eventId, Result.failure(NotificationResolverException.EventFilteredOut))
                                    }
                                    NotificationStatus.EventRedacted -> {
                                        Timber.d("Could not retrieve event for notification with $eventId - event redacted")
                                        put(eventId, Result.failure(NotificationResolverException.EventRedacted))
                                    }
                                }
                            }
                            is BatchNotificationResult.Error -> {
                                Timber.e("Error while retrieving notification with $rawEventId - ${result.message}")
                                put(
                                    eventId,
                                    Result.failure(NotificationResolverException.UnknownError(result.message))
                                )
                            }
                            null -> {
                                Timber.e("The notification data for $rawEventId was not in the retrieved results. This is unexpected.")
                                put(
                                    eventId,
                                    Result.failure(NotificationResolverException.UnknownError("Notification data not found"))
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 2ecure: a reaction only gets through the push rules when the account turned on notifications for reactions to its
     * own messages. Whether to show it, and what to show, depends on the message reacted to: who sent it and what it
     * said. Loaded from the event cache, or else from the server, for at most [REACTED_EVENT_TIMEOUT]; without it the
     * reaction stays as it is (and isn't shown). A network error fails the item, so the push is tried again later.
     */
    private suspend fun NotificationData.withReactedEvent(roomId: RoomId): Result<NotificationData> {
        val reaction = content as? NotificationContent.MessageLike.ReactionContent ?: return Result.success(this)
        // Own reactions are never shown: nothing to load.
        if (reaction.senderId == null || reaction.senderId == sessionId) return Result.success(this)
        val reactedEvent = runCatchingExceptions {
            withTimeoutOrNull(REACTED_EVENT_TIMEOUT) {
                notificationClient.getRoom(roomId.value)?.use { room ->
                    val event = room.loadOrFetchEvent(reaction.relatedEventId)
                    val senderId = runCatchingExceptions { UserId(event.senderId()) }
                    // The mapper closes the event.
                    val content = timelineEventMapper.map(event).getOrNull()
                    senderId.getOrThrow() to content
                }
            }
        }
        reactedEvent.exceptionOrNull()?.let { error ->
            val clientException = error.mapClientException()
            if (clientException.isNetworkError()) {
                Timber.w("Network error while loading the event reacted to by $eventId, trying again later")
                return Result.failure(clientException)
            }
            Timber.w(error, "Could not load the event reacted to by $eventId")
        }
        val (relatedEventSenderId, relatedEventContent) = reactedEvent.getOrNull() ?: return Result.success(this)
        return Result.success(
            copy(content = reaction.copy(relatedEventSenderId = relatedEventSenderId, relatedEventContent = relatedEventContent))
        )
    }

    fun close() {
        notificationClient.close()
    }
}
