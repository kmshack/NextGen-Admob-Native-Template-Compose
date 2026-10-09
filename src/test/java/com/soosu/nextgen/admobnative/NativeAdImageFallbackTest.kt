package com.soosu.nextgen.admobnative

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

    private fun nativeAd(media: MediaContent?): NativeAd = Proxy.newProxyInstance(
        NativeAd::class.java.classLoader,
        arrayOf(NativeAd::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "getMediaContent" -> media
            else -> null
        }
    } as NativeAd
}
