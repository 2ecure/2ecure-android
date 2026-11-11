/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.roomlist.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.ui.privacy.PrivacyChoiceOptions
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * 2ecure: the question «Who can find you?» over the chat list, which cannot be skipped (docs/privacy.md).
 */
@Composable
fun PrivacyChoiceDialog(state: PrivacyChoiceState) {
    if (!state.isRequired) return
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        PrivacyChoiceContent(state)
    }
    if (state.saveAction is AsyncAction.Failure) {
        ErrorDialog(
            content = stringResource(CommonStrings.twoecure_privacy_choice_error),
            onSubmit = { state.eventSink(PrivacyChoiceEvent.ClearError) },
        )
    }
}

@Composable
private fun PrivacyChoiceContent(state: PrivacyChoiceState) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ElementTheme.colors.bgCanvasDefault,
    ) {
        Column(
            modifier = Modifier
                .systemBarsPadding()
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(CommonStrings.twoecure_privacy_choice_title),
                    modifier = Modifier.padding(horizontal = 24.dp),
                    style = ElementTheme.typography.fontHeadingMdBold,
                    color = ElementTheme.colors.textPrimary,
                )
                Text(
                    text = stringResource(CommonStrings.twoecure_privacy_choice_message),
                    modifier = Modifier.padding(horizontal = 24.dp),
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                )
                PrivacyChoiceOptions(
                    selection = state.selection,
                    onSelect = { state.eventSink(PrivacyChoiceEvent.Select(it)) },
                    enabled = !state.saveAction.isLoading(),
                )
                Text(
                    text = stringResource(CommonStrings.twoecure_privacy_choice_footer),
                    modifier = Modifier.padding(horizontal = 24.dp),
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            }
            Button(
                text = stringResource(CommonStrings.twoecure_privacy_choice_continue),
                onClick = { state.eventSink(PrivacyChoiceEvent.Save) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                enabled = state.selection != null && !state.saveAction.isLoading(),
                showProgress = state.saveAction.isLoading(),
            )
        }
    }
}

@PreviewsDayNight
@Composable
internal fun PrivacyChoiceContentPreview() = ElementPreview {
    PrivacyChoiceContent(aPrivacyChoiceState(isRequired = true, selection = false))
}
