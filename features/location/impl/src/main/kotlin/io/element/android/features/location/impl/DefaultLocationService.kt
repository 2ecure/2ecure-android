/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.location.impl

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.features.enterprise.api.remoteconfig.MapTilerConfig
import io.element.android.features.location.api.LocationService
import io.element.android.libraries.di.SessionScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DefaultLocationService : LocationService {
    // Location sharing is off in 2ecure: no map provider, whatever the homeserver or the build configures. This
    // capability gates the location-sharing attachment UI and map rendering.
    override val mapTilerConfig: StateFlow<MapTilerConfig?> = MutableStateFlow(null)

    override fun isServiceAvailable(): StateFlow<Boolean> = MutableStateFlow(false)
}
