package com.soosu.nextgen.admobnative

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.soosu.nextgen.admobnative.databinding.GntAdSmallTemplateViewBinding

/**
 * A View-based native ad template for small ad placements.
 *
 * Usage in XML:
 * ```xml
 * <com.soosu.nextgen.admobnative.NativeAdSmallView
 *     android:id="@+id/nativeAdView"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content" />
 * ```
 *
 * Usage in Code:
 * ```kotlin
 * nativeAdView.setNativeAd(nativeAd)
 * // Optional: customize colors
 * nativeAdView.setBackgroundColor(Color.WHITE)
 * nativeAdView.setTextColor(Color.BLACK)
 * ```
 */
class NativeAdSmallView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: GntAdSmallTemplateViewBinding
    private var nativeAd: NativeAd? = null

    @ColorInt
    private var bgColor: Int = Color.parseColor("#F5F5F5")

    @ColorInt
    private var txtColor: Int = Color.parseColor("#222222")

    init {
        binding = GntAdSmallTemplateViewBinding.inflate(
            LayoutInflater.from(context),
            this,
            true
        )
    }

    /**
     * Set the native ad to display.
     * @param nativeAd The native ad to display, or null to clear.
     */
    fun setNativeAd(nativeAd: NativeAd?) {
        this.nativeAd = nativeAd
        if (nativeAd != null) {
            bindAd(nativeAd)
            visibility = View.VISIBLE
        } else {
            visibility = View.GONE
        }
    }

    /**
     * Set the background color of the ad container.
     * @param color The background color as an ARGB integer.
     */
    override fun setBackgroundColor(@ColorInt color: Int) {
        bgColor = color
        binding.background.setBackgroundColor(color)
        nativeAd?.let { bindAd(it) }
    }

    /**
     * Set the text color for all text elements.
     * @param color The text color as an ARGB integer.
     */
    fun setTextColor(@ColorInt color: Int) {
        txtColor = color
        nativeAd?.let { bindAd(it) }
    }

    private fun bindAd(nativeAd: NativeAd) {
        binding.apply {
            // Setup ad view
            nativeAdView.adChoicesView = adChoice
            nativeAdView.headlineView = primary
            nativeAdView.bodyView = secondary
            nativeAdView.iconView = icon
            nativeAdView.callToActionView = nativeAdView

            // Apply colors
            background.setBackgroundColor(bgColor)
            secondary.setTextColor(txtColor)
            primary.setTextColor(txtColor)

            // Set AD badge colors
            ad.setTextColor(txtColor)
            ad.background = GradientDrawable().apply {
                setColor(ColorUtils.setAlphaComponent(txtColor, 38))
                cornerRadius = 6f * context.resources.displayMetrics.density
            }

            // Set secondary text
            secondary.text = when {
                !nativeAd.body.isNullOrEmpty() -> nativeAd.body
                !nativeAd.advertiser.isNullOrEmpty() -> nativeAd.advertiser
                !nativeAd.store.isNullOrEmpty() -> nativeAd.store
                !nativeAd.callToAction.isNullOrEmpty() -> nativeAd.callToAction
                else -> "ˑˑˑ"
            }

            // Set headline
            nativeAd.headline?.let { headline ->
                primary.text = headline
            }

            // Set icon
            nativeAd.icon?.drawable?.let { drawable ->
                iconContainer.visibility = View.VISIBLE
                icon.visibility = View.VISIBLE
                icon.setImageDrawable(drawable)
            } ?: run {
                iconContainer.visibility = View.GONE
            }

            // Set media content for image display
            nativeAd.mediaContent?.let { mediaContent ->
                mediaContent.mainImage?.let { drawable ->
                    adImageContainer.visibility = View.VISIBLE
                    adImage.setImageDrawable(drawable)
                } ?: run {
                    adImageContainer.visibility = View.GONE
                }
            } ?: run {
                adImageContainer.visibility = View.GONE
            }

            // Register the ad
            nativeAdView.registerNativeAd(nativeAd, null)
        }
    }
}
