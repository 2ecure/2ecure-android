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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

@ContributesBinding(SessionScope::class)
@SingleIn(SessionScope::class)
class DefaultPresenceStatusStore(
    private val matrixClient: MatrixClient,
) : PresenceStatusStore {
    private val mutex = Mutex()
    private val attempted = mutableMapOf<UserId, Long>()
    private val answered = mutableMapOf<UserId, Long>()
    private val inFlight = mutableSetOf<UserId>()
    private val mutableStatuses = MutableStateFlow<Map<UserId, PresenceStatus>>(emptyMap())
    override val statuses: StateFlow<Map<UserId, PresenceStatus>> = mutableStatuses

    override suspend fun refresh(userIds: List<UserId>) {
        val now = System.currentTimeMillis()
        // Failed requests wait like answered ones, so a failing server doesn't get a request storm.
        val due = mutex.withLock {
            userIds.distinct()
                .filter { userId -> userId !in inFlight && attempted[userId]?.let { now - it > RETRY_AFTER_MILLIS } ?: true }
                .onEach { userId ->
                    inFlight.add(userId)
                    attempted[userId] = now
                }
        }
        try {
            for (userId in due) {
                matrixClient.getPresence(userId).fold(
                    onSuccess = { status ->
                        mutex.withLock { answered[userId] = System.currentTimeMillis() }
                        mutableStatuses.update { if (status == null) it - userId else it + (userId to status) }
                    },
                    onFailure = {
                        // An old answer isn't shown for longer than the server keeps someone online without news.
                        val isStale = mutex.withLock {
                            answered[userId]?.let { System.currentTimeMillis() - it > EXPIRY_MILLIS } ?: true
                        }
                        if (isStale) mutableStatuses.update { it - userId }
                    },
                )
            }
        } finally {
            mutex.withLock { inFlight.removeAll(due.toSet()) }
        }
    }

    private companion object {
        const val RETRY_AFTER_MILLIS = 50_000L
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
