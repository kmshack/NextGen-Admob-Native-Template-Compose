package com.soosu.nextgen.admobnative

import android.graphics.drawable.Drawable
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaContent
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd

internal fun NativeAd.primaryImageDrawable(): Drawable? =
    mediaContent?.mainImage
        ?: image?.drawable
        ?: icon?.drawable

internal fun NativeAd.iconImageDrawable(): Drawable? =
    icon?.drawable
        ?: image?.drawable
        ?: mediaContent?.mainImage

internal fun NativeAd.mediaContentWithImageFallback(): MediaContent? {
    val content = mediaContent ?: return null
    if (!content.hasVideoContent && content.mainImage == null) {
        content.mainImage = image?.drawable ?: icon?.drawable
    }
    return if (content.hasVideoContent || content.mainImage != null) content else null
}
