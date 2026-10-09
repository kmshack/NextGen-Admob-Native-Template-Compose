package com.soosu.nextgen.admobnative

import android.graphics.drawable.Drawable
import android.net.Uri
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaContent
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd

internal fun NativeAd.primaryImageDrawable(): Drawable? =
    mediaContent?.mainImage
        ?: image?.drawable
        ?: icon?.drawable

internal fun NativeAd.primaryImageUri(): Uri? =
    image?.uri
        ?: icon?.uri

internal fun NativeAd.iconImageDrawable(): Drawable? =
    icon?.drawable
        ?: image?.drawable
        ?: mediaContent?.mainImage

internal fun NativeAd.iconImageUri(): Uri? =
    icon?.uri
        ?: image?.uri

internal fun NativeAd.mediaContentWithImageFallback(): MediaContent? {
    val content = mediaContent ?: return null
    if (!content.hasVideoContent && content.mainImage == null) {
        image?.drawable?.let { content.mainImage = it }
    }
    // A mediated static ad can render its main image only through MediaView (Meta does this),
    // without exposing a Drawable via mainImage or image. Preserve that media content so the
    // adapter can populate its view; an app icon is not a replacement for the main creative.
    return content
}
