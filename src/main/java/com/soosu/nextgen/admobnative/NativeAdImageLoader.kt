package com.soosu.nextgen.admobnative

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Resolves a native ad image asset into an [ImageBitmap] usable by Compose.
 *
 * The SDK exposes image assets either as an already decoded [Drawable] or as a [Uri] that has to be
 * fetched. Drawables resolve synchronously; URIs are decoded off the main thread and cached, so
 * this returns `null` until the download completes (and stays `null` when there is no asset).
 */
@Composable
internal fun rememberNativeAdImage(drawable: Drawable?, uri: Uri?): ImageBitmap? {
    val context = LocalContext.current
    val fromDrawable = remember(drawable) { drawable?.toImageBitmap() }

    // Only fetch when there is no drawable to render right away.
    val key = if (fromDrawable == null) uri?.toString() else null
    var downloaded by remember(key) { mutableStateOf(key?.let(NativeAdImageCache::get)) }

    LaunchedEffect(key) {
        if (key != null && uri != null && downloaded == null) {
            downloaded = NativeAdImageCache.load(context.applicationContext, uri)
        }
    }

    return fromDrawable ?: downloaded
}

private fun Drawable.toImageBitmap(): ImageBitmap? {
    if (this is BitmapDrawable) {
        return bitmap?.asImageBitmap()
    }

    val width = intrinsicWidth
    val height = intrinsicHeight
    if (width <= 0 || height <= 0) return null

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap.asImageBitmap()
}

private object NativeAdImageCache {

    private const val CACHE_SIZE_KB = 4 * 1024
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 5_000

    private val cache = object : LruCache<String, ImageBitmap>(CACHE_SIZE_KB) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return (value.width.toLong() * value.height * 4 / 1024).toInt().coerceAtLeast(1)
        }
    }

    fun get(key: String): ImageBitmap? = cache.get(key)

    suspend fun load(context: Context, uri: Uri): ImageBitmap? {
        val key = uri.toString()
        cache.get(key)?.let { return it }

        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                when (uri.scheme?.lowercase()) {
                    "http", "https" -> decodeRemoteBitmap(uri)
                    else -> context.contentResolver.openInputStream(uri)?.use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                }
            }.getOrNull()
        } ?: return null

        return bitmap.asImageBitmap().also { cache.put(key, it) }
    }

    private fun decodeRemoteBitmap(uri: Uri): Bitmap? {
        val connection = URL(uri.toString()).openConnection().apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
        }

        return try {
            if (connection is HttpURLConnection) {
                connection.instanceFollowRedirects = true
                if (connection.responseCode !in 200..299) {
                    return null
                }
            }

            connection.getInputStream().use { input ->
                BitmapFactory.decodeStream(input)
            }
        } finally {
            (connection as? HttpURLConnection)?.disconnect()
        }
    }
}
