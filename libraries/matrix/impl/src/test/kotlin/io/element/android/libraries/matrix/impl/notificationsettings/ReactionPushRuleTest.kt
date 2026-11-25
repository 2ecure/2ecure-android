/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.notificationsettings

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.matrix.rustcomponents.sdk.Action
import org.matrix.rustcomponents.sdk.PushCondition

class ReactionPushRuleTest {
    @Test
    fun `the rule matches reactions and notifies without a sound when on`() {
        assertThat(ReactionPushRule.conditions).containsExactly(PushCondition.EventMatch(key = "type", pattern = "m.reaction"))
        assertThat(ReactionPushRule.actions(enabled = true)).containsExactly(Action.Notify)
        assertThat(ReactionPushRule.actions(enabled = false)).isEmpty()
    }

    @Test
    fun `on only when the rule exists, is enabled and notifies`() {
        assertThat(ReactionPushRule.isEnabled(null)).isFalse()
        assertThat(ReactionPushRule.isEnabled("not json")).isFalse()
        assertThat(ReactionPushRule.isEnabled(pushRules(rule = null))).isFalse()
        assertThat(ReactionPushRule.isEnabled(pushRules(rule = reactionRule(actions = """["notify"]""")))).isTrue()
        assertThat(ReactionPushRule.isEnabled(pushRules(rule = reactionRule(actions = """["notify", {"set_tweak": "sound", "value": "default"}]""")))).isTrue()
        assertThat(ReactionPushRule.isEnabled(pushRules(rule = reactionRule(actions = "[]")))).isFalse()
        assertThat(ReactionPushRule.isEnabled(pushRules(rule = reactionRule(actions = """["notify"]""", enabled = false)))).isFalse()
        assertThat(ReactionPushRule.isEnabled(pushRules(rule = reactionRule(actions = """["notify"]""", ruleId = "something.else")))).isFalse()
        // Without the `global` wrapper too.
        assertThat(ReactionPushRule.isEnabled("""{"override": [${reactionRule(actions = """["notify"]""")}]}""")).isTrue()
    }

    private fun reactionRule(actions: String, enabled: Boolean = true, ruleId: String = ReactionPushRule.RULE_ID) = """
        {"rule_id": "$ruleId", "default": false, "enabled": $enabled, "actions": $actions,
         "conditions": [{"kind": "event_match", "key": "type", "pattern": "m.reaction"}]}
    """.trimIndent()

    private fun pushRules(rule: String?) = """
        {"global": {"content": [], "underride": [], "room": [], "sender": [], "override": [
            {"rule_id": ".m.rule.master", "default": true, "enabled": false, "actions": [], "conditions": []}
            ${if (rule != null) ", $rule" else ""},
            {"rule_id": ".m.rule.reaction", "default": true, "enabled": true, "actions": [],
             "conditions": [{"kind": "event_match", "key": "type", "pattern": "m.reaction"}]}
        ]}}
    """.trimIndent()
}
