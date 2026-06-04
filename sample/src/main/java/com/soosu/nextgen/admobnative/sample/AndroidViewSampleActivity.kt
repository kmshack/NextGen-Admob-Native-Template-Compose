package com.soosu.nextgen.admobnative.sample

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.soosu.nextgen.admobnative.NativeAdAppInstallBox
import com.soosu.nextgen.admobnative.NativeAdContentBox
import com.soosu.nextgen.admobnative.NativeAdFullWidthMediaBox
import com.soosu.nextgen.admobnative.NativeAdHeadlineBox
import com.soosu.nextgen.admobnative.NativeAdIconSmallBox
import com.soosu.nextgen.admobnative.NativeAdLargeBox
import com.soosu.nextgen.admobnative.NativeAdMediumBox
import com.soosu.nextgen.admobnative.NativeAdSmallBox

/**
 * Sample Activity demonstrating the use of Compose NativeAd components
 * inside traditional Android Views using ComposeView.
 *
 * This tests the scenario:
 * View (XML) -> ComposeView -> NativeAd Compose Component -> AndroidViewBinding -> NativeAdView
 */
class AndroidViewSampleActivity : ComponentActivity() {

    // CTR Optimized Templates
    private var fullWidthMediaAd by mutableStateOf<NativeAd?>(null)
    private var contentAd by mutableStateOf<NativeAd?>(null)
    private var appInstallAd by mutableStateOf<NativeAd?>(null)

    // Standard Templates
    private var headlineAd by mutableStateOf<NativeAd?>(null)
    private var smallAd by mutableStateOf<NativeAd?>(null)
    private var iconSmallAd by mutableStateOf<NativeAd?>(null)
    private var mediumAd by mutableStateOf<NativeAd?>(null)
    private var largeAd by mutableStateOf<NativeAd?>(null)

    private val nativeAds = mutableListOf<NativeAd>()
    private var loadedCount = 0
    private var errorCount = 0
    private val totalAds = 8
    private var isDestroyed = false

    private lateinit var statusText: TextView

    // Progress bars
    private lateinit var fullWidthMediaAdProgress: ProgressBar
    private lateinit var contentAdProgress: ProgressBar
    private lateinit var appInstallAdProgress: ProgressBar
    private lateinit var headlineAdProgress: ProgressBar
    private lateinit var smallAdProgress: ProgressBar
    private lateinit var iconSmallAdProgress: ProgressBar
    private lateinit var mediumAdProgress: ProgressBar
    private lateinit var largeAdProgress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_android_view_sample)

        // Initialize views
        statusText = findViewById(R.id.statusText)

        // Initialize progress bars
        fullWidthMediaAdProgress = findViewById(R.id.fullWidthMediaAdProgress)
        contentAdProgress = findViewById(R.id.contentAdProgress)
        appInstallAdProgress = findViewById(R.id.appInstallAdProgress)
        headlineAdProgress = findViewById(R.id.headlineAdProgress)
        smallAdProgress = findViewById(R.id.smallAdProgress)
        iconSmallAdProgress = findViewById(R.id.iconSmallAdProgress)
        mediumAdProgress = findViewById(R.id.mediumAdProgress)
        largeAdProgress = findViewById(R.id.largeAdProgress)

        // Back button
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            finish()
        }

        // Setup ComposeViews
        setupComposeViews()

        // Load ads
        loadAds()
    }

    private fun setupComposeViews() {
        // Full Width Media Ad ComposeView (CTR+)
        setupComposeView(R.id.fullWidthMediaAdComposeView) {
            FullWidthMediaAdContent()
        }

        // Content Ad ComposeView (CTR+)
        setupComposeView(R.id.contentAdComposeView) {
            ContentAdContent()
        }

        // App Install Ad ComposeView (CTR+)
        setupComposeView(R.id.appInstallAdComposeView) {
            AppInstallAdContent()
        }

        // Headline Ad ComposeView
        setupComposeView(R.id.headlineAdComposeView) {
            HeadlineAdContent()
        }

        // Small Ad ComposeView
        setupComposeView(R.id.smallAdComposeView) {
            SmallAdContent()
        }

        // Icon Small Ad ComposeView
        setupComposeView(R.id.iconSmallAdComposeView) {
            IconSmallAdContent()
        }

        // Medium Ad ComposeView
        setupComposeView(R.id.mediumAdComposeView) {
            MediumAdContent()
        }

        // Large Ad ComposeView
        setupComposeView(R.id.largeAdComposeView) {
            LargeAdContent()
        }
    }

    private fun setupComposeView(viewId: Int, content: @Composable () -> Unit) {
        findViewById<ComposeView>(viewId).apply {
            setViewTreeLifecycleOwner(this@AndroidViewSampleActivity)
            setViewTreeSavedStateRegistryOwner(this@AndroidViewSampleActivity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AdMobNativeSampleTheme {
                    content()
                }
            }
        }
    }

    // CTR Optimized Templates
    @Composable
    private fun FullWidthMediaAdContent() {
        fullWidthMediaAd?.let { ad ->
            NativeAdFullWidthMediaBox(
                nativeAd = ad,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            )
        }
    }

    @Composable
    private fun ContentAdContent() {
        contentAd?.let { ad ->
            NativeAdContentBox(
                nativeAd = ad,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            )
        }
    }

    @Composable
    private fun AppInstallAdContent() {
        appInstallAd?.let { ad ->
            NativeAdAppInstallBox(
                nativeAd = ad,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            )
        }
    }

    // Standard Templates
    @Composable
    private fun HeadlineAdContent() {
        headlineAd?.let { ad ->
            NativeAdHeadlineBox(
                nativeAd = ad,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    @Composable
    private fun SmallAdContent() {
        smallAd?.let { ad ->
            NativeAdSmallBox(
                nativeAd = ad,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            )
        }
    }

    @Composable
    private fun IconSmallAdContent() {
        iconSmallAd?.let { ad ->
            NativeAdIconSmallBox(
                nativeAd = ad,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            )
        }
    }

    @Composable
    private fun MediumAdContent() {
        mediumAd?.let { ad ->
            NativeAdMediumBox(
                nativeAd = ad,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
            )
        }
    }

    @Composable
    private fun LargeAdContent() {
        largeAd?.let { ad ->
            NativeAdLargeBox(
                nativeAd = ad,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            )
        }
    }

    private fun loadAds() {
        val testAdUnitId = "ca-app-pub-3940256099942544/2247696110"

        // Load Full Width Media Ad (CTR+)
        loadNativeAd(testAdUnitId) { ad, _ ->
            if (ad != null) {
                fullWidthMediaAd = ad
                fullWidthMediaAdProgress.visibility = View.GONE
                onAdLoaded()
            } else {
                fullWidthMediaAdProgress.visibility = View.GONE
                onAdError()
            }
        }

        // Load Content Ad (CTR+)
        loadNativeAd(testAdUnitId) { ad, _ ->
            if (ad != null) {
                contentAd = ad
                contentAdProgress.visibility = View.GONE
                onAdLoaded()
            } else {
                contentAdProgress.visibility = View.GONE
                onAdError()
            }
        }

        // Load App Install Ad (CTR+)
        loadNativeAd(testAdUnitId) { ad, _ ->
            if (ad != null) {
                appInstallAd = ad
                appInstallAdProgress.visibility = View.GONE
                onAdLoaded()
            } else {
                appInstallAdProgress.visibility = View.GONE
                onAdError()
            }
        }

        // Load Headline Ad
        loadNativeAd(testAdUnitId) { ad, _ ->
            if (ad != null) {
                headlineAd = ad
                headlineAdProgress.visibility = View.GONE
                onAdLoaded()
            } else {
                headlineAdProgress.visibility = View.GONE
                onAdError()
            }
        }

        // Load Small Ad
        loadNativeAd(testAdUnitId) { ad, _ ->
            if (ad != null) {
                smallAd = ad
                smallAdProgress.visibility = View.GONE
                onAdLoaded()
            } else {
                smallAdProgress.visibility = View.GONE
                onAdError()
            }
        }

        // Load Icon Small Ad
        loadNativeAd(testAdUnitId) { ad, _ ->
            if (ad != null) {
                iconSmallAd = ad
                iconSmallAdProgress.visibility = View.GONE
                onAdLoaded()
            } else {
                iconSmallAdProgress.visibility = View.GONE
                onAdError()
            }
        }

        // Load Medium Ad
        loadNativeAd(testAdUnitId) { ad, _ ->
            if (ad != null) {
                mediumAd = ad
                mediumAdProgress.visibility = View.GONE
                onAdLoaded()
            } else {
                mediumAdProgress.visibility = View.GONE
                onAdError()
            }
        }

        // Load Large Ad
        loadNativeAd(testAdUnitId) { ad, _ ->
            if (ad != null) {
                largeAd = ad
                largeAdProgress.visibility = View.GONE
                onAdLoaded()
            } else {
                largeAdProgress.visibility = View.GONE
                onAdError()
            }
        }
    }

    private fun loadNativeAd(adUnitId: String, callback: (NativeAd?, String?) -> Unit) {
        val adRequest = NativeAdRequest.Builder(
            adUnitId,
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
                    callback(ad, null)
                }
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                runOnUiThread {
                    if (isDestroyed) {
                        return@runOnUiThread
                    }
                    callback(null, error.message)
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
                append("\n\nAll ads processed. Compose components are working correctly inside AndroidView!")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isDestroyed = true
        fullWidthMediaAd = null
        contentAd = null
        appInstallAd = null
        headlineAd = null
        smallAd = null
        iconSmallAd = null
        mediumAd = null
        largeAd = null
        nativeAds.forEach { it.destroy() }
        nativeAds.clear()
    }
}
