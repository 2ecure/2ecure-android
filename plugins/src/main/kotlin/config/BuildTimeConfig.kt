/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package config

object BuildTimeConfig {
    const val APPLICATION_ID = "com.twoecure.messenger"
    const val APPLICATION_NAME = "2ecure"
    val METADATA_HOST_REVERSED: String? = "com.2ecure"
    val OAUTH_CLIENT_URL_PATH: String? = "apps/android"
    val URL_WEBSITE: String? = "https://2ecure.com"
    val URL_LOGO: String? = "https://2ecure.com/logo-512.png"
    val URL_COPYRIGHT: String? = "https://2ecure.com/source"
    val URL_ACCEPTABLE_USE: String? = "https://2ecure.com/agb"
    val URL_PRIVACY: String? = "https://2ecure.com/datenschutz"
    val URL_POLICY: String? = "https://2ecure.com/datenschutz"
    val SERVICES_MAPTILER_BASE_URL: String? = null
    val SERVICES_MAPTILER_APIKEY: String? = null
    val SERVICES_MAPTILER_LIGHT_MAPID: String? = null
    val SERVICES_MAPTILER_DARK_MAPID: String? = null
    val SERVICES_POSTHOG_HOST: String? = null
    val SERVICES_POSTHOG_APIKEY: String? = null
    val SERVICES_SENTRY_DSN: String? = null
    val SERVICES_SENTRY_DSN_RUST: String? = null
    val BUG_REPORT_URL: String? = null
    val BUG_REPORT_APP_NAME: String? = null
    const val PUSH_CONFIG_INCLUDE_FIREBASE: Boolean = true
    const val PUSH_CONFIG_INCLUDE_UNIFIED_PUSH: Boolean = false
    val PUSHER_APP_ID_RELEASE: String? = "com.twoecure.messenger.android"
    val PUSHER_APP_ID_DEBUG: String? = "com.twoecure.messenger.android.dev"
    val PUSHER_APP_ID_NIGHTLY: String? = "com.twoecure.messenger.android.nightly"
}
