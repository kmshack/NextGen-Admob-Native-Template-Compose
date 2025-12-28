package com.soosu.nextgen.admobnative.sample

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import com.soosu.nextgen.admobnative.NativeAdContentBox
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

    private var smallAd by mutableStateOf<NativeAd?>(null)
    private var mediumAd by mutableStateOf<NativeAd?>(null)
    private var largeAd by mutableStateOf<NativeAd?>(null)
    private var contentAd by mutableStateOf<NativeAd?>(null)

    private var loadedCount = 0
    private var errorCount = 0

    private lateinit var statusText: TextView
    private lateinit var smallAdProgress: ProgressBar
    private lateinit var mediumAdProgress: ProgressBar
    private lateinit var largeAdProgress: ProgressBar
    private lateinit var contentAdProgress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_android_view_sample)

        // Initialize views
        statusText = findViewById(R.id.statusText)
        smallAdProgress = findViewById(R.id.smallAdProgress)
        mediumAdProgress = findViewById(R.id.mediumAdProgress)
        largeAdProgress = findViewById(R.id.largeAdProgress)
        contentAdProgress = findViewById(R.id.contentAdProgress)

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
        // Small Ad ComposeView
        findViewById<ComposeView>(R.id.smallAdComposeView).apply {
            setViewTreeLifecycleOwner(this@AndroidViewSampleActivity)
            setViewTreeSavedStateRegistryOwner(this@AndroidViewSampleActivity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AdMobNativeSampleTheme {
                    SmallAdContent()
                }
            }
        }

        // Medium Ad ComposeView
        findViewById<ComposeView>(R.id.mediumAdComposeView).apply {
            setViewTreeLifecycleOwner(this@AndroidViewSampleActivity)
            setViewTreeSavedStateRegistryOwner(this@AndroidViewSampleActivity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AdMobNativeSampleTheme {
                    MediumAdContent()
                }
            }
        }

        // Large Ad ComposeView
        findViewById<ComposeView>(R.id.largeAdComposeView).apply {
            setViewTreeLifecycleOwner(this@AndroidViewSampleActivity)
            setViewTreeSavedStateRegistryOwner(this@AndroidViewSampleActivity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AdMobNativeSampleTheme {
                    LargeAdContent()
                }
            }
        }

        // Content Ad ComposeView
        findViewById<ComposeView>(R.id.contentAdComposeView).apply {
            setViewTreeLifecycleOwner(this@AndroidViewSampleActivity)
            setViewTreeSavedStateRegistryOwner(this@AndroidViewSampleActivity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AdMobNativeSampleTheme {
                    ContentAdContent()
                }
            }
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

    private fun loadAds() {
        val testAdUnitId = "ca-app-pub-3940256099942544/2247696110"

        // Load Small Ad
        loadNativeAd(testAdUnitId) { ad, error ->
            if (ad != null) {
                smallAd = ad
                smallAdProgress.visibility = View.GONE
                onAdLoaded()
            } else {
                smallAdProgress.visibility = View.GONE
                onAdError(error)
            }
        }

        // Load Medium Ad
        loadNativeAd(testAdUnitId) { ad, error ->
            if (ad != null) {
                mediumAd = ad
                mediumAdProgress.visibility = View.GONE
                onAdLoaded()
            } else {
                mediumAdProgress.visibility = View.GONE
                onAdError(error)
            }
        }

        // Load Large Ad
        loadNativeAd(testAdUnitId) { ad, error ->
            if (ad != null) {
                largeAd = ad
                largeAdProgress.visibility = View.GONE
                onAdLoaded()
            } else {
                largeAdProgress.visibility = View.GONE
                onAdError(error)
            }
        }

        // Load Content Ad
        loadNativeAd(testAdUnitId) { ad, error ->
            if (ad != null) {
                contentAd = ad
                contentAdProgress.visibility = View.GONE
                onAdLoaded()
            } else {
                contentAdProgress.visibility = View.GONE
                onAdError(error)
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
                callback(ad, null)
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                callback(null, error.message)
            }
        })
    }

    private fun onAdLoaded() {
        loadedCount++
        updateStatus()
    }

    private fun onAdError(error: String?) {
        errorCount++
        updateStatus()
    }

    private fun updateStatus() {
        val total = 4
        val completed = loadedCount + errorCount
        statusText.text = buildString {
            append("Loaded: $loadedCount / $total")
            if (errorCount > 0) {
                append(" (Errors: $errorCount)")
            }
            if (completed == total) {
                append("\n\nAll ads processed. Compose components are working correctly inside AndroidView!")
            }
        }
    }
}
