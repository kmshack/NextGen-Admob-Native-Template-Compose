package com.soosu.nextgen.admobnative

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.ImageView
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaContent
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import kotlin.math.roundToInt

/** Fallback ratio used when the ad does not report a usable media aspect ratio. */
private const val DEFAULT_MEDIA_ASPECT_RATIO = 16f / 9f

/** Opacity of the "AD" badge background, matching the previous `#26` alpha of the templates. */
private const val AD_BADGE_BACKGROUND_ALPHA = 38f / 255f

/**
 * The secondary line shown by most templates: body, falling back to the advertiser, the store and
 * finally the call to action.
 */
internal fun NativeAd.secondaryText(): String = when {
    !body.isNullOrEmpty() -> body!!
    !advertiser.isNullOrEmpty() -> advertiser!!
    !store.isNullOrEmpty() -> store!!
    !callToAction.isNullOrEmpty() -> callToAction!!
    else -> "ˑˑˑ"
}

/** The headline of the ad, falling back to the advertiser and the store. */
internal fun NativeAd.headlineText(): String = when {
    !headline.isNullOrEmpty() -> headline!!
    !advertiser.isNullOrEmpty() -> advertiser!!
    !store.isNullOrEmpty() -> store!!
    else -> ""
}

/**
 * Blends [this] towards [other] the same way `ColorUtils.blendARGB` does, so the templates keep the
 * muted secondary text tones they had while rendered as Views.
 */
internal fun Color.blendWith(other: Color, ratio: Float): Color = Color(
    red = red * (1f - ratio) + other.red * ratio,
    green = green * (1f - ratio) + other.green * ratio,
    blue = blue * (1f - ratio) + other.blue * ratio,
    alpha = alpha * (1f - ratio) + other.alpha * ratio,
)

/** The "AD" badge shown by the templates. */
@Composable
internal fun NativeAdBadge(
    textColor: Color,
    modifier: Modifier = Modifier,
    containerColor: Color = textColor.copy(alpha = AD_BADGE_BACKGROUND_ALPHA),
    text: String = "AD",
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(containerColor)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * The icon asset of the ad, registered with the enclosing [NativeAdView].
 *
 * Renders nothing when the ad has no icon, matching the `GONE` icon container of the old layouts.
 */
@Composable
internal fun NativeAdIconAsset(
    image: ImageBitmap?,
    size: Dp,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    if (image == null) return
    NativeAdIconView(modifier = modifier.size(size)) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(shape),
        )
    }
}

/** The media assets of an ad: a video/media view when available, otherwise a static image. */
@Immutable
internal class NativeAdMediaState(
    val mediaContent: MediaContent?,
    val fallbackImage: ImageBitmap?,
    val hasFallbackSource: Boolean,
) {
    /** Whether the ad will eventually render media, even if the fallback image is still loading. */
    val hasMedia: Boolean
        get() = mediaContent != null || hasFallbackSource

    /** Width / height ratio to size the media with. */
    val aspectRatio: Float
        get() {
            val reported = mediaContent?.aspectRatio ?: 0f
            if (reported > 0f) return reported
            val image = fallbackImage
            if (image != null && image.height > 0) {
                return image.width.toFloat() / image.height.toFloat()
            }
            return DEFAULT_MEDIA_ASPECT_RATIO
        }
}

/**
 * Resolves the media of [nativeAd]: the SDK media content when present, otherwise the primary image
 * asset, which may still be downloading.
 */
@Composable
internal fun rememberNativeAdMediaState(nativeAd: NativeAd): NativeAdMediaState {
    val mediaContent = remember(nativeAd) { nativeAd.mediaContentWithImageFallback() }
    val fallbackDrawable = remember(nativeAd, mediaContent) {
        if (mediaContent == null) nativeAd.primaryImageDrawable() else null
    }
    val fallbackUri = remember(nativeAd, mediaContent) {
        if (mediaContent == null) nativeAd.primaryImageUri() else null
    }
    val fallbackImage = rememberNativeAdImage(fallbackDrawable, fallbackUri)

    return remember(mediaContent, fallbackImage) {
        NativeAdMediaState(
            mediaContent = mediaContent,
            fallbackImage = fallbackImage,
            hasFallbackSource = fallbackDrawable != null || fallbackUri != null,
        )
    }
}

/**
 * Renders [state] either through the SDK media view (video capable) or as a plain image.
 *
 * Callers size the media themselves, typically with `Modifier.aspectRatio(state.aspectRatio)`.
 */
@Composable
internal fun NativeAdMediaContent(
    state: NativeAdMediaState,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    scaleType: ImageView.ScaleType? = null,
) {
    when {
        state.mediaContent != null -> NativeAdMediaView(modifier = modifier, scaleType = scaleType)
        state.fallbackImage != null -> Image(
            bitmap = state.fallbackImage,
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier,
        )
    }
}

/** A five star rating with half star steps, mirroring the small `RatingBar` of the old templates. */
@Composable
internal fun NativeAdStarRating(
    rating: Double,
    modifier: Modifier = Modifier,
    filledColor: Color = Color(0xFFFFB300),
    unfilledColor: Color = filledColor.copy(alpha = 0.3f),
    fontSize: TextUnit = 12.sp,
) {
    val steps = (rating * 2).roundToInt().coerceIn(0, 10) / 2f

    Row(modifier = modifier) {
        repeat(5) { index ->
            val fraction = (steps - index).coerceIn(0f, 1f)
            Box {
                Text(text = "★", color = unfilledColor, fontSize = fontSize)
                if (fraction > 0f) {
                    Text(
                        text = "★",
                        color = filledColor,
                        fontSize = fontSize,
                        modifier = Modifier.drawWithContent {
                            clipRect(right = size.width * fraction) { this@drawWithContent.drawContent() }
                        },
                    )
                }
            }
        }
    }
}
