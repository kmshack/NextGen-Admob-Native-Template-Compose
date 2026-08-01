package com.soosu.nextgen.admobnative

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd

/**
 * Premium template with large media, icon, body and a full width call to action button.
 *
 * The whole card acts as the call to action, so a tap anywhere opens the ad.
 *
 * @param nativeAd The native ad to display. Nothing is rendered while it is `null`.
 * @param modifier Compose modifier
 * @param backgroundColor Card background color
 * @param textColor Primary text color
 * @param ctaButtonColor Call to action button background color
 * @param ctaTextColor Call to action button text color
 */
@Composable
fun NativeAdLargeBox(
    nativeAd: NativeAd?,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    textColor: Color = MaterialTheme.colorScheme.onBackground,
    ctaButtonColor: Color = Color(0xFF1976D2),
    ctaTextColor: Color = Color.White
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
                        .heightIn(min = 120.dp)
                ) {
                    if (media.hasMedia) {
                        NativeAdMediaContent(
                            state = media,
                            scaleType = ImageView.ScaleType.FIT_CENTER,
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
                            .padding(start = 16.dp, top = 16.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NativeAdIconAsset(
                            image = iconImage,
                            size = 50.dp,
                            shape = CircleShape,
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = if (iconImage != null) 12.dp else 0.dp)
                        ) {
                            NativeAdHeadlineView(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = nativeAd.headline.orEmpty(),
                                    color = textColor,
                                    fontSize = 17.sp,
                                    lineHeight = 22.sp,
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
                                NativeAdBadge(
                                    textColor = textColor,
                                    modifier = Modifier.alpha(0.8f),
                                )

                                Text(
                                    text = nativeAd.secondaryText(),
                                    color = textColor,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .alpha(0.8f),
                                )
                            }
                        }
                    }

                    nativeAd.body?.let { body ->
                        NativeAdBodyView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 18.dp, end = 16.dp)
                        ) {
                            Text(
                                text = body,
                                color = textColor,
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 20.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ctaButtonColor)
                            .heightIn(min = 52.dp)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = nativeAd.callToAction.orEmpty(),
                            color = ctaTextColor,
                            fontSize = 17.sp,
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
