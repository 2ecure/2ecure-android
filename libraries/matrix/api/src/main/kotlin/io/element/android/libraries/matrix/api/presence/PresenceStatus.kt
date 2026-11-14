/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.presence

import io.element.android.libraries.matrix.api.core.UserId
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
 * is asked directly: once a minute for the chat list, every 15 seconds for the open one-to-one chat, and again when the
 * app comes back to the foreground. Synapse answers only for people who share a room.
 */
interface PresenceStatusStore {
    /** The statuses known so far; people who don't share theirs are missing. */
    val statuses: StateFlow<Map<UserId, PresenceStatus>>

    /** Asks the homeserver for the people in [userIds] who were never asked, or last asked [maxAge] ago or longer. */
    suspend fun refresh(userIds: List<UserId>, maxAge: Duration = ROUND_MAX_AGE)

    companion object {
        /** The chat list's rounds, once a minute. */
        val ROUND_MAX_AGE = 50.seconds

        /** Coming back to the foreground: whoever wasn't asked in the last 15 seconds. */
        val FOREGROUND_MAX_AGE = 15.seconds

        /** The open one-to-one chat is asked every [OPEN_CHAT_INTERVAL], and when it opens unless just asked. */
        val OPEN_CHAT_INTERVAL = 15.seconds
        val OPEN_CHAT_MAX_AGE = 10.seconds
    }
}
