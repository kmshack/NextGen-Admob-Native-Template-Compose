package com.soosu.nextgen.admobnative

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.PaintDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidViewBinding
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.soosu.nextgen.admobnative.databinding.GntAdFullwidthMediaTemplateViewBinding

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
 * @param nativeAd The native ad to display
 * @param modifier Compose modifier
 * @param ctaButtonColor CTA button background color
 * @param ctaTextColor CTA button text color
 */
@SuppressLint("SetTextI18n")
@Composable
fun NativeAdFullWidthMediaBox(
    nativeAd: NativeAd?,
    modifier: Modifier = Modifier,
    ctaButtonColor: Color = Color.White,
    ctaTextColor: Color = Color(0xFF1976D2)
) {

    Box(modifier = modifier) {

        if (nativeAd != null) {
            val ctaBgColor = ctaButtonColor.toArgb()
            val ctaTxtColor = ctaTextColor.toArgb()

            AndroidViewBinding(
                factory = GntAdFullwidthMediaTemplateViewBinding::inflate,
            ) {

                val adView = nativeAdView.also { adView ->
                    adView.iconView = icon
                    adView.bodyView = description
                }

                // Configure CTA button colors
                ctaContainer.backgroundTintList = ColorStateList.valueOf(ctaBgColor)
                ctaContainerFallback.backgroundTintList = ColorStateList.valueOf(ctaBgColor)
                cta.setTextColor(ctaTxtColor)
                ctaFallback.setTextColor(ctaTxtColor)

                // Set headline
                val headlineText = when {
                    !nativeAd.headline.isNullOrEmpty() -> nativeAd.headline
                    !nativeAd.advertiser.isNullOrEmpty() -> nativeAd.advertiser
                    !nativeAd.store.isNullOrEmpty() -> nativeAd.store
                    else -> ""
                }
                primary.text = headlineText
                primaryFallback.text = headlineText

                // Set AD badge with semi-transparent background for overlay style
                ad.setTextColor(ctaBgColor)
                ad.background = GradientDrawable().apply {
                    setColor(ctaTxtColor)
                    cornerRadius = 6f * ad.context.resources.displayMetrics.density
                }

                // Set advertiser or store
                secondary.text = when {
                    !nativeAd.body.isNullOrEmpty() -> nativeAd.body
                    !nativeAd.advertiser.isNullOrEmpty() -> nativeAd.advertiser
                    !nativeAd.store.isNullOrEmpty() -> nativeAd.store
                    !nativeAd.callToAction.isNullOrEmpty() -> nativeAd.callToAction
                    else -> "ˑˑˑ"
                }

                // Set call to action
                val callToActionText = nativeAd.callToAction ?: ""
                cta.text = callToActionText
                ctaFallback.text = callToActionText

                // Set body description (for fallback)
                nativeAd.body?.let { body ->
                    description.text = body
                    description.visibility = View.VISIBLE
                } ?: run {
                    description.text = ""
                    description.visibility = View.GONE
                }

                // Set icon (for fallback)
                nativeAd.iconImageDrawable()?.let { drawable ->
                    iconContainer.visibility = View.VISIBLE
                    icon.setImageDrawable(drawable)
                } ?: run {
                    iconContainer.visibility = View.GONE
                    icon.setImageDrawable(null)
                }

                // Set media content with gradient overlay
                val mediaContent = nativeAd.mediaContentWithImageFallback()
                if (mediaContent != null) {
                    adView.callToActionView = ctaContainer
                    adView.headlineView = primary
                    adMedia.mediaContent = mediaContent
                    adMedia.visibility = View.VISIBLE
                    adImageContainer.visibility = View.VISIBLE
                    fallbackContainer.visibility = View.GONE

                    // Apply gradient overlay
                    applyGradientOverlay(gradientOverlay)
                } else {
                    adView.callToActionView = ctaContainerFallback
                    adView.headlineView = primaryFallback
                    adMedia.mediaContent = null
                    adMedia.visibility = View.GONE
                    adImageContainer.visibility = View.GONE
                    fallbackContainer.visibility = View.VISIBLE
                }

                adView.registerNativeAd(nativeAd, if (mediaContent != null) adMedia else null)

            }

        }
    }

}

/**
 * Apply a bottom-to-top gradient overlay for text readability
 */
private fun applyGradientOverlay(view: View) {
    val shapeDrawable = PaintDrawable()
    shapeDrawable.shape = RectShape()
    shapeDrawable.shaderFactory = object : ShapeDrawable.ShaderFactory() {
        override fun resize(width: Int, height: Int): Shader {
            return LinearGradient(
                0f, height.toFloat(), 0f, height * 0.3f,
                intArrayOf(
                    android.graphics.Color.parseColor("#CC000000"),
                    android.graphics.Color.parseColor("#80000000"),
                    android.graphics.Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
    }
    view.background = shapeDrawable
}
