/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.notificationsettings

import io.element.android.libraries.core.extensions.runCatchingExceptions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.matrix.rustcomponents.sdk.Action
import org.matrix.rustcomponents.sdk.PushCondition

/**
 * 2ecure: notifications for reactions to your own messages (docs/reaction-notifications.md).
 *
 * Matrix's server default rule `.m.rule.reaction` silences every reaction, and the SDK applies the push rules again
 * after decrypting an event. A user defined override rule comes before every server default override rule, so this
 * one decides for reactions instead. Push rules can't ask who wrote the message reacted to, so the rule lets every
 * reaction through and the app shows only those to the account's own messages (DefaultNotifiableEventResolver).
 *
 * No sound tweak: Element Web plays the sound of every notifying event, also of those it shows no text for; the
 * apps pick the sound themselves. Off is the same rule without actions, as quiet as `.m.rule.reaction`.
 */
internal object ReactionPushRule {
    const val RULE_ID = "com.2ecure.reactions"

    val conditions: List<PushCondition> = listOf(
        PushCondition.EventMatch(key = "type", pattern = "m.reaction"),
    )

    fun actions(enabled: Boolean): List<Action> = if (enabled) listOf(Action.Notify) else emptyList()

    /** Whether the account's push rules (the JSON of the `m.push_rules` account data) have this rule on. */
    fun isEnabled(rawPushRules: String?): Boolean {
        if (rawPushRules == null) return false
        val rule = runCatchingExceptions {
            val root = Json.parseToJsonElement(rawPushRules).jsonObject
            val ruleset = root["global"]?.jsonObject ?: root
            ruleset["override"]?.jsonArray
                ?.map { it.jsonObject }
                ?.firstOrNull { it["rule_id"]?.jsonPrimitive?.contentOrNull == RULE_ID }
        }.getOrNull() ?: return false
        return rule.isEnabled() && rule.notifies()
    }

    private fun JsonObject.isEnabled(): Boolean = (this["enabled"] as? JsonPrimitive)?.booleanOrNull ?: true

    private fun JsonObject.notifies(): Boolean = runCatchingExceptions {
        this["actions"]?.jsonArray?.any { it is JsonPrimitive && it.isString && it.content == "notify" } ?: false
    }.getOrDefault(false)
}
