package com.soosu.nextgen.admobnative

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd

/**
 * Content feed-style ad template that blends naturally with social media feeds.
 *
 * Features:
 * - Social media post-style layout
 * - Profile icon + advertiser name header
 * - "Sponsored" label for transparency
 * - Post-style headline with natural line spacing
 * - Full-width media with rounded corners
 * - Engagement-style CTA button
 *
 * Best used for:
 * - News feed placements
 * - Content discovery feeds
 * - Social media style apps
 * - Blog/article listings
 *
 * CTR Optimization:
 * - Native feel reduces ad blindness
 * - Familiar social post layout increases engagement
 * - Clear but non-intrusive sponsorship disclosure
 *
 * @param nativeAd The native ad to display. Nothing is rendered while it is `null`.
 * @param modifier Compose modifier
 * @param backgroundColor Background color
 * @param textColor Primary text color
 * @param ctaButtonColor CTA button background color
 * @param ctaTextColor CTA button text color
 */
@Composable
fun NativeAdContentBox(
    nativeAd: NativeAd?,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    ctaButtonColor: Color = Color(0xFF1976D2),
    ctaTextColor: Color = Color.White
) {
    Box(modifier = modifier) {
        if (nativeAd == null) return@Box

        val iconImage = rememberNativeAdImage(nativeAd.iconImageDrawable(), nativeAd.iconImageUri())
        val media = rememberNativeAdMediaState(nativeAd)
        val descriptionColor = textColor.blendWith(backgroundColor, 0.3f)
        val sponsoredColor = textColor.blendWith(backgroundColor, 0.4f)
        val description = nativeAd.body?.takeIf { nativeAd.headline != null && it != nativeAd.headline }

        NativeAdView(nativeAd = nativeAd, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .padding(vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NativeAdIconAsset(
                        image = iconImage,
                        size = 40.dp,
                        shape = RoundedCornerShape(20.dp),
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = if (iconImage != null) 10.dp else 0.dp)
                    ) {
                        NativeAdHeadlineView(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = nativeAd.headlineText(),
                                color = textColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Text(
                            text = "Sponsored",
                            color = sponsoredColor,
                            fontSize = 12.sp,
                        )
                    }
                }

                nativeAd.body?.let { body ->
                    Text(
                        text = body,
                        color = textColor,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 12.dp, end = 16.dp),
                    )
                }

                if (media.hasMedia) {
                    NativeAdMediaContent(
                        state = media,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 12.dp, end = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF0F0F0))
                            .let {
                                if (media.mediaContent != null) {
                                    it.aspectRatio(media.aspectRatio)
                                } else {
                                    it
                                }
                            },
                    )
                }

                if (description != null) {
                    NativeAdBodyView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 10.dp, end = 16.dp)
                    ) {
                        Text(
                            text = description,
                            color = descriptionColor,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                NativeAdCallToActionView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 12.dp, end = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ctaButtonColor)
                            .heightIn(min = 44.dp)
                            .padding(start = 16.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = nativeAd.callToAction.orEmpty(),
                            color = ctaTextColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
