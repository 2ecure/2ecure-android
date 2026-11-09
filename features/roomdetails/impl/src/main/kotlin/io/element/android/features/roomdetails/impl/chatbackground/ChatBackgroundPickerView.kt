/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomdetails.impl.chatbackground

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.api.background.ChatBackground
import io.element.android.features.messages.api.background.ChatBackgroundGradient
import io.element.android.features.roomdetails.impl.R
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.designsystem.components.async.AsyncActionView
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListSectionHeader
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar

/**
 * 2ecure: the screen for choosing a chat's background: none, a photo or one of the gradients.
 */
@Composable
fun ChatBackgroundPickerView(
    state: ChatBackgroundPickerState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                titleStr = stringResource(R.string.twoecure_chat_background_title),
                navigationIcon = { BackButton(onClick = onBackClick) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            ListItem(
                leadingContent = ListItemContent.Custom { Swatch(gradient = null) },
                trailingContent = ListItemContent.RadioButton(selected = state.selection == null),
                onClick = { state.eventSink(ChatBackgroundPickerEvent.Select(null)) },
            ) {
                Text(stringResource(R.string.twoecure_chat_background_none))
            }
            ListItem(
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Image())),
                trailingContent = ListItemContent.RadioButton(selected = state.selection is ChatBackground.Image),
                onClick = { state.eventSink(ChatBackgroundPickerEvent.PickPhoto) },
            ) {
                Text(stringResource(R.string.twoecure_chat_background_photo))
            }
            ListSectionHeader(title = stringResource(R.string.twoecure_chat_background_colours))
            ChatBackgroundGradient.entries.forEach { gradient ->
                ListItem(
                    leadingContent = ListItemContent.Custom { Swatch(gradient = gradient) },
                    trailingContent = ListItemContent.RadioButton(selected = state.selection == ChatBackground.Gradient(gradient)),
                    onClick = { state.eventSink(ChatBackgroundPickerEvent.Select(ChatBackground.Gradient(gradient))) },
                ) {
                    Text(stringResource(gradient.nameRes()))
                }
            }
            Text(
                text = stringResource(R.string.twoecure_chat_background_footer),
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
    AsyncActionView(
        async = state.saveAction,
        onSuccess = {},
        onErrorDismiss = { state.eventSink(ChatBackgroundPickerEvent.ClearError) },
        errorMessage = { stringResource(R.string.twoecure_chat_background_error) },
    )
}

@Composable
private fun Swatch(gradient: ChatBackgroundGradient?) {
    val colours = gradient?.colours(isDark = !ElementTheme.isLightTheme)
        ?: listOf(ElementTheme.colors.bgCanvasDefault, ElementTheme.colors.bgCanvasDefault)
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(Brush.verticalGradient(colours))
            .border(1.dp, ElementTheme.colors.borderInteractiveSecondary, CircleShape)
    )
}

private fun ChatBackgroundGradient.nameRes(): Int = when (this) {
    ChatBackgroundGradient.Sky -> R.string.twoecure_chat_background_sky
    ChatBackgroundGradient.Sea -> R.string.twoecure_chat_background_sea
    ChatBackgroundGradient.Forest -> R.string.twoecure_chat_background_forest
    ChatBackgroundGradient.Sand -> R.string.twoecure_chat_background_sand
    ChatBackgroundGradient.Rose -> R.string.twoecure_chat_background_rose
    ChatBackgroundGradient.Lavender -> R.string.twoecure_chat_background_lavender
}

internal class ChatBackgroundPickerStateProvider : PreviewParameterProvider<ChatBackgroundPickerState> {
    override val values: Sequence<ChatBackgroundPickerState>
        get() = sequenceOf(
            aChatBackgroundPickerState(selection = null),
            aChatBackgroundPickerState(selection = ChatBackground.Gradient(ChatBackgroundGradient.Sea)),
            aChatBackgroundPickerState(selection = ChatBackground.Image("mxc://2ecure.com/photo", "image/jpeg")),
        )
}

internal fun aChatBackgroundPickerState(
    selection: ChatBackground? = null,
    saveAction: AsyncAction<Unit> = AsyncAction.Uninitialized,
    eventSink: (ChatBackgroundPickerEvent) -> Unit = {},
) = ChatBackgroundPickerState(
    selection = selection,
    saveAction = saveAction,
    eventSink = eventSink,
)

@PreviewsDayNight
@Composable
internal fun ChatBackgroundPickerViewPreview(
    @PreviewParameter(ChatBackgroundPickerStateProvider::class) state: ChatBackgroundPickerState,
) = ElementPreview {
    ChatBackgroundPickerView(
        state = state,
        onBackClick = {},
    )
}
