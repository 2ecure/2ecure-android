/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.background

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.features.messages.api.background.ChatBackground
import io.element.android.features.messages.api.background.ChatBackgroundColour
import io.element.android.features.messages.api.background.ChatBackgroundGradient
import io.element.android.features.messages.api.background.ChatBackgroundPattern
import io.element.android.features.messages.api.background.ChatBackgroundStore
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.mimetype.MimeTypes
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import timber.log.Timber
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * 2ecure: writes replace the whole event, so they start from the content in the SDK's store, which sync keeps up to
 * date. Until sync brings a write back, that content is older than the write: this device's edits stay on top of it
 * until the store has them, or until another device has changed the same chat since.
 */
@ContributesBinding(SessionScope::class)
@SingleIn(SessionScope::class)
class DefaultChatBackgroundStore(
    private val matrixClient: MatrixClient,
    @ApplicationContext private val context: Context,
    private val dispatchers: CoroutineDispatchers,
) : ChatBackgroundStore {
    /** The content as last read from the SDK's store. */
    private var storedContent = JsonObject(emptyMap())
    private val pendingEdits = mutableMapOf<String, ChatBackgroundEdit>()
    private val backgrounds = MutableStateFlow<Map<String, ChatBackground>>(emptyMap())
    private val mutex = Mutex()

    override fun backgroundFlow(roomId: RoomId): Flow<ChatBackground?> =
        backgrounds.map { it[roomId.value] }.distinctUntilChanged()

    override suspend fun refresh() {
        mutex.withLock { readStoredContent() }
    }

    override suspend fun setBackground(roomId: RoomId, background: ChatBackground?): Result<Unit> = mutex.withLock {
        // Without the current content, the write would replace the other chats' backgrounds.
        readStoredContent().onFailure { return@withLock Result.failure(it) }
        val entry = background?.let { ChatBackgroundContent.entryOf(it) }
        val content = ChatBackgroundContent.withEntry(currentContent(), roomId.value, entry)
        matrixClient.setAccountData(EVENT_TYPE, content.toString())
            .onSuccess {
                val storedEntry = ChatBackgroundContent.entry(storedContent, roomId.value)
                // Earlier writes of this device that sync may still bring back count as old, too.
                val superseded = pendingEdits[roomId.value]?.let { it.superseded + listOf(it.entry) }.orEmpty() + listOf(storedEntry)
                pendingEdits[roomId.value] = ChatBackgroundEdit(entry, superseded)
                publish()
            }
            .onFailure { Timber.e(it, "Failed saving the chat background") }
    }

    override suspend fun uploadPhoto(uri: Uri): Result<ChatBackground> {
        val jpeg = withContext(dispatchers.io) {
            runCatching { ChatBackgroundPhoto.prepareJpeg(context.contentResolver, uri) }.getOrNull()
        } ?: return Result.failure(IllegalArgumentException("The photo could not be read"))
        return matrixClient.uploadMedia(MimeTypes.Jpeg, jpeg)
            .map { url -> ChatBackground.Image(url, MimeTypes.Jpeg) }
            .onFailure { Timber.e(it, "Failed uploading a chat background photo") }
    }

    /** The stored content with this device's pending edits on top. */
    private fun currentContent(): JsonObject =
        pendingEdits.entries.fold(storedContent) { content, (roomId, edit) -> ChatBackgroundContent.withEntry(content, roomId, edit.entry) }

    /** Reads the content from the SDK's store; fails when it couldn't be read or isn't a JSON object. */
    private suspend fun readStoredContent(): Result<Unit> {
        val json = matrixClient.getAccountData(EVENT_TYPE).getOrElse { return Result.failure(it) }
        val content = if (json == null) {
            JsonObject(emptyMap())
        } else {
            ChatBackgroundContent.parse(json) ?: return Result.failure(IllegalStateException("The chat backgrounds account data is not a JSON object"))
        }
        storedContent = content
        pendingEdits.entries.removeAll { (roomId, edit) -> !edit.isPending(ChatBackgroundContent.entry(content, roomId)) }
        publish()
        return Result.success(Unit)
    }

    private fun publish() {
        backgrounds.value = ChatBackgroundContent.backgrounds(currentContent())
    }

    companion object {
        const val EVENT_TYPE = "com.2ecure.chat_backgrounds"
    }
}

/**
 * 2ecure: one chat's background as written by this device, until the SDK's store has it. [superseded] holds the
 * entries the store had before this device's writes for the chat: while it still has one of them, sync hasn't
 * brought the latest write back; anything else is that write, or a newer change from another device.
 */
internal data class ChatBackgroundEdit(
    val entry: JsonObject?,
    val superseded: List<JsonObject?>,
) {
    fun isPending(storedEntry: JsonObject?): Boolean = storedEntry != entry && storedEntry in superseded
}

/**
 * 2ecure: reads and changes the content of `com.2ecure.chat_backgrounds`; keys this version doesn't know stay.
 */
internal object ChatBackgroundContent {
    fun parse(json: String): JsonObject? = runCatching { Json.parseToJsonElement(json) as? JsonObject }.getOrNull()

    fun backgrounds(content: JsonObject): Map<String, ChatBackground> {
        val rooms = content["rooms"] as? JsonObject ?: return emptyMap()
        return rooms.mapNotNull { (roomId, entry) -> (entry as? JsonObject)?.let(::background)?.let { roomId to it } }.toMap()
    }

    fun withBackground(content: JsonObject, roomId: String, background: ChatBackground?): JsonObject =
        withEntry(content, roomId, background?.let { entryOf(it) })

    /** The content with one room's raw entry replaced, or removed for `null`. */
    fun withEntry(content: JsonObject, roomId: String, entry: JsonObject?): JsonObject {
        val rooms = (content["rooms"] as? JsonObject).orEmpty().toMutableMap()
        if (entry == null) {
            rooms.remove(roomId)
        } else {
            rooms[roomId] = entry
        }
        return JsonObject(content + ("rooms" to JsonObject(rooms)))
    }

    /** One room's raw entry. */
    fun entry(content: JsonObject, roomId: String): JsonObject? = (content["rooms"] as? JsonObject)?.get(roomId) as? JsonObject

    /** One room's entry; `null` for a kind or gradient this version doesn't know. */
    private fun background(entry: JsonObject): ChatBackground? = when (entry.string("type")) {
        "gradient" -> if (entry.containsKey("id")) {
            entry.string("id")?.let { ChatBackgroundGradient.fromId(it) }?.let { ChatBackground.Gradient(it) }
        } else {
            val colours = (entry["colours"] as? JsonArray)?.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.let { ChatBackgroundColour.fromHex(it) } }
            if (colours?.size == 2 && colours.all { it != null }) ChatBackground.CustomGradient(colours[0]!!, colours[1]!!) else null
        }
        "colour" -> entry.string("colour")?.let { ChatBackgroundColour.fromHex(it) }?.let { ChatBackground.Colour(it) }
        "pattern" -> {
            val pattern = entry.string("id")?.let { ChatBackgroundPattern.fromId(it) }
            val colour = entry.string("colour")?.let { ChatBackgroundColour.fromHex(it) }
            if (pattern != null && colour != null) ChatBackground.Pattern(pattern, colour) else null
        }
        "image" -> entry.string("url")?.takeIf { it.startsWith("mxc://") }?.let { ChatBackground.Image(it, entry.string("mimetype")) }
        else -> null
    }

    fun entryOf(background: ChatBackground): JsonObject = when (background) {
        is ChatBackground.Gradient -> buildJsonObject {
            put("type", "gradient")
            put("id", background.gradient.id)
        }
        is ChatBackground.CustomGradient -> buildJsonObject {
            put("type", "gradient")
            putJsonArray("colours") {
                add(background.top.hex)
                add(background.bottom.hex)
            }
        }
        is ChatBackground.Colour -> buildJsonObject {
            put("type", "colour")
            put("colour", background.colour.hex)
        }
        is ChatBackground.Pattern -> buildJsonObject {
            put("type", "pattern")
            put("id", background.pattern.id)
            put("colour", background.colour.hex)
        }
        is ChatBackground.Image -> buildJsonObject {
            put("type", "image")
            put("url", background.url)
            put("mimetype", background.mimeType ?: MimeTypes.Jpeg)
        }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}

/**
 * 2ecure: a picked photo as a JPEG of at most 2048 px on the long side, upright, without the original's metadata.
 */
internal object ChatBackgroundPhoto {
    private const val MAX_PIXEL_SIZE = 2048
    private const val JPEG_QUALITY = 80

    fun prepareJpeg(resolver: ContentResolver, uri: Uri): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_PIXEL_SIZE) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val exif = resolver.openInputStream(uri)?.use { ExifInterface(it) }

        val scale = min(1f, MAX_PIXEL_SIZE.toFloat() / max(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(if (exif?.isFlipped == true) -scale else scale, scale)
            postRotate(exif?.rotationDegrees?.toFloat() ?: 0f)
        }
        val bitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            output.toByteArray()
        }
    }
}
