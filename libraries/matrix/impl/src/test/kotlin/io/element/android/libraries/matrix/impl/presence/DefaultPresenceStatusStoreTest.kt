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
import io.element.android.libraries.matrix.test.A_USER_ID
import io.element.android.libraries.matrix.test.A_USER_ID_2
import io.element.android.libraries.matrix.test.FakeMatrixClient
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
            )
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
            )
        )

        store.refresh(listOf(A_USER_ID))
        store.refresh(listOf(A_USER_ID))

        assertThat(calls).isEqualTo(1)
        assertThat(store.statuses.value).isEmpty()
    }
}
