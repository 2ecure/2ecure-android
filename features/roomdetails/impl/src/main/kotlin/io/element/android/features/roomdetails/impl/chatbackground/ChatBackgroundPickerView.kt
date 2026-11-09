/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomdetails.impl.chatbackground

import androidx.annotation.StringRes
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.api.background.ChatBackground
import io.element.android.features.messages.api.background.ChatBackgroundColour
import io.element.android.features.messages.api.background.ChatBackgroundGradient
import io.element.android.features.messages.api.background.ChatBackgroundPattern
import io.element.android.features.messages.api.background.PatternBackground
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
 * 2ecure: the screen for choosing a chat's background: none, a photo, a colour, a gradient or a pattern.
 */
@Composable
fun ChatBackgroundPickerView(
    state: ChatBackgroundPickerState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The colours in the colour rows; the chat's current choice fills them in.
    var colour by rememberSaveable { mutableIntStateOf(ChatBackgroundColour.Default.rgb) }
    var gradientTop by rememberSaveable { mutableIntStateOf(ChatBackgroundColour.Default.rgb) }
    var gradientBottom by rememberSaveable { mutableIntStateOf(ChatBackgroundColour.DefaultGradientBottom.rgb) }
    var patternColour by rememberSaveable { mutableIntStateOf(ChatBackgroundColour.DefaultPattern.rgb) }
    var editing by remember { mutableStateOf<ColourTarget?>(null) }
    LaunchedEffect(state.selection) {
        when (val selection = state.selection) {
            is ChatBackground.Colour -> colour = selection.colour.rgb
            is ChatBackground.CustomGradient -> {
                gradientTop = selection.top.rgb
                gradientBottom = selection.bottom.rgb
            }
            is ChatBackground.Pattern -> patternColour = selection.colour.rgb
            else -> Unit
        }
    }
    fun select(background: ChatBackground?) = state.eventSink(ChatBackgroundPickerEvent.Select(background))
    val customGradient = ChatBackground.CustomGradient(ChatBackgroundColour(gradientTop), ChatBackgroundColour(gradientBottom))

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
            ChoiceItem(
                title = stringResource(R.string.twoecure_chat_background_none),
                leading = ListItemContent.Custom { Swatch(SolidColor(ElementTheme.colors.bgCanvasDefault)) },
                isSelected = state.selection == null,
                onClick = { select(null) },
            )
            ChoiceItem(
                title = stringResource(R.string.twoecure_chat_background_photo),
                leading = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Image())),
                isSelected = state.selection is ChatBackground.Image,
                onClick = { state.eventSink(ChatBackgroundPickerEvent.PickPhoto) },
            )

            ListSectionHeader(title = stringResource(R.string.twoecure_chat_background_colour_section))
            ChoiceItem(
                title = stringResource(R.string.twoecure_chat_background_your_colour),
                leading = ListItemContent.Custom { Swatch(SolidColor(ChatBackgroundColour(colour).toColor())) },
                isSelected = state.selection is ChatBackground.Colour,
                onClick = { select(ChatBackground.Colour(ChatBackgroundColour(colour))) },
            )
            EditColourItem(R.string.twoecure_chat_background_change_colour, null) { editing = ColourTarget.Colour }

            ListSectionHeader(title = stringResource(R.string.twoecure_chat_background_gradients))
            ChatBackgroundGradient.entries.forEach { gradient ->
                ChoiceItem(
                    title = stringResource(gradient.nameRes()),
                    leading = ListItemContent.Custom { Swatch(Brush.verticalGradient(gradient.colours(isDark = !ElementTheme.isLightTheme))) },
                    isSelected = state.selection == ChatBackground.Gradient(gradient),
                    onClick = { select(ChatBackground.Gradient(gradient)) },
                )
            }
            ChoiceItem(
                title = stringResource(R.string.twoecure_chat_background_your_gradient),
                leading = ListItemContent.Custom { Swatch(Brush.verticalGradient(listOf(customGradient.top.toColor(), customGradient.bottom.toColor()))) },
                isSelected = state.selection is ChatBackground.CustomGradient,
                onClick = { select(customGradient) },
            )
            EditColourItem(R.string.twoecure_chat_background_top_colour, ChatBackgroundColour(gradientTop)) { editing = ColourTarget.GradientTop }
            EditColourItem(R.string.twoecure_chat_background_bottom_colour, ChatBackgroundColour(gradientBottom)) { editing = ColourTarget.GradientBottom }

            ListSectionHeader(title = stringResource(R.string.twoecure_chat_background_patterns))
            EditColourItem(R.string.twoecure_chat_background_pattern_colour, ChatBackgroundColour(patternColour)) { editing = ColourTarget.Pattern }
            ChatBackgroundPattern.entries.forEach { pattern ->
                ChoiceItem(
                    title = stringResource(pattern.nameRes()),
                    leading = ListItemContent.Custom {
                        PatternBackground(
                            pattern = pattern,
                            colour = ChatBackgroundColour(patternColour),
                            scale = 0.5f,
                            modifier = Modifier.swatchShape(),
                        )
                    },
                    isSelected = (state.selection as? ChatBackground.Pattern)?.pattern == pattern,
                    onClick = { select(ChatBackground.Pattern(pattern, ChatBackgroundColour(patternColour))) },
                )
            }
            Text(
                text = stringResource(R.string.twoecure_chat_background_footer),
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }

    editing?.let { target ->
        val initial = when (target) {
            ColourTarget.Colour -> colour
            ColourTarget.GradientTop -> gradientTop
            ColourTarget.GradientBottom -> gradientBottom
            ColourTarget.Pattern -> patternColour
        }
        ChatBackgroundColourDialog(
            title = stringResource(target.titleRes),
            initial = ChatBackgroundColour(initial),
            onDismiss = { editing = null },
            onSubmit = { chosen ->
                editing = null
                when (target) {
                    ColourTarget.Colour -> {
                        colour = chosen.rgb
                        select(ChatBackground.Colour(chosen))
                    }
                    ColourTarget.GradientTop -> {
                        gradientTop = chosen.rgb
                        select(ChatBackground.CustomGradient(chosen, ChatBackgroundColour(gradientBottom)))
                    }
                    ColourTarget.GradientBottom -> {
                        gradientBottom = chosen.rgb
                        select(ChatBackground.CustomGradient(ChatBackgroundColour(gradientTop), chosen))
                    }
                    ColourTarget.Pattern -> {
                        patternColour = chosen.rgb
                        (state.selection as? ChatBackground.Pattern)?.let { select(ChatBackground.Pattern(it.pattern, chosen)) }
                    }
                }
            },
        )
    }

    AsyncActionView(
        async = state.saveAction,
        onSuccess = {},
        onErrorDismiss = { state.eventSink(ChatBackgroundPickerEvent.ClearError) },
        errorMessage = { stringResource(R.string.twoecure_chat_background_error) },
    )
}

private enum class ColourTarget(@StringRes val titleRes: Int) {
    Colour(R.string.twoecure_chat_background_choose_colour),
    GradientTop(R.string.twoecure_chat_background_top_colour),
    GradientBottom(R.string.twoecure_chat_background_bottom_colour),
    Pattern(R.string.twoecure_chat_background_pattern_colour),
}

@Composable
private fun ChoiceItem(
    title: String,
    leading: ListItemContent,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        leadingContent = leading,
        trailingContent = ListItemContent.RadioButton(selected = isSelected),
        onClick = onClick,
    ) {
        Text(title)
    }
}

/** A row that opens the colour dialog; [colour] shows the current one, if the row has one of its own. */
@Composable
private fun EditColourItem(
    @StringRes titleRes: Int,
    colour: ChatBackgroundColour?,
    onClick: () -> Unit,
) {
    ListItem(
        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Edit())),
        trailingContent = colour?.let { chosen -> ListItemContent.Custom { Swatch(SolidColor(chosen.toColor())) } },
        onClick = onClick,
    ) {
        Text(stringResource(titleRes))
    }
}

@Composable
private fun Swatch(brush: Brush) {
    Box(
        modifier = Modifier
            .swatchShape()
            .background(brush)
    )
}

@Composable
private fun Modifier.swatchShape(): Modifier = this
    .size(24.dp)
    .clip(CircleShape)
    .border(1.dp, ElementTheme.colors.borderInteractiveSecondary, CircleShape)

private fun ChatBackgroundGradient.nameRes(): Int = when (this) {
    ChatBackgroundGradient.Sky -> R.string.twoecure_chat_background_sky
    ChatBackgroundGradient.Sea -> R.string.twoecure_chat_background_sea
    ChatBackgroundGradient.Forest -> R.string.twoecure_chat_background_forest
    ChatBackgroundGradient.Sand -> R.string.twoecure_chat_background_sand
    ChatBackgroundGradient.Rose -> R.string.twoecure_chat_background_rose
    ChatBackgroundGradient.Lavender -> R.string.twoecure_chat_background_lavender
}

private fun ChatBackgroundPattern.nameRes(): Int = when (this) {
    ChatBackgroundPattern.Regal -> R.string.twoecure_chat_background_pattern_regal
    ChatBackgroundPattern.Dots -> R.string.twoecure_chat_background_pattern_dots
    ChatBackgroundPattern.Stripes -> R.string.twoecure_chat_background_pattern_stripes
    ChatBackgroundPattern.Checks -> R.string.twoecure_chat_background_pattern_checks
    ChatBackgroundPattern.Waves -> R.string.twoecure_chat_background_pattern_waves
    ChatBackgroundPattern.Zigzag -> R.string.twoecure_chat_background_pattern_zigzag
    ChatBackgroundPattern.Stars -> R.string.twoecure_chat_background_pattern_stars
}

internal class ChatBackgroundPickerStateProvider : PreviewParameterProvider<ChatBackgroundPickerState> {
    override val values: Sequence<ChatBackgroundPickerState>
        get() = sequenceOf(
            aChatBackgroundPickerState(selection = null),
            aChatBackgroundPickerState(selection = ChatBackground.Gradient(ChatBackgroundGradient.Sea)),
            aChatBackgroundPickerState(selection = ChatBackground.Image("mxc://2ecure.com/photo", "image/jpeg")),
            aChatBackgroundPickerState(selection = ChatBackground.Pattern(ChatBackgroundPattern.Regal, ChatBackgroundColour.DefaultPattern)),
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
