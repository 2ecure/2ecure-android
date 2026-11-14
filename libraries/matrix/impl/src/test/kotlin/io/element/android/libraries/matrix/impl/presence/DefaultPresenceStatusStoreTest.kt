/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.presence

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.presence.PresenceStatus
import io.element.android.libraries.matrix.api.presence.PresenceStatusStore
import io.element.android.libraries.matrix.test.A_USER_ID
import io.element.android.libraries.matrix.test.A_USER_ID_2
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultPresenceStatusStoreTest {
    @Test
    fun `2ecure - the homeserver's answer becomes online or last seen`() {
        assertThat(parsePresenceStatus("""{"presence":"online","last_active_ago":5000,"currently_active":true}""", now = 1_000_000))
            .isEqualTo(PresenceStatus(isOnline = true, lastActiveTimestamp = 995_000))
        assertThat(parsePresenceStatus("""{"presence":"offline"}""", now = 1_000_000))
            .isEqualTo(PresenceStatus(isOnline = false, lastActiveTimestamp = null))
        assertThat(parsePresenceStatus("""{"presence":"unavailable","last_active_ago":"soon"}""", now = 1_000_000))
            .isEqualTo(PresenceStatus(isOnline = false, lastActiveTimestamp = null))
        assertThat(parsePresenceStatus("{}", now = 0)).isNull()
        assertThat(parsePresenceStatus("not JSON", now = 0)).isNull()
    }

    @Test
    fun `2ecure - each person is asked at most once a minute, people who don't share their status are left out`() = runTest {
        val asked = mutableListOf<UserId>()
        val store = DefaultPresenceStatusStore(
            FakeMatrixClient(
                getPresenceLambda = { userId ->
                    asked.add(userId)
                    Result.success(if (userId == A_USER_ID) PresenceStatus(isOnline = true, lastActiveTimestamp = null) else null)
                }
            ),
            FakeSystemClock(),
        )

        store.refresh(listOf(A_USER_ID, A_USER_ID_2, A_USER_ID))
        store.refresh(listOf(A_USER_ID, A_USER_ID_2))

        assertThat(asked).containsExactly(A_USER_ID, A_USER_ID_2)
        assertThat(store.statuses.value).containsExactly(A_USER_ID, PresenceStatus(isOnline = true, lastActiveTimestamp = null))
    }

    @Test
    fun `2ecure - a failed request waits like an answered one`() = runTest {
        var calls = 0
        val store = DefaultPresenceStatusStore(
            FakeMatrixClient(
                getPresenceLambda = {
                    calls++
                    Result.failure(IllegalStateException("HTTP 401"))
                }
            ),
            FakeSystemClock(),
        )

        store.refresh(listOf(A_USER_ID))
        store.refresh(listOf(A_USER_ID))

        assertThat(calls).isEqualTo(1)
        assertThat(store.statuses.value).isEmpty()
    }

    @Test
    fun `2ecure - the open chat and the return to the foreground ask again sooner than the chat list`() = runTest {
        var calls = 0
        val clock = FakeSystemClock(epochMillisResult = 1_000_000)
        val store = DefaultPresenceStatusStore(
            FakeMatrixClient(
                getPresenceLambda = {
                    calls++
                    Result.success(null)
                }
            ),
            clock,
        )

        store.refresh(listOf(A_USER_ID))
        clock.epochMillisResult += 12_000
        store.refresh(listOf(A_USER_ID))
        assertThat(calls).isEqualTo(1)
        store.refresh(listOf(A_USER_ID), PresenceStatusStore.OPEN_CHAT_MAX_AGE)
        assertThat(calls).isEqualTo(2)
        clock.epochMillisResult += 14_000
        store.refresh(listOf(A_USER_ID), PresenceStatusStore.FOREGROUND_MAX_AGE)
        assertThat(calls).isEqualTo(2)
        clock.epochMillisResult += 1_000
        store.refresh(listOf(A_USER_ID), PresenceStatusStore.FOREGROUND_MAX_AGE)
        assertThat(calls).isEqualTo(3)
        clock.epochMillisResult += 49_000
        store.refresh(listOf(A_USER_ID))
        assertThat(calls).isEqualTo(3)
        clock.epochMillisResult += 1_000
        store.refresh(listOf(A_USER_ID))
        assertThat(calls).isEqualTo(4)
    }

    @Test
    fun `2ecure - people a cancelled refresh didn't reach are asked next time`() = runTest {
        val asked = mutableListOf<UserId>()
        val store = DefaultPresenceStatusStore(
            FakeMatrixClient(
                getPresenceLambda = { userId ->
                    asked.add(userId)
                    if (asked.size == 1) throw CancellationException("The chat list changed")
                    Result.success(null)
                }
            ),
            FakeSystemClock(),
        )

        runCatching { store.refresh(listOf(A_USER_ID, A_USER_ID_2)) }
        store.refresh(listOf(A_USER_ID, A_USER_ID_2))

        assertThat(asked).containsExactly(A_USER_ID, A_USER_ID_2).inOrder()
    }
}
