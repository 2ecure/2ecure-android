/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.test.background

import android.net.Uri
import io.element.android.features.messages.api.background.ChatBackground
import io.element.android.features.messages.api.background.ChatBackgroundStore
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class FakeChatBackgroundStore(
    backgrounds: Map<RoomId, ChatBackground> = emptyMap(),
    private val setBackgroundResult: (RoomId, ChatBackground?) -> Result<Unit> = { _, _ -> Result.success(Unit) },
    private val uploadPhotoResult: (Uri) -> Result<ChatBackground> = { Result.success(ChatBackground.Image("mxc://2ecure.com/photo", "image/jpeg")) },
) : ChatBackgroundStore {
    private val backgrounds = MutableStateFlow(backgrounds)

    var refreshCount = 0
        private set

    override fun backgroundFlow(roomId: RoomId): Flow<ChatBackground?> = backgrounds.map { it[roomId] }.distinctUntilChanged()

    override suspend fun refresh() {
        refreshCount++
    }

    override suspend fun setBackground(roomId: RoomId, background: ChatBackground?): Result<Unit> =
        setBackgroundResult(roomId, background).onSuccess {
            backgrounds.value = if (background == null) backgrounds.value - roomId else backgrounds.value + (roomId to background)
        }

    override suspend fun uploadPhoto(uri: Uri): Result<ChatBackground> = uploadPhotoResult(uri)
}
