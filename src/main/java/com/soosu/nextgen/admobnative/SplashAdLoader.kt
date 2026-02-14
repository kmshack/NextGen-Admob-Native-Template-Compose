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

        // 1. Gather consent with timeout
        val consentManager = AdmobConsentManager(activity)
        val canRequestAds = withTimeoutOrNull(config.consentTimeoutMs) {
            consentManager.gatherConsent(activity)
        } ?: consentManager.canRequestAds

        if (!canRequestAds) {
            Log.d(TAG, "Cannot request ads after consent")
            return SplashAdResult.AD_NOT_AVAILABLE
        }

        // 2. Initialize SDK
        AdmobInitializer.initialize(activity, config.admobAppId)

        // 3. Load ad with timeout
        val ad = withTimeoutOrNull(config.splashAdLoadTimeoutMs) {
            loadAd(adUnitId)
        }

        if (ad == null) {
            Log.d(TAG, "Ad load timed out or failed")
            return SplashAdResult.AD_NOT_AVAILABLE
        }

        // 4. Show ad and wait for dismiss
        return showAd(activity, ad)
    }

    private suspend fun loadAd(
        adUnitId: String,
    ): AppOpenAd? = suspendCancellableCoroutine { cont ->
        val request = AdRequest.Builder(adUnitId).build()
        AppOpenAd.load(
            request,
            object : AdLoadCallback<AppOpenAd> {
                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.d(TAG, "Splash ad loaded")
                    if (cont.isActive) {
                        cont.resume(ad)
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
