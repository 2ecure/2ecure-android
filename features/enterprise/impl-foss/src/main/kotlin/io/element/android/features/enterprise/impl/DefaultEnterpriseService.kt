/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.enterprise.impl

import androidx.compose.ui.graphics.Color
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.compound.annotations.CoreColorToken
import io.element.android.compound.colors.SemanticColorsLightDark
import io.element.android.compound.tokens.generated.SemanticColors
import io.element.android.compound.tokens.generated.compoundColorsDark
import io.element.android.compound.tokens.generated.compoundColorsLight
import io.element.android.compound.tokens.generated.internal.DarkColorTokens
import io.element.android.compound.tokens.generated.internal.LightColorTokens
import io.element.android.features.enterprise.api.BugReportUrl
import io.element.android.features.enterprise.api.EnterpriseService
import io.element.android.libraries.androidutils.json.JsonProvider
import io.element.android.libraries.core.extensions.mapCatchingExceptions
import io.element.android.libraries.core.uri.ensureProtocol
import io.element.android.libraries.matrix.api.ClientUrlContentFetcher
import io.element.android.libraries.matrix.api.TemporaryMatrixClientFactory
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber

@ContributesBinding(AppScope::class)
class DefaultEnterpriseService(
    private val temporaryMatrixClientFactory: TemporaryMatrixClientFactory,
    private val jsonProvider: JsonProvider,
) : EnterpriseService {
    override suspend fun isEnterpriseUser(sessionId: SessionId) = false
    override suspend fun tweakMasUrl(url: String, urlContentFetcher: ClientUrlContentFetcher) = url
    override fun homeserverAllowList(): List<String> = listOf("2ecure.com")
    override suspend fun isAllowedToConnectToHomeserver(homeserverUrl: String): Boolean {
        // Exact matches only: reject lookalike hosts, credentials, paths and non-HTTPS URLs.
        // 2ecure.com delegates via /.well-known/matrix/client to https://matrix.2ecure.com.
        return homeserverUrl.removeSuffix("/") in setOf(
            "2ecure.com",
            "https://2ecure.com",
            "https://matrix.2ecure.com",
        )
    }
    override suspend fun isElementProEnforced(serverName: String): Boolean {
        val temporaryMatrixClient = temporaryMatrixClientFactory.create(serverName).getOrElse { return false }
        return temporaryMatrixClient.use { client ->
            val baseUrl = serverName.ensureProtocol().removeSuffix("/")
            // We'll always perform a network request here since we're not interested in any cached value.
            client.getUrl("$baseUrl/.well-known/element/element.json")
                .mapCatchingExceptions { response ->
                    val remoteConfig = jsonProvider().decodeFromString<MinimalEnterpriseConfig>(String(response))
                    remoteConfig.enforceElementPro ?: false
                }
                .onFailure {
                    Timber.e(it, "Failed to fetch enterprise config for checking if Element Pro is enforced for $serverName")
                }
                .getOrElse { false }
        }
    }

    override suspend fun overrideBrandColor(sessionId: SessionId?, brandColor: String?) = Unit

    override fun brandColorsFlow(sessionId: SessionId?): Flow<Color?> {
        return flowOf(null)
    }

    override fun semanticColorsFlow(sessionId: SessionId?): Flow<SemanticColorsLightDark> {
        return flowOf(twoecureSemanticColors)
    }

    override fun firebasePushGateway(): String = "https://push.2ecure.com/_matrix/push/v1/notify"
    override fun unifiedPushDefaultPushGateway(): String? = null

    override fun bugReportUrlFlow(sessionId: SessionId?): Flow<BugReportUrl> {
        return flowOf(BugReportUrl.Disabled)
    }

    override fun getNoisyNotificationChannelId(sessionId: SessionId): String? = null
}

/**
 * 2ecure is black and white. Compound's green accent tokens take the neutral values Compound itself uses for
 * primary text, icons and actions, and the green gradients become solid or grey, so both themes stay consistent.
 */
@OptIn(CoreColorToken::class)
internal val twoecureSemanticColors = SemanticColorsLightDark(
    light = compoundColorsLight.withNeutralAccents(
        LightColorTokens.colorAlphaGray500,
        LightColorTokens.colorAlphaGray400,
        LightColorTokens.colorAlphaGray300,
        LightColorTokens.colorAlphaGray200,
        LightColorTokens.colorAlphaGray100,
    ),
    dark = compoundColorsDark.withNeutralAccents(
        DarkColorTokens.colorAlphaGray500,
        DarkColorTokens.colorAlphaGray400,
        DarkColorTokens.colorAlphaGray300,
        DarkColorTokens.colorAlphaGray200,
        DarkColorTokens.colorAlphaGray100,
    ),
)

private fun SemanticColors.withNeutralAccents(
    alpha500: Color,
    alpha400: Color,
    alpha300: Color,
    alpha200: Color,
    alpha100: Color,
) = copy(
    bgAccentRest = bgActionPrimaryRest,
    bgAccentHovered = bgActionPrimaryHovered,
    bgAccentPressed = bgActionPrimaryPressed,
    bgAccentSelected = alpha300,
    bgAccentSubtle = bgSubtleSecondary,
    bgBadgeAccent = bgSubtleSecondary,
    borderAccentPrimary = iconPrimary,
    borderAccentSubtle = borderInteractiveSecondary,
    iconAccentPrimary = iconPrimary,
    iconAccentTertiary = iconPrimary,
    textActionAccent = textActionPrimary,
    textBadgeAccent = textPrimary,
    gradientActionStop1 = bgActionPrimaryRest,
    gradientActionStop2 = bgActionPrimaryRest,
    gradientActionStop3 = bgActionPrimaryRest,
    gradientActionStop4 = bgActionPrimaryRest,
    gradientSubtleStop1 = alpha500,
    gradientSubtleStop2 = alpha400,
    gradientSubtleStop3 = alpha300,
    gradientSubtleStop4 = alpha200,
    gradientSubtleStop5 = alpha100,
)

/**
 * A minimal version of the enterprise config that only contains the fields we need to check if Element Pro is enforced.
 */
@Serializable
private data class MinimalEnterpriseConfig(
    @SerialName("enforce_element_pro") val enforceElementPro: Boolean? = null,
)
