/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.privacy

import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * 2ecure: who can find the account in the user search (docs/privacy.md). The choice is the global account data
 * `com.2ecure.privacy` = `{"discoverable": Boolean}`, which the server's `twoecure_privacy` module reads.
 */
object PrivacyChoice {
    const val EVENT_TYPE = "com.2ecure.privacy"

    /** Only a JSON boolean counts, as on the server; null when the content has none. */
    fun parseDiscoverable(json: String): Boolean? {
        val content = runCatching { Json.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return null
        return (content["discoverable"] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
    }

    fun json(discoverable: Boolean): String = """{"discoverable":$discoverable}"""
}

/** True for «Everyone on 2ecure», false for «Only people I chat with», null while the account hasn't chosen. */
suspend fun MatrixClient.getDiscoverable(): Result<Boolean?> {
    return getAccountData(PrivacyChoice.EVENT_TYPE).map { json -> json?.let(PrivacyChoice::parseDiscoverable) }
}

suspend fun MatrixClient.setDiscoverable(discoverable: Boolean): Result<Unit> {
    return setAccountData(PrivacyChoice.EVENT_TYPE, PrivacyChoice.json(discoverable))
}
