/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.test.presence

import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.presence.PresenceStatus
import io.element.android.libraries.matrix.api.presence.PresenceStatusStore
import kotlinx.coroutines.flow.MutableStateFlow

class FakePresenceStatusStore(
    initialStatuses: Map<UserId, PresenceStatus> = emptyMap(),
) : PresenceStatusStore {
    override val statuses = MutableStateFlow(initialStatuses)
    val refreshedUserIds = mutableListOf<List<UserId>>()

    override suspend fun refresh(userIds: List<UserId>) {
        refreshedUserIds.add(userIds)
    }
}
