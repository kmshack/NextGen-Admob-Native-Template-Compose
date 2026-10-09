package com.soosu.nextgen.admobnative

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd

/**
 * Icon focused compact template, ideal for content feeds and list rows.
 *
 * The whole row acts as the call to action, so a tap anywhere opens the ad.
 *
 * @param nativeAd The native ad to display. Nothing is rendered while it is `null`.
 * @param modifier Compose modifier
 * @param backgroundColor Background color
 * @param textColor Primary text color
 */
@Composable
fun NativeAdIconSmallBox(
    nativeAd: NativeAd?,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    textColor: Color = MaterialTheme.colorScheme.onBackground
) {
    Box(modifier = modifier) {
        if (nativeAd == null) return@Box

        val iconImage = rememberNativeAdImage(nativeAd.iconImageDrawable(), nativeAd.iconImageUri())

        NativeAdView(nativeAd = nativeAd, modifier = Modifier.fillMaxWidth()) {
            NativeAdCallToActionView(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(backgroundColor)
                        .heightIn(min = 80.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NativeAdIconAsset(
                        image = iconImage,
                        size = 48.dp,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(start = 15.dp),
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 15.dp, end = 15.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NativeAdBadge(
                                textColor = textColor,
                                modifier = Modifier.padding(end = 3.dp),
                            )

                            NativeAdBodyView(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = nativeAd.secondaryText(),
                                    color = textColor,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.alpha(0.8f),
                                )
                            }
                        }

                        NativeAdHeadlineView(
                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = nativeAd.headline.orEmpty(),
                                color = textColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
