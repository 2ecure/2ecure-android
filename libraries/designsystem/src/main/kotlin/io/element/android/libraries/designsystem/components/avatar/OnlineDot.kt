/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import io.element.android.compound.theme.ElementTheme

/**
 * 2ecure: the green dot on the avatar of someone who is online (docs/privacy.md).
 */
@Composable
fun OnlineDot(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(ElementTheme.colors.bgCanvasDefault)
            .padding(size / 6)
            .clip(CircleShape)
            .background(ElementTheme.colors.iconSuccessPrimary)
    )
}
