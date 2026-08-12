package com.soosu.nextgen.admobnative

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd

/** Height of the media area, matching the previous 280dp media container. */
private val FULL_WIDTH_MEDIA_HEIGHT = 280.dp

/** Bottom-to-top scrim keeping the overlaid text readable on top of the media. */
private val MEDIA_SCRIM = Brush.verticalGradient(
    0.3f to Color.Transparent,
    0.65f to Color(0x80000000),
    1.0f to Color(0xCC000000),
)

/**
 * Full-width media-centric ad template optimized for high visual impact and CTR.
 *
 * Features:
 * - Large media/image display (280dp height)
 * - Gradient overlay for text readability
 * - Overlay CTA button for immediate action
 * - Fallback layout when no media is available
 *
 * Best used for:
 * - Hero placements
 * - Full-screen interstitial-style native ads
 * - High-impact banner replacements
 *
 * @param nativeAd The native ad to display. Nothing is rendered while it is `null`.
 * @param modifier Compose modifier
 * @param ctaButtonColor CTA button background color
 * @param ctaTextColor CTA button text color
 */
@Composable
fun NativeAdFullWidthMediaBox(
    nativeAd: NativeAd?,
    modifier: Modifier = Modifier,
    ctaButtonColor: Color = Color.White,
    ctaTextColor: Color = Color(0xFF1976D2)
) {
    Box(modifier = modifier) {
        if (nativeAd == null) return@Box

        val media = rememberNativeAdMediaState(nativeAd)

        NativeAdView(nativeAd = nativeAd, modifier = Modifier.fillMaxWidth()) {
            if (media.hasMedia) {
                MediaLayout(
                    nativeAd = nativeAd,
                    media = media,
                    ctaButtonColor = ctaButtonColor,
                    ctaTextColor = ctaTextColor,
                )
            } else {
                FallbackLayout(
                    nativeAd = nativeAd,
                    ctaButtonColor = ctaButtonColor,
                    ctaTextColor = ctaTextColor,
                )
            }
        }
    }
}

@Composable
private fun MediaLayout(
    nativeAd: NativeAd,
    media: NativeAdMediaState,
    ctaButtonColor: Color,
    ctaTextColor: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(FULL_WIDTH_MEDIA_HEIGHT)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
    ) {
        NativeAdMediaContent(
            state = media,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MEDIA_SCRIM)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            NativeAdBadge(textColor = ctaButtonColor, containerColor = ctaTextColor)

            NativeAdHeadlineView(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            ) {
                Text(
                    text = nativeAd.headlineText(),
                    color = Color.White,
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    // Copy so the ambient style, font padding included, is kept.
                    style = LocalTextStyle.current.copy(
                        shadow = Shadow(
                            color = Color(0x80000000),
                            offset = Offset(0f, 1f),
                            blurRadius = 3f,
                        )
                    ),
                )
            }

            Text(
                text = nativeAd.secondaryText(),
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
                    .alpha(0.9f),
            )

            NativeAdCallToActionView(modifier = Modifier.padding(top = 8.dp)) {
                CallToActionButton(
                    text = nativeAd.callToAction.orEmpty(),
                    containerColor = ctaButtonColor,
                    contentColor = ctaTextColor,
                )
            }
        }
    }
}

@Composable
private fun FallbackLayout(
    nativeAd: NativeAd,
    ctaButtonColor: Color,
    ctaTextColor: Color,
) {
    val iconImage = rememberNativeAdImage(nativeAd.iconImageDrawable(), nativeAd.iconImageUri())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .heightIn(min = 200.dp)
            .padding(20.dp)
    ) {
        NativeAdIconAsset(
            image = iconImage,
            size = 72.dp,
            shape = RoundedCornerShape(16.dp),
        )

        NativeAdBadge(
            textColor = Color(0xFF555555),
            containerColor = Color(0xFFE0E0E0),
            modifier = Modifier.padding(top = 12.dp),
        )

        NativeAdHeadlineView(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text(
                text = nativeAd.headlineText(),
                color = Color(0xFF111111),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        nativeAd.body?.let { body ->
            NativeAdBodyView(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(
                    text = body,
                    color = Color(0xFF666666),
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        NativeAdCallToActionView(modifier = Modifier.padding(top = 16.dp)) {
            CallToActionButton(
                text = nativeAd.callToAction.orEmpty(),
                containerColor = ctaButtonColor,
                contentColor = ctaTextColor,
                showChevron = true,
            )
        }
    }
}

@Composable
private fun CallToActionButton(
    text: String,
    containerColor: Color,
    contentColor: Color,
    showChevron: Boolean = false,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(containerColor)
            .widthIn(min = 100.dp)
            .padding(start = 20.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (showChevron) {
            Icon(
                painter = painterResource(R.drawable.round_chevron_right_24),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(20.dp),
            )
        }
    }
}
