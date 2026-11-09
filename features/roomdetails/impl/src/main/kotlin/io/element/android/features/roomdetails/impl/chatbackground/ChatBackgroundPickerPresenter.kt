/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomdetails.impl.chatbackground

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.zacsweers.metro.Inject
import io.element.android.features.messages.api.background.ChatBackground
import io.element.android.features.messages.api.background.ChatBackgroundStore
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.architecture.runUpdatingState
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.mediapickers.api.PickerProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 2ecure: the chat settings' screen for choosing a chat's background (see [ChatBackgroundStore]).
 */
@Inject
class ChatBackgroundPickerPresenter(
    private val room: JoinedRoom,
    private val chatBackgroundStore: ChatBackgroundStore,
    private val pickerProvider: PickerProvider,
) : Presenter<ChatBackgroundPickerState> {
    @Composable
    override fun present(): ChatBackgroundPickerState {
        val coroutineScope = rememberCoroutineScope()
        val selection by remember { chatBackgroundStore.backgroundFlow(room.roomId) }.collectAsState(initial = null)
        val saveAction = remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }

        LaunchedEffect(Unit) {
            chatBackgroundStore.refresh()
        }

        val photoPicker = pickerProvider.registerGalleryImagePicker { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    saveAction.runUpdatingState {
                        chatBackgroundStore.uploadPhoto(uri).fold(
                            onSuccess = { chatBackgroundStore.setBackground(room.roomId, it) },
                            onFailure = { Result.failure(it) },
                        )
                    }
                }
            }
        }

        fun handleEvent(event: ChatBackgroundPickerEvent) {
            when (event) {
                is ChatBackgroundPickerEvent.Select -> coroutineScope.select(event.background, saveAction)
                ChatBackgroundPickerEvent.PickPhoto -> photoPicker.launch()
                ChatBackgroundPickerEvent.ClearError -> saveAction.value = AsyncAction.Uninitialized
            }
        }

        return ChatBackgroundPickerState(
            selection = selection,
            saveAction = saveAction.value,
            eventSink = ::handleEvent,
        )
    }

    private fun CoroutineScope.select(background: ChatBackground?, saveAction: MutableState<AsyncAction<Unit>>) = launch {
        saveAction.runUpdatingState {
            chatBackgroundStore.setBackground(room.roomId, background)
        }
    }
}

data class ChatBackgroundPickerState(
    val selection: ChatBackground?,
    val saveAction: AsyncAction<Unit>,
    val eventSink: (ChatBackgroundPickerEvent) -> Unit,
)

sealed interface ChatBackgroundPickerEvent {
    data class Select(val background: ChatBackground?) : ChatBackgroundPickerEvent
    data object PickPhoto : ChatBackgroundPickerEvent
    data object ClearError : ChatBackgroundPickerEvent
}
