package com.soosu.nextgen.admobnative

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd

/**
 * Compact horizontal template: headline, icon + advertiser line and a small thumbnail.
 *
 * The whole row acts as the call to action, so a tap anywhere opens the ad.
 *
 * @param nativeAd The native ad to display. Nothing is rendered while it is `null`.
 * @param modifier Compose modifier
 * @param backgroundColor Background color
 * @param textColor Primary text color
 */
@Composable
fun NativeAdSmallBox(
    nativeAd: NativeAd?,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    textColor: Color = MaterialTheme.colorScheme.onBackground
) {
    Box(modifier = modifier) {
        if (nativeAd == null) return@Box

        val iconImage = rememberNativeAdImage(nativeAd.iconImageDrawable(), nativeAd.iconImageUri())
        val thumbnail =
            rememberNativeAdImage(nativeAd.primaryImageDrawable(), nativeAd.primaryImageUri())

        NativeAdView(nativeAd = nativeAd, modifier = Modifier.fillMaxWidth()) {
            NativeAdCallToActionView(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(backgroundColor),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 12.dp)
                    ) {
                        NativeAdHeadlineView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 18.dp, end = 4.dp)
                        ) {
                            Text(
                                text = nativeAd.headline.orEmpty(),
                                color = textColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 18.dp, top = 4.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NativeAdIconAsset(
                                image = iconImage,
                                size = 14.dp,
                                shape = RoundedCornerShape(7.dp),
                            )

                            NativeAdBadge(
                                textColor = textColor,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .alpha(0.8f),
                            )

                            NativeAdBodyView(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 4.dp)
                            ) {
                                Text(
                                    text = nativeAd.secondaryText(),
                                    color = textColor,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.alpha(0.8f),
                                )
                            }
                        }
                    }

                    if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .padding(top = 8.dp, end = 16.dp, bottom = 8.dp)
                                .height(64.dp)
                                .widthIn(max = 120.dp)
                                .clip(RoundedCornerShape(6.dp)),
                        )
                    }
                }
            }
        }
    }
}
