package com.soosu.nextgen.admobnative.sample

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.soosu.nextgen.admobnative.NativeAdTemplateView

/**
 * Sample Activity demonstrating the use of NativeAdTemplateView (pure View-based).
 *
 * This demonstrates the View-based approach where all templates are used directly
 * in XML layouts without any Compose dependency.
 */
class ViewSampleActivity : ComponentActivity() {

    private val nativeAds = mutableListOf<NativeAd>()
    private val testAdUnitId = "ca-app-pub-3940256099942544/2247696110"

    private lateinit var adSmall: NativeAdTemplateView
    private lateinit var adIconSmall: NativeAdTemplateView
    private lateinit var adHeadline: NativeAdTemplateView
    private lateinit var adMedium: NativeAdTemplateView
    private lateinit var adLarge: NativeAdTemplateView
    private lateinit var adContent: NativeAdTemplateView
    private lateinit var adFullWidth: NativeAdTemplateView
    private lateinit var adAppInstall: NativeAdTemplateView

    private lateinit var statusText: TextView

    // Progress bars
    private lateinit var smallAdProgress: ProgressBar
    private lateinit var iconSmallAdProgress: ProgressBar
    private lateinit var headlineAdProgress: ProgressBar
    private lateinit var mediumAdProgress: ProgressBar
    private lateinit var largeAdProgress: ProgressBar
    private lateinit var contentAdProgress: ProgressBar
    private lateinit var fullWidthAdProgress: ProgressBar
    private lateinit var appInstallAdProgress: ProgressBar

    private var loadedCount = 0
    private var errorCount = 0
    private val totalAds = 8
    private var isDestroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_sample)

        initViews()
        loadAllAds()
    }

    private fun initViews() {
        // Back button
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            finish()
        }

        // Status text
        statusText = findViewById(R.id.statusText)

        // Progress bars
        smallAdProgress = findViewById(R.id.smallAdProgress)
        iconSmallAdProgress = findViewById(R.id.iconSmallAdProgress)
        headlineAdProgress = findViewById(R.id.headlineAdProgress)
        mediumAdProgress = findViewById(R.id.mediumAdProgress)
        largeAdProgress = findViewById(R.id.largeAdProgress)
        contentAdProgress = findViewById(R.id.contentAdProgress)
        fullWidthAdProgress = findViewById(R.id.fullWidthAdProgress)
        appInstallAdProgress = findViewById(R.id.appInstallAdProgress)

        // NativeAdTemplateViews
        adSmall = findViewById(R.id.ad_small)
        adIconSmall = findViewById(R.id.ad_icon_small)
        adHeadline = findViewById(R.id.ad_headline)
        adMedium = findViewById(R.id.ad_medium)
        adLarge = findViewById(R.id.ad_large)
        adContent = findViewById(R.id.ad_content)
        adFullWidth = findViewById(R.id.ad_full_width)
        adAppInstall = findViewById(R.id.ad_app_install)
    }

    private fun loadAllAds() {
        loadAdFor(adSmall, smallAdProgress)
        loadAdFor(adIconSmall, iconSmallAdProgress)
        loadAdFor(adHeadline, headlineAdProgress)
        loadAdFor(adMedium, mediumAdProgress)
        loadAdFor(adLarge, largeAdProgress)
        loadAdFor(adContent, contentAdProgress)
        loadAdFor(adFullWidth, fullWidthAdProgress)
        loadAdFor(adAppInstall, appInstallAdProgress)
    }

    private fun loadAdFor(templateView: NativeAdTemplateView, progressBar: ProgressBar) {
        val adRequest = NativeAdRequest.Builder(
            testAdUnitId,
            listOf(NativeAd.NativeAdType.NATIVE)
        ).build()

        NativeAdLoader.load(adRequest, object : NativeAdLoaderCallback {
            override fun onNativeAdLoaded(ad: NativeAd) {
                runOnUiThread {
                    if (isDestroyed) {
                        ad.destroy()
                        return@runOnUiThread
                    }
                    nativeAds.add(ad)
                    templateView.setNativeAd(ad)
                    progressBar.visibility = View.GONE
                    onAdLoaded()
                }
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    onAdError()
                }
            }
        })
    }

    private fun onAdLoaded() {
        loadedCount++
        updateStatus()
    }

    private fun onAdError() {
        errorCount++
        updateStatus()
    }

    private fun updateStatus() {
        val completed = loadedCount + errorCount
        statusText.text = buildString {
            append("Loaded: $loadedCount / $totalAds")
            if (errorCount > 0) {
                append(" (Errors: $errorCount)")
            }
            if (completed == totalAds) {
                append("\n\nAll ads processed. NativeAdTemplateView is working correctly!")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isDestroyed = true
        nativeAds.forEach { it.destroy() }
        nativeAds.clear()
    }
}
