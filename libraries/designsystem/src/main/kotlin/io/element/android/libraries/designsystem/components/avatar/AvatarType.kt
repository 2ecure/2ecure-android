/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components.avatar

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
sealed interface AvatarType {
    data object User : AvatarType

    data class Room(
        val isTombstoned: Boolean = false,
        val heroes: ImmutableList<AvatarData> = persistentListOf(),
        // 2ecure: a one-to-one chat keeps the round avatar; every other room gets a rounded square.
        val isDm: Boolean = false,
    ) : AvatarType

    data class Space(
        val isTombstoned: Boolean = false,
    ) : AvatarType
}
