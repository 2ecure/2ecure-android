/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.permalink

/**
 * 2ecure: the personal invitation link `https://account.2ecure.com/register#code=<invitation code>`, which invitations
 * carry as text and as a QR code. Scanned with «Scan QR code», it creates the account in the app: the account
 * server's registration opens with the same `#code=`, and 2ecure's account pages put it into the invitation code field.
 */
object InvitationLinks {
    private const val REGISTRATION_URL = "https://account.2ecure.com/register"

    /** The invitation code in a scanned QR code, or null when it isn't a 2ecure invitation link. */
    fun invitationCode(qrCode: ByteArray): String? {
        val text = qrCode.toString(Charsets.UTF_8).trim()
        val hash = text.indexOf('#')
        if (hash < 0 || !text.substring(0, hash).removeSuffix("/").equals(REGISTRATION_URL, ignoreCase = true)) return null
        return text.substring(hash + 1)
            .split('&')
            .firstOrNull { it.startsWith("code=") }
            ?.removePrefix("code=")
            ?.let(::percentDecode)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    /** The account server [url] with the invitation code in its fragment, as the invitation link carries it. */
    fun withInvitationCode(url: String, code: String): String = url.substringBefore('#') + "#code=" + percentEncode(code)

    internal fun percentEncode(value: String): String = buildString {
        value.toByteArray(Charsets.UTF_8).forEach { byte ->
            val c = byte.toInt().toChar()
            if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in "-._~") append(c) else append("%%%02X".format(byte.toInt() and 0xFF))
        }
    }

    private fun percentDecode(value: String): String? = runCatching {
        val bytes = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < value.length) {
            if (value[i] == '%') {
                bytes.write(value.substring(i + 1, i + 3).toInt(16))
                i += 3
            } else {
                bytes.write(value[i].toString().toByteArray(Charsets.UTF_8))
                i++
            }
        }
        bytes.toString(Charsets.UTF_8.name())
    }.getOrNull()
}
