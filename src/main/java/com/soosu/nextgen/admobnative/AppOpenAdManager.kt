package com.soosu.nextgen.admobnative

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError

class AppOpenAdManager(private val config: AdmobConfig) {

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false

    @Volatile
    private var isShowingAd = false
    private var adLoadedTime: Long = 0
    private var lastAdShownTime: Long = 0
    private var lastAdLoadAttemptTime: Long = 0

    fun isShowingAd(): Boolean = isShowingAd

    fun isAdAvailable(): Boolean {
        val ad = appOpenAd ?: return false
        val elapsed = System.currentTimeMillis() - adLoadedTime
        if (elapsed > config.foregroundAdExpirationMs) {
            Log.d(TAG, "Ad expired after ${elapsed}ms")
            appOpenAd = null
            return false
        }
        return true
    }

    fun loadAd(context: Context) {
        if (!AdmobInitializer.isInitialized()) {
            Log.d(TAG, "MobileAds is not initialized yet, skipping app open ad load")
            return
        }

        if (isLoadingAd || isAdAvailable()) return

        val adUnitId = config.foregroundAdUnitId ?: return

        val now = System.currentTimeMillis()
        if (lastAdLoadAttemptTime > 0 && now - lastAdLoadAttemptTime < config.foregroundAdCooldownMs) {
            Log.d(TAG, "Load cooldown active, skipping")
            return
        }

        isLoadingAd = true
        lastAdLoadAttemptTime = now

        val request = AdRequest.Builder(adUnitId).build()
        AppOpenAd.load(
            request,
            object : AdLoadCallback<AppOpenAd> {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    adLoadedTime = System.currentTimeMillis()
                    isLoadingAd = false
                    Log.d(TAG, "Ad loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingAd = false
                    Log.d(TAG, "Ad failed to load: ${error.message}")
                }
            }
        )
    }

    fun showAdIfAvailable(
        activity: Activity,
        onShowAdComplete: () -> Unit = {},
        loadAndShowIfMissing: Boolean = false,
    ) {
        if (isShowingAd) {
            Log.d(TAG, "Gating: already showing ad")
            onShowAdComplete()
            return
        }

        if (config.shouldSuppressAds()) {
            Log.d(TAG, "Gating: ads suppressed by config")
            onShowAdComplete()
            return
        }

        if (config.foregroundAdShowIntervalMs > 0 && lastAdShownTime > 0) {
            val elapsed = System.currentTimeMillis() - lastAdShownTime
            if (elapsed < config.foregroundAdShowIntervalMs) {
                Log.d(TAG, "Gating: show interval not met (${elapsed}ms < ${config.foregroundAdShowIntervalMs}ms)")
                onShowAdComplete()
                return
            }
        }

        val ad = appOpenAd
        if (ad == null || !isAdAvailable()) {
            Log.d(TAG, "Gating: no ad available (ad=${if (ad == null) "null" else "expired"})")
            if (loadAndShowIfMissing) {
                loadAdAndShow(activity, onShowAdComplete)
            } else {
                onShowAdComplete()
                loadAd(activity)
            }
            return
        }

        ad.adEventCallback = object : AppOpenAdEventCallback {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                isShowingAd = false
                lastAdShownTime = System.currentTimeMillis()
                Log.d(TAG, "Ad dismissed")
                onShowAdComplete()
                loadAd(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                appOpenAd = null
                isShowingAd = false
                Log.d(TAG, "Ad failed to show: ${error.message}")
                onShowAdComplete()
                loadAd(activity)
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Ad shown")
            }

            override fun onAdImpression() {}

            override fun onAdClicked() {}
        }

        isShowingAd = true
        ad.show(activity)
    }

    private fun loadAdAndShow(activity: Activity, onShowAdComplete: () -> Unit) {
        if (!AdmobInitializer.isInitialized()) {
            Log.d(TAG, "loadAdAndShow: SDK not initialized, skipping")
            onShowAdComplete()
            return
        }

        val adUnitId = config.foregroundAdUnitId
        if (adUnitId == null) {
            onShowAdComplete()
            return
        }

        if (isLoadingAd) {
            Log.d(TAG, "loadAdAndShow: already loading")
            onShowAdComplete()
            return
        }

        isLoadingAd = true
        lastAdLoadAttemptTime = System.currentTimeMillis()

        val request = AdRequest.Builder(adUnitId).build()
        AppOpenAd.load(
            request,
            object : AdLoadCallback<AppOpenAd> {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    adLoadedTime = System.currentTimeMillis()
                    isLoadingAd = false
                    Log.d(TAG, "loadAdAndShow: ad loaded, showing immediately")
                    showAdIfAvailable(activity, onShowAdComplete)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingAd = false
                    Log.d(TAG, "loadAdAndShow: failed to load - ${error.message}")
                    onShowAdComplete()
                }
            }
        )
    }

    companion object {
        private const val TAG = "AppOpenAdManager"
    }
}
