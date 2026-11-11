/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.roomlist.privacy

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.matrix.api.roomlist.RoomList
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.roomlist.FakeDynamicRoomList
import io.element.android.libraries.matrix.test.roomlist.FakeRoomListService
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class PrivacyChoicePresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - 2ecure - asks once the room list has loaded when there is no choice, then saves the answer`() = runTest {
        val written = mutableListOf<Pair<String, String>>()
        val loadingState = MutableStateFlow<RoomList.LoadingState>(RoomList.LoadingState.NotLoaded)
        val presenter = PrivacyChoicePresenter(
            FakeMatrixClient(
                roomListService = FakeRoomListService(createRoomListLambda = { FakeDynamicRoomList(loadingState = loadingState) }),
                getAccountDataLambda = { Result.success(null) },
                setAccountDataLambda = { type, content ->
                    written.add(type to content)
                    Result.success(Unit)
                },
            )
        )
        presenter.test {
            assertThat(awaitItem().isRequired).isFalse()
            loadingState.value = RoomList.LoadingState.Loaded(1)
            val asked = consumeItemsUntilPredicate { it.isRequired }.last()
            asked.eventSink(PrivacyChoiceEvent.Select(false))
            val selected = consumeItemsUntilPredicate { it.selection == false }.last()
            selected.eventSink(PrivacyChoiceEvent.Save)
            val done = consumeItemsUntilPredicate { !it.isRequired }.last()
            assertThat(done.saveAction).isEqualTo(AsyncAction.Success(Unit))
            assertThat(written).containsExactly("com.2ecure.privacy" to """{"discoverable":false}""")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - 2ecure - a choice made on another device closes the question and is not overwritten`() = runTest {
        var stored: String? = null
        var writes = 0
        val presenter = PrivacyChoicePresenter(
            FakeMatrixClient(
                roomListService = FakeRoomListService(
                    createRoomListLambda = { FakeDynamicRoomList(loadingState = MutableStateFlow(RoomList.LoadingState.Loaded(1))) }
                ),
                getAccountDataLambda = { Result.success(stored) },
                setAccountDataLambda = { _, _ ->
                    writes++
                    Result.success(Unit)
                },
            )
        )
        presenter.test {
            val asked = consumeItemsUntilPredicate { it.isRequired }.last()
            asked.eventSink(PrivacyChoiceEvent.Select(true))
            val selected = consumeItemsUntilPredicate { it.selection == true }.last()
            stored = """{"discoverable":false}"""
            selected.eventSink(PrivacyChoiceEvent.Save)
            consumeItemsUntilPredicate { !it.isRequired }
            assertThat(writes).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - 2ecure - an account with a choice is not asked`() = runTest {
        val presenter = PrivacyChoicePresenter(
            FakeMatrixClient(
                roomListService = FakeRoomListService(
                    createRoomListLambda = { FakeDynamicRoomList(loadingState = MutableStateFlow(RoomList.LoadingState.Loaded(1))) }
                ),
                getAccountDataLambda = { Result.success("""{"discoverable":true}""") },
            )
        )
        presenter.test {
            assertThat(awaitItem().isRequired).isFalse()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
