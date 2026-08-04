package com.soosu.nextgen.admobnative

import android.app.Activity
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

enum class SplashAdResult {
    AD_SHOWN,
    AD_NOT_AVAILABLE,
    SKIPPED,
}

object SplashAdLoader {

    private const val TAG = "SplashAdLoader"

    suspend fun execute(activity: Activity, config: AdmobConfig): SplashAdResult {
        val adUnitId = config.splashAdUnitId
        if (adUnitId == null) {
            Log.d(TAG, "Splash ad unit ID not configured, skipping")
            return SplashAdResult.SKIPPED
        }

        if (config.shouldSuppressAds()) {
            Log.d(TAG, "Ads suppressed, skipping")
            return SplashAdResult.SKIPPED
        }

        // 1. Initialize SDK (동의 수집은 앱 측에서 별도 처리)
        AdmobInitializer.initialize(activity, config.admobAppId, config)

        // 2. Load ad with timeout
        val ad = withTimeoutOrNull(config.splashAdLoadTimeoutMs) {
            loadAd(adUnitId, config.keywords)
        }

        if (ad == null) {
            Log.d(TAG, "Ad load timed out or failed")
            return SplashAdResult.AD_NOT_AVAILABLE
        }

        // 3. Show ad and wait for dismiss
        return showAd(activity, ad)
    }

    private suspend fun loadAd(
        adUnitId: String,
        keywords: List<String> = emptyList(),
    ): AppOpenAd? = suspendCancellableCoroutine { cont ->
        val request = AdRequest.Builder(adUnitId).apply {
            keywords.forEach { addKeyword(it) }
        }.build()
        AppOpenAd.load(
            request,
            object : AdLoadCallback<AppOpenAd> {
                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.d(TAG, "Splash ad loaded")
                    if (cont.isActive) {
                        cont.resume(ad)
                    } else {
                        ad.destroy()
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.d(TAG, "Splash ad failed to load: ${error.message}")
                    if (cont.isActive) {
                        cont.resume(null)
                    }
                }
            }
        )
    }

    private suspend fun showAd(
        activity: Activity,
        ad: AppOpenAd,
    ): SplashAdResult = suspendCancellableCoroutine { cont ->
        ad.adEventCallback = object : AppOpenAdEventCallback {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Splash ad dismissed")
                if (cont.isActive) {
                    cont.resume(SplashAdResult.AD_SHOWN)
                }
            }

            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                Log.d(TAG, "Splash ad failed to show: ${error.message}")
                if (cont.isActive) {
                    cont.resume(SplashAdResult.AD_NOT_AVAILABLE)
                }
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Splash ad shown")
            }

            override fun onAdImpression() {}

            override fun onAdClicked() {}
        }
        ad.show(activity)
    }
}
