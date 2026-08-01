package com.soosu.nextgen.admobnative

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd

/**
 * Full featured card template: headline, advertiser line, inline call to action, body and media.
 *
 * The whole card acts as the call to action, so a tap anywhere opens the ad.
 *
 * @param nativeAd The native ad to display. Nothing is rendered while it is `null`.
 * @param modifier Compose modifier
 * @param backgroundColor Card background color
 * @param textColor Text and chevron color
 */
@Composable
fun NativeAdMediumBox(
    nativeAd: NativeAd?,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    textColor: Color = MaterialTheme.colorScheme.onBackground
) {
    Box(modifier = modifier) {
        if (nativeAd == null) return@Box

        val iconImage = rememberNativeAdImage(nativeAd.iconImageDrawable(), nativeAd.iconImageUri())
        val media = rememberNativeAdMediaState(nativeAd)

        NativeAdView(nativeAd = nativeAd, modifier = Modifier.fillMaxWidth()) {
            NativeAdCallToActionView(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(backgroundColor)
                        .heightIn(min = 80.dp)
                ) {
                    NativeAdHeadlineView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 20.dp, end = 16.dp)
                    ) {
                        Text(
                            text = nativeAd.headline.orEmpty(),
                            color = textColor,
                            fontSize = 15.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 2.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NativeAdIconAsset(
                            image = iconImage,
                            size = 16.dp,
                            shape = RoundedCornerShape(8.dp),
                        )

                        NativeAdBadge(
                            textColor = textColor,
                            modifier = Modifier
                                .padding(start = 4.dp, end = 4.dp)
                                .alpha(0.8f),
                        )

                        Text(
                            text = nativeAd.secondaryText(),
                            color = textColor,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .alpha(0.8f),
                        )
                    }

                    Row(
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = nativeAd.callToAction.orEmpty(),
                            color = textColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Icon(
                            painter = painterResource(R.drawable.round_chevron_right_24),
                            contentDescription = null,
                            tint = textColor,
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    nativeAd.body?.let { body ->
                        NativeAdBodyView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 8.dp, end = 16.dp)
                        ) {
                            Text(
                                text = body,
                                color = textColor,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    if (media.hasMedia) {
                        NativeAdMediaContent(
                            state = media,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 20.dp)
                                .let {
                                    if (media.mediaContent != null) {
                                        it.aspectRatio(media.aspectRatio)
                                    } else {
                                        it
                                    }
                                },
                        )
                    }
                }
            }
        }
    }
}
