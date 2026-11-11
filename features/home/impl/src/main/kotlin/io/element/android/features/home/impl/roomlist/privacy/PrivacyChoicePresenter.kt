/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.roomlist.privacy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.privacy.getDiscoverable
import io.element.android.libraries.matrix.api.privacy.setDiscoverable
import io.element.android.libraries.matrix.api.roomlist.RoomList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * 2ecure: asks «Who can find you?» while the account has no choice (docs/privacy.md). The chat list shows it, so it
 * comes after the first-run screens and once the room list has loaded, when the account data has synced too.
 */
@Inject
class PrivacyChoicePresenter(
    private val client: MatrixClient,
) : Presenter<PrivacyChoiceState> {
    @Composable
    override fun present(): PrivacyChoiceState {
        val coroutineScope = rememberCoroutineScope()
        var isRequired by remember { mutableStateOf(false) }
        var selection by remember { mutableStateOf<Boolean?>(null) }
        var saveAction by remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }

        LaunchedEffect(Unit) {
            client.roomListService.allRooms.loadingState.first { it is RoomList.LoadingState.Loaded }
            // A choice that couldn't be read is asked for at the next start, not over an existing one.
            client.getDiscoverable().onSuccess { discoverable -> isRequired = discoverable == null }
        }
        LaunchedEffect(isRequired) {
            // A choice made on another device in the meantime closes the question once it has synced.
            while (isRequired) {
                delay(10.seconds)
                if (!saveAction.isLoading() && client.getDiscoverable().getOrNull() != null) isRequired = false
            }
        }

        fun handleEvent(event: PrivacyChoiceEvent) {
            when (event) {
                is PrivacyChoiceEvent.Select -> selection = event.discoverable
                PrivacyChoiceEvent.Save -> {
                    val discoverable = selection ?: return
                    if (saveAction.isLoading()) return
                    saveAction = AsyncAction.Loading
                    coroutineScope.launch {
                        // A choice made on another device in the meantime is kept, not overwritten.
                        if (client.getDiscoverable().getOrNull() != null) {
                            isRequired = false
                            saveAction = AsyncAction.Uninitialized
                            return@launch
                        }
                        client.setDiscoverable(discoverable).fold(
                            onSuccess = {
                                isRequired = false
                                saveAction = AsyncAction.Success(Unit)
                            },
                            onFailure = { saveAction = AsyncAction.Failure(it) },
                        )
                    }
                }
                PrivacyChoiceEvent.ClearError -> saveAction = AsyncAction.Uninitialized
            }
        }

        return PrivacyChoiceState(
            isRequired = isRequired,
            selection = selection,
            saveAction = saveAction,
            eventSink = ::handleEvent,
        )
    }
}
