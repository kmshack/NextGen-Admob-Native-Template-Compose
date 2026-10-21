package com.soosu.nextgen.admobnative

import android.graphics.drawable.Drawable
import android.net.Uri
import com.google.android.libraries.ads.mobile.sdk.common.Image
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaContent
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd

internal fun NativeAd.primaryImageDrawable(): Drawable? =
    mediaContent?.mainImage
        ?: image?.drawable
        ?: icon?.drawable

internal fun NativeAd.primaryImageUri(): Uri? =
    image?.uri
        ?: icon?.uri

/** Keep the drawable and URI tied to the same asset, including URI-only mediated icons. */
internal fun NativeAd.iconImageAsset(): Image? = icon ?: image

internal fun NativeAd.iconImageDrawable(): Drawable? {
    val asset = iconImageAsset()
    return if (asset != null) asset.drawable else mediaContent?.mainImage
}

internal fun NativeAd.iconImageUri(): Uri? =
    iconImageAsset()?.uri

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
