/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.api.background

import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToInt

/**
 * 2ecure: a background behind one chat's timeline that only the account's owner sees. An account's choices live in the
 * global account data event `com.2ecure.chat_backgrounds`, so they follow it to its other devices. The format is
 * shared with the iOS app (docs/chat-backgrounds.md in the 2ecure repository):
 * `{"rooms": {"!room:2ecure.com": {"type": "gradient", "id": "sky"}, "!other:2ecure.com": {"type": "image",
 * "url": "mxc://…", "mimetype": "image/jpeg"}}}`.
 */
sealed interface ChatBackground {
    data class Gradient(val gradient: ChatBackgroundGradient) : ChatBackground
    data class CustomGradient(val top: ChatBackgroundColour, val bottom: ChatBackgroundColour) : ChatBackground
    data class Colour(val colour: ChatBackgroundColour) : ChatBackground
    data class Pattern(val pattern: ChatBackgroundPattern, val colour: ChatBackgroundColour) : ChatBackground
    data class Image(val url: String, val mimeType: String?) : ChatBackground
}

/**
 * 2ecure: a colour chosen for a chat background, stored as `#RRGGBB`.
 */
@JvmInline
value class ChatBackgroundColour(val rgb: Int) {
    val hex: String get() = "#%06X".format(rgb and 0xFFFFFF)

    fun toColor(): Color = Color(0xFF shl 24 or (rgb and 0xFFFFFF))

    /** The colour patterns draw in: the base a little darker, or lighter when the base is dark. */
    val motif: ChatBackgroundColour
        get() {
            val red = (rgb shr 16 and 0xFF) / 255.0
            val green = (rgb shr 8 and 0xFF) / 255.0
            val blue = (rgb and 0xFF) / 255.0
            val luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue
            fun channel(value: Double): Int = ((if (luminance > 0.5) value * 0.84 else value + 0.2 * (1 - value)) * 255).roundToInt()
            return ChatBackgroundColour(channel(red) shl 16 or (channel(green) shl 8) or channel(blue))
        }

    companion object {
        val Default = ChatBackgroundColour(0x5B8DEF)
        val DefaultGradientBottom = ChatBackgroundColour(0xB57BEE)
        val DefaultPattern = ChatBackgroundColour(0xDCE6F2)

        fun fromHex(hex: String): ChatBackgroundColour? =
            if (hex.length == 7 && hex.startsWith("#")) hex.substring(1).toIntOrNull(16)?.let { ChatBackgroundColour(it) } else null

        fun fromColor(color: Color): ChatBackgroundColour = ChatBackgroundColour(color.toArgb() and 0xFFFFFF)
    }
}

/**
 * 2ecure: patterns drawn in code over a base colour ([PatternBackground]).
 */
enum class ChatBackgroundPattern(val id: String) {
    Regal("regal"),
    Dots("dots"),
    Stripes("stripes"),
    Checks("checks"),
    Waves("waves"),
    Zigzag("zigzag"),
    Stars("stars");

    companion object {
        fun fromId(id: String): ChatBackgroundPattern? = entries.firstOrNull { it.id == id }
    }
}

/**
 * 2ecure: the built-in backgrounds, top-to-bottom gradients in a light and a dark variant.
 */
enum class ChatBackgroundGradient(
    val id: String,
    private val light: Pair<Long, Long>,
    private val dark: Pair<Long, Long>,
) {
    Sky("sky", 0xFFD9E8F8 to 0xFFF4F8FD, 0xFF14263A to 0xFF0B121B),
    Sea("sea", 0xFFD3EFEA to 0xFFF2FAF8, 0xFF10302C to 0xFF0A1614),
    Forest("forest", 0xFFDDEFD5 to 0xFFF4FAF1, 0xFF1A2E1E to 0xFF0C150F),
    Sand("sand", 0xFFF4E6D0 to 0xFFFBF7F0, 0xFF2E2617 to 0xFF16120B),
    Rose("rose", 0xFFF6DAE3 to 0xFFFCF2F5, 0xFF33202A to 0xFF170E13),
    Lavender("lavender", 0xFFE4DDF6 to 0xFFF6F3FC, 0xFF231D3A to 0xFF100D1A);

    fun colours(isDark: Boolean): List<Color> {
        val (top, bottom) = if (isDark) dark else light
        return listOf(Color(top), Color(bottom))
    }

    companion object {
        fun fromId(id: String): ChatBackgroundGradient? = entries.firstOrNull { it.id == id }
    }
}

/**
 * 2ecure: the chat backgrounds of the session's account, shared by its chat screens and chat settings.
 */
interface ChatBackgroundStore {
    fun backgroundFlow(roomId: RoomId): Flow<ChatBackground?>

    /** Reads the account data from the SDK's store, which sync keeps up to date. */
    suspend fun refresh()

    /** Saves the background of one chat; `null` removes it. */
    suspend fun setBackground(roomId: RoomId, background: ChatBackground?): Result<Unit>

    /** Shrinks a picked photo, re-encodes it without its metadata (camera, location) and uploads it, unencrypted. */
    suspend fun uploadPhoto(uri: Uri): Result<ChatBackground>
}
