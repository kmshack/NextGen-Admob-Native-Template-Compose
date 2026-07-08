package com.soosu.nextgen.admobnative

import android.annotation.SuppressLint
import android.util.TypedValue
import android.view.LayoutInflater
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.soosu.nextgen.admobnative.databinding.GntAdHeadlineTemplateViewBinding

@SuppressLint("SetTextI18n")
@Composable
fun NativeAdHeadlineBox(
    nativeAd: NativeAd?,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    textColor: Color = MaterialTheme.colorScheme.onBackground
) {
    val bgColor = backgroundColor.toArgb()
    val txtColor = textColor.toArgb()

    Box(modifier = modifier, contentAlignment = Alignment.Center) {

        if (nativeAd != null) {
            AndroidView(
                factory = { context ->
                    val binding = GntAdHeadlineTemplateViewBinding.inflate(
                        LayoutInflater.from(context)
                    )
                    binding.root
                },
                update = { view ->
                    val binding = GntAdHeadlineTemplateViewBinding.bind(view)

                    binding.apply {
                        val adView = nativeAdView.also { adView ->
                            adView.callToActionView = background
                            adView.headlineView = primary
                            adView.iconView = icon
                        }

                        background.setBackgroundColor(bgColor)
                        primary.setTextColor(txtColor)
                        cta.setTextColor(txtColor)
                        bar.setTextColor(txtColor)
                        arrow.setColorFilter(txtColor)

                        icon.setNativeAdImage(
                            drawable = nativeAd.primaryImageDrawable(),
                            uri = nativeAd.primaryImageUri(),
                            container = iconContainer
                        )

                        // 화면 너비를 기반으로 primary의 maxWidth를 동적으로 설정
                        view.post {
                            val displayMetrics = view.context.resources.displayMetrics
                            val screenWidth = displayMetrics.widthPixels

                            // AD 배지, bar, 화살표, 여백 등을 위한 예상 공간 (약 150dp)
                            val reservedSpace = TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                180f,
                                displayMetrics
                            ).toInt()

                            val maxWidthForPrimary = screenWidth - reservedSpace
                            primary.maxWidth = if (maxWidthForPrimary > 0) maxWidthForPrimary else screenWidth
                        }

                        nativeAd.headline?.let { headline ->
                            primary.text = headline
                        }

                        nativeAd.callToAction?.let { callToAction ->
                            cta.text = callToAction
                        }


                        adView.registerNativeAd(nativeAd, null)

                    }
                }
            )
        }
    }
}
