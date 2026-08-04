package com.soosu.nextgen.admobnative

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdPreloader
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.PreloadCallback
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Configuration for one independently managed banner ad preload pool.
 *
 * Pass a fully built [BannerAdRequest] so the ad size, keywords, collapsible
 * options, and mediation extras are preserved. The size is part of the request,
 * so a pool serves exactly one (ad unit, size) combination.
 *
 * @param request request used whenever this pool needs another ad
 * @param bufferSize maximum number of ready ads for this pool. `null` delegates
 * sizing to the SDK, whose current default is two. The library default is one
 * because a banner slot consumes a single ad at a time, and every buffered ad
 * that is never shown is still a billed ad request.
 * @param maxAdAgeMs maximum queued-ad age this manager will return. Expiration
 * is checked when the pool is accessed; ads already handed to a caller are not
 * managed by this value.
 * @param shouldSuppressAds returns true when this pool must not request ads,
 * such as after a premium purchase.
 */
data class BannerAdLoadConfig @JvmOverloads constructor(
    val request: BannerAdRequest,
    val bufferSize: Int? = DEFAULT_BUFFER_SIZE,
    val maxAdAgeMs: Long = DEFAULT_MAX_AD_AGE_MS,
    val shouldSuppressAds: () -> Boolean = { false },
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
 * Snapshot of a registered banner ad pool.
 *
 * [isLoading] means that the SDK preloader is active but currently has no
 * pollable ad. A preload failure is non-terminal because the SDK keeps retrying.
 */
data class BannerAdLoadState(
    val isStarted: Boolean,
    val availableCount: Int,
    val isLoading: Boolean,
    val lastError: LoadAdError?,
)

/**
 * Listener for a single key registered in [BannerAdLoadManager].
 *
 * Listener methods are posted to the main thread after the SDK's
 * [PreloadCallback] has returned, so it is safe to update UI state or call back
 * into [BannerAdLoadManager].
 */
interface BannerAdLoadListener {
    fun onStateChanged(key: String, state: BannerAdLoadState) = Unit

    fun onAdPreloaded(
        key: String,
        availableCount: Int,
        responseInfo: ResponseInfo,
    ) = Unit

    fun onAdsExhausted(key: String) = Unit

    fun onAdFailedToPreload(key: String, error: LoadAdError) = Unit
}

/**
 * Java-friendly no-op adapter for [BannerAdLoadListener].
 *
 * Kotlin callers can implement [BannerAdLoadListener] directly. Java callers
 * can extend this class and override only the callbacks they need.
 */
abstract class BannerAdLoadListenerAdapter : BannerAdLoadListener {
    override fun onStateChanged(key: String, state: BannerAdLoadState) = Unit

    override fun onAdPreloaded(
        key: String,
        availableCount: Int,
        responseInfo: ResponseInfo,
    ) = Unit

    override fun onAdsExhausted(key: String) = Unit

    override fun onAdFailedToPreload(key: String, error: LoadAdError) = Unit
}

/**
 * Application-scoped manager for Next-Gen SDK banner ad preload pools.
 *
 * Loading mechanics live here; app policy does not. Consent, paid-user,
 * connectivity, and Remote Config checks should decide when the app calls
 * [start], [stop], or [unregister].
 *
 * Each key owns an independent SDK buffer. Calling [pollAd] transfers ownership
 * of the returned [BannerAd] to the caller, which then attaches it with
 * `getView(activity)` and must eventually call `destroy()` on it. Stopping this
 * manager only destroys ads that are still queued in the SDK.
 *
 * Do not tie a pool's lifetime to a screen. Destroying a pool on every screen
 * exit throws away buffered ads that were already requested and re-requests the
 * whole buffer on the next entry. Keep pools at application or ad-controller
 * scope and only destroy the individual [BannerAd] instances that a screen owns.
 */
class BannerAdLoadManager private constructor(
    private val gateway: BannerAdPreloaderGateway,
    private val isSdkInitialized: () -> Boolean,
    private val elapsedRealtime: () -> Long,
    private val postToMain: (() -> Unit) -> Unit,
    private val registerInitListener: (listener: () -> Unit) -> Unit,
) : AutoCloseable {

    constructor() : this(
        gateway = SdkBannerAdPreloaderGateway,
        isSdkInitialized = { MobileAds.isInitialized },
        elapsedRealtime = SystemClock::elapsedRealtime,
        postToMain = createMainThreadPoster(),
        registerInitListener = AdmobInitializer::whenInitialized,
    )

    private val lock = Any()
    private val pools = linkedMapOf<String, Pool>()
    private val managerId = nextManagerId.getAndIncrement()
    private var nextPoolId = 0L

    // Guarded by [lock]. True while an SDK-initialization listener that will
    // resume pending starts is registered but has not fired yet.
    private var initResumeListenerRegistered = false

    /**
     * Registers or replaces a pool configuration.
     *
     * This is destructive: replacing an active key destroys its queued ads and
     * immediately starts a new generation, which costs `bufferSize` fresh ad
     * requests. It does so even when the new configuration is equivalent,
     * because [BannerAdRequest] has no usable identity. Prefer
     * [registerIfAbsent] for the common "register once per key" case and call
     * this only when the request really changed. Existing listeners remain
     * attached. Register stable keys once at application/controller scope
     * rather than from a recomposing UI function.
     */
    fun register(key: String, config: BannerAdLoadConfig) {
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
     * Convenience overload that creates [BannerAdLoadConfig] from a fully built
     * request.
     */
    @JvmOverloads
    fun register(
        key: String,
        request: BannerAdRequest,
        bufferSize: Int? = BannerAdLoadConfig.DEFAULT_BUFFER_SIZE,
        maxAdAgeMs: Long = BannerAdLoadConfig.DEFAULT_MAX_AD_AGE_MS,
    ) {
        register(
            key = key,
            config = BannerAdLoadConfig(
                request = request,
                bufferSize = bufferSize,
                maxAdAgeMs = maxAdAgeMs,
            ),
        )
    }

    /**
     * Convenience overload that builds a plain request from an ad unit ID and
     * size. Use the [BannerAdRequest] overload when keywords, collapsible
     * options, or mediation extras are needed.
     */
    @JvmOverloads
    fun register(
        key: String,
        adUnitId: String,
        adSize: AdSize,
        bufferSize: Int? = BannerAdLoadConfig.DEFAULT_BUFFER_SIZE,
        maxAdAgeMs: Long = BannerAdLoadConfig.DEFAULT_MAX_AD_AGE_MS,
    ) {
        register(
            key = key,
            config = BannerAdLoadConfig(
                request = BannerAdRequest.Builder(adUnitId, adSize).build(),
                bufferSize = bufferSize,
                maxAdAgeMs = maxAdAgeMs,
            ),
        )
    }

    /**
     * Registers [config] only when [key] has no pool yet.
     *
     * Unlike [register] this never stops a running pool and never discards
     * queued ads, so it is safe to call on every screen entry.
     *
     * @return `true` when a new pool was created
     */
    fun registerIfAbsent(key: String, config: BannerAdLoadConfig): Boolean {
        requireValidKey(key)

        val created = synchronized(lock) {
            if (pools.containsKey(key)) return@synchronized false
            pools[key] = Pool(
                key = key,
                sdkPreloadId = createSdkPreloadId(),
                config = config,
            )
            true
        }

        if (created) {
            dispatchStateChanged(key)
        }
        return created
    }

    /**
     * Convenience overload that creates [BannerAdLoadConfig] from a fully built
     * request.
     */
    @JvmOverloads
    fun registerIfAbsent(
        key: String,
        request: BannerAdRequest,
        bufferSize: Int? = BannerAdLoadConfig.DEFAULT_BUFFER_SIZE,
        maxAdAgeMs: Long = BannerAdLoadConfig.DEFAULT_MAX_AD_AGE_MS,
    ): Boolean {
        return registerIfAbsent(
            key = key,
            config = BannerAdLoadConfig(
                request = request,
                bufferSize = bufferSize,
                maxAdAgeMs = maxAdAgeMs,
            ),
        )
    }

    /**
     * Convenience overload that builds a plain request from an ad unit ID and
     * size.
     */
    @JvmOverloads
    fun registerIfAbsent(
        key: String,
        adUnitId: String,
        adSize: AdSize,
        bufferSize: Int? = BannerAdLoadConfig.DEFAULT_BUFFER_SIZE,
        maxAdAgeMs: Long = BannerAdLoadConfig.DEFAULT_MAX_AD_AGE_MS,
    ): Boolean {
        return registerIfAbsent(
            key = key,
            config = BannerAdLoadConfig(
                request = BannerAdRequest.Builder(adUnitId, adSize).build(),
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
        val suppressed = synchronized(lock) {
            pools[key]?.config?.shouldSuppressAds?.invoke() == true
        }
        if (suppressed) {
            stop(key)
            return false
        }
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
     * Restarts a registered pool with its current configuration. This destroys
     * queued ads and costs a fresh request for each buffer slot; prefer [start]
     * for an idempotent call.
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
     * Consumes one ready [BannerAd]. Ownership transfers to the caller, which
     * must attach it with `getView(activity)` and eventually `destroy()` it.
     */
    fun pollAd(key: String): BannerAd? {
        requireValidKey(key)
        if (!isSdkInitialized()) return null

        var adForCaller: BannerAd? = null
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
                    pool.forgetPendingResponseId(responseId)
                }

                // An unknown load time is not treated as expired. The pool
                // generation timestamp is only a lower bound, so using it here
                // would discard ads that had just been refilled.
                if (!isExpired(pool, loadedAt)) {
                    adForCaller = ad
                    break
                }

                // Only the expired ad is dropped. The SDK refills one ad per
                // consumed ad, so the pool recovers without a restart.
                ad.destroy()
            }
        }

        if (poolChanged) {
            dispatchStateChanged(key)
        }
        return adForCaller
    }

    /**
     * Drops queued ads that are older than the pool's `maxAdAgeMs`.
     *
     * Expiration cleanup is never performed by the query APIs, so call this
     * explicitly when an app wants stale inventory dropped without consuming
     * an ad. The SDK refills one ad per dropped ad.
     *
     * @return the number of ads that were destroyed
     */
    fun pruneExpired(key: String): Int {
        requireValidKey(key)
        if (!isSdkInitialized()) return 0

        var destroyed = 0
        synchronized(lock) {
            val pool = pools[key] ?: return@synchronized
            if (!pool.isStarted) return@synchronized

            var remaining = gateway.getNumAdsAvailable(pool.sdkPreloadId)
            while (remaining-- > 0) {
                val loadedAt = gateway.peekAdResponseInfo(pool.sdkPreloadId)
                    ?.responseId
                    ?.let(pool.loadedAtByResponseId::get)
                if (!isExpired(pool, loadedAt)) break

                val ad = gateway.pollAd(pool.sdkPreloadId) ?: break
                ad.responseIdOrNull()?.let { responseId ->
                    if (pool.loadedAtByResponseId.remove(responseId) == null) {
                        pool.forgetPendingResponseId(responseId)
                    }
                }
                ad.destroy()
                destroyed++
            }
        }

        if (destroyed > 0) {
            dispatchStateChanged(key)
        }
        return destroyed
    }

    /**
     * Waits until an ad is ready in an already-started pool and consumes it.
     * Returns `null` on timeout or when the pool is stopped.
     *
     * A start that was deferred until SDK initialization still counts as
     * started, so a caller that races initialization keeps waiting instead of
     * treating a healthy pool as a load failure.
     *
     * This method never starts a stopped pool. App policy must explicitly call
     * [start] after consent, paid-user, and Remote Config checks.
     */
    suspend fun awaitAd(
        key: String,
        timeoutMs: Long = DEFAULT_AWAIT_TIMEOUT_MS,
    ): BannerAd? {
        requireValidKey(key)
        require(timeoutMs > 0) { "timeoutMs must be greater than 0" }

        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                lateinit var listener: BannerAdLoadListener
                val completed = AtomicBoolean(false)

                fun finish(ad: BannerAd?) {
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
                    if (!continuation.isActive || !completed.compareAndSet(false, true)) return
                    val ad = pollAd(key)
                    if (ad == null) {
                        completed.set(false)
                        return
                    }
                    if (!continuation.isActive) {
                        ad.destroy()
                        removeListener(key, listener)
                        return
                    }
                    removeListener(key, listener)
                    continuation.resume(
                        value = ad,
                        onCancellation = { _, cancelledAd, _ -> cancelledAd?.destroy() },
                    )
                }

                listener = object : BannerAdLoadListener {
                    override fun onStateChanged(
                        key: String,
                        state: BannerAdLoadState,
                    ) {
                        if (state.isStarted) {
                            // The pool may have become active with inventory
                            // that predates this listener.
                            pollIfReady()
                        } else if (!isStartPending(key)) {
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

                if (!isStarted(key) && !isStartPending(key)) {
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
     * Reports whether the next ad in the queue is usable.
     *
     * This is a pure query: it never polls, destroys, or restarts a pool.
     * Use [pruneExpired] to drop stale inventory.
     */
    fun isAdAvailable(key: String): Boolean {
        requireValidKey(key)
        if (!isSdkInitialized()) return false

        return synchronized(lock) {
            val pool = pools[key] ?: return@synchronized false
            if (!pool.isStarted || !gateway.isAdAvailable(pool.sdkPreloadId)) {
                return@synchronized false
            }

            val loadedAt = gateway.peekAdResponseInfo(pool.sdkPreloadId)
                ?.responseId
                ?.let(pool.loadedAtByResponseId::get)
            !isExpired(pool, loadedAt)
        }
    }

    /**
     * Returns the queued ad count reported by the SDK. Pure query.
     */
    fun getNumAdsAvailable(key: String): Int {
        requireValidKey(key)
        if (!isSdkInitialized()) return 0
        return synchronized(lock) {
            val pool = pools[key] ?: return@synchronized 0
            if (pool.isStarted) {
                gateway.getNumAdsAvailable(pool.sdkPreloadId)
            } else {
                0
            }
        }
    }

    /**
     * Returns a snapshot of the pool. Pure query.
     */
    fun getState(key: String): BannerAdLoadState? {
        requireValidKey(key)
        return synchronized(lock) {
            pools[key]?.let(::stateLocked)
        }
    }

    fun getConfig(key: String): BannerAdLoadConfig? {
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

    /**
     * Reports whether [start] was called before SDK initialization and is still
     * waiting to be replayed.
     */
    fun isStartPending(key: String): Boolean {
        requireValidKey(key)
        return synchronized(lock) { pools[key]?.startPending == true }
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
        listener: BannerAdLoadListener,
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

    fun removeListener(key: String, listener: BannerAdLoadListener): Boolean {
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
     * Stops and removes a key. Ads already returned by [pollAd] or [awaitAd]
     * remain the caller's responsibility.
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
     * This intentionally does not call `BannerAdPreloader.destroyAll()`, which
     * could destroy buffers owned by another manager or library.
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
        notify: (BannerAdLoadListener) -> Unit,
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

    private fun stateLocked(pool: Pool): BannerAdLoadState {
        val availableCount = if (pool.isStarted && isSdkInitialized()) {
            gateway.getNumAdsAvailable(pool.sdkPreloadId)
        } else {
            0
        }
        return BannerAdLoadState(
            isStarted = pool.isStarted,
            availableCount = availableCount,
            isLoading = pool.isStarted && availableCount == 0,
            lastError = pool.lastError,
        )
    }

    private fun stoppedState(pool: Pool): BannerAdLoadState {
        return BannerAdLoadState(
            isStarted = false,
            availableCount = 0,
            isLoading = false,
            lastError = pool.lastError,
        )
    }

    private fun dispatchStateChanged(
        key: String,
        onlyListener: BannerAdLoadListener? = null,
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

    /**
     * A `null` [loadedAtElapsedMs] means the load time is unknown, which is
     * never treated as expired.
     */
    private fun isExpired(pool: Pool, loadedAtElapsedMs: Long?): Boolean {
        if (loadedAtElapsedMs == null || loadedAtElapsedMs <= 0L) return false
        return elapsedRealtime() - loadedAtElapsedMs >= pool.config.maxAdAgeMs
    }

    private fun createSdkPreloadId(): String {
        val poolId = nextPoolId++
        return "$SDK_PRELOAD_ID_PREFIX${managerId.toString(36)}-${poolId.toString(36)}"
    }

    private data class Pool(
        val key: String,
        val sdkPreloadId: String,
        var config: BannerAdLoadConfig,
        val listeners: MutableSet<BannerAdLoadListener> = linkedSetOf(),
        var isStarted: Boolean = false,
        var startPending: Boolean = false,
        var generation: Long = 0L,
        var startedAtElapsedMs: Long = 0L,
        var lastError: LoadAdError? = null,
        val loadedAtByResponseId: MutableMap<String, Long> = mutableMapOf(),
        val consumedBeforeCallbackResponseIds: MutableSet<String> =
            object : LinkedHashSet<String>() {
                override fun add(element: String): Boolean {
                    val added = super.add(element)
                    // A preload callback that never arrives would otherwise
                    // retain its response id for the process lifetime.
                    while (size > MAX_PENDING_RESPONSE_IDS) {
                        val oldest = iterator()
                        oldest.next()
                        oldest.remove()
                    }
                    return added
                }
            },
    ) {
        fun forgetPendingResponseId(responseId: String) {
            consumedBeforeCallbackResponseIds.add(responseId)
        }
    }

    private data class CallbackEvent(
        val listeners: List<BannerAdLoadListener>,
        val state: BannerAdLoadState,
    )

    companion object {
        const val DEFAULT_AWAIT_TIMEOUT_MS: Long = 10_000L

        private const val SDK_PRELOAD_ID_PREFIX = "nextgen-banner-"
        private const val MAX_PENDING_RESPONSE_IDS = 64
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
            gateway: BannerAdPreloaderGateway,
            isSdkInitialized: () -> Boolean,
            elapsedRealtime: () -> Long,
            postToMain: (() -> Unit) -> Unit,
            registerInitListener: (listener: () -> Unit) -> Unit = {},
        ): BannerAdLoadManager = BannerAdLoadManager(
            gateway = gateway,
            isSdkInitialized = isSdkInitialized,
            elapsedRealtime = elapsedRealtime,
            postToMain = postToMain,
            registerInitListener = registerInitListener,
        )
    }
}

internal interface BannerAdPreloaderGateway {
    fun start(
        preloadId: String,
        configuration: PreloadConfiguration,
        callback: PreloadCallback,
    ): Boolean

    fun pollAd(preloadId: String): BannerAd?

    fun peekAdResponseInfo(preloadId: String): ResponseInfo?

    fun destroy(preloadId: String): Boolean

    fun isAdAvailable(preloadId: String): Boolean

    fun getNumAdsAvailable(preloadId: String): Int
}

private object SdkBannerAdPreloaderGateway : BannerAdPreloaderGateway {
    override fun start(
        preloadId: String,
        configuration: PreloadConfiguration,
        callback: PreloadCallback,
    ): Boolean = BannerAdPreloader.start(preloadId, configuration, callback)

    override fun pollAd(preloadId: String): BannerAd? =
        BannerAdPreloader.pollAd(preloadId)

    override fun peekAdResponseInfo(preloadId: String): ResponseInfo? =
        BannerAdPreloader.peekAdResponseInfo(preloadId)

    override fun destroy(preloadId: String): Boolean =
        BannerAdPreloader.destroy(preloadId)

    override fun isAdAvailable(preloadId: String): Boolean =
        BannerAdPreloader.isAdAvailable(preloadId)

    override fun getNumAdsAvailable(preloadId: String): Int =
        BannerAdPreloader.getNumAdsAvailable(preloadId)
}

private fun BannerAd.responseIdOrNull(): String? =
    runCatching { getResponseInfo().responseId }.getOrNull()
