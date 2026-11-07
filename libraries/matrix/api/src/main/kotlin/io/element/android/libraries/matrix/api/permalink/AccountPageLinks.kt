/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.permalink

/**
 * 2ecure: the account pages (account.2ecure.com) open in a Custom Tab with the browser's own sign-in, which can be
 * another account than the app's, and their «Sign out of account» only ended that web sign-in. The app opens them with
 * `#2ecure-app=android&user=<its Matrix ID>`; 2ecure's account page templates (`components/2ecure_app_signout.html`)
 * then cover the page when the web sign-in is another account, and after a sign-out there hand back to the app with
 * `com.2ecure:/signed-out` (the OAuth redirect scheme), on which the app runs its own sign-out.
 */
object AccountPageLinks {
    private const val ACCOUNT_PAGES = "https://account.2ecure.com/"
    private const val APP_MARKER = "2ecure-app=android"
    private val signedOutLink = Regex("^com\\.2ecure(\\.debug|\\.nightly)?:/signed-out$")

    /** The account page [url], marked as opened from the app by [userId] (unchanged for other hosts or with a fragment already). */
    fun appUrl(url: String, userId: String): String {
        if (!url.startsWith(ACCOUNT_PAGES, ignoreCase = true) || url.contains('#')) return url
        return "$url#$APP_MARKER&user=${InvitationLinks.percentEncode(userId)}"
    }

    /** Whether [url] is the account pages' hand-back after a sign-out there. */
    fun isSignedOutLink(url: String): Boolean = signedOutLink.matches(url)
}
