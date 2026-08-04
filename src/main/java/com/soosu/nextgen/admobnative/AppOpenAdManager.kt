package com.soosu.nextgen.admobnative

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdPreloader
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.PreloadCallback
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicLong

class AppOpenAdManager(private val config: AdmobConfig) {

    /**
     * A one-shot ad is retained only when the SDK-managed preloader has been
     * explicitly disabled.
     */
    @Volatile
    private var appOpenAd: AppOpenAd? = null

    @Volatile
    private var isLoadingAd = false

    @Volatile
    private var isShowingAd = false

    private var adLoadedTime: Long = 0
    private var lastAdShownTime: Long = 0
    private var lastAdLoadAttemptTime: Long = 0

    @Volatile
    private var stoppedByPolicy = false

    private val keywordLock = Any()
    private val preloaderLock = Any()
    private val runtimeKeywords: MutableList<String> = mutableListOf()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val preloadId = createPreloadId(config, nextManagerId.getAndIncrement())

    private val initLoadLock = Any()

    // Accessed only while holding initLoadLock.
    private var pendingInitLoadContext: Context? = null
    private var initLoadListenerRegistered = false

    // Accessed only while holding preloaderLock.
    private var activePreloadKeywords: List<String>? = null
    private var preloadGeneration: Long = 0
    private var preloadStartedAtElapsedMs: Long = 0
    private val preloadedAtByResponseId: MutableMap<String, Long> = mutableMapOf()
    private val consumedBeforeCallbackResponseIds: MutableSet<String> = LinkedHashSet()
    private var pendingShow: PendingShow? = null

    /**
     * Adds a runtime targeting keyword. Adding one that is already present is a
     * no-op, because a duplicate does not change targeting but would otherwise
     * restart the preload buffer and cost a fresh ad request.
     */
    fun addKeyword(keyword: String) {
        val added = synchronized(keywordLock) {
            if (runtimeKeywords.contains(keyword)) {
                false
            } else {
                runtimeKeywords.add(keyword)
                true
            }
        }
        if (added) {
            restartPreloaderAfterTargetingChange()
        }
    }

    /**
     * Adds runtime targeting keywords, skipping ones already present.
     */
    fun addKeywords(keywords: List<String>) {
        if (keywords.isEmpty()) return
        val added = synchronized(keywordLock) {
            val fresh = keywords.filterNot(runtimeKeywords::contains).distinct()
            runtimeKeywords.addAll(fresh)
            fresh.isNotEmpty()
        }
        if (added) {
            restartPreloaderAfterTargetingChange()
        }
    }

    fun removeKeyword(keyword: String) {
        val removed = synchronized(keywordLock) {
            runtimeKeywords.remove(keyword)
        }
        if (removed) {
            restartPreloaderAfterTargetingChange()
        }
    }

    fun clearKeywords() {
        val changed = synchronized(keywordLock) {
            if (runtimeKeywords.isEmpty()) {
                false
            } else {
                runtimeKeywords.clear()
                true
            }
        }
        if (changed) {
            restartPreloaderAfterTargetingChange()
        }
    }

    fun getKeywords(): List<String> = synchronized(keywordLock) {
        config.keywords + runtimeKeywords
    }

    fun isShowingAd(): Boolean = isShowingAd

    /**
     * Returns the number of ads currently available in the SDK-managed buffer.
     *
     * A one-shot ad loaded while preloading is disabled is not included.
     */
    fun getNumPreloadedAds(): Int {
        if (!config.useAppOpenAdPreloader || !MobileAds.isInitialized) return 0
        return synchronized(preloaderLock) {
            AppOpenAdPreloader.getNumAdsAvailable(preloadId)
        }
    }

    fun isAdAvailable(): Boolean {
        if (getValidOneShotAd() != null) return true
        if (!config.useAppOpenAdPreloader || !MobileAds.isInitialized) return false
        return hasValidPreloadedAd()
    }

    /**
     * Starts the SDK-managed preloader, or performs the original one-shot load
     * when preloading has been explicitly disabled.
     *
     * When the SDK is not initialized yet, the call is recorded and replayed
     * automatically once [AdmobInitializer.initialize] completes. A
     * [stopPreloading] call before that point cancels the pending load.
     *
     * The [context] parameter is intentionally retained for source and binary
     * compatibility with existing applications.
     */
    fun loadAd(context: Context) {
        stoppedByPolicy = false
        if (!MobileAds.isInitialized) {
            Log.d(TAG, "MobileAds is not initialized yet, deferring app open ad load")
            schedulePendingInitLoad(context.applicationContext)
            return
        }

        if (config.shouldSuppressAds()) {
            Log.d(TAG, "Ads are suppressed; stopping app open ad preloading")
            stopPreloading()
            return
        }

        if (config.foregroundAdUnitId == null) return

        if (config.useAppOpenAdPreloader) {
            ensurePreloaderStarted()
        } else {
            loadOneShotAd()
        }
    }

    /**
     * Records a [loadAd] call made before SDK initialization and replays it
     * once [AdmobInitializer.initialize] completes. Only the application
     * context is retained. The replayed call re-runs every [loadAd] gate
     * (suppression, ad unit, preloader mode), so policy checks still apply.
     */
    private fun schedulePendingInitLoad(appContext: Context) {
        val register = synchronized(initLoadLock) {
            pendingInitLoadContext = appContext
            if (initLoadListenerRegistered) {
                false
            } else {
                initLoadListenerRegistered = true
                true
            }
        }
        if (!register) return

        AdmobInitializer.whenInitialized {
            val context = synchronized(initLoadLock) {
                initLoadListenerRegistered = false
                pendingInitLoadContext.also { pendingInitLoadContext = null }
            }
            context?.let(::loadAd)
        }
    }

    /**
     * Stops this manager's SDK-managed buffer and releases all queued ads.
     *
     * A later [loadAd] or [showAdIfAvailable] call can start it again. The
     * stopped flag prevents a synchronous show callback from reviving a pool
     * after an app has explicitly ended a one-shot flow.
     */
    fun stopPreloading(): Boolean {
        stoppedByPolicy = true
        synchronized(initLoadLock) {
            pendingInitLoadContext = null
        }
        val pending: PendingShow?
        val stopped: Boolean
        synchronized(preloaderLock) {
            stopped = destroyPreloaderLocked()
            pending = pendingShow
            pendingShow = null
        }
        pending?.let { completePendingShow(it) }
        return stopped
    }

    fun showAdIfAvailable(
        activity: Activity,
        onShowAdComplete: () -> Unit = {},
        loadAndShowIfMissing: Boolean = false,
        stopAfterShow: Boolean = false,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post {
                showAdIfAvailable(activity, onShowAdComplete, loadAndShowIfMissing, stopAfterShow)
            }
            return
        }

        if (isShowingAd) {
            Log.d(TAG, "Gating: already showing ad")
            onShowAdComplete()
            return
        }

        if (activity.isFinishing || activity.isDestroyed) {
            Log.d(TAG, "Gating: activity not in a valid state to show ad")
            onShowAdComplete()
            return
        }

        if (config.shouldSuppressAds()) {
            Log.d(TAG, "Gating: ads suppressed by config")
            stopPreloading()
            onShowAdComplete()
            return
        }

        if (config.foregroundAdShowIntervalMs > 0 && lastAdShownTime > 0) {
            val elapsed = SystemClock.elapsedRealtime() - lastAdShownTime
            if (elapsed < config.foregroundAdShowIntervalMs) {
                Log.d(
                    TAG,
                    "Gating: show interval not met " +
                        "(${elapsed}ms < ${config.foregroundAdShowIntervalMs}ms)",
                )
                onShowAdComplete()
                return
            }
        }

        val oneShotAd = getValidOneShotAd()
        val ad = oneShotAd ?: if (stopAfterShow) null else pollPreloadedAd()
        if (ad == null) {
            Log.d(TAG, "Gating: no ad available")
            if (loadAndShowIfMissing) {
                if (config.useAppOpenAdPreloader) {
                    waitForPreloadedAdAndShow(activity, onShowAdComplete)
                } else {
                    loadOneShotAdAndShow(activity, onShowAdComplete, stopAfterShow)
                }
            } else {
                onShowAdComplete()
                loadAd(activity)
            }
            return
        }

        showAd(activity, ad, onShowAdComplete, stopAfterShow)
    }

    private fun ensurePreloaderStarted(): Boolean {
        if (stoppedByPolicy || !config.useAppOpenAdPreloader || !MobileAds.isInitialized) return false

        val adUnitId = config.foregroundAdUnitId ?: return false
        val keywords = getKeywords()

        return synchronized(preloaderLock) {
            val existingConfiguration = AppOpenAdPreloader.getConfiguration(preloadId)
            if (existingConfiguration != null && activePreloadKeywords == keywords) {
                return@synchronized true
            }

            if (existingConfiguration != null) {
                destroyPreloaderLocked()
            }

            val request = AdRequest.Builder(adUnitId).applyKeywords(keywords).build()
            val preloadConfiguration = config.appOpenAdPreloadBufferSize?.let { bufferSize ->
                PreloadConfiguration(request, bufferSize)
            } ?: PreloadConfiguration(request)

            val generation = ++preloadGeneration
            preloadStartedAtElapsedMs = SystemClock.elapsedRealtime()
            val started = AppOpenAdPreloader.start(
                preloadId,
                preloadConfiguration,
                createPreloadCallback(generation),
            )
            if (started) {
                activePreloadKeywords = keywords
                Log.d(
                    TAG,
                    "App open ad preloading active " +
                        "(buffer=${config.appOpenAdPreloadBufferSize ?: "SDK optimized"})",
                )
            } else {
                preloadGeneration++
                preloadStartedAtElapsedMs = 0
                activePreloadKeywords = null
                preloadedAtByResponseId.clear()
                consumedBeforeCallbackResponseIds.clear()
            }
            started
        }
    }

    private fun createPreloadCallback(generation: Long): PreloadCallback =
        object : PreloadCallback {
            override fun onAdPreloaded(preloadId: String, responseInfo: ResponseInfo) {
                val accepted = synchronized(preloaderLock) {
                    if (generation != preloadGeneration) {
                        false
                    } else {
                        responseInfo.responseId?.let { responseId ->
                            if (!consumedBeforeCallbackResponseIds.remove(responseId)) {
                                preloadedAtByResponseId.putIfAbsent(
                                    responseId,
                                    SystemClock.elapsedRealtime(),
                                )
                            }
                        }
                        true
                    }
                }
                if (!accepted) return

                // Always post so start/poll is never re-entered from inside an
                // SDK PreloadCallback.
                mainHandler.post {
                    if (!isCurrentPreloadGeneration(generation)) return@post
                    Log.d(TAG, "Ad preloaded (id=$preloadId)")
                    showPendingAdIfAvailable(completeIfUnavailable = true)
                }
            }

            override fun onAdsExhausted(preloadId: String) {
                mainHandler.post {
                    if (!isCurrentPreloadGeneration(generation)) return@post
                    Log.d(
                        TAG,
                        "Preloaded ads exhausted (id=$preloadId); SDK will refill the buffer",
                    )
                }
            }

            override fun onAdFailedToPreload(preloadId: String, error: LoadAdError) {
                mainHandler.post {
                    if (!isCurrentPreloadGeneration(generation)) return@post
                    Log.d(
                        TAG,
                        "Ad failed to preload (id=$preloadId): " +
                            "${error.message}; SDK will retry",
                    )
                    handlePendingPreloadFailure()
                }
            }
        }

    private fun isCurrentPreloadGeneration(generation: Long): Boolean =
        synchronized(preloaderLock) {
            generation == preloadGeneration
        }

    private fun pollPreloadedAd(): AppOpenAd? {
        if (!config.useAppOpenAdPreloader || !MobileAds.isInitialized) return null
        if (!ensurePreloaderStarted()) return null

        var adsRemaining = synchronized(preloaderLock) {
            AppOpenAdPreloader.getNumAdsAvailable(preloadId)
        }
        while (adsRemaining-- > 0) {
            val adToShow = synchronized(preloaderLock) {
                val ad = AppOpenAdPreloader.pollAd(preloadId) ?: return@synchronized null
                val responseId = ad.getResponseInfo().responseId
                val preloadedAt = if (responseId != null) {
                    preloadedAtByResponseId.remove(responseId)
                } else {
                    null
                }

                if (preloadedAt == null && responseId != null) {
                    // The SDK can make an ad pollable immediately before its
                    // callback records the response ID. Ignore that late
                    // callback after this ad has already been consumed.
                    forgetPendingResponseId(responseId)
                }

                // An unknown load time is not treated as expired. The preloader
                // start time is only a lower bound, so using it here discarded
                // ads that had just been refilled and forced a full restart,
                // which cost an extra ad request per expiration.
                if (!isExpired(preloadedAt)) {
                    return@synchronized ad
                }

                // Only the expired ad is dropped. The SDK refills one ad per
                // consumed ad, so the buffer recovers without a restart.
                Log.d(TAG, "Discarding expired preloaded ad")
                ad.destroy()
                null
            }

            if (adToShow != null) return adToShow
        }
        return null
    }

    /**
     * Reports whether the next queued ad is usable.
     *
     * This is a pure query: it never polls, destroys, or restarts the buffer.
     * Use [pruneExpiredPreloadedAds] to drop stale inventory.
     */
    private fun hasValidPreloadedAd(): Boolean {
        if (!config.useAppOpenAdPreloader || !MobileAds.isInitialized) return false

        return synchronized(preloaderLock) {
            if (!AppOpenAdPreloader.isAdAvailable(preloadId)) return@synchronized false

            val preloadedAt = AppOpenAdPreloader.peekAdResponseInfo(preloadId)
                ?.responseId
                ?.let(preloadedAtByResponseId::get)
            !isExpired(preloadedAt)
        }
    }

    /**
     * Drops queued app open ads older than `foregroundAdExpirationMs`.
     *
     * Expiration cleanup is never performed by the query APIs, so call this
     * explicitly when stale inventory should be dropped without showing an ad.
     * The SDK refills one ad per dropped ad.
     *
     * @return the number of ads that were destroyed
     */
    fun pruneExpiredPreloadedAds(): Int {
        if (!config.useAppOpenAdPreloader || !MobileAds.isInitialized) return 0

        var destroyed = 0
        synchronized(preloaderLock) {
            var adsRemaining = AppOpenAdPreloader.getNumAdsAvailable(preloadId)
            while (adsRemaining-- > 0) {
                val preloadedAt = AppOpenAdPreloader.peekAdResponseInfo(preloadId)
                    ?.responseId
                    ?.let(preloadedAtByResponseId::get)
                if (!isExpired(preloadedAt)) break

                val expiredAd = AppOpenAdPreloader.pollAd(preloadId) ?: break
                expiredAd.getResponseInfo().responseId?.let { responseId ->
                    if (preloadedAtByResponseId.remove(responseId) == null) {
                        forgetPendingResponseId(responseId)
                    }
                }
                Log.d(TAG, "Discarding expired preloaded ad")
                expiredAd.destroy()
                destroyed++
            }
        }
        return destroyed
    }

    /**
     * One-shot ads always have a known load time, so an unset value here means
     * the ad is stale rather than "age unknown".
     */
    private fun isOneShotExpired(loadedAt: Long): Boolean {
        val elapsed = SystemClock.elapsedRealtime() - loadedAt
        return elapsed > config.foregroundAdExpirationMs
    }

    /**
     * A `null` [loadedAt] means the load time is unknown, which is never
     * treated as expired.
     */
    private fun isExpired(loadedAt: Long?): Boolean {
        if (loadedAt == null || loadedAt <= 0L) return false
        val elapsed = SystemClock.elapsedRealtime() - loadedAt
        return elapsed > config.foregroundAdExpirationMs
    }

    // Must be called while holding preloaderLock. A preload callback that never
    // arrives would otherwise retain its response ID for the process lifetime.
    private fun forgetPendingResponseId(responseId: String) {
        consumedBeforeCallbackResponseIds.add(responseId)
        while (consumedBeforeCallbackResponseIds.size > MAX_PENDING_RESPONSE_IDS) {
            val oldest = consumedBeforeCallbackResponseIds.iterator()
            oldest.next()
            oldest.remove()
        }
    }

    private fun restartPreloaderAfterTargetingChange() {
        if (!config.useAppOpenAdPreloader) return

        val keywords = getKeywords()
        val wasActive = synchronized(preloaderLock) {
            when {
                activePreloadKeywords == null -> false
                // Restarting costs the whole buffer plus a fresh request, so
                // only do it when the effective targeting actually changed.
                activePreloadKeywords == keywords -> return
                else -> {
                    destroyPreloaderLocked()
                    true
                }
            }
        }
        if (!wasActive) return

        if (MobileAds.isInitialized &&
            config.foregroundAdUnitId != null &&
            !config.shouldSuppressAds()
        ) {
            if (!ensurePreloaderStarted()) {
                completePendingShow()
            }
        } else {
            completePendingShow()
        }
    }

    // Must be called while holding preloaderLock.
    private fun destroyPreloaderLocked(): Boolean {
        preloadGeneration++
        val stopped = if (MobileAds.isInitialized) {
            AppOpenAdPreloader.destroy(preloadId)
        } else {
            false
        }
        activePreloadKeywords = null
        preloadStartedAtElapsedMs = 0
        preloadedAtByResponseId.clear()
        consumedBeforeCallbackResponseIds.clear()
        if (stopped) {
            Log.d(TAG, "App open ad preloading stopped")
        }
        return stopped
    }

    private fun waitForPreloadedAdAndShow(
        activity: Activity,
        onShowAdComplete: () -> Unit,
    ) {
        val accepted = synchronized(preloaderLock) {
            if (pendingShow != null) {
                false
            } else {
                pendingShow = PendingShow(WeakReference(activity), onShowAdComplete)
                true
            }
        }
        if (!accepted) {
            Log.d(TAG, "loadAndShow: already waiting for a preloaded ad")
            onShowAdComplete()
            return
        }

        if (!ensurePreloaderStarted()) {
            completePendingShow()
            return
        }

        // Covers the race where an ad became ready between the first poll and
        // registration of pendingShow.
        showPendingAdIfAvailable()
    }

    private fun showPendingAdIfAvailable(completeIfUnavailable: Boolean = false) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post {
                showPendingAdIfAvailable(completeIfUnavailable)
            }
            return
        }

        val hasPending = synchronized(preloaderLock) { pendingShow != null }
        if (!hasPending) return
        if (!hasValidPreloadedAd()) {
            if (completeIfUnavailable) {
                completePendingShow()
            }
            return
        }

        val pending = synchronized(preloaderLock) {
            pendingShow.also { pendingShow = null }
        } ?: return
        val activity = pending.activity.get()
        if (activity == null) {
            pending.onComplete()
            return
        }
        showAdIfAvailable(activity, pending.onComplete)
    }

    private fun handlePendingPreloadFailure() {
        if (hasValidPreloadedAd()) {
            showPendingAdIfAvailable()
        } else {
            completePendingShow()
        }
    }

    private fun completePendingShow() {
        val pending = synchronized(preloaderLock) {
            pendingShow.also { pendingShow = null }
        } ?: return
        completePendingShow(pending)
    }

    private fun completePendingShow(pending: PendingShow) {
        runOnMainThread(pending.onComplete)
    }

    private fun getValidOneShotAd(): AppOpenAd? {
        val ad = appOpenAd ?: return null
        if (isOneShotExpired(adLoadedTime)) {
            Log.d(TAG, "One-shot ad expired")
            ad.destroy()
            appOpenAd = null
            return null
        }
        return ad
    }

    private fun loadOneShotAd() {
        if (isLoadingAd || getValidOneShotAd() != null) return

        val adUnitId = config.foregroundAdUnitId ?: return
        val now = SystemClock.elapsedRealtime()
        if (lastAdLoadAttemptTime > 0 &&
            now - lastAdLoadAttemptTime < config.foregroundAdCooldownMs
        ) {
            Log.d(TAG, "Load cooldown active, skipping")
            return
        }

        isLoadingAd = true
        lastAdLoadAttemptTime = now

        val request = AdRequest.Builder(adUnitId).applyKeywords(getKeywords()).build()
        AppOpenAd.load(
            request,
            object : AdLoadCallback<AppOpenAd> {
                override fun onAdLoaded(ad: AppOpenAd) {
                    runOnMainThread {
                        appOpenAd = ad
                        adLoadedTime = SystemClock.elapsedRealtime()
                        isLoadingAd = false
                        Log.d(TAG, "One-shot ad loaded")
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    runOnMainThread {
                        isLoadingAd = false
                        Log.d(TAG, "One-shot ad failed to load: ${error.message}")
                    }
                }
            },
        )
    }

    private fun showAd(
        activity: Activity,
        ad: AppOpenAd,
        onShowAdComplete: () -> Unit,
        stopAfterShow: Boolean = false,
    ) {
        ad.adEventCallback = object : AppOpenAdEventCallback {
            override fun onAdDismissedFullScreenContent() {
                runOnMainThread {
                    if (appOpenAd === ad) {
                        appOpenAd = null
                    }
                    isShowingAd = false
                    lastAdShownTime = SystemClock.elapsedRealtime()
                    Log.d(TAG, "Ad dismissed")
                    onShowAdComplete()
                    if (stopAfterShow) stopPreloading() else loadAd(activity)
                }
            }

            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                runOnMainThread {
                    if (appOpenAd === ad) {
                        appOpenAd = null
                    }
                    isShowingAd = false
                    ad.destroy()
                    Log.d(TAG, "Ad failed to show: ${error.message}")
                    onShowAdComplete()
                    if (stopAfterShow) stopPreloading() else loadAd(activity)
                }
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Ad shown")
            }

            override fun onAdImpression() {}

            override fun onAdClicked() {}
        }

        isShowingAd = true
        try {
            ad.show(activity)
        } catch (_: RuntimeException) {
            isShowingAd = false
            ad.destroy()
            if (stopAfterShow) stopPreloading()
            onShowAdComplete()
        }
    }

    private fun loadOneShotAdAndShow(
        activity: Activity,
        onShowAdComplete: () -> Unit,
        stopAfterShow: Boolean,
    ) {
        if (stoppedByPolicy || !MobileAds.isInitialized) {
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
        lastAdLoadAttemptTime = SystemClock.elapsedRealtime()

        val request = AdRequest.Builder(adUnitId).applyKeywords(getKeywords()).build()
        AppOpenAd.load(
            request,
            object : AdLoadCallback<AppOpenAd> {
                override fun onAdLoaded(ad: AppOpenAd) {
                    runOnMainThread {
                        appOpenAd = ad
                        adLoadedTime = SystemClock.elapsedRealtime()
                        isLoadingAd = false
                        Log.d(TAG, "loadAdAndShow: ad loaded, showing immediately")
                        showAdIfAvailable(
                            activity,
                            onShowAdComplete,
                            stopAfterShow = stopAfterShow,
                        )
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    runOnMainThread {
                        isLoadingAd = false
                        Log.d(TAG, "loadAdAndShow: failed to load - ${error.message}")
                        onShowAdComplete()
                    }
                }
            },
        )
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private data class PendingShow(
        val activity: WeakReference<Activity>,
        val onComplete: () -> Unit,
    )

    companion object {
        private const val TAG = "AppOpenAdManager"
        private const val PRELOAD_ID_PREFIX = "nextgen-admob-app-open-"
        private const val MAX_PENDING_RESPONSE_IDS = 64
        private val nextManagerId = AtomicLong()

        private fun createPreloadId(config: AdmobConfig, managerId: Long): String {
            val key = buildString {
                append(config.admobAppId)
                append('\u0000')
                append(config.foregroundAdUnitId.orEmpty())
                append('\u0000')
                append(config.keywords.joinToString(separator = "\u0000"))
            }
            return PRELOAD_ID_PREFIX +
                Integer.toHexString(key.hashCode()) +
                "-" +
                managerId.toString(16)
        }
    }
}

private fun <T : com.google.android.libraries.ads.mobile.sdk.common.BaseRequestBuilder<T>> T.applyKeywords(
    keywords: List<String>,
): T {
    keywords.forEach { addKeyword(it) }
    return this
}
