/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.notification

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.exception.NotificationResolverException
import io.element.android.libraries.matrix.api.exception.isNetworkError
import io.element.android.libraries.matrix.api.notification.NotificationContent
import io.element.android.libraries.matrix.api.timeline.item.event.TextMessageType
import io.element.android.libraries.matrix.impl.fixtures.factories.aRustBatchNotificationResultOk
import io.element.android.libraries.matrix.impl.fixtures.factories.aRustNotificationEventTimeline
import io.element.android.libraries.matrix.impl.fixtures.factories.aRustNotificationItem
import io.element.android.libraries.matrix.impl.fixtures.factories.aRustTimelineEventContentMessageLike
import io.element.android.libraries.matrix.impl.fixtures.fakes.FakeFfiNotificationClient
import io.element.android.libraries.matrix.impl.fixtures.fakes.FakeFfiRoom
import io.element.android.libraries.matrix.impl.fixtures.fakes.FakeFfiTimelineEvent
import io.element.android.libraries.matrix.test.AN_EVENT_ID
import io.element.android.libraries.matrix.test.AN_EVENT_ID_2
import io.element.android.libraries.matrix.test.A_MESSAGE
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.A_USER_ID
import io.element.android.libraries.matrix.test.A_USER_ID_2
import io.element.android.services.toolbox.api.systemclock.SystemClock
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.matrix.rustcomponents.sdk.ClientException as RustClientException
import org.matrix.rustcomponents.sdk.MessageLikeEventContent
import org.matrix.rustcomponents.sdk.NotificationClient
import org.matrix.rustcomponents.sdk.NotificationStatus
import org.matrix.rustcomponents.sdk.TimelineEventContent

class RustNotificationServiceTest {
    @Test
    fun test() = runTest {
        val notificationClient = FakeFfiNotificationClient(
            notificationItemResult = mapOf(AN_EVENT_ID.value to aRustBatchNotificationResultOk()),
        )
        val sut = createRustNotificationService(
            notificationClient = notificationClient,
        )
        val result = sut.getNotifications(mapOf(A_ROOM_ID to listOf(AN_EVENT_ID))).getOrThrow()[AN_EVENT_ID]!!.getOrThrow()
        assertThat(result.isEncrypted).isTrue()
        assertThat(result.content).isEqualTo(
            NotificationContent.MessageLike.RoomMessage(
                senderId = A_USER_ID_2,
                messageType = TextMessageType(
                    body = A_MESSAGE,
                    formatted = null,
                )
            )
        )
    }

    @Test
    fun `test mapping invalid item only drops that item`() = runTest {
        val error = IllegalStateException("This event content is not supported")
        val faultyEvent = object : FakeFfiTimelineEvent() {
            override fun content(): TimelineEventContent {
                throw error
            }
        }
        val notificationClient = FakeFfiNotificationClient(
            notificationItemResult = mapOf(
                AN_EVENT_ID.value to aRustBatchNotificationResultOk(
                    notificationStatus = NotificationStatus.Event(aRustNotificationItem(aRustNotificationEventTimeline(faultyEvent)))
                ),
                AN_EVENT_ID_2.value to aRustBatchNotificationResultOk()
            ),
        )
        val sut = createRustNotificationService(
            notificationClient = notificationClient,
        )
        val result = sut.getNotifications(mapOf(A_ROOM_ID to listOf(AN_EVENT_ID, AN_EVENT_ID_2))).getOrThrow()
        val exception = result[AN_EVENT_ID]!!.exceptionOrNull()
        assertThat(exception).isEqualTo(error)

        val successfulResult = result[AN_EVENT_ID_2]
        assertThat(successfulResult?.isSuccess).isTrue()
    }

    // 2ecure: a reaction comes with the message it reacts to, so the resolver can tell whose message it was.
    @Test
    fun `a reaction is completed with its key and the message reacted to`() = runTest {
        val reactionEvent = FakeFfiTimelineEvent(
            timelineEventContent = aRustTimelineEventContentMessageLike(
                content = MessageLikeEventContent.ReactionContent(relatedEventId = AN_EVENT_ID_2.value),
            ),
            senderId = A_USER_ID_2.value,
        )
        val rawEvent = """{"type": "m.reaction", "content": {"m.relates_to": {"rel_type": "m.annotation", "event_id": "${AN_EVENT_ID_2.value}", "key": "👍"}}}"""
        val loadedEventIds = mutableListOf<String>()
        val notificationClient = FakeFfiNotificationClient(
            notificationItemResult = mapOf(
                AN_EVENT_ID.value to aRustBatchNotificationResultOk(
                    notificationStatus = NotificationStatus.Event(
                        aRustNotificationItem(event = aRustNotificationEventTimeline(reactionEvent), rawEvent = rawEvent)
                    )
                ),
            ),
            getRoomResult = { roomId ->
                assertThat(roomId).isEqualTo(A_ROOM_ID.value)
                FakeFfiRoom(
                    loadOrFetchEventLambda = { eventId ->
                        loadedEventIds += eventId
                        FakeFfiTimelineEvent(senderId = A_USER_ID.value)
                    },
                )
            },
        )
        val sut = createRustNotificationService(notificationClient = notificationClient)
        val result = sut.getNotifications(mapOf(A_ROOM_ID to listOf(AN_EVENT_ID))).getOrThrow()[AN_EVENT_ID]!!.getOrThrow()
        assertThat(loadedEventIds).containsExactly(AN_EVENT_ID_2.value)
        assertThat(result.content).isEqualTo(
            NotificationContent.MessageLike.ReactionContent(
                relatedEventId = AN_EVENT_ID_2.value,
                senderId = A_USER_ID_2,
                key = "👍",
                relatedEventSenderId = A_USER_ID,
                relatedEventContent = NotificationContent.MessageLike.RoomMessage(
                    senderId = A_USER_ID,
                    messageType = TextMessageType(body = A_MESSAGE, formatted = null),
                ),
            )
        )
    }

    @Test
    fun `a reaction whose message can't be loaded stays as it is`() = runTest {
        val reactionEvent = FakeFfiTimelineEvent(
            timelineEventContent = aRustTimelineEventContentMessageLike(
                content = MessageLikeEventContent.ReactionContent(relatedEventId = AN_EVENT_ID_2.value),
            ),
        )
        val notificationClient = FakeFfiNotificationClient(
            notificationItemResult = mapOf(
                AN_EVENT_ID.value to aRustBatchNotificationResultOk(
                    notificationStatus = NotificationStatus.Event(aRustNotificationItem(event = aRustNotificationEventTimeline(reactionEvent)))
                ),
            ),
            getRoomResult = { FakeFfiRoom(loadOrFetchEventLambda = { error("Event not found") }) },
        )
        val sut = createRustNotificationService(notificationClient = notificationClient)
        val result = sut.getNotifications(mapOf(A_ROOM_ID to listOf(AN_EVENT_ID))).getOrThrow()[AN_EVENT_ID]!!.getOrThrow()
        assertThat(result.content).isEqualTo(
            NotificationContent.MessageLike.ReactionContent(relatedEventId = AN_EVENT_ID_2.value, senderId = A_USER_ID_2)
        )
    }

    @Test
    fun `an own reaction loads nothing`() = runTest {
        val reactionEvent = FakeFfiTimelineEvent(
            timelineEventContent = aRustTimelineEventContentMessageLike(
                content = MessageLikeEventContent.ReactionContent(relatedEventId = AN_EVENT_ID_2.value),
            ),
            senderId = A_SESSION_ID.value,
        )
        val notificationClient = FakeFfiNotificationClient(
            notificationItemResult = mapOf(
                AN_EVENT_ID.value to aRustBatchNotificationResultOk(
                    notificationStatus = NotificationStatus.Event(aRustNotificationItem(event = aRustNotificationEventTimeline(reactionEvent)))
                ),
            ),
            getRoomResult = { error("Nothing should be loaded for an own reaction") },
        )
        val sut = createRustNotificationService(notificationClient = notificationClient)
        val result = sut.getNotifications(mapOf(A_ROOM_ID to listOf(AN_EVENT_ID))).getOrThrow()[AN_EVENT_ID]!!.getOrThrow()
        assertThat(result.content).isEqualTo(
            NotificationContent.MessageLike.ReactionContent(relatedEventId = AN_EVENT_ID_2.value, senderId = A_SESSION_ID)
        )
    }

    @Test
    fun `a reaction whose message can't be loaded for a network error is tried again later`() = runTest {
        val reactionEvent = FakeFfiTimelineEvent(
            timelineEventContent = aRustTimelineEventContentMessageLike(
                content = MessageLikeEventContent.ReactionContent(relatedEventId = AN_EVENT_ID_2.value),
            ),
        )
        val notificationClient = FakeFfiNotificationClient(
            notificationItemResult = mapOf(
                AN_EVENT_ID.value to aRustBatchNotificationResultOk(
                    notificationStatus = NotificationStatus.Event(aRustNotificationItem(event = aRustNotificationEventTimeline(reactionEvent)))
                ),
            ),
            getRoomResult = {
                FakeFfiRoom(
                    loadOrFetchEventLambda = {
                        throw RustClientException.Generic(msg = "error sending request for url (https://matrix.example.org)", details = null)
                    },
                )
            },
        )
        val sut = createRustNotificationService(notificationClient = notificationClient)
        val exception = sut.getNotifications(mapOf(A_ROOM_ID to listOf(AN_EVENT_ID))).getOrThrow()[AN_EVENT_ID]!!.exceptionOrNull()
        assertThat(exception?.isNetworkError()).isTrue()
    }

    @Test
    fun `test unable to resolve event`() = runTest {
        val notificationClient = FakeFfiNotificationClient(
            notificationItemResult = emptyMap(),
        )
        val sut = createRustNotificationService(
            notificationClient = notificationClient,
        )
        val exception = sut.getNotifications(mapOf(A_ROOM_ID to listOf(AN_EVENT_ID))).getOrThrow()[AN_EVENT_ID]!!.exceptionOrNull()
        assertThat(exception).isInstanceOf(NotificationResolverException::class.java)
    }

    @Test
    fun `close should invoke the close method of the service`() = runTest {
        val closeResult = lambdaRecorder<Unit> { }
        val notificationClient = FakeFfiNotificationClient(
            closeResult = closeResult,
        )
        val sut = createRustNotificationService(
            notificationClient = notificationClient,
        )
        sut.close()
        closeResult.assertions().isCalledOnce()
    }

    private fun TestScope.createRustNotificationService(
        notificationClient: NotificationClient = FakeFfiNotificationClient(),
        clock: SystemClock = FakeSystemClock(),
    ) =
        RustNotificationService(
            sessionId = A_SESSION_ID,
            notificationClient = notificationClient,
            dispatchers = testCoroutineDispatchers(),
            clock = clock,
        )
}
