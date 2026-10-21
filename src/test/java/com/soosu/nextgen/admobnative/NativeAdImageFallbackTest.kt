package com.soosu.nextgen.admobnative

import com.google.android.libraries.ads.mobile.sdk.common.Image
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaContent
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import java.lang.reflect.Proxy
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeAdImageFallbackTest {
    @Test
    fun `static mediated media is retained when the adapter exposes no main image drawable`() {
        var mainImageAssignments = 0
        val media = Proxy.newProxyInstance(
            MediaContent::class.java.classLoader,
            arrayOf(MediaContent::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getHasVideoContent" -> false
                "getMainImage" -> null
                "setMainImage" -> { mainImageAssignments++; null }
                else -> null
            }
        } as MediaContent

        assertSame(media, nativeAd(media).mediaContentWithImageFallback())
        assertEquals(0, mainImageAssignments)
    }

    @Test
    fun `ad without SDK media keeps the plain image fallback path`() {
        assertNull(nativeAd(null).mediaContentWithImageFallback())
    }

    @Test
    fun `icon source remains the app icon when a separate main image is also available`() {
        val icon = imageAsset()
        val mainImage = imageAsset()

        assertSame(icon, nativeAd(null, icon = icon, image = mainImage).iconImageAsset())
    }

    @Test
    fun `missing decoded icon does not substitute media drawable before its URI can load`() {
        // Pangle and Liftoff expose icons without a decoded drawable. The chosen asset's URI
        // must remain the source, instead of a drawable from a different creative winning.
        val icon = imageAsset()
        val media = Proxy.newProxyInstance(
            MediaContent::class.java.classLoader,
            arrayOf(MediaContent::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getMainImage" -> error("The main creative must not replace the icon")
                else -> null
            }
        } as MediaContent

        assertNull(nativeAd(media, icon = icon).iconImageDrawable())
    }

    @Test
    fun `main image fallback remains available when the app icon asset is absent`() {
        val mainImage = imageAsset()

        assertSame(mainImage, nativeAd(null, image = mainImage).iconImageAsset())
    }

    private fun nativeAd(
        media: MediaContent?,
        icon: Image? = null,
        image: Image? = null,
    ): NativeAd = Proxy.newProxyInstance(
        NativeAd::class.java.classLoader,
        arrayOf(NativeAd::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "getMediaContent" -> media
            "getIcon" -> icon
            "getImage" -> image
            else -> null
        }
    } as NativeAd

    // As in the manager tests, allocate an SDK value without invoking Android stub constructors.
    // Asset identity and its absent decoded drawable are all these selection tests need.
    private fun imageAsset(): Image {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        return unsafeClass.getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, Image::class.java) as Image
    }
}
