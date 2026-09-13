package io.legado.app.help.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import splitties.init.appCtx
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * Copies character avatar images into app-private storage so paths remain durable.
 */
object CharacterAvatarStore {

    private const val DIR_NAME = "character_avatars"
    private const val MAX_EDGE_PX = 512
    private const val JPEG_QUALITY = 85

    fun avatarsDir(context: Context = appCtx): File =
        File(context.filesDir, DIR_NAME).also { it.mkdirs() }

    fun pathFor(cardId: String, context: Context = appCtx): File =
        File(avatarsDir(context), "$cardId.jpg")

    /**
     * Copy and compress [uri] into a unique file under `character_avatars/`.
     * Replaces any existing avatar for this card (unique path so UI caches invalidate).
     *
     * Copies the content URI **once** into a temp file first — many gallery providers
     * only allow a single open of the picked URI.
     *
     * @return absolute path of the saved file
     */
    fun copyFromUri(cardId: String, uri: Uri, context: Context = appCtx): String {
        require(cardId.isNotBlank()) { "cardId is required" }
        val dir = avatarsDir(context)
        val temp = File(dir, "tmp_${System.currentTimeMillis()}.bin")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Cannot open avatar image")
            if (temp.length() <= 0L) error("Empty avatar image")
            return persistDecodedFile(cardId, temp, context)
        } finally {
            temp.delete()
        }
    }

    /**
     * Decode [bytes] (PNG/JPEG/…) and store a compressed JPEG avatar for [cardId].
     */
    fun copyFromBytes(cardId: String, bytes: ByteArray, context: Context = appCtx): String {
        require(cardId.isNotBlank()) { "cardId is required" }
        require(bytes.isNotEmpty()) { "Empty avatar image" }
        val dir = avatarsDir(context)
        val temp = File(dir, "tmp_${System.currentTimeMillis()}.bin")
        try {
            temp.writeBytes(bytes)
            return persistDecodedFile(cardId, temp, context)
        } finally {
            temp.delete()
        }
    }

    private fun persistDecodedFile(cardId: String, sourceFile: File, context: Context): String {
        val dest = File(avatarsDir(context), "$cardId-${System.currentTimeMillis()}.jpg")
        try {
            val decoded = decodeAvatarBitmap(sourceFile)
            val software = ensureSoftwareBitmap(decoded)
            if (software !== decoded) decoded.recycle()
            val scaled = scaleDown(software, MAX_EDGE_PX)
            if (scaled !== software) software.recycle()
            try {
                FileOutputStream(dest).use { out ->
                    if (!scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                        error("Cannot compress avatar image")
                    }
                }
            } finally {
                if (!scaled.isRecycled) scaled.recycle()
            }
            deleteOtherAvatars(cardId, keep = dest, context)
            return dest.absolutePath
        } catch (t: Throwable) {
            dest.delete()
            throw t
        }
    }

    fun delete(cardId: String, context: Context = appCtx) {
        deleteOtherAvatars(cardId, keep = null, context)
    }

    fun deleteFileIfExists(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }

    private fun decodeAvatarBitmap(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            val opts = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE_PX)
            }
            BitmapFactory.decodeFile(file.absolutePath, opts)?.let { return it }
        }
        // HEIC / other formats BitmapFactory may not handle — ImageDecoder (API 28+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val longest = max(w, h)
                if (longest > MAX_EDGE_PX) {
                    val scale = MAX_EDGE_PX.toFloat() / longest
                    decoder.setTargetSize(
                        (w * scale).toInt().coerceAtLeast(1),
                        (h * scale).toInt().coerceAtLeast(1),
                    )
                }
            }
        }
        error("Cannot decode avatar image")
    }

    private fun ensureSoftwareBitmap(src: Bitmap): Bitmap {
        if (src.config != Bitmap.Config.HARDWARE) return src
        return src.copy(Bitmap.Config.ARGB_8888, false)
            ?: error("Cannot convert avatar bitmap")
    }

    private fun deleteOtherAvatars(cardId: String, keep: File?, context: Context) {
        val prefix = "$cardId-"
        avatarsDir(context).listFiles()?.forEach { file ->
            if (file == keep) return@forEach
            if (file.name == "$cardId.jpg" || file.name.startsWith(prefix)) {
                file.delete()
            }
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        val longest = max(width, height)
        if (longest > maxEdge) {
            val half = longest / 2
            while (half / sample >= maxEdge) {
                sample *= 2
            }
        }
        return sample
    }

    private fun scaleDown(src: Bitmap, maxEdge: Int): Bitmap {
        val w = src.width
        val h = src.height
        val longest = maxOf(w, h)
        if (longest <= maxEdge) return src
        val scale = maxEdge.toFloat() / longest
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }
}
