/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.api.background

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 2ecure: a pattern over its base colour; the geometry is shared with iOS (docs/chat-backgrounds.md). Sizes are in dp,
 * times [scale] (swatches draw it smaller).
 */
@Composable
fun PatternBackground(
    pattern: ChatBackgroundPattern,
    colour: ChatBackgroundColour,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    Spacer(
        modifier = modifier.drawWithCache {
            val unit = density * scale
            val drawing = patternDrawing(pattern, size.width / unit, size.height / unit)
            val base = colour.toColor()
            val motif = colour.motif.toColor()
            onDrawBehind {
                drawRect(base)
                scale(unit, unit, pivot = Offset.Zero) {
                    drawing.fill?.let { drawPath(it, motif) }
                    drawing.stroke?.let { drawPath(it, motif, style = Stroke(width = drawing.strokeWidth, join = drawing.join)) }
                }
            }
        }
    )
}

private class PatternDrawing(
    val fill: Path? = null,
    val stroke: Path? = null,
    val strokeWidth: Float = 0f,
    val join: StrokeJoin = StrokeJoin.Miter,
)

private fun patternDrawing(pattern: ChatBackgroundPattern, width: Float, height: Float): PatternDrawing = when (pattern) {
    ChatBackgroundPattern.Regal -> {
        val diamonds = Path()
        val dots = Path()
        tiles(width, height, 40f, 56f) { x, y ->
            diamonds.moveTo(x + 20, y)
            diamonds.lineTo(x + 40, y + 28)
            diamonds.lineTo(x + 20, y + 56)
            diamonds.lineTo(x, y + 28)
            diamonds.close()
            for ((dx, dy) in listOf(20f to 22f, 20f to 34f, 14f to 28f, 26f to 28f)) {
                dots.addCircle(x + dx, y + dy, 2.2f)
            }
            dots.addCircle(x + 20, y + 28, 1.6f)
            for ((dx, dy) in listOf(0f to 0f, 40f to 0f, 0f to 56f, 40f to 56f)) {
                dots.addCircle(x + dx, y + dy, 3f)
            }
        }
        PatternDrawing(fill = dots, stroke = diamonds, strokeWidth = 1.5f)
    }
    ChatBackgroundPattern.Dots -> {
        val dots = Path()
        tiles(width, height, 24f, 24f) { x, y ->
            dots.addCircle(x + 6, y + 6, 3f)
            dots.addCircle(x + 18, y + 18, 3f)
        }
        PatternDrawing(fill = dots)
    }
    ChatBackgroundPattern.Stripes -> {
        val stripes = Path()
        var offset = 0f
        while (offset <= width + height) {
            stripes.moveTo(offset, 0f)
            stripes.lineTo(offset - height, height)
            offset += 20
        }
        PatternDrawing(stroke = stripes, strokeWidth = 6f)
    }
    ChatBackgroundPattern.Checks -> {
        val squares = Path()
        tiles(width, height, 32f, 32f) { x, y ->
            squares.addRect(Rect(x, y, x + 16, y + 16))
            squares.addRect(Rect(x + 16, y + 16, x + 32, y + 32))
        }
        PatternDrawing(fill = squares)
    }
    ChatBackgroundPattern.Waves -> {
        val waves = Path()
        var y = 0f
        while (y <= height) {
            waves.moveTo(0f, y + 10)
            var x = 0f
            while (x <= width) {
                waves.quadraticTo(x + 10, y + 2, x + 20, y + 10)
                waves.quadraticTo(x + 30, y + 18, x + 40, y + 10)
                x += 40
            }
            y += 20
        }
        PatternDrawing(stroke = waves, strokeWidth = 2f)
    }
    ChatBackgroundPattern.Zigzag -> {
        val zigzag = Path()
        var y = 0f
        while (y <= height) {
            zigzag.moveTo(0f, y + 12)
            var x = 0f
            while (x <= width) {
                zigzag.lineTo(x + 12, y + 4)
                zigzag.lineTo(x + 24, y + 12)
                x += 24
            }
            y += 16
        }
        PatternDrawing(stroke = zigzag, strokeWidth = 2f, join = StrokeJoin.Round)
    }
    ChatBackgroundPattern.Stars -> {
        val stars = Path()
        tiles(width, height, 48f, 48f) { x, y ->
            stars.addStar(x + 12, y + 12)
            stars.addStar(x + 36, y + 36)
        }
        PatternDrawing(fill = stars)
    }
}

private inline fun tiles(width: Float, height: Float, tileWidth: Float, tileHeight: Float, block: (Float, Float) -> Unit) {
    var y = 0f
    while (y <= height) {
        var x = 0f
        while (x <= width) {
            block(x, y)
            x += tileWidth
        }
        y += tileHeight
    }
}

private fun Path.addCircle(x: Float, y: Float, radius: Float) {
    addOval(Rect(x - radius, y - radius, x + radius, y + radius))
}

private fun Path.addStar(centreX: Float, centreY: Float) {
    for (index in 0 until 10) {
        val radius = if (index % 2 == 0) 6f else 2.6f
        val angle = -PI / 2 + index * PI / 5
        val x = centreX + radius * cos(angle).toFloat()
        val y = centreY + radius * sin(angle).toFloat()
        if (index == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
