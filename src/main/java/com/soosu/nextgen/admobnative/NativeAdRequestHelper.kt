package com.soosu.nextgen.admobnative

import com.google.android.libraries.ads.mobile.sdk.common.VideoOptions
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import java.lang.reflect.Method

private val MULTIPLE_IMAGE_OPTION_METHOD_NAMES = listOf(
    "setRequestMultipleImages",
    "setMultipleImageAdAllowed",
    "setMultipleImagesEnabled"
)

/**
 * Applies default native ad options for better fill/match rate.
 *
 * Defaults:
 * - mediaAspectRatio = [NativeAd.NativeMediaAspectRatio.ANY]
 * - multipleImageAdAllowed = true (best-effort, depending on SDK support)
 * - startMuted = true
 */
@JvmOverloads
fun NativeAdRequest.Builder.applyDefaultNativeAdOptions(
    mediaAspectRatio: NativeAd.NativeMediaAspectRatio = NativeAd.NativeMediaAspectRatio.ANY,
    multipleImageAdAllowed: Boolean = true,
    startMuted: Boolean = true
): NativeAdRequest.Builder {
    setMediaAspectRatio(mediaAspectRatio)
    setVideoOptions(
        VideoOptions.Builder()
            .setStartMuted(startMuted)
            .build()
    )
    applyMultipleImageOptionCompat(multipleImageAdAllowed)
    return this
}

/**
 * Creates a [NativeAdRequest.Builder] with library defaults already applied.
 */
@JvmOverloads
fun nativeAdRequestBuilder(
    adUnitId: String,
    nativeAdTypes: List<NativeAd.NativeAdType> = listOf(NativeAd.NativeAdType.NATIVE),
    mediaAspectRatio: NativeAd.NativeMediaAspectRatio = NativeAd.NativeMediaAspectRatio.ANY,
    multipleImageAdAllowed: Boolean = true,
    startMuted: Boolean = true
): NativeAdRequest.Builder {
    return NativeAdRequest.Builder(adUnitId, nativeAdTypes)
        .applyDefaultNativeAdOptions(
            mediaAspectRatio = mediaAspectRatio,
            multipleImageAdAllowed = multipleImageAdAllowed,
            startMuted = startMuted
        )
}

/**
 * Creates a [NativeAdRequest] with library defaults already applied.
 */
@JvmOverloads
fun createNativeAdRequestWithDefaults(
    adUnitId: String,
    nativeAdTypes: List<NativeAd.NativeAdType> = listOf(NativeAd.NativeAdType.NATIVE),
    mediaAspectRatio: NativeAd.NativeMediaAspectRatio = NativeAd.NativeMediaAspectRatio.ANY,
    multipleImageAdAllowed: Boolean = true,
    startMuted: Boolean = true
): NativeAdRequest {
    return nativeAdRequestBuilder(
        adUnitId = adUnitId,
        nativeAdTypes = nativeAdTypes,
        mediaAspectRatio = mediaAspectRatio,
        multipleImageAdAllowed = multipleImageAdAllowed,
        startMuted = startMuted
    ).build()
}

private fun NativeAdRequest.Builder.applyMultipleImageOptionCompat(enabled: Boolean) {
    val method = findMultipleImageMethod() ?: return
    runCatching { method.invoke(this, enabled) }
}

private fun NativeAdRequest.Builder.findMultipleImageMethod(): Method? {
    return MULTIPLE_IMAGE_OPTION_METHOD_NAMES.firstNotNullOfOrNull { methodName ->
        runCatching {
            javaClass.getMethod(methodName, Boolean::class.javaPrimitiveType)
        }.getOrNull()
    }
}
