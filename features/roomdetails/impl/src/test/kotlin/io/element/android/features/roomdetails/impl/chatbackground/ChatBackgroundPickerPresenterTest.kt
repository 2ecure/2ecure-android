/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomdetails.impl.chatbackground

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.api.background.ChatBackground
import io.element.android.features.messages.api.background.ChatBackgroundGradient
import io.element.android.features.messages.test.background.FakeChatBackgroundStore
import io.element.android.libraries.matrix.test.AN_EXCEPTION
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.mediapickers.test.FakePickerProvider
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChatBackgroundPickerPresenterTest {
    private val room = FakeJoinedRoom()

    @Test
    fun `present - shows the stored background and refreshes the store`() = runTest {
        val store = FakeChatBackgroundStore(mapOf(room.roomId to ChatBackground.Gradient(ChatBackgroundGradient.Sky)))
        createPresenter(store).test {
            val state = consumeItemsUntilPredicate { it.selection != null }.last()
            assertThat(state.selection).isEqualTo(ChatBackground.Gradient(ChatBackgroundGradient.Sky))
            assertThat(store.refreshCount).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - selecting a gradient saves it`() = runTest {
        val saved = mutableListOf<ChatBackground?>()
        val store = FakeChatBackgroundStore(setBackgroundResult = { _, background -> Result.success(Unit).also { saved += background } })
        createPresenter(store).test {
            awaitItem().eventSink(ChatBackgroundPickerEvent.Select(ChatBackground.Gradient(ChatBackgroundGradient.Rose)))
            val state = consumeItemsUntilPredicate { it.selection == ChatBackground.Gradient(ChatBackgroundGradient.Rose) }.last()
            assertThat(state.selection).isEqualTo(ChatBackground.Gradient(ChatBackgroundGradient.Rose))
            assertThat(saved).containsExactly(ChatBackground.Gradient(ChatBackgroundGradient.Rose))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - selecting no background removes it`() = runTest {
        val store = FakeChatBackgroundStore(mapOf(room.roomId to ChatBackground.Gradient(ChatBackgroundGradient.Sand)))
        createPresenter(store).test {
            consumeItemsUntilPredicate { it.selection != null }.last().eventSink(ChatBackgroundPickerEvent.Select(null))
            val state = consumeItemsUntilPredicate { it.selection == null }.last()
            assertThat(state.selection).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - a picked photo is uploaded and saved`() = runTest {
        val pickerProvider = FakePickerProvider().apply { givenResult(Uri.parse("content://photo")) }
        val photo = ChatBackground.Image("mxc://2ecure.com/abc", "image/jpeg")
        val store = FakeChatBackgroundStore(uploadPhotoResult = { Result.success(photo) })
        createPresenter(store, pickerProvider).test {
            awaitItem().eventSink(ChatBackgroundPickerEvent.PickPhoto)
            val state = consumeItemsUntilPredicate { it.selection == photo }.last()
            assertThat(state.selection).isEqualTo(photo)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - a failed save shows the error until it is dismissed`() = runTest {
        val store = FakeChatBackgroundStore(setBackgroundResult = { _, _ -> Result.failure(AN_EXCEPTION) })
        createPresenter(store).test {
            awaitItem().eventSink(ChatBackgroundPickerEvent.Select(ChatBackground.Gradient(ChatBackgroundGradient.Sea)))
            val failed = consumeItemsUntilPredicate { it.saveAction.isFailure() }.last()
            assertThat(failed.selection).isNull()
            failed.eventSink(ChatBackgroundPickerEvent.ClearError)
            val cleared = consumeItemsUntilPredicate { it.saveAction.isUninitialized() }.last()
            assertThat(cleared.saveAction.isUninitialized()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createPresenter(
        store: FakeChatBackgroundStore = FakeChatBackgroundStore(),
        pickerProvider: FakePickerProvider = FakePickerProvider(),
    ) = ChatBackgroundPickerPresenter(
        room = room,
        chatBackgroundStore = store,
        pickerProvider = pickerProvider,
    )
}
