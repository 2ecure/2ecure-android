/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.presence

import io.element.android.libraries.matrix.api.core.UserId
import kotlinx.coroutines.flow.StateFlow

/**
 * 2ecure: someone's online status as the homeserver reports it (docs/privacy.md).
 *
 * @property isOnline whether the person is online.
 * @property lastActiveTimestamp when the person was last active, in milliseconds since the epoch, if the homeserver knows.
 */
data class PresenceStatus(
    val isOnline: Boolean,
    val lastActiveTimestamp: Long?,
)

/**
 * 2ecure: other people's online status. The Rust SDK sets the own presence but cannot read anyone's, so the homeserver
 * is asked directly, at most once a minute per person; Synapse answers only for people who share a room.
 */
interface PresenceStatusStore {
    /** The statuses known so far; people who don't share theirs are missing. */
    val statuses: StateFlow<Map<UserId, PresenceStatus>>

    /** Asks the homeserver for the people in [userIds] whose status is older than a minute. */
    suspend fun refresh(userIds: List<UserId>)
}
