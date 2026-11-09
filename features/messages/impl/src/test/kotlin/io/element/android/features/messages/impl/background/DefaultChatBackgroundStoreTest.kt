/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.background

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.api.background.ChatBackground
import io.element.android.features.messages.api.background.ChatBackgroundGradient
import io.element.android.libraries.matrix.test.AN_EXCEPTION
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_ROOM_ID_2
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DefaultChatBackgroundStoreTest {
    private val stored = """
        {"other": 1, "rooms": {
            "${A_ROOM_ID.value}": {"type": "gradient", "id": "sky"},
            "${A_ROOM_ID_2.value}": {"type": "image", "url": "mxc://2ecure.com/abc", "mimetype": "image/jpeg"},
            "!unknownType:domain": {"type": "video", "url": "mxc://2ecure.com/v"},
            "!unknownGradient:domain": {"type": "gradient", "id": "neon"},
            "!notMxc:domain": {"type": "image", "url": "https://example.org/a.jpg"}
        }}
    """.trimIndent()

    @Test
    fun `the content gives the known backgrounds by room and skips the rest`() {
        val backgrounds = ChatBackgroundContent.backgrounds(ChatBackgroundContent.parse(stored)!!)
        assertThat(backgrounds).containsExactly(
            A_ROOM_ID.value, ChatBackground.Gradient(ChatBackgroundGradient.Sky),
            A_ROOM_ID_2.value, ChatBackground.Image("mxc://2ecure.com/abc", "image/jpeg"),
        )
        assertThat(ChatBackgroundContent.parse("not json")).isNull()
        assertThat(ChatBackgroundContent.backgrounds(JsonObject(emptyMap()))).isEmpty()
    }

    @Test
    fun `changing one room keeps the other rooms and unknown keys`() {
        val content = ChatBackgroundContent.parse(stored)!!
        val changed = ChatBackgroundContent.withBackground(content, A_ROOM_ID.value, ChatBackground.Gradient(ChatBackgroundGradient.Rose))
        assertThat(changed["other"].toString()).isEqualTo("1")
        val rooms = changed["rooms"]!!.jsonObject
        assertThat(rooms.keys).hasSize(5)
        assertThat(rooms[A_ROOM_ID.value].toString()).isEqualTo("""{"type":"gradient","id":"rose"}""")
        assertThat(rooms["!unknownType:domain"]).isEqualTo(content["rooms"]!!.jsonObject["!unknownType:domain"])

        val removed = ChatBackgroundContent.withBackground(content, A_ROOM_ID_2.value, null)
        assertThat(removed["rooms"]!!.jsonObject.keys).doesNotContain(A_ROOM_ID_2.value)
    }

    @Test
    fun `refresh reads the account data`() = runTest {
        val requested = mutableListOf<String>()
        val store = createStore(FakeMatrixClient(getAccountDataLambda = { requested += it; Result.success(stored) }))
        assertThat(store.backgroundFlow(A_ROOM_ID).first()).isNull()
        store.refresh()
        assertThat(requested).containsExactly("com.2ecure.chat_backgrounds")
        assertThat(store.backgroundFlow(A_ROOM_ID).first()).isEqualTo(ChatBackground.Gradient(ChatBackgroundGradient.Sky))
    }

    @Test
    fun `a saved background shows at once and survives a refresh before the sync`() = runTest {
        val written = mutableListOf<Pair<String, String>>()
        val store = createStore(
            FakeMatrixClient(
                getAccountDataLambda = { Result.success(stored) },
                setAccountDataLambda = { type, content -> written += type to content; Result.success(Unit) },
            )
        )
        val result = store.setBackground(A_ROOM_ID, ChatBackground.Gradient(ChatBackgroundGradient.Lavender))
        assertThat(result.isSuccess).isTrue()
        assertThat(written.single().first).isEqualTo("com.2ecure.chat_backgrounds")
        val content = Json.parseToJsonElement(written.single().second).jsonObject
        assertThat(content["rooms"]!!.jsonObject[A_ROOM_ID.value].toString()).isEqualTo("""{"type":"gradient","id":"lavender"}""")
        assertThat(content["rooms"]!!.jsonObject.keys).contains(A_ROOM_ID_2.value)

        // The SDK's store still has the old content until sync brings the write back.
        store.refresh()
        assertThat(store.backgroundFlow(A_ROOM_ID).first()).isEqualTo(ChatBackground.Gradient(ChatBackgroundGradient.Lavender))
    }

    @Test
    fun `a failed save changes nothing`() = runTest {
        val store = createStore(
            FakeMatrixClient(
                getAccountDataLambda = { Result.success(stored) },
                setAccountDataLambda = { _, _ -> Result.failure(AN_EXCEPTION) },
            )
        )
        val result = store.setBackground(A_ROOM_ID, null)
        assertThat(result.isFailure).isTrue()
        assertThat(store.backgroundFlow(A_ROOM_ID).first()).isEqualTo(ChatBackground.Gradient(ChatBackgroundGradient.Sky))
    }

    @Test
    fun `nothing is saved when the current content can't be read`() = runTest {
        val readFailure = createStore(
            FakeMatrixClient(
                getAccountDataLambda = { Result.failure(AN_EXCEPTION) },
                setAccountDataLambda = { _, _ -> error("must not write") },
            )
        )
        assertThat(readFailure.setBackground(A_ROOM_ID, ChatBackground.Gradient(ChatBackgroundGradient.Sea)).isFailure).isTrue()

        val notAnObject = createStore(
            FakeMatrixClient(
                getAccountDataLambda = { Result.success("[1, 2]") },
                setAccountDataLambda = { _, _ -> error("must not write") },
            )
        )
        assertThat(notAnObject.setBackground(A_ROOM_ID, ChatBackground.Gradient(ChatBackgroundGradient.Sea)).isFailure).isTrue()
    }

    @Test
    fun `an absent event is an empty content`() = runTest {
        val written = mutableListOf<String>()
        val store = createStore(
            FakeMatrixClient(
                getAccountDataLambda = { Result.success(null) },
                setAccountDataLambda = { _, content -> written += content; Result.success(Unit) },
            )
        )
        assertThat(store.setBackground(A_ROOM_ID, ChatBackground.Gradient(ChatBackgroundGradient.Sea)).isSuccess).isTrue()
        assertThat(written.single()).isEqualTo("""{"rooms":{"${A_ROOM_ID.value}":{"type":"gradient","id":"sea"}}}""")
    }

    @Test
    fun `pending edits stay until sync brings them back and go into the next write`() = runTest {
        var server = stored
        val written = mutableListOf<String>()
        val store = createStore(
            FakeMatrixClient(
                getAccountDataLambda = { Result.success(server) },
                setAccountDataLambda = { _, content -> written += content; Result.success(Unit) },
            )
        )
        store.setBackground(A_ROOM_ID, ChatBackground.Gradient(ChatBackgroundGradient.Lavender))
        // However long sync takes, the store's old content doesn't undo the edit.
        repeat(3) { store.refresh() }
        assertThat(store.backgroundFlow(A_ROOM_ID).first()).isEqualTo(ChatBackground.Gradient(ChatBackgroundGradient.Lavender))

        // A second write, for another room, still carries the first edit.
        store.setBackground(A_ROOM_ID_2, null)
        val rooms = Json.parseToJsonElement(written.last()).jsonObject["rooms"]!!.jsonObject
        assertThat(rooms[A_ROOM_ID.value].toString()).isEqualTo("""{"type":"gradient","id":"lavender"}""")
        assertThat(rooms.keys).doesNotContain(A_ROOM_ID_2.value)

        // Sync brings the write back; after that, another device's change shows.
        server = written.last()
        store.refresh()
        server = server.replace("lavender", "forest")
        store.refresh()
        assertThat(store.backgroundFlow(A_ROOM_ID).first()).isEqualTo(ChatBackground.Gradient(ChatBackgroundGradient.Forest))
        assertThat(store.backgroundFlow(A_ROOM_ID_2).first()).isNull()
    }

    @Test
    fun `a newer change from another device wins over a pending edit`() = runTest {
        var server = stored
        val store = createStore(
            FakeMatrixClient(
                getAccountDataLambda = { Result.success(server) },
                setAccountDataLambda = { _, _ -> Result.success(Unit) },
            )
        )
        store.setBackground(A_ROOM_ID, ChatBackground.Gradient(ChatBackgroundGradient.Lavender))
        server = stored.replace("\"sky\"", "\"forest\"")
        store.refresh()
        assertThat(store.backgroundFlow(A_ROOM_ID).first()).isEqualTo(ChatBackground.Gradient(ChatBackgroundGradient.Forest))
    }

    @Test
    fun `an older write coming back after a newer save keeps the newer one`() = runTest {
        var server = stored
        val written = mutableListOf<String>()
        val store = createStore(
            FakeMatrixClient(
                getAccountDataLambda = { Result.success(server) },
                setAccountDataLambda = { _, content -> written += content; Result.success(Unit) },
            )
        )
        store.setBackground(A_ROOM_ID, ChatBackground.Gradient(ChatBackgroundGradient.Lavender))
        store.setBackground(A_ROOM_ID, ChatBackground.Gradient(ChatBackgroundGradient.Rose))
        server = written.first()
        store.refresh()
        assertThat(store.backgroundFlow(A_ROOM_ID).first()).isEqualTo(ChatBackground.Gradient(ChatBackgroundGradient.Rose))
        server = written.last()
        store.refresh()
        assertThat(store.backgroundFlow(A_ROOM_ID).first()).isEqualTo(ChatBackground.Gradient(ChatBackgroundGradient.Rose))
    }

    private fun TestScope.createStore(matrixClient: FakeMatrixClient) = DefaultChatBackgroundStore(
        matrixClient = matrixClient,
        context = RuntimeEnvironment.getApplication(),
        dispatchers = testCoroutineDispatchers(),
    )
}
