package com.soosu.nextgen.admobnative

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaContent
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

internal fun ImageView.setNativeAdImage(
    drawable: Drawable?,
    uri: Uri?,
    container: View
): Boolean {
    return NativeAdImageLoader.setImage(this, drawable, uri, container)
}

internal fun MediaView.setNativeAdMediaOrImage(
    nativeAd: NativeAd,
    fallbackImageView: ImageView,
    container: View,
    onMediaContent: (MediaContent) -> Unit = {}
): MediaContent? {
    val mediaContent = nativeAd.mediaContentWithImageFallback()
    if (mediaContent != null) {
        fallbackImageView.setTag(R.id.native_ad_image_request_key, null)
        fallbackImageView.visibility = View.GONE
        fallbackImageView.setImageDrawable(null)
        this.mediaContent = mediaContent
        this.visibility = View.VISIBLE
        container.visibility = View.VISIBLE
        onMediaContent(mediaContent)
        return mediaContent
    }

    this.mediaContent = null
    this.visibility = View.GONE
    fallbackImageView.setNativeAdImage(
        drawable = nativeAd.primaryImageDrawable(),
        uri = nativeAd.primaryImageUri(),
        container = container
    )
    return null
}

private object NativeAdImageLoader {

    private const val CACHE_SIZE_KB = 4 * 1024
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 5_000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val bitmapCache = object : LruCache<String, Bitmap>(CACHE_SIZE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.byteCount / 1024).coerceAtLeast(1)
        }
    }

    fun setImage(
        imageView: ImageView,
        drawable: Drawable?,
        uri: Uri?,
        container: View
    ): Boolean {
        if (drawable != null) {
            imageView.setTag(R.id.native_ad_image_request_key, null)
            container.visibility = View.VISIBLE
            imageView.visibility = View.VISIBLE
            imageView.setImageDrawable(drawable)
            return true
        }

        if (uri == null) {
            imageView.setTag(R.id.native_ad_image_request_key, null)
            container.visibility = View.GONE
            imageView.setImageDrawable(null)
            return false
        }

        val key = uri.toString()
        bitmapCache.get(key)?.let { bitmap ->
            imageView.setTag(R.id.native_ad_image_request_key, key)
            container.visibility = View.VISIBLE
            imageView.visibility = View.VISIBLE
            imageView.setImageDrawable(BitmapDrawable(imageView.resources, bitmap))
            return true
        }

        if (imageView.getTag(R.id.native_ad_image_request_key) == key) {
            container.visibility = View.VISIBLE
            imageView.visibility = View.VISIBLE
            return true
        }

        imageView.setTag(R.id.native_ad_image_request_key, key)
        container.visibility = View.VISIBLE
        imageView.visibility = View.VISIBLE
        imageView.setImageDrawable(null)

        val appContext = imageView.context.applicationContext
        scope.launch {
            val bitmap = loadBitmap(appContext, uri)
            if (imageView.getTag(R.id.native_ad_image_request_key) != key) {
                return@launch
            }

            if (bitmap != null) {
                bitmapCache.put(key, bitmap)
                container.visibility = View.VISIBLE
                imageView.visibility = View.VISIBLE
                imageView.setImageDrawable(BitmapDrawable(imageView.resources, bitmap))
            } else {
                imageView.setTag(R.id.native_ad_image_request_key, null)
                container.visibility = View.GONE
                imageView.setImageDrawable(null)
            }
        }
        return true
    }

    private suspend fun loadBitmap(context: Context, uri: Uri): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                when (uri.scheme?.lowercase()) {
                    "http", "https" -> decodeRemoteBitmap(uri)
                    else -> context.contentResolver.openInputStream(uri)?.use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                }
            }.getOrNull()
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
