/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.moderation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.privacy.getDiscoverable
import io.element.android.libraries.matrix.api.privacy.setDiscoverable
import io.element.android.libraries.preferences.api.store.SessionPreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Inject
class ModerationAndSafetyPresenter(
    private val matrixClient: MatrixClient,
    private val sessionPreferencesStore: SessionPreferencesStore,
    private val mediaPreviewConfigStateStore: MediaPreviewConfigStateStore,
    @SessionCoroutineScope
    private val sessionCoroutineScope: CoroutineScope,
) : Presenter<ModerationAndSafetyState> {
    @Composable
    override fun present(): ModerationAndSafetyState {
        val isSharePresenceEnabled by remember {
            sessionPreferencesStore.isSharePresenceEnabled()
        }.collectAsState(initial = true)

        val mediaPreviewConfigState = mediaPreviewConfigStateStore.state()

        // 2ecure: who can find the account (docs/privacy.md).
        var discoverable by remember { mutableStateOf<Boolean?>(null) }
        var isSavingDiscoverable by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            matrixClient.getDiscoverable().onSuccess { if (!isSavingDiscoverable) discoverable = it }
        }

        val numberOfBlockedUsers by produceState(initialValue = 0) {
            matrixClient.ignoredUsersFlow
                .onEach { value = it.size }
                .launchIn(this)
        }

        fun handleEvent(event: ModerationAndSafetyEvent) {
            when (event) {
                is ModerationAndSafetyEvent.SetDiscoverable -> if (!isSavingDiscoverable && event.discoverable != discoverable) {
                    val previous = discoverable
                    discoverable = event.discoverable
                    isSavingDiscoverable = true
                    sessionCoroutineScope.launch {
                        matrixClient.setDiscoverable(event.discoverable).onFailure { discoverable = previous }
                        isSavingDiscoverable = false
                    }
                }
                is ModerationAndSafetyEvent.SetSharePresenceEnabled -> sessionCoroutineScope.launch {
                    sessionPreferencesStore.setSharePresence(event.enabled)
                }
                is ModerationAndSafetyEvent.SetHideInviteAvatars -> mediaPreviewConfigStateStore.setHideInviteAvatars(event.value)
                is ModerationAndSafetyEvent.SetTimelineMediaPreviewValue -> mediaPreviewConfigStateStore.setTimelineMediaPreviewValue(event.value)
            }
        }

        return ModerationAndSafetyState(
            discoverable = discoverable,
            isSavingDiscoverable = isSavingDiscoverable,
            isSharePresenceEnabled = isSharePresenceEnabled,
            mediaPreviewConfigState = mediaPreviewConfigState,
            numberOfBlockedUsers = numberOfBlockedUsers,
            eventSink = ::handleEvent,
        )
    }
}
