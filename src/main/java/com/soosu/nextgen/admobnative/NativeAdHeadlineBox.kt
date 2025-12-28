package com.soosu.nextgen.admobnative

import android.annotation.SuppressLint
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
                            adView.adChoicesView = adChoice
                            adView.callToActionView = background
                            adView.headlineView = primary
                        }

                        background.setBackgroundColor(bgColor)
                        primary.setTextColor(txtColor)
                        cta.setTextColor(txtColor)
                        bar.setTextColor(txtColor)
                        arrow.setColorFilter(txtColor)

                        nativeAd.headline?.let { headline ->
                            primary.text = headline
                        }

                        nativeAd.callToAction?.let { callToAction ->
                            cta.text = callToAction
                        }

                        // Register after view is laid out
                        adView.post {
                            adView.registerNativeAd(nativeAd, null)
                        }
                    }
                }
            )
        }
    }
}
