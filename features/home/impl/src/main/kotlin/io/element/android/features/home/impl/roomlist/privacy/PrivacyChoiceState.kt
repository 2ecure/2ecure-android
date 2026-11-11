/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.roomlist.privacy

import io.element.android.libraries.architecture.AsyncAction

/**
 * 2ecure: the question «Who can find you?» after signing in (docs/privacy.md).
 *
 * @property isRequired whether the account has no choice yet, so the question is shown.
 * @property selection true for «Everyone on 2ecure», false for «Only people I chat with», null while nothing is chosen.
 */
data class PrivacyChoiceState(
    val isRequired: Boolean,
    val selection: Boolean?,
    val saveAction: AsyncAction<Unit>,
    val eventSink: (PrivacyChoiceEvent) -> Unit,
)

sealed interface PrivacyChoiceEvent {
    data class Select(val discoverable: Boolean) : PrivacyChoiceEvent
    data object Save : PrivacyChoiceEvent
    data object ClearError : PrivacyChoiceEvent
}

fun aPrivacyChoiceState(
    isRequired: Boolean = false,
    selection: Boolean? = null,
    saveAction: AsyncAction<Unit> = AsyncAction.Uninitialized,
    eventSink: (PrivacyChoiceEvent) -> Unit = {},
) = PrivacyChoiceState(
    isRequired = isRequired,
    selection = selection,
    saveAction = saveAction,
    eventSink = eventSink,
)
