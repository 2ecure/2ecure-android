/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.moderation

data class ModerationAndSafetyState(
    // 2ecure: who can find the account (docs/privacy.md); null until read or while not chosen.
    val discoverable: Boolean? = null,
    val isSavingDiscoverable: Boolean = false,
    val isSharePresenceEnabled: Boolean,
    val mediaPreviewConfigState: MediaPreviewConfigState,
    val numberOfBlockedUsers: Int,
    val eventSink: (ModerationAndSafetyEvent) -> Unit
) {
    val showBlockedUsersItem = numberOfBlockedUsers > 0
}
