/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.push.impl.notifications

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import androidx.core.graphics.drawable.IconCompat
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Size
import coil3.toBitmap
import coil3.transform.CircleCropTransformation
import coil3.transform.Transformation
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrixmedia.api.AVATAR_THUMBNAIL_SIZE_IN_PIXEL
import io.element.android.libraries.matrixmedia.api.InitialsAvatarBitmapGenerator
import io.element.android.libraries.matrixmedia.api.MediaRequestData
import io.element.android.libraries.push.api.notifications.NotificationBitmapLoader
import io.element.android.services.toolbox.api.sdk.BuildVersionSdkIntProvider
import timber.log.Timber
import kotlin.math.min

@ContributesBinding(AppScope::class)
class DefaultNotificationBitmapLoader(
    @ApplicationContext private val context: Context,
    private val sdkIntProvider: BuildVersionSdkIntProvider,
    private val initialsAvatarBitmapGenerator: InitialsAvatarBitmapGenerator,
) : NotificationBitmapLoader {
    override suspend fun getRoomBitmap(
        avatarData: AvatarData,
        imageLoader: ImageLoader,
        targetSize: Long,
        isDm: Boolean,
    ): Bitmap? {
        return try {
            loadBitmap(
                avatarData = avatarData,
                imageLoader = imageLoader,
                targetSize = targetSize,
                transformation = if (isDm) CircleCropTransformation() else RoundedSquareTransformation(),
            )
        } catch (e: Throwable) {
            Timber.e(e, "Unable to load room bitmap")
            null
        }
    }

    override suspend fun getUserIcon(
        avatarData: AvatarData,
        imageLoader: ImageLoader,
    ): IconCompat? {
        if (sdkIntProvider.get() < Build.VERSION_CODES.P) {
            return null
        }
        return try {
            loadBitmap(
                avatarData = avatarData,
                imageLoader = imageLoader,
                targetSize = AVATAR_THUMBNAIL_SIZE_IN_PIXEL,
            )
                ?.let { IconCompat.createWithBitmap(it) }
        } catch (e: Throwable) {
            Timber.e(e, "Unable to load user bitmap")
            null
        }
    }

    private fun isDarkTheme(): Boolean {
        return context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    private suspend fun loadBitmap(
        avatarData: AvatarData,
        imageLoader: ImageLoader,
        targetSize: Long,
        transformation: Transformation = CircleCropTransformation(),
    ): Bitmap? {
        val path = avatarData.url
        val data = if (path != null) {
            MediaRequestData(
                source = MediaSource(path),
                kind = MediaRequestData.Kind.Thumbnail(targetSize),
            )
        } else {
            initialsAvatarBitmapGenerator.generateBitmap(
                size = targetSize.toInt(),
                avatarData = avatarData,
                useDarkTheme = isDarkTheme(),
            )
        }
        val imageRequest = ImageRequest.Builder(context)
            .data(data)
            .transformations(transformation)
            .build()
        return imageLoader.execute(imageRequest).image?.toBitmap()
    }
}

/**
 * 2ecure: crops to a square with rounded corners (a quarter of the side), the shape rooms have in the app.
 */
private class RoundedSquareTransformation : Transformation() {
    override val cacheKey: String = "2ecure.RoundedSquareTransformation"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val side = min(input.width, input.height)
        val output = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val shader = BitmapShader(input, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply { setTranslate(-(input.width - side) / 2f, -(input.height - side) / 2f) })
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader }
        val radius = side * 0.25f
        Canvas(output).drawRoundRect(0f, 0f, side.toFloat(), side.toFloat(), radius, radius, paint)
        return output
    }
}
