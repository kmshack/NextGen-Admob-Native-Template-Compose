package com.soosu.nextgen.admobnative

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.PreloadCallback
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdPreloader
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Configuration for one independently managed interstitial ad preload pool.
 *
 * Pass a fully built [AdRequest] so keywords, custom targeting, and mediation
 * extras are preserved.
 *
 * @param request request used whenever this pool needs another ad
 * @param bufferSize maximum number of ready ads for this pool. `null` delegates
 * sizing to the SDK. The library default is one because interstitial fill is
 * usually consumed at a single transition point.
 * @param maxAdAgeMs maximum queued-ad age this manager will return. Expiration
 * is checked when the pool is accessed; ads already handed to a caller are not
 * managed by this value.
 */
data class InterstitialAdLoadConfig @JvmOverloads constructor(
    val request: AdRequest,
    val bufferSize: Int? = DEFAULT_BUFFER_SIZE,
    val maxAdAgeMs: Long = DEFAULT_MAX_AD_AGE_MS,
) {
    init {
        require(bufferSize == null || bufferSize >= 1) {
            "bufferSize must be null or at least 1"
        }
        require(maxAdAgeMs > 0) {
            "maxAdAgeMs must be greater than 0"
        }
    }

    companion object {
        const val DEFAULT_BUFFER_SIZE: Int = 1
        const val DEFAULT_MAX_AD_AGE_MS: Long = 60L * 60L * 1_000L
    }
}

/**
 * Snapshot of a registered interstitial ad pool.
 *
 * [isLoading] means that the SDK preloader is active but currently has no
 * pollable ad. A preload failure is non-terminal because the SDK keeps retrying.
 */
data class InterstitialAdLoadState(
    val isStarted: Boolean,
    val availableCount: Int,
    val isLoading: Boolean,
    val lastError: LoadAdError?,
)

/**
 * Listener for a single key registered in [InterstitialAdLoadManager].
 *
 * Listener methods are posted to the main thread after the SDK's
 * [PreloadCallback] has returned, so it is safe to update UI state or call back
 * into [InterstitialAdLoadManager].
 */
interface InterstitialAdLoadListener {
    fun onStateChanged(key: String, state: InterstitialAdLoadState) = Unit

    fun onAdPreloaded(
        key: String,
        availableCount: Int,
        responseInfo: ResponseInfo,
    ) = Unit

    fun onAdsExhausted(key: String) = Unit

    fun onAdFailedToPreload(key: String, error: LoadAdError) = Unit
}

/**
 * Java-friendly no-op adapter for [InterstitialAdLoadListener].
 *
 * Kotlin callers can implement [InterstitialAdLoadListener] directly. Java
 * callers can extend this class and override only the callbacks they need.
 */
abstract class InterstitialAdLoadListenerAdapter : InterstitialAdLoadListener {
    override fun onStateChanged(key: String, state: InterstitialAdLoadState) = Unit

    override fun onAdPreloaded(
        key: String,
        availableCount: Int,
        responseInfo: ResponseInfo,
    ) = Unit

    override fun onAdsExhausted(key: String) = Unit

    override fun onAdFailedToPreload(key: String, error: LoadAdError) = Unit
}

/**
 * Application-scoped manager for Next-Gen SDK interstitial ad preload pools.
 *
 * Loading mechanics live here; app policy does not. Consent, paid-user,
 * connectivity, show-frequency, and Remote Config checks should decide when the
 * app calls [start], [stop], [showAdIfAvailable], or [unregister].
 *
 * Each key owns an independent SDK buffer. Calling [pollAd] transfers ownership
 * of the returned ad to the caller. The caller must eventually call `destroy()`
 * on that ad; stopping this manager only destroys ads that are still queued in
 * the SDK. [showAdIfAvailable] keeps ownership inside the manager and destroys
 * the ad after it leaves the screen.
 */
class InterstitialAdLoadManager private constructor(
    private val gateway: InterstitialAdPreloaderGateway,
    private val isSdkInitialized: () -> Boolean,
    private val elapsedRealtime: () -> Long,
    private val postToMain: (() -> Unit) -> Unit,
    private val beforeExpirationRestart: () -> Unit,
    private val registerInitListener: (listener: () -> Unit) -> Unit,
) : AutoCloseable {

    constructor() : this(
        gateway = SdkInterstitialAdPreloaderGateway,
        isSdkInitialized = { MobileAds.isInitialized },
        elapsedRealtime = SystemClock::elapsedRealtime,
        postToMain = createMainThreadPoster(),
        beforeExpirationRestart = {},
        registerInitListener = AdmobInitializer::whenInitialized,
    )

    private val lock = Any()
    private val pools = linkedMapOf<String, Pool>()
    private val managerId = nextManagerId.getAndIncrement()
    private var nextPoolId = 0L

    // Guarded by [lock]. True while an SDK-initialization listener that will
    // resume pending starts is registered but has not fired yet.
    private var initResumeListenerRegistered = false

    @Volatile
    private var isShowingAd = false

    /**
     * Registers or replaces a pool configuration.
     *
     * Replacing an active key destroys its queued ads and immediately starts a
     * new generation with the new request. Existing listeners remain attached.
     * Register stable keys once at application/controller scope rather than
     * from a recomposing UI function.
     */
    fun register(key: String, config: InterstitialAdLoadConfig) {
        requireValidKey(key)

        var replacementFailed = false
        val restart = synchronized(lock) {
            val existing = pools[key]
            if (existing == null) {
                pools[key] = Pool(
                    key = key,
                    sdkPreloadId = createSdkPreloadId(),
                    config = config,
                )
                false
            } else {
                val wasStarted = existing.isStarted
                if (wasStarted && !stopLocked(existing)) {
                    replacementFailed = true
                    false
                } else {
                    existing.config = config
                    existing.lastError = null
                    wasStarted
                }
            }
        }

        if (replacementFailed) {
            dispatchStateChanged(key)
        } else if (restart) {
            start(key)
        } else {
            dispatchStateChanged(key)
        }
    }

    /**
     * Convenience overload that creates [InterstitialAdLoadConfig] from an ad
     * unit ID.
     */
    @JvmOverloads
    fun register(
        key: String,
        adUnitId: String,
        bufferSize: Int? = InterstitialAdLoadConfig.DEFAULT_BUFFER_SIZE,
        maxAdAgeMs: Long = InterstitialAdLoadConfig.DEFAULT_MAX_AD_AGE_MS,
    ) {
        register(
            key = key,
            config = InterstitialAdLoadConfig(
                request = AdRequest.Builder(adUnitId).build(),
                bufferSize = bufferSize,
                maxAdAgeMs = maxAdAgeMs,
            ),
        )
    }

    /**
     * Convenience overload that creates [InterstitialAdLoadConfig] from a
     * fully built request.
     */
    @JvmOverloads
    fun register(
        key: String,
        request: AdRequest,
        bufferSize: Int? = InterstitialAdLoadConfig.DEFAULT_BUFFER_SIZE,
        maxAdAgeMs: Long = InterstitialAdLoadConfig.DEFAULT_MAX_AD_AGE_MS,
    ) {
        register(
            key = key,
            config = InterstitialAdLoadConfig(
                request = request,
                bufferSize = bufferSize,
                maxAdAgeMs = maxAdAgeMs,
            ),
        )
    }

    /**
     * Starts the registered pool. Repeated calls are idempotent.
     *
     * When the SDK is not initialized yet, the start is recorded and retried
     * automatically once [AdmobInitializer.initialize] completes. A [stop] or
     * [unregister] before that point cancels the pending start.
     *
     * @return `true` when the pool is active (including when it was already
     * active), or `false` when the key is missing, the SDK is not initialized
     * (start deferred), or the SDK rejected the configuration.
     */
    fun start(key: String): Boolean {
        requireValidKey(key)
        if (!isSdkInitialized()) {
            schedulePendingStart(key)
            return false
        }

        val started = synchronized(lock) {
            val pool = pools[key] ?: return@synchronized false
            if (pool.isStarted) return@synchronized true

            val generation = ++pool.generation
            pool.startedAtElapsedMs = elapsedRealtime()
            pool.loadedAtByResponseId.clear()
            pool.consumedBeforeCallbackResponseIds.clear()
            pool.lastError = null
            pool.isStarted = true

            val preloadConfiguration = pool.config.bufferSize?.let { bufferSize ->
                PreloadConfiguration(pool.config.request, bufferSize)
            } ?: PreloadConfiguration(pool.config.request)

            val accepted = try {
                gateway.start(
                    pool.sdkPreloadId,
                    preloadConfiguration,
                    createPreloadCallback(
                        key = key,
                        sdkPreloadId = pool.sdkPreloadId,
                        generation = generation,
                    ),
                )
            } catch (_: Exception) {
                false
            }

            if (!accepted) {
                pool.generation++
                pool.isStarted = false
                pool.startedAtElapsedMs = 0L
                pool.loadedAtByResponseId.clear()
                pool.consumedBeforeCallbackResponseIds.clear()
            }
            accepted
        }

        dispatchStateChanged(key)
        return started
    }

    private fun schedulePendingStart(key: String) {
        val register = synchronized(lock) {
            val pool = pools[key] ?: return
            pool.startPending = true
            if (initResumeListenerRegistered) {
                false
            } else {
                initResumeListenerRegistered = true
                true
            }
        }
        if (!register) return

        registerInitListener {
            val pendingKeys = synchronized(lock) {
                initResumeListenerRegistered = false
                pools.values
                    .filter { it.startPending }
                    .onEach { it.startPending = false }
                    .map { it.key }
            }
            pendingKeys.forEach(::start)
        }
    }

    /**
     * Starts every registered key and returns the number of active pools.
     */
    fun startAll(): Int {
        val keys = synchronized(lock) { pools.keys.toList() }
        return keys.count(::start)
    }

    /**
     * Restarts a registered pool with its current configuration.
     */
    fun refresh(key: String): Boolean {
        requireValidKey(key)
        val canStart = synchronized(lock) {
            val pool = pools[key] ?: return false
            !pool.isStarted || stopLocked(pool)
        }
        if (!canStart) {
            dispatchStateChanged(key)
            return false
        }
        return start(key)
    }

    /**
     * Polls the next valid preloaded ad. Ownership transfers to the caller,
     * who must set the event callback, show, and eventually `destroy()` it.
     * Prefer [showAdIfAvailable] unless custom show handling is required.
     */
    fun pollAd(key: String): InterstitialAd? {
        requireValidKey(key)
        if (!isSdkInitialized()) return null

        var adForCaller: InterstitialAd? = null
        var expiredGeneration: PoolToken? = null
        var poolChanged = false

        synchronized(lock) {
            val pool = pools[key] ?: return@synchronized
            if (!pool.isStarted) return@synchronized

            var remaining = gateway.getNumAdsAvailable(pool.sdkPreloadId)
            while (remaining-- > 0) {
                val ad = gateway.pollAd(pool.sdkPreloadId) ?: break
                poolChanged = true

                val responseId = ad.responseIdOrNull()
                val loadedAt = responseId?.let(pool.loadedAtByResponseId::remove)
                if (loadedAt == null && responseId != null) {
                    // An ad can become pollable before its preload callback is
                    // delivered. Ignore that callback if it arrives later.
                    pool.consumedBeforeCallbackResponseIds.add(responseId)
                }

                val effectiveLoadedAt = loadedAt ?: pool.startedAtElapsedMs
                if (!isExpired(pool, effectiveLoadedAt)) {
                    adForCaller = ad
                    break
                }

                ad.destroy()
                if (loadedAt == null) {
                    // A generation timestamp is only a conservative lower
                    // bound. Once it expires, restart instead of treating a
                    // just-refilled but not-yet-tracked ad as old.
                    expiredGeneration = PoolToken(
                        sdkPreloadId = pool.sdkPreloadId,
                        generation = pool.generation,
                    )
                    break
                }
            }
        }

        val generationToRestart = expiredGeneration
        if (generationToRestart != null) {
            beforeExpirationRestart()
            restartExpiredPoolIfCurrent(key, generationToRestart)
        } else if (poolChanged) {
            dispatchStateChanged(key)
        }
        return adForCaller
    }

    /**
     * Waits until an ad is ready in an already-started pool and consumes it.
     * Returns `null` on timeout or when the pool is stopped.
     *
     * This method never starts a stopped pool. App policy must explicitly call
     * [start] after consent, paid-user, and Remote Config checks.
     */
    suspend fun awaitAd(
        key: String,
        timeoutMs: Long = DEFAULT_AWAIT_TIMEOUT_MS,
    ): InterstitialAd? {
        requireValidKey(key)
        require(timeoutMs > 0) { "timeoutMs must be greater than 0" }

        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                lateinit var listener: InterstitialAdLoadListener
                val completed = AtomicBoolean(false)

                fun finish(ad: InterstitialAd?) {
                    if (!completed.compareAndSet(false, true)) {
                        ad?.destroy()
                        return
                    }
                    if (!continuation.isActive) {
                        ad?.destroy()
                        return
                    }
                    removeListener(key, listener)
                    continuation.resume(
                        value = ad,
                        onCancellation = { _, cancelledAd, _ ->
                            cancelledAd?.destroy()
                        },
                    )
                }

                fun pollIfReady() {
                    if (!continuation.isActive) return
                    pollAd(key)?.let(::finish)
                }

                listener = object : InterstitialAdLoadListener {
                    override fun onStateChanged(
                        key: String,
                        state: InterstitialAdLoadState,
                    ) {
                        if (!state.isStarted) {
                            finish(null)
                        }
                    }

                    override fun onAdPreloaded(
                        key: String,
                        availableCount: Int,
                        responseInfo: ResponseInfo,
                    ) {
                        pollIfReady()
                    }
                }

                if (!addListener(key, listener, emitCurrentState = false)) {
                    finish(null)
                    return@suspendCancellableCoroutine
                }

                continuation.invokeOnCancellation {
                    removeListener(key, listener)
                }

                if (!isStarted(key)) {
                    finish(null)
                    return@suspendCancellableCoroutine
                }

                // Covers an ad that was already available or became available
                // between listener registration and the active-state check.
                postToMain(::pollIfReady)
            }
        }
    }

    /**
     * Polls the next valid ad and shows it over [activity].
     *
     * Ownership stays in the manager: the ad is destroyed after it is
     * dismissed or fails to show. [onShowAdComplete] is always invoked on the
     * main thread exactly once — immediately when no ad is available, the
     * activity is finishing, or another ad from this manager is showing.
     *
     * Show-frequency policy (intervals, caps) intentionally stays in the app.
     */
    @JvmOverloads
    fun showAdIfAvailable(
        activity: Activity,
        key: String,
        onShowAdComplete: () -> Unit = {},
    ) {
        requireValidKey(key)
        if (Looper.myLooper() != Looper.getMainLooper()) {
            postToMain {
                showAdIfAvailable(activity, key, onShowAdComplete)
            }
            return
        }

        if (isShowingAd) {
            onShowAdComplete()
            return
        }
        if (activity.isFinishing || activity.isDestroyed) {
            onShowAdComplete()
            return
        }

        val ad = pollAd(key)
        if (ad == null) {
            onShowAdComplete()
            return
        }

        ad.adEventCallback = object : InterstitialAdEventCallback {
            override fun onAdDismissedFullScreenContent() {
                postToMain {
                    isShowingAd = false
                    ad.destroy()
                    onShowAdComplete()
                }
            }

            override fun onAdFailedToShowFullScreenContent(
                fullScreenContentError: FullScreenContentError,
            ) {
                postToMain {
                    isShowingAd = false
                    ad.destroy()
                    onShowAdComplete()
                }
            }
        }

        isShowingAd = true
        ad.show(activity)
    }

    fun isShowingAd(): Boolean = isShowingAd

    fun isAdAvailable(key: String): Boolean {
        requireValidKey(key)
        if (!isSdkInitialized()) return false

        while (true) {
            var expiredGeneration: PoolToken? = null
            val available = synchronized(lock) {
                val pool = pools[key] ?: return@synchronized false
                if (!pool.isStarted ||
                    !gateway.isAdAvailable(pool.sdkPreloadId)
                ) {
                    return@synchronized false
                }

                val responseInfo = gateway.peekAdResponseInfo(pool.sdkPreloadId)
                val loadedAt = responseInfo
                    ?.responseId
                    ?.let(pool.loadedAtByResponseId::get)
                val effectiveLoadedAt = loadedAt ?: pool.startedAtElapsedMs
                if (!isExpired(pool, effectiveLoadedAt)) {
                    return@synchronized true
                }

                if (loadedAt == null) {
                    expiredGeneration = PoolToken(
                        sdkPreloadId = pool.sdkPreloadId,
                        generation = pool.generation,
                    )
                    return@synchronized false
                }

                val expiredAd = gateway.pollAd(pool.sdkPreloadId)
                    ?: return@synchronized false
                val expiredResponseId = expiredAd.responseIdOrNull()
                if (expiredResponseId != null) {
                    val removed = pool.loadedAtByResponseId.remove(expiredResponseId)
                    if (removed == null) {
                        pool.consumedBeforeCallbackResponseIds.add(expiredResponseId)
                    }
                }
                expiredAd.destroy()
                null
            }

            val generationToRestart = expiredGeneration
            if (generationToRestart != null) {
                beforeExpirationRestart()
                restartExpiredPoolIfCurrent(key, generationToRestart)
                return false
            }
            when (available) {
                true -> return true
                false -> return false
                null -> Unit
            }
        }
    }

    fun getNumAdsAvailable(key: String): Int {
        requireValidKey(key)
        if (!isSdkInitialized()) return 0
        isAdAvailable(key)
        return synchronized(lock) {
            val pool = pools[key] ?: return@synchronized 0
            if (pool.isStarted) {
                gateway.getNumAdsAvailable(pool.sdkPreloadId)
            } else {
                0
            }
        }
    }

    fun getState(key: String): InterstitialAdLoadState? {
        requireValidKey(key)
        if (isSdkInitialized()) {
            isAdAvailable(key)
        }
        return synchronized(lock) {
            pools[key]?.let(::stateLocked)
        }
    }

    fun getConfig(key: String): InterstitialAdLoadConfig? {
        requireValidKey(key)
        return synchronized(lock) { pools[key]?.config }
    }

    fun isRegistered(key: String): Boolean {
        requireValidKey(key)
        return synchronized(lock) { pools.containsKey(key) }
    }

    fun isStarted(key: String): Boolean {
        requireValidKey(key)
        return synchronized(lock) { pools[key]?.isStarted == true }
    }

    fun registeredKeys(): Set<String> = synchronized(lock) {
        pools.keys.toSet()
    }

    /**
     * Adds a listener to a registered key.
     *
     * @return `false` when the key is not registered
     */
    @JvmOverloads
    fun addListener(
        key: String,
        listener: InterstitialAdLoadListener,
        emitCurrentState: Boolean = true,
    ): Boolean {
        requireValidKey(key)
        val added = synchronized(lock) {
            pools[key]?.listeners?.add(listener) ?: false
        }
        if (added && emitCurrentState) {
            dispatchStateChanged(key, listener)
        }
        return added
    }

    fun removeListener(key: String, listener: InterstitialAdLoadListener): Boolean {
        requireValidKey(key)
        return synchronized(lock) {
            pools[key]?.listeners?.remove(listener) ?: false
        }
    }

    /**
     * Stops a pool and destroys only ads still queued under this manager.
     * Registered configuration and listeners are retained.
     */
    fun stop(key: String): Boolean {
        requireValidKey(key)
        val stopped = synchronized(lock) {
            pools[key]?.let { pool ->
                pool.startPending = false
                stopLocked(pool)
            } ?: false
        }
        dispatchStateChanged(key)
        return stopped
    }

    /**
     * Stops and removes a key. Ads already returned by poll/await remain the
     * caller's responsibility.
     */
    fun unregister(key: String): Boolean {
        requireValidKey(key)
        var cleanupFailed = false
        val event = synchronized(lock) {
            val pool = pools[key] ?: return@synchronized null
            if (pool.isStarted && !stopLocked(pool)) {
                cleanupFailed = true
                return@synchronized null
            }
            pools.remove(key)
            CallbackEvent(
                listeners = pool.listeners.toList(),
                state = stoppedState(pool),
            )
        }
        if (cleanupFailed) {
            dispatchStateChanged(key)
            return false
        }
        event ?: return false

        dispatchFinalState(key, event)
        return true
    }

    private fun dispatchFinalState(
        key: String,
        event: CallbackEvent,
    ) {
        postToMain {
            event.listeners.forEach { listener ->
                val reattachedToNewPool = synchronized(lock) {
                    pools[key]?.listeners?.contains(listener) == true
                }
                if (reattachedToNewPool) return@forEach
                runCatching { listener.onStateChanged(key, event.state) }
            }
        }
    }

    /**
     * Stops all pools owned by this manager while retaining registrations.
     *
     * This intentionally does not call `InterstitialAdPreloader.destroyAll()`,
     * which could destroy buffers owned by another manager or library.
     */
    fun stopAll() {
        val keys = synchronized(lock) {
            pools.values.forEach { pool ->
                pool.startPending = false
                if (pool.isStarted) {
                    stopLocked(pool)
                }
            }
            pools.keys.toList()
        }
        keys.forEach(::dispatchStateChanged)
    }

    fun unregisterAll() {
        val events = synchronized(lock) {
            val snapshots = linkedMapOf<String, CallbackEvent>()
            val iterator = pools.iterator()
            while (iterator.hasNext()) {
                val (_, pool) = iterator.next()
                if (!pool.isStarted || stopLocked(pool)) {
                    snapshots[pool.key] = CallbackEvent(
                        listeners = pool.listeners.toList(),
                        state = stoppedState(pool),
                    )
                    iterator.remove()
                }
            }
            snapshots
        }
        events.forEach { (key, event) ->
            dispatchFinalState(key, event)
        }
    }

    override fun close() {
        unregisterAll()
    }

    private fun createPreloadCallback(
        key: String,
        sdkPreloadId: String,
        generation: Long,
    ): PreloadCallback = object : PreloadCallback {
        override fun onAdPreloaded(
            preloadId: String,
            responseInfo: ResponseInfo,
        ) {
            val accepted = synchronized(lock) {
                val pool = currentPoolLocked(
                    key = key,
                    sdkPreloadId = sdkPreloadId,
                    generation = generation,
                ) ?: return@synchronized false

                responseInfo.responseId?.let { responseId ->
                    if (!pool.consumedBeforeCallbackResponseIds.remove(responseId)) {
                        pool.loadedAtByResponseId.putIfAbsent(
                            responseId,
                            elapsedRealtime(),
                        )
                    }
                }
                pool.lastError = null
                true
            }
            if (!accepted) return

            // Always post. The SDK explicitly disallows calling start/poll
            // from within a PreloadCallback stack.
            postToMain {
                val event = synchronized(lock) {
                    val pool = currentPoolLocked(
                        key = key,
                        sdkPreloadId = sdkPreloadId,
                        generation = generation,
                    ) ?: return@postToMain
                    CallbackEvent(
                        listeners = pool.listeners.toList(),
                        state = stateLocked(pool),
                    )
                }
                event.listeners.forEach { listener ->
                    runCatching {
                        listener.onAdPreloaded(
                            key = key,
                            availableCount = event.state.availableCount,
                            responseInfo = responseInfo,
                        )
                    }
                    runCatching { listener.onStateChanged(key, event.state) }
                }
            }
        }

        override fun onAdsExhausted(preloadId: String) {
            postCallbackEvent(key, sdkPreloadId, generation) { listener ->
                listener.onAdsExhausted(key)
            }
        }

        override fun onAdFailedToPreload(
            preloadId: String,
            error: LoadAdError,
        ) {
            val accepted = synchronized(lock) {
                val pool = currentPoolLocked(
                    key = key,
                    sdkPreloadId = sdkPreloadId,
                    generation = generation,
                ) ?: return@synchronized false
                pool.lastError = error
                true
            }
            if (!accepted) return

            postCallbackEvent(key, sdkPreloadId, generation) { listener ->
                listener.onAdFailedToPreload(key, error)
            }
        }
    }

    private fun postCallbackEvent(
        key: String,
        sdkPreloadId: String,
        generation: Long,
        notify: (InterstitialAdLoadListener) -> Unit,
    ) {
        postToMain {
            val event = synchronized(lock) {
                val pool = currentPoolLocked(
                    key = key,
                    sdkPreloadId = sdkPreloadId,
                    generation = generation,
                ) ?: return@postToMain
                CallbackEvent(
                    listeners = pool.listeners.toList(),
                    state = stateLocked(pool),
                )
            }
            event.listeners.forEach { listener ->
                runCatching { notify(listener) }
                runCatching { listener.onStateChanged(key, event.state) }
            }
        }
    }

    private fun currentPoolLocked(
        key: String,
        sdkPreloadId: String,
        generation: Long,
    ): Pool? {
        val pool = pools[key] ?: return null
        return pool.takeIf {
            it.isStarted &&
                it.sdkPreloadId == sdkPreloadId &&
                it.generation == generation
        }
    }

    private fun stateLocked(pool: Pool): InterstitialAdLoadState {
        val availableCount = if (pool.isStarted && isSdkInitialized()) {
            gateway.getNumAdsAvailable(pool.sdkPreloadId)
        } else {
            0
        }
        return InterstitialAdLoadState(
            isStarted = pool.isStarted,
            availableCount = availableCount,
            isLoading = pool.isStarted && availableCount == 0,
            lastError = pool.lastError,
        )
    }

    private fun stoppedState(pool: Pool): InterstitialAdLoadState {
        return InterstitialAdLoadState(
            isStarted = false,
            availableCount = 0,
            isLoading = false,
            lastError = pool.lastError,
        )
    }

    private fun dispatchStateChanged(
        key: String,
        onlyListener: InterstitialAdLoadListener? = null,
    ) {
        postToMain {
            val event = synchronized(lock) {
                val pool = pools[key] ?: return@postToMain
                CallbackEvent(
                    listeners = onlyListener?.let(::listOf)
                        ?: pool.listeners.toList(),
                    state = stateLocked(pool),
                )
            }
            event.listeners.forEach { listener ->
                runCatching { listener.onStateChanged(key, event.state) }
            }
        }
    }

    // Must be called while holding [lock].
    private fun stopLocked(pool: Pool): Boolean {
        if (!pool.isStarted) return false

        if (isSdkInitialized()) {
            try {
                gateway.destroy(pool.sdkPreloadId)
            } catch (_: Exception) {
                // Preserve the active state and SDK ID so a later stop,
                // unregister, or close can retry cleanup.
                return false
            }
        }
        pool.generation++
        pool.isStarted = false
        pool.startedAtElapsedMs = 0L
        pool.loadedAtByResponseId.clear()
        pool.consumedBeforeCallbackResponseIds.clear()
        return true
    }

    private fun isExpired(pool: Pool, loadedAtElapsedMs: Long): Boolean {
        if (loadedAtElapsedMs <= 0L) return false
        return elapsedRealtime() - loadedAtElapsedMs >= pool.config.maxAdAgeMs
    }

    private fun restartExpiredPoolIfCurrent(
        key: String,
        expected: PoolToken,
    ): Boolean {
        return synchronized(lock) {
            val pool = pools[key] ?: return@synchronized false
            if (!pool.isStarted ||
                pool.sdkPreloadId != expected.sdkPreloadId ||
                pool.generation != expected.generation
            ) {
                return@synchronized false
            }

            // Keep the validation, stop, and restart under one monitor so an
            // app-policy stop cannot be followed by an expiration restart.
            if (!stopLocked(pool)) {
                false
            } else {
                start(key)
            }
        }
    }

    private fun createSdkPreloadId(): String {
        val poolId = nextPoolId++
        return "$SDK_PRELOAD_ID_PREFIX${managerId.toString(36)}-${poolId.toString(36)}"
    }

    private data class Pool(
        val key: String,
        val sdkPreloadId: String,
        var config: InterstitialAdLoadConfig,
        val listeners: MutableSet<InterstitialAdLoadListener> = linkedSetOf(),
        var isStarted: Boolean = false,
        var startPending: Boolean = false,
        var generation: Long = 0L,
        var startedAtElapsedMs: Long = 0L,
        var lastError: LoadAdError? = null,
        val loadedAtByResponseId: MutableMap<String, Long> = mutableMapOf(),
        val consumedBeforeCallbackResponseIds: MutableSet<String> = mutableSetOf(),
    )

    private data class CallbackEvent(
        val listeners: List<InterstitialAdLoadListener>,
        val state: InterstitialAdLoadState,
    )

    private data class PoolToken(
        val sdkPreloadId: String,
        val generation: Long,
    )

    companion object {
        const val DEFAULT_AWAIT_TIMEOUT_MS: Long = 10_000L

        private const val SDK_PRELOAD_ID_PREFIX = "nextgen-interstitial-"
        private val nextManagerId = AtomicLong()

        private fun requireValidKey(key: String) {
            require(key.isNotBlank()) { "key must not be blank" }
        }

        private fun createMainThreadPoster(): (() -> Unit) -> Unit {
            val handler = Handler(Looper.getMainLooper())
            return { block -> handler.post(block) }
        }

        @JvmSynthetic
        internal fun createForTesting(
            gateway: InterstitialAdPreloaderGateway,
            isSdkInitialized: () -> Boolean,
            elapsedRealtime: () -> Long,
            postToMain: (() -> Unit) -> Unit,
            beforeExpirationRestart: () -> Unit = {},
            registerInitListener: (listener: () -> Unit) -> Unit = {},
        ): InterstitialAdLoadManager = InterstitialAdLoadManager(
            gateway = gateway,
            isSdkInitialized = isSdkInitialized,
            elapsedRealtime = elapsedRealtime,
            postToMain = postToMain,
            beforeExpirationRestart = beforeExpirationRestart,
            registerInitListener = registerInitListener,
        )
    }
}

internal interface InterstitialAdPreloaderGateway {
    fun start(
        preloadId: String,
        configuration: PreloadConfiguration,
        callback: PreloadCallback,
    ): Boolean

    fun pollAd(preloadId: String): InterstitialAd?

    fun peekAdResponseInfo(preloadId: String): ResponseInfo?

    fun destroy(preloadId: String): Boolean

    fun isAdAvailable(preloadId: String): Boolean

    fun getNumAdsAvailable(preloadId: String): Int
}

private object SdkInterstitialAdPreloaderGateway : InterstitialAdPreloaderGateway {
    override fun start(
        preloadId: String,
        configuration: PreloadConfiguration,
        callback: PreloadCallback,
    ): Boolean = InterstitialAdPreloader.start(preloadId, configuration, callback)

    override fun pollAd(preloadId: String): InterstitialAd? =
        InterstitialAdPreloader.pollAd(preloadId)

    override fun peekAdResponseInfo(preloadId: String): ResponseInfo? =
        InterstitialAdPreloader.peekAdResponseInfo(preloadId)

    override fun destroy(preloadId: String): Boolean =
        InterstitialAdPreloader.destroy(preloadId)

    override fun isAdAvailable(preloadId: String): Boolean =
        InterstitialAdPreloader.isAdAvailable(preloadId)

    override fun getNumAdsAvailable(preloadId: String): Int =
        InterstitialAdPreloader.getNumAdsAvailable(preloadId)
}

private fun InterstitialAd.responseIdOrNull(): String? =
    runCatching { getResponseInfo().responseId }.getOrNull()
