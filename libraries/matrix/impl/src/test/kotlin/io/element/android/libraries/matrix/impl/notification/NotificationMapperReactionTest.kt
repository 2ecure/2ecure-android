/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.notification

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.notification.NotificationContent
import io.element.android.libraries.matrix.test.AN_EVENT_ID
import io.element.android.libraries.matrix.test.A_USER_ID_2
import org.junit.Test

class NotificationMapperReactionTest {
    private val reaction = NotificationContent.MessageLike.ReactionContent(relatedEventId = AN_EVENT_ID.value, senderId = A_USER_ID_2)

    @Test
    fun `the key of a reaction comes from the decrypted event`() {
        val rawEvent = """
            {"type": "m.reaction", "sender": "@bob:server.org", "event_id": "${'$'}reaction",
             "content": {"m.relates_to": {"rel_type": "m.annotation", "event_id": "${AN_EVENT_ID.value}", "key": "👍"}}}
        """.trimIndent()
        assertThat(reaction.withReactionKey(rawEvent)).isEqualTo(reaction.copy(key = "👍"))
    }

    @Test
    fun `a reaction without a readable key keeps none`() {
        assertThat(reaction.withReactionKey("")).isEqualTo(reaction)
        assertThat(reaction.withReactionKey("""{"content": {}}""")).isEqualTo(reaction)
        assertThat(reaction.withReactionKey("""{"content": {"m.relates_to": {"key": " "}}}""")).isEqualTo(reaction)
    }

    @Test
    fun `other content is left as it is`() {
        val content = NotificationContent.MessageLike.RoomEncrypted
        assertThat(content.withReactionKey("""{"content": {"m.relates_to": {"key": "👍"}}}""")).isEqualTo(content)
    }
}
