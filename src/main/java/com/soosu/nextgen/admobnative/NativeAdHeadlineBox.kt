package com.soosu.nextgen.admobnative

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd

/** Space reserved for the badge, the icon, the call to action and the chevron. */
private val HEADLINE_RESERVED_WIDTH = 180.dp

/**
 * Ultra compact single line template for headers, toolbars and other tight spaces.
 *
 * The whole row acts as the call to action, so a tap anywhere opens the ad.
 *
 * @param nativeAd The native ad to display. Nothing is rendered while it is `null`.
 * @param modifier Compose modifier
 * @param backgroundColor Background color
 * @param textColor Text and chevron color
 * @param showImage Whether to show the ad image between the badge and the headline. When `false`
 *   the image is neither rendered nor downloaded.
 */
@Composable
fun NativeAdHeadlineBox(
    nativeAd: NativeAd?,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    textColor: Color = MaterialTheme.colorScheme.onBackground,
    showImage: Boolean = true
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (nativeAd == null) return@Box

        val iconImage = rememberNativeAdImage(
            drawable = nativeAd.primaryImageDrawable().takeIf { showImage },
            uri = nativeAd.primaryImageUri().takeIf { showImage },
        )
        val screenWidth = LocalConfiguration.current.screenWidthDp.dp
        val maxHeadlineWidth = (screenWidth - HEADLINE_RESERVED_WIDTH).coerceAtLeast(screenWidth / 3)

        NativeAdView(nativeAd = nativeAd, modifier = Modifier.fillMaxWidth()) {
            // The ad only takes the width it needs, centered in whatever space it is given.
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                NativeAdCallToActionView {
                    Row(
                        modifier = Modifier
                            .padding(2.dp)
                            .background(backgroundColor)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NativeAdBadge(
                            textColor = Color(0xFF666666),
                            containerColor = Color(0xFFE0E0E0),
                        )

                        NativeAdIconAsset(
                            image = iconImage,
                            size = 16.dp,
                            shape = CircleShape,
                            modifier = Modifier.padding(start = 8.dp),
                        )

                        NativeAdHeadlineView(
                            modifier = Modifier
                                .padding(start = 8.dp, end = 4.dp)
                                .widthIn(max = maxHeadlineWidth)
                        ) {
                            Text(
                                text = nativeAd.headline.orEmpty(),
                                color = textColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Text(
                            text = "|",
                            color = textColor,
                            fontSize = 13.sp,
                            maxLines = 1,
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .alpha(0.3f),
                        )

                        Text(
                            text = nativeAd.callToAction.orEmpty(),
                            color = textColor,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Icon(
                            painter = painterResource(R.drawable.round_chevron_right_24),
                            contentDescription = null,
                            tint = textColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
