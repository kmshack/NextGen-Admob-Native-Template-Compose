package com.soosu.nextgen.admobnative

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
 * App install ad template optimized for app promotion and downloads.
 *
 * Features:
 * - App Store style layout
 * - Large app icon with rounded corners
 * - Star rating display
 * - Price/Free indicator
 * - Prominent "Install" button
 * - Optional screenshot/media preview
 *
 * Best used for:
 * - App promotion campaigns
 * - Game advertisements
 * - App store style placements
 * - Mobile app discovery feeds
 *
 * CTR Optimization:
 * - Familiar app store layout increases trust
 * - Clear rating display builds credibility
 * - Prominent install button drives action
 * - Price transparency reduces friction
 *
 * @param nativeAd The native ad to display. Nothing is rendered while it is `null`.
 * @param modifier Compose modifier
 * @param backgroundColor Card background color
 * @param textColor Primary text color
 * @param ctaButtonColor Install button background color
 * @param ctaTextColor Install button text color
 */
@Composable
fun NativeAdAppInstallBox(
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
        val secondaryColor = textColor.blendWith(backgroundColor, 0.4f)
        val descriptionColor = textColor.blendWith(backgroundColor, 0.3f)
        val starRating = nativeAd.starRating

        NativeAdView(nativeAd = nativeAd, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
            ) {
                if (media.hasMedia) {
                    NativeAdMediaContent(
                        state = media,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .let {
                                if (media.mediaContent != null) {
                                    it.aspectRatio(media.aspectRatio)
                                } else {
                                    it
                                }
                            },
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NativeAdIconAsset(
                        image = iconImage,
                        size = 72.dp,
                        shape = RoundedCornerShape(20.dp),
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = if (iconImage != null) 14.dp else 0.dp)
                    ) {
                        NativeAdHeadlineView(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = nativeAd.headline.orEmpty(),
                                color = textColor,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NativeAdBadge(textColor = textColor)

                            Text(
                                text = nativeAd.secondaryText(),
                                color = secondaryColor,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 6.dp),
                            )
                        }

                        Row(
                            modifier = Modifier.padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (starRating != null) {
                                NativeAdStarRatingView {
                                    NativeAdStarRating(rating = starRating)
                                }

                                Text(
                                    text = String.format("%.1f", starRating),
                                    color = secondaryColor,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 4.dp),
                                )

                                Text(
                                    text = "•",
                                    color = Color(0xFF999999),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                            }

                            NativeAdPriceView {
                                Text(
                                    text = nativeAd.price ?: "Free",
                                    color = Color(0xFF2E7D32),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    NativeAdCallToActionView(modifier = Modifier.padding(start = 12.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(ctaButtonColor)
                                .widthIn(min = 80.dp)
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = nativeAd.callToAction ?: "Install",
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

                nativeAd.body?.let { body ->
                    NativeAdBodyView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    ) {
                        Text(
                            text = body,
                            color = descriptionColor,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
