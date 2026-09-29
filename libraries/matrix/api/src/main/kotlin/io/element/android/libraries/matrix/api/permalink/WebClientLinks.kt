/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.permalink

/**
 * 2ecure: links people share (profile, room, space, message, invite text) point at the web client, in Element Web's
 * own format, instead of matrix.to, which does not know the closed 2ecure service. The apps and Element Web open
 * these links; people without the app land in the web client.
 *
 * - https://matrix.to/#/@alice:2ecure.com -> https://chat.2ecure.com/#/user/@alice:2ecure.com
 * - https://matrix.to/#/!room:2ecure.com/$event?via=2ecure.com -> https://chat.2ecure.com/#/room/!room:2ecure.com/$event?via=2ecure.com
 *
 * Anything that is not a matrix.to link is returned unchanged. Mentions inside messages keep matrix.to links, which
 * every Matrix client understands.
 */
object WebClientLinks {
    const val WEB_CLIENT_BASE_URL = "https://chat.2ecure.com/#/"
    private const val MATRIX_TO_BASE_URL = "https://matrix.to/#/"

    fun fromMatrixTo(permalink: String): String {
        if (!permalink.startsWith(MATRIX_TO_BASE_URL)) return permalink
        val entity = permalink.removePrefix(MATRIX_TO_BASE_URL)
        val kind = if (entity.startsWith("@") || entity.startsWith("%40")) "user" else "room"
        return "$WEB_CLIENT_BASE_URL$kind/$entity"
    }
}
