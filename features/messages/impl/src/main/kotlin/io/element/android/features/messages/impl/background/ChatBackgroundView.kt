/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.background

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.api.background.ChatBackground
import io.element.android.features.messages.api.background.ChatBackgroundGradient
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrixmedia.api.MediaRequestData

private const val VEIL_ALPHA_LIGHT = 0.45f
private const val VEIL_ALPHA_DARK = 0.55f

/**
 * 2ecure: draws a chat's background behind its timeline.
 */
@Composable
internal fun ChatBackgroundView(
    background: ChatBackground,
    modifier: Modifier = Modifier,
) {
    val isDark = !ElementTheme.isLightTheme
    when (background) {
        is ChatBackground.Gradient -> Box(
            modifier = modifier.background(Brush.verticalGradient(background.gradient.colours(isDark)))
        )
        is ChatBackground.Image -> Box(modifier = modifier.clipToBounds()) {
            // The photo keeps the screen's height and its top edge, so it doesn't jump when the keyboard opens.
            AsyncImage(
                model = MediaRequestData(MediaSource(background.url), MediaRequestData.Kind.Content),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .height(LocalConfiguration.current.screenHeightDp.dp),
            )
            // A veil in the canvas colour keeps what is drawn straight on the background (dates, «X joined») readable.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ElementTheme.colors.bgCanvasDefault.copy(alpha = if (isDark) VEIL_ALPHA_DARK else VEIL_ALPHA_LIGHT))
            )
        }
    }
}

@PreviewsDayNight
@Composable
internal fun ChatBackgroundViewPreview() = ElementPreview {
    ChatBackgroundView(
        background = ChatBackground.Gradient(ChatBackgroundGradient.Sky),
        modifier = Modifier.fillMaxSize(),
    )
}
