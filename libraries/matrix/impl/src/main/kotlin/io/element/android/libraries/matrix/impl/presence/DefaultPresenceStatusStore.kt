/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.presence

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.presence.PresenceStatus
import io.element.android.libraries.matrix.api.presence.PresenceStatusStore
import io.element.android.services.toolbox.api.systemclock.SystemClock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Duration

@ContributesBinding(SessionScope::class)
@SingleIn(SessionScope::class)
class DefaultPresenceStatusStore(
    private val matrixClient: MatrixClient,
    private val clock: SystemClock,
) : PresenceStatusStore {
    private val mutex = Mutex()
    private val attempted = mutableMapOf<UserId, Long>()
    private val answered = mutableMapOf<UserId, Long>()
    private val inFlight = mutableSetOf<UserId>()
    private val mutableStatuses = MutableStateFlow<Map<UserId, PresenceStatus>>(emptyMap())
    override val statuses: StateFlow<Map<UserId, PresenceStatus>> = mutableStatuses

    override suspend fun refresh(userIds: List<UserId>, maxAge: Duration) {
        val now = clock.epochMillis()
        val due = mutex.withLock {
            userIds.distinct()
                .filter { userId -> userId !in inFlight && attempted[userId]?.let { now - it >= maxAge.inWholeMilliseconds } ?: true }
                .onEach { userId -> inFlight.add(userId) }
        }
        try {
            for (userId in due) {
                // Failed requests wait like answered ones, so a failing server doesn't get a request storm. Only people
                // actually asked wait: a refresh cancelled halfway (the list changed) leaves the others due.
                mutex.withLock { attempted[userId] = clock.epochMillis() }
                matrixClient.getPresence(userId).fold(
                    onSuccess = { status ->
                        mutex.withLock { answered[userId] = clock.epochMillis() }
                        mutableStatuses.update { if (status == null) it - userId else it + (userId to status) }
                    },
                    onFailure = {
                        // An old answer isn't shown for longer than the server keeps someone online without news.
                        val isStale = mutex.withLock {
                            answered[userId]?.let { clock.epochMillis() - it > EXPIRY_MILLIS } ?: true
                        }
                        if (isStale) mutableStatuses.update { it - userId }
                    },
                )
            }
        } finally {
            // Also when cancelled, or these people would never be asked again.
            withContext(NonCancellable) {
                mutex.withLock { inFlight.removeAll(due.toSet()) }
            }
        }
    }

    private companion object {
        const val EXPIRY_MILLIS = 180_000L
    }
}

/** Parses the body of `GET /_matrix/client/v3/presence/{userId}/status`; null when it isn't one. */
fun parsePresenceStatus(json: String, now: Long): PresenceStatus? {
    val content = runCatching { Json.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return null
    val presence = (content["presence"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
    val lastActiveAgo = (content["last_active_ago"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
    return PresenceStatus(
        isOnline = presence == "online",
        lastActiveTimestamp = lastActiveAgo?.let { now - it },
    )
}
