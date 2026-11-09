/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomdetails.impl.chatbackground

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.api.background.ChatBackgroundColour
import io.element.android.libraries.designsystem.components.dialogs.ListDialog
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Text

/**
 * 2ecure: picks any colour: a hue bar and a saturation/brightness field, saved with OK.
 */
@Composable
internal fun ChatBackgroundColourDialog(
    title: String,
    initial: ChatBackgroundColour,
    onDismiss: () -> Unit,
    onSubmit: (ChatBackgroundColour) -> Unit,
) {
    val hsv = remember(initial) {
        FloatArray(3).also { android.graphics.Color.RGBToHSV(initial.rgb shr 16 and 0xFF, initial.rgb shr 8 and 0xFF, initial.rgb and 0xFF, it) }
    }
    var hue by remember(initial) { mutableFloatStateOf(hsv[0]) }
    var saturation by remember(initial) { mutableFloatStateOf(hsv[1]) }
    var brightness by remember(initial) { mutableFloatStateOf(hsv[2]) }
    val colour = Color.hsv(hue, saturation, brightness)

    ListDialog(
        title = title,
        onSubmit = { onSubmit(ChatBackgroundColour.fromColor(colour)) },
        onDismissRequest = onDismiss,
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SaturationBrightnessField(
                    hue = hue,
                    saturation = saturation,
                    brightness = brightness,
                    onChange = { newSaturation, newBrightness ->
                        saturation = newSaturation
                        brightness = newBrightness
                    },
                )
                HueBar(hue = hue, onChange = { hue = it })
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colour)
                            .border(1.dp, ElementTheme.colors.borderInteractiveSecondary, CircleShape)
                    )
                    Text(
                        text = ChatBackgroundColour.fromColor(colour).hex,
                        style = ElementTheme.typography.fontBodyMdRegular,
                        color = ElementTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SaturationBrightnessField(
    hue: Float,
    saturation: Float,
    brightness: Float,
    onChange: (saturation: Float, brightness: Float) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            .pointerInput(Unit) {
                trackPosition { x, y -> onChange(x, 1f - y) }
            }
            .drawWithContent {
                drawContent()
                val centre = Offset(saturation * size.width, (1f - brightness) * size.height)
                drawCircle(Color.White, radius = 9.dp.toPx(), center = centre, style = Stroke(width = 2.dp.toPx()))
            }
    )
}

@Composable
private fun HueBar(
    hue: Float,
    onChange: (Float) -> Unit,
) {
    val hues = remember { (0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) } }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(hues))
            .pointerInput(Unit) {
                trackPosition { x, _ -> onChange(x * 359.9f) }
            }
            .drawWithContent {
                drawContent()
                val centre = Offset(hue / 360f * size.width, size.height / 2)
                drawCircle(Color.White, radius = 11.dp.toPx(), center = centre, style = Stroke(width = 2.dp.toPx()))
            }
    )
}

/** Reports the touched point as fractions (0…1) of the width and height, while the finger is down. */
private suspend fun PointerInputScope.trackPosition(onPosition: (x: Float, y: Float) -> Unit) {
    fun report(position: Offset) {
        onPosition((position.x / size.width).coerceIn(0f, 1f), (position.y / size.height).coerceIn(0f, 1f))
    }
    awaitEachGesture {
        val down = awaitFirstDown()
        report(down.position)
        down.consume()
        drag(down.id) { change ->
            report(change.position)
            change.consume()
        }
    }
}

@PreviewsDayNight
@Composable
internal fun ChatBackgroundColourDialogPreview() = ElementPreview {
    ChatBackgroundColourDialog(
        title = "Choose a colour",
        initial = ChatBackgroundColour.Default,
        onDismiss = {},
        onSubmit = {},
    )
}
