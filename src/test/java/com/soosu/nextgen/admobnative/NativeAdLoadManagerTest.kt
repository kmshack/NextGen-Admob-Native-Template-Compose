package com.soosu.nextgen.admobnative

import android.os.Bundle
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.PreloadCallback
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo
import com.google.android.libraries.ads.mobile.sdk.nativead.CustomNativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoadResult
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import java.lang.reflect.Proxy
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAdLoadManagerTest {

    @Test
    fun `native preload buffer defaults to one per key`() {
        val gateway = FakeNativeAdPreloaderGateway()
        val manager = createManager(gateway)
        val request = nativeRequest(TEST_AD_UNIT_ID)

        manager.register("feed", request)

        assertTrue(manager.start("feed"))
        assertEquals(1, gateway.entries.single().configuration.bufferSize)
        assertSame(request, gateway.entries.single().configuration.request)
    }

    @Test
    fun `invalid configuration is rejected before calling the SDK`() {
        val request = nativeRequest(TEST_AD_UNIT_ID)

        assertThrows(IllegalArgumentException::class.java) {
            NativeAdLoadConfig(request, bufferSize = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeAdLoadConfig(request, maxAdAgeMs = 0)
        }
    }

    @Test
    fun `start is idempotent and a configuration replacement restarts the pool`() {
        val gateway = FakeNativeAdPreloaderGateway()
        val manager = createManager(gateway)
        val firstRequest = nativeRequest(TEST_AD_UNIT_ID)
        val secondRequest = nativeRequest(SECOND_TEST_AD_UNIT_ID)

        manager.register("home", firstRequest)
        assertTrue(manager.start("home"))
        assertTrue(manager.start("home"))
        assertEquals(1, gateway.startCalls)

        val firstEntry = gateway.entries.single()
        manager.register("home", secondRequest, bufferSize = 2)

        assertEquals(2, gateway.startCalls)
        assertEquals(listOf(firstEntry.preloadId), gateway.destroyedIds)
        assertSame(secondRequest, gateway.entries.single().configuration.request)
        assertEquals(2, gateway.entries.single().configuration.bufferSize)
    }

    @Test
    fun `stale callbacks are ignored after an active pool is replaced`() {
        val gateway = FakeNativeAdPreloaderGateway()
        val manager = createManager(gateway)
        val exhaustedEvents = AtomicInteger()
        val listener = object : NativeAdLoadListener {
            override fun onAdsExhausted(key: String) {
                exhaustedEvents.incrementAndGet()
            }
        }

        manager.register("home", nativeRequest(TEST_AD_UNIT_ID))
        manager.addListener("home", listener, emitCurrentState = false)
        manager.start("home")
        val staleCallback = gateway.entries.single().callback

        manager.register("home", nativeRequest(SECOND_TEST_AD_UNIT_ID))
        staleCallback.onAdsExhausted("ignored-sdk-id")

        assertEquals(0, exhaustedEvents.get())
    }

    @Test
    fun `manager instances use isolated SDK preload ids`() {
        val gateway = FakeNativeAdPreloaderGateway()
        val first = createManager(gateway)
        val second = createManager(gateway)

        first.register("feed", nativeRequest(TEST_AD_UNIT_ID))
        second.register("feed", nativeRequest(TEST_AD_UNIT_ID))
        first.start("feed")
        second.start("feed")

        val preloadIds = gateway.entries.map { it.preloadId }
        assertEquals(2, preloadIds.size)
        assertNotEquals(preloadIds[0], preloadIds[1])

        first.stopAll()
        assertEquals(listOf(preloadIds[0]), gateway.destroyedIds)
        assertTrue(second.isStarted("feed"))
    }

    @Test
    fun `polled native ad ownership is transferred to caller`() {
        val gateway = FakeNativeAdPreloaderGateway()
        val manager = createManager(gateway)
        val destroyCalls = AtomicInteger()
        val nativeAd = fakeNativeAd(destroyCalls)

        manager.register("article", nativeRequest(TEST_AD_UNIT_ID))
        manager.start("article")
        gateway.entries.single().results.add(
            NativeAdLoadResult.NativeAdSuccess(nativeAd),
        )

        assertSame(nativeAd, manager.pollNativeAd("article"))
        manager.stop("article")
        assertEquals(0, destroyCalls.get())

        nativeAd.destroy()
        assertEquals(1, destroyCalls.get())
    }

    @Test
    fun `custom native and banner results are preserved by typed pollers`() {
        val gateway = FakeNativeAdPreloaderGateway()
        val manager = createManager(gateway)
        val customAd = fakeAd(CustomNativeAd::class.java, AtomicInteger())
        val bannerAd = fakeAd(BannerAd::class.java, AtomicInteger())

        manager.register("mixed", nativeRequest(TEST_AD_UNIT_ID))
        manager.start("mixed")
        gateway.entries.single().results.apply {
            add(NativeAdLoadResult.CustomNativeAdSuccess(customAd))
            add(NativeAdLoadResult.BannerAdSuccess(bannerAd))
        }

        assertSame(customAd, manager.pollCustomNativeAd("mixed"))
        assertSame(bannerAd, manager.pollBannerAd("mixed"))
    }

    @Test
    fun `typed poller destroys a consumed result of another ad type`() {
        val gateway = FakeNativeAdPreloaderGateway()
        val manager = createManager(gateway)
        val destroyCalls = AtomicInteger()
        val customAd = fakeAd(CustomNativeAd::class.java, destroyCalls)

        manager.register("mixed", nativeRequest(TEST_AD_UNIT_ID))
        manager.start("mixed")
        gateway.entries.single().results.add(
            NativeAdLoadResult.CustomNativeAdSuccess(customAd),
        )

        assertNull(manager.pollNativeAd("mixed"))
        assertEquals(1, destroyCalls.get())
    }

    @Test
    fun `await consumes an ad that is already available`() = runBlocking {
        val gateway = FakeNativeAdPreloaderGateway()
        val manager = createManager(gateway)
        val nativeAd = fakeNativeAd(AtomicInteger())

        manager.register("article", nativeRequest(TEST_AD_UNIT_ID))
        manager.start("article")
        gateway.entries.single().results.add(
            NativeAdLoadResult.NativeAdSuccess(nativeAd),
        )

        assertSame(
            nativeAd,
            manager.awaitNativeAd("article", timeoutMs = 1_000L),
        )
    }

    @Test
    fun `await never starts a pool that app policy left stopped`() = runBlocking {
        val gateway = FakeNativeAdPreloaderGateway()
        val manager = createManager(gateway)

        manager.register("premium-gated", nativeRequest(TEST_AD_UNIT_ID))

        assertNull(
            manager.awaitNativeAd("premium-gated", timeoutMs = 1_000L),
        )
        assertEquals(0, gateway.startCalls)
        assertFalse(manager.isStarted("premium-gated"))
    }

    @Test
    fun `unregister immediately completes an active await`() = runBlocking {
        val gateway = FakeNativeAdPreloaderGateway()
        val manager = createManager(gateway)

        manager.register("article", nativeRequest(TEST_AD_UNIT_ID))
        manager.start("article")
        val awaiting = async(start = CoroutineStart.UNDISPATCHED) {
            manager.awaitNativeAd("article", timeoutMs = Long.MAX_VALUE)
        }

        assertFalse(awaiting.isCompleted)
        assertTrue(manager.unregister("article"))
        assertNull(awaiting.await())
    }

    @Test
    fun `SDK start exception rolls back active state and can be retried`() {
        val gateway = FakeNativeAdPreloaderGateway().apply {
            throwOnStart = true
        }
        val manager = createManager(gateway)

        manager.register("feed", nativeRequest(TEST_AD_UNIT_ID))

        assertFalse(manager.start("feed"))
        assertFalse(manager.isStarted("feed"))

        gateway.throwOnStart = false
        assertTrue(manager.start("feed"))
        assertTrue(manager.isStarted("feed"))
    }

    @Test
    fun `SDK destroy exception preserves cleanup handle for retry`() {
        val gateway = FakeNativeAdPreloaderGateway()
        val manager = createManager(gateway)

        manager.register("feed", nativeRequest(TEST_AD_UNIT_ID))
        manager.start("feed")
        gateway.throwOnDestroy = true

        assertFalse(manager.stop("feed"))
        assertTrue(manager.isStarted("feed"))
        assertTrue(manager.isRegistered("feed"))
        assertFalse(manager.unregister("feed"))
        assertTrue(manager.isRegistered("feed"))

        gateway.throwOnDestroy = false
        assertTrue(manager.unregister("feed"))
        assertFalse(manager.isRegistered("feed"))
        assertEquals(1, gateway.destroyedIds.size)
    }

    @Test
    fun `an untracked expired generation is discarded and restarted`() {
        val gateway = FakeNativeAdPreloaderGateway()
        var now = 1L
        val manager = NativeAdLoadManager.createForTesting(
            gateway = gateway,
            isSdkInitialized = { true },
            elapsedRealtime = { now },
            postToMain = { it() },
        )
        val destroyCalls = AtomicInteger()

        manager.register(
            key = "short-cache",
            request = nativeRequest(TEST_AD_UNIT_ID),
            maxAdAgeMs = 10L,
        )
        manager.start("short-cache")
        gateway.entries.single().results.add(
            NativeAdLoadResult.NativeAdSuccess(fakeNativeAd(destroyCalls)),
        )
        now = 20L

        assertNull(manager.pollNativeAd("short-cache"))
        assertEquals(1, destroyCalls.get())
        assertEquals(2, gateway.startCalls)
        assertTrue(manager.isStarted("short-cache"))
    }

    @Test
    fun `app policy stop wins over an expiration restart race`() {
        val gateway = FakeNativeAdPreloaderGateway()
        val now = AtomicLong(1L)
        val expirationDetected = CountDownLatch(1)
        val allowRestartCheck = CountDownLatch(1)
        val manager = NativeAdLoadManager.createForTesting(
            gateway = gateway,
            isSdkInitialized = { true },
            elapsedRealtime = now::get,
            postToMain = { it() },
            beforeExpirationRestart = {
                expirationDetected.countDown()
                check(allowRestartCheck.await(1, TimeUnit.SECONDS))
            },
        )
        val workerFailure = AtomicReference<Throwable?>()

        manager.register(
            key = "policy-gated",
            request = nativeRequest(TEST_AD_UNIT_ID),
            maxAdAgeMs = 10L,
        )
        manager.start("policy-gated")
        gateway.entries.single().results.add(
            NativeAdLoadResult.NativeAdSuccess(fakeNativeAd(AtomicInteger())),
        )
        now.set(20L)

        val worker = thread {
            runCatching {
                manager.pollNativeAd("policy-gated")
            }.exceptionOrNull()?.let(workerFailure::set)
        }
        assertTrue(expirationDetected.await(1, TimeUnit.SECONDS))

        manager.stop("policy-gated")
        allowRestartCheck.countDown()
        worker.join(1_000L)

        workerFailure.get()?.let { throw it }
        assertFalse(worker.isAlive)
        assertFalse(manager.isStarted("policy-gated"))
        assertEquals(1, gateway.startCalls)
    }

    @Test
    fun `final unregister state is skipped after listener reattaches to same key`() {
        val gateway = FakeNativeAdPreloaderGateway()
        val posted = ArrayDeque<() -> Unit>()
        val manager = NativeAdLoadManager.createForTesting(
            gateway = gateway,
            isSdkInitialized = { true },
            elapsedRealtime = { 1L },
            postToMain = posted::add,
        )
        val startedStates = mutableListOf<Boolean>()
        val listener = object : NativeAdLoadListener {
            override fun onStateChanged(
                key: String,
                state: NativeAdLoadState,
            ) {
                startedStates += state.isStarted
            }
        }

        manager.register("feed", nativeRequest(TEST_AD_UNIT_ID))
        manager.addListener("feed", listener, emitCurrentState = false)
        manager.start("feed")
        manager.unregister("feed")

        manager.register("feed", nativeRequest(SECOND_TEST_AD_UNIT_ID))
        manager.addListener("feed", listener, emitCurrentState = false)
        manager.start("feed")

        while (posted.isNotEmpty()) {
            posted.removeFirst().invoke()
        }
        assertTrue(startedStates.isNotEmpty())
        assertTrue(startedStates.all { it })
    }

    @Test
    fun `SDK callback events are posted instead of delivered inline`() {
        val gateway = FakeNativeAdPreloaderGateway()
        val posted = ArrayDeque<() -> Unit>()
        val manager = NativeAdLoadManager.createForTesting(
            gateway = gateway,
            isSdkInitialized = { true },
            elapsedRealtime = { 1L },
            postToMain = posted::add,
        )
        val exhaustedEvents = AtomicInteger()
        val listener = object : NativeAdLoadListener {
            override fun onAdsExhausted(key: String) {
                exhaustedEvents.incrementAndGet()
            }
        }

        manager.register("feed", nativeRequest(TEST_AD_UNIT_ID))
        manager.addListener("feed", listener, emitCurrentState = false)
        manager.start("feed")
        gateway.entries.single().callback.onAdsExhausted("sdk-id")

        assertEquals(0, exhaustedEvents.get())
        while (posted.isNotEmpty()) {
            posted.removeFirst().invoke()
        }
        assertEquals(1, exhaustedEvents.get())
    }

    @Test
    fun `failure state is exposed and cleared by the next preload success`() {
        val gateway = FakeNativeAdPreloaderGateway()
        val manager = createManager(gateway)
        val responseInfo = fakeResponseInfo("response-1")
        val error = LoadAdError(
            LoadAdError.ErrorCode.NO_FILL,
            "Synthetic no fill",
            responseInfo,
        )

        manager.register("feed", nativeRequest(TEST_AD_UNIT_ID))
        manager.start("feed")
        gateway.entries.single().callback.onAdFailedToPreload("sdk-id", error)
        assertSame(error, manager.getState("feed")?.lastError)

        gateway.entries.single().callback.onAdPreloaded("sdk-id", responseInfo)
        assertNull(manager.getState("feed")?.lastError)
    }

    @Test
    fun `cancelled await removes its consumer and leaves later ad available`() =
        runBlocking {
            val gateway = FakeNativeAdPreloaderGateway()
            val manager = createManager(gateway)
            val responseInfo = fakeResponseInfo("response-after-cancel")
            val nativeAd = fakeNativeAd(AtomicInteger(), responseInfo)

            manager.register("feed", nativeRequest(TEST_AD_UNIT_ID))
            manager.start("feed")
            val awaiting = async(start = CoroutineStart.UNDISPATCHED) {
                manager.awaitNativeAd("feed", timeoutMs = 60_000L)
            }
            awaiting.cancelAndJoin()

            gateway.entries.single().results.add(
                NativeAdLoadResult.NativeAdSuccess(nativeAd),
            )
            gateway.entries.single().callback.onAdPreloaded(
                "sdk-id",
                responseInfo,
            )

            assertEquals(1, manager.getNumAdsAvailable("feed"))
            assertSame(nativeAd, manager.pollNativeAd("feed"))
        }

    @Test
    fun `start fails safely before SDK initialization`() {
        val gateway = FakeNativeAdPreloaderGateway()
        val manager = NativeAdLoadManager.createForTesting(
            gateway = gateway,
            isSdkInitialized = { false },
            elapsedRealtime = { 1L },
            postToMain = { it() },
        )

        manager.register("feed", nativeRequest(TEST_AD_UNIT_ID))

        assertFalse(manager.start("feed"))
        assertTrue(manager.isRegistered("feed"))
        assertFalse(manager.isStarted("feed"))
        assertEquals(0, gateway.startCalls)
        assertNull(manager.getState("missing"))
    }

    private fun createManager(
        gateway: FakeNativeAdPreloaderGateway,
    ): NativeAdLoadManager = NativeAdLoadManager.createForTesting(
        gateway = gateway,
        isSdkInitialized = { true },
        elapsedRealtime = { 1L },
        postToMain = { it() },
    )

    private fun nativeRequest(adUnitId: String): NativeAdRequest {
        return NativeAdRequest.Builder(
            adUnitId,
            listOf(NativeAd.NativeAdType.NATIVE),
        ).build()
    }

    @Suppress("UNCHECKED_CAST")
    private fun fakeNativeAd(
        destroyCalls: AtomicInteger,
        responseInfo: ResponseInfo? = null,
    ): NativeAd {
        return fakeAd(NativeAd::class.java, destroyCalls, responseInfo)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> fakeAd(
        adClass: Class<T>,
        destroyCalls: AtomicInteger,
        responseInfo: ResponseInfo? = null,
    ): T {
        return Proxy.newProxyInstance(
            adClass.classLoader,
            arrayOf(adClass),
        ) { _, method, _ ->
            when {
                method.name == "destroy" -> {
                    destroyCalls.incrementAndGet()
                    Unit
                }
                method.name == "getResponseInfo" -> responseInfo
                method.returnType == Boolean::class.javaPrimitiveType -> false
                method.returnType == Long::class.javaPrimitiveType -> 0L
                method.returnType == Int::class.javaPrimitiveType -> 0
                method.returnType == Float::class.javaPrimitiveType -> 0f
                method.returnType == Double::class.javaPrimitiveType -> 0.0
                method.returnType == Void.TYPE -> Unit
                else -> null
            }
        } as T
    }

    private fun fakeResponseInfo(responseId: String): ResponseInfo {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafeField = unsafeClass.getDeclaredField("theUnsafe").apply {
            isAccessible = true
        }
        val unsafe = unsafeField.get(null)
        val allocateInstance = unsafeClass.getMethod(
            "allocateInstance",
            Class::class.java,
        )
        val bundle = allocateInstance.invoke(unsafe, Bundle::class.java) as Bundle
        return ResponseInfo(
            null,
            responseId,
            bundle,
            null,
            emptyList(),
        )
    }

    private class FakeNativeAdPreloaderGateway : NativeAdPreloaderGateway {
        val entries = mutableListOf<Entry>()
        val destroyedIds = mutableListOf<String>()
        var startCalls = 0
        var throwOnStart = false
        var throwOnDestroy = false

        override fun start(
            preloadId: String,
            configuration: PreloadConfiguration,
            callback: PreloadCallback,
        ): Boolean {
            startCalls++
            if (throwOnStart) error("Synthetic SDK start failure")
            if (entries.any { it.preloadId == preloadId }) return false
            entries += Entry(preloadId, configuration, callback)
            return true
        }

        override fun pollAd(
            preloadId: String,
        ): NativeAdLoadResult.NativeAdLoadSuccessResult? {
            val results = entries.firstOrNull { it.preloadId == preloadId }?.results
                ?: return null
            return if (results.isEmpty()) null else results.removeFirst()
        }

        override fun peekAdResponseInfo(preloadId: String): ResponseInfo? = null

        override fun destroy(preloadId: String): Boolean {
            if (throwOnDestroy) error("Synthetic SDK destroy failure")
            val removed = entries.removeAll { it.preloadId == preloadId }
            if (removed) destroyedIds += preloadId
            return removed
        }

        override fun isAdAvailable(preloadId: String): Boolean {
            return entries
                .firstOrNull { it.preloadId == preloadId }
                ?.results
                ?.isNotEmpty() == true
        }

        override fun getNumAdsAvailable(preloadId: String): Int {
            return entries
                .firstOrNull { it.preloadId == preloadId }
                ?.results
                ?.size ?: 0
        }
    }

    private data class Entry(
        val preloadId: String,
        val configuration: PreloadConfiguration,
        val callback: PreloadCallback,
        val results: ArrayDeque<NativeAdLoadResult.NativeAdLoadSuccessResult> =
            ArrayDeque(),
    )

    private companion object {
        const val TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"
        const val SECOND_TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/1044960115"
    }
}
