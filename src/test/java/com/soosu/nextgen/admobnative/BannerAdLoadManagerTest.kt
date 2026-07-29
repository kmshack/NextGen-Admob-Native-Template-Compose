package com.soosu.nextgen.admobnative

import android.os.Bundle
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.PreloadCallback
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo
import java.lang.reflect.Proxy
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BannerAdLoadManagerTest {

    @Test
    fun `banner preload buffer defaults to one per key`() {
        val gateway = FakeBannerAdPreloaderGateway()
        val manager = createManager(gateway)
        val request = bannerRequest(TEST_AD_UNIT_ID)

        manager.register("home-banner", request)

        assertTrue(manager.start("home-banner"))
        assertEquals(1, gateway.entries.single().configuration.bufferSize)
        assertSame(request, gateway.entries.single().configuration.request)
    }

    @Test
    fun `invalid configuration is rejected before calling the SDK`() {
        val request = bannerRequest(TEST_AD_UNIT_ID)

        assertThrows(IllegalArgumentException::class.java) {
            BannerAdLoadConfig(request, bufferSize = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BannerAdLoadConfig(request, maxAdAgeMs = 0)
        }
    }

    @Test
    fun `ad unit and size overload builds a request for that size`() {
        val gateway = FakeBannerAdPreloaderGateway()
        val manager = createManager(gateway)

        manager.register(
            key = "inline",
            adUnitId = TEST_AD_UNIT_ID,
            adSize = AdSize.MEDIUM_RECTANGLE,
        )
        manager.start("inline")

        val request = gateway.entries.single().configuration.request as BannerAdRequest
        assertEquals(AdSize.MEDIUM_RECTANGLE, request.adSize)
    }

    @Test
    fun `start is idempotent and a configuration replacement restarts the pool`() {
        val gateway = FakeBannerAdPreloaderGateway()
        val manager = createManager(gateway)
        val firstRequest = bannerRequest(TEST_AD_UNIT_ID)
        val secondRequest = bannerRequest(SECOND_TEST_AD_UNIT_ID)

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
    fun `registerIfAbsent leaves a running pool untouched`() {
        val gateway = FakeBannerAdPreloaderGateway()
        val manager = createManager(gateway)

        assertTrue(manager.registerIfAbsent("home", bannerRequest(TEST_AD_UNIT_ID)))
        manager.start("home")
        gateway.entries.single().ads.add(fakeBannerAd(AtomicInteger()))

        assertFalse(
            manager.registerIfAbsent("home", bannerRequest(SECOND_TEST_AD_UNIT_ID)),
        )

        assertEquals(1, gateway.startCalls)
        assertTrue(gateway.destroyedIds.isEmpty())
        assertEquals(1, gateway.entries.single().ads.size)
    }

    @Test
    fun `manager instances use isolated SDK preload ids`() {
        val gateway = FakeBannerAdPreloaderGateway()
        val first = createManager(gateway)
        val second = createManager(gateway)

        first.register("home", bannerRequest(TEST_AD_UNIT_ID))
        second.register("home", bannerRequest(TEST_AD_UNIT_ID))
        first.start("home")
        second.start("home")

        val preloadIds = gateway.entries.map { it.preloadId }
        assertEquals(2, preloadIds.size)
        assertNotEquals(preloadIds[0], preloadIds[1])

        first.stopAll()
        assertEquals(listOf(preloadIds[0]), gateway.destroyedIds)
        assertTrue(second.isStarted("home"))
    }

    @Test
    fun `polled banner ad ownership is transferred to caller`() {
        val gateway = FakeBannerAdPreloaderGateway()
        val manager = createManager(gateway)
        val destroyCalls = AtomicInteger()
        val bannerAd = fakeBannerAd(destroyCalls)

        manager.register("home", bannerRequest(TEST_AD_UNIT_ID))
        manager.start("home")
        gateway.entries.single().ads.add(bannerAd)

        assertSame(bannerAd, manager.pollAd("home"))
        manager.stop("home")
        assertEquals(0, destroyCalls.get())

        bannerAd.destroy()
        assertEquals(1, destroyCalls.get())
    }

    @Test
    fun `await consumes an ad that is already available`() = runBlocking {
        val gateway = FakeBannerAdPreloaderGateway()
        val manager = createManager(gateway)
        val bannerAd = fakeBannerAd(AtomicInteger())

        manager.register("home", bannerRequest(TEST_AD_UNIT_ID))
        manager.start("home")
        gateway.entries.single().ads.add(bannerAd)

        assertSame(bannerAd, manager.awaitAd("home", timeoutMs = 1_000L))
    }

    @Test
    fun `await never starts a pool that app policy left stopped`() = runBlocking {
        val gateway = FakeBannerAdPreloaderGateway()
        val manager = createManager(gateway)

        manager.register("premium-gated", bannerRequest(TEST_AD_UNIT_ID))

        assertNull(manager.awaitAd("premium-gated", timeoutMs = 1_000L))
        assertEquals(0, gateway.startCalls)
        assertFalse(manager.isStarted("premium-gated"))
    }

    @Test
    fun `await waits for a start that was deferred until SDK initialization`() =
        runBlocking {
            val gateway = FakeBannerAdPreloaderGateway()
            val initListeners = mutableListOf<() -> Unit>()
            var initialized = false
            val manager = BannerAdLoadManager.createForTesting(
                gateway = gateway,
                isSdkInitialized = { initialized },
                elapsedRealtime = { 1L },
                postToMain = { it() },
                registerInitListener = { initListeners.add(it) },
            )

            manager.register("home", bannerRequest(TEST_AD_UNIT_ID))
            assertFalse(manager.start("home"))
            assertTrue(manager.isStartPending("home"))

            val awaiting = async(start = CoroutineStart.UNDISPATCHED) {
                manager.awaitAd("home", timeoutMs = Long.MAX_VALUE)
            }
            assertFalse(awaiting.isCompleted)

            initialized = true
            initListeners.single().invoke()

            val entry = gateway.entries.single()
            val responseInfo = fakeResponseInfo("late-response")
            entry.ads.add(fakeBannerAd(AtomicInteger(), responseInfo))
            entry.callback.onAdPreloaded(entry.preloadId, responseInfo)

            assertNotNull(awaiting.await())
        }

    @Test
    fun `unregister immediately completes an active await`() = runBlocking {
        val gateway = FakeBannerAdPreloaderGateway()
        val manager = createManager(gateway)

        manager.register("home", bannerRequest(TEST_AD_UNIT_ID))
        manager.start("home")
        val awaiting = async(start = CoroutineStart.UNDISPATCHED) {
            manager.awaitAd("home", timeoutMs = Long.MAX_VALUE)
        }

        assertFalse(awaiting.isCompleted)
        assertTrue(manager.unregister("home"))
        assertNull(awaiting.await())
    }

    @Test
    fun `cancelled await removes its consumer and leaves later ad available`() =
        runBlocking {
            val gateway = FakeBannerAdPreloaderGateway()
            val manager = createManager(gateway)

            manager.register("home", bannerRequest(TEST_AD_UNIT_ID))
            manager.start("home")

            val awaiting = async(start = CoroutineStart.UNDISPATCHED) {
                manager.awaitAd("home", timeoutMs = Long.MAX_VALUE)
            }
            awaiting.cancelAndJoin()

            val bannerAd = fakeBannerAd(AtomicInteger())
            gateway.entries.single().ads.add(bannerAd)
            assertSame(bannerAd, manager.pollAd("home"))
        }

    @Test
    fun `an ad with an unknown load time is served instead of discarded`() {
        val gateway = FakeBannerAdPreloaderGateway()
        var now = 1L
        val manager = BannerAdLoadManager.createForTesting(
            gateway = gateway,
            isSdkInitialized = { true },
            elapsedRealtime = { now },
            postToMain = { it() },
        )
        val destroyCalls = AtomicInteger()

        manager.register(
            key = "short-cache",
            request = bannerRequest(TEST_AD_UNIT_ID),
            maxAdAgeMs = 10L,
        )
        manager.start("short-cache")
        // No preload callback was delivered, so the load time is unknown.
        gateway.entries.single().ads.add(fakeBannerAd(destroyCalls))
        now = 20L

        assertNotNull(manager.pollAd("short-cache"))
        assertEquals(0, destroyCalls.get())
        assertEquals(1, gateway.startCalls)
    }

    @Test
    fun `a tracked expired ad is dropped without restarting the pool`() {
        val gateway = FakeBannerAdPreloaderGateway()
        var now = 1L
        val manager = BannerAdLoadManager.createForTesting(
            gateway = gateway,
            isSdkInitialized = { true },
            elapsedRealtime = { now },
            postToMain = { it() },
        )
        val destroyCalls = AtomicInteger()

        manager.register(
            key = "short-cache",
            request = bannerRequest(TEST_AD_UNIT_ID),
            maxAdAgeMs = 10L,
        )
        manager.start("short-cache")
        val entry = gateway.entries.single()
        val responseInfo = fakeResponseInfo("expired-response")
        entry.ads.add(fakeBannerAd(destroyCalls, responseInfo))
        entry.callback.onAdPreloaded(entry.preloadId, responseInfo)
        now = 20L

        assertNull(manager.pollAd("short-cache"))
        assertEquals(1, destroyCalls.get())
        // The SDK refills one ad per consumed ad, so no restart is needed.
        assertEquals(1, gateway.startCalls)
        assertTrue(manager.isStarted("short-cache"))
    }

    @Test
    fun `query APIs never poll destroy or restart a pool`() {
        val gateway = FakeBannerAdPreloaderGateway()
        var now = 1L
        val manager = BannerAdLoadManager.createForTesting(
            gateway = gateway,
            isSdkInitialized = { true },
            elapsedRealtime = { now },
            postToMain = { it() },
        )
        val destroyCalls = AtomicInteger()

        manager.register(
            key = "short-cache",
            request = bannerRequest(TEST_AD_UNIT_ID),
            maxAdAgeMs = 10L,
        )
        manager.start("short-cache")
        val entry = gateway.entries.single()
        val responseInfo = fakeResponseInfo("expired-response")
        entry.ads.add(fakeBannerAd(destroyCalls, responseInfo))
        entry.callback.onAdPreloaded(entry.preloadId, responseInfo)
        now = 20L

        assertFalse(manager.isAdAvailable("short-cache"))
        assertEquals(1, manager.getNumAdsAvailable("short-cache"))
        assertNotNull(manager.getState("short-cache"))

        assertEquals(0, destroyCalls.get())
        assertEquals(1, gateway.startCalls)
        assertTrue(gateway.destroyedIds.isEmpty())
        assertEquals(1, entry.ads.size)

        // Cleanup is opt-in.
        assertEquals(1, manager.pruneExpired("short-cache"))
        assertEquals(1, destroyCalls.get())
        assertEquals(1, gateway.startCalls)
    }

    @Test
    fun `stale callbacks are ignored after an active pool is replaced`() {
        val gateway = FakeBannerAdPreloaderGateway()
        val manager = createManager(gateway)
        val exhaustedEvents = AtomicInteger()
        val listener = object : BannerAdLoadListener {
            override fun onAdsExhausted(key: String) {
                exhaustedEvents.incrementAndGet()
            }
        }

        manager.register("home", bannerRequest(TEST_AD_UNIT_ID))
        manager.addListener("home", listener, emitCurrentState = false)
        manager.start("home")
        val staleCallback = gateway.entries.single().callback

        manager.register("home", bannerRequest(SECOND_TEST_AD_UNIT_ID))
        staleCallback.onAdsExhausted("ignored-sdk-id")

        assertEquals(0, exhaustedEvents.get())
    }

    @Test
    fun `failure state is exposed and cleared by the next preload success`() {
        val gateway = FakeBannerAdPreloaderGateway()
        val manager = createManager(gateway)

        manager.register("home", bannerRequest(TEST_AD_UNIT_ID))
        manager.start("home")
        val entry = gateway.entries.single()

        val error = fakeLoadAdError()
        entry.callback.onAdFailedToPreload(entry.preloadId, error)
        assertSame(error, manager.getState("home")?.lastError)

        val responseInfo = fakeResponseInfo("recovered")
        entry.ads.add(fakeBannerAd(AtomicInteger(), responseInfo))
        entry.callback.onAdPreloaded(entry.preloadId, responseInfo)
        assertNull(manager.getState("home")?.lastError)
    }

    @Test
    fun `SDK start exception rolls back active state and can be retried`() {
        val gateway = FakeBannerAdPreloaderGateway().apply { throwOnStart = true }
        val manager = createManager(gateway)

        manager.register("home", bannerRequest(TEST_AD_UNIT_ID))

        assertFalse(manager.start("home"))
        assertFalse(manager.isStarted("home"))

        gateway.throwOnStart = false
        assertTrue(manager.start("home"))
        assertTrue(manager.isStarted("home"))
    }

    @Test
    fun `SDK destroy exception preserves cleanup handle for retry`() {
        val gateway = FakeBannerAdPreloaderGateway()
        val manager = createManager(gateway)

        manager.register("home", bannerRequest(TEST_AD_UNIT_ID))
        manager.start("home")
        gateway.throwOnDestroy = true

        assertFalse(manager.stop("home"))
        assertTrue(manager.isStarted("home"))
        assertFalse(manager.unregister("home"))
        assertTrue(manager.isRegistered("home"))

        gateway.throwOnDestroy = false
        assertTrue(manager.unregister("home"))
        assertFalse(manager.isRegistered("home"))
        assertEquals(1, gateway.destroyedIds.size)
    }

    @Test
    fun `stop before initialization cancels the pending start`() {
        val gateway = FakeBannerAdPreloaderGateway()
        val initListeners = mutableListOf<() -> Unit>()
        var initialized = false
        val manager = BannerAdLoadManager.createForTesting(
            gateway = gateway,
            isSdkInitialized = { initialized },
            elapsedRealtime = { 1L },
            postToMain = { it() },
            registerInitListener = { initListeners.add(it) },
        )

        manager.register("home", bannerRequest(TEST_AD_UNIT_ID))
        assertFalse(manager.start("home"))
        assertTrue(manager.isStartPending("home"))

        manager.stop("home")
        assertFalse(manager.isStartPending("home"))

        initialized = true
        initListeners.single().invoke()

        assertEquals(0, gateway.startCalls)
        assertFalse(manager.isStarted("home"))
    }

    private fun createManager(
        gateway: FakeBannerAdPreloaderGateway,
    ): BannerAdLoadManager = BannerAdLoadManager.createForTesting(
        gateway = gateway,
        isSdkInitialized = { true },
        elapsedRealtime = { 1L },
        postToMain = { it() },
    )

    private fun bannerRequest(
        adUnitId: String,
        adSize: AdSize = AdSize.BANNER,
    ): BannerAdRequest = BannerAdRequest.Builder(adUnitId, adSize).build()

    @Suppress("UNCHECKED_CAST")
    private fun fakeBannerAd(
        destroyCalls: AtomicInteger,
        responseInfo: ResponseInfo? = null,
    ): BannerAd {
        return Proxy.newProxyInstance(
            BannerAd::class.java.classLoader,
            arrayOf(BannerAd::class.java),
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
        } as BannerAd
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

    private fun fakeLoadAdError(): LoadAdError {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafeField = unsafeClass.getDeclaredField("theUnsafe").apply {
            isAccessible = true
        }
        val unsafe = unsafeField.get(null)
        val allocateInstance = unsafeClass.getMethod(
            "allocateInstance",
            Class::class.java,
        )
        return allocateInstance.invoke(unsafe, LoadAdError::class.java) as LoadAdError
    }

    private class FakeBannerAdPreloaderGateway : BannerAdPreloaderGateway {
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

        override fun pollAd(preloadId: String): BannerAd? {
            val ads = entries.firstOrNull { it.preloadId == preloadId }?.ads
                ?: return null
            return if (ads.isEmpty()) null else ads.removeFirst()
        }

        override fun peekAdResponseInfo(preloadId: String): ResponseInfo? {
            return entries
                .firstOrNull { it.preloadId == preloadId }
                ?.ads
                ?.firstOrNull()
                ?.getResponseInfo()
        }

        override fun destroy(preloadId: String): Boolean {
            if (throwOnDestroy) error("Synthetic SDK destroy failure")
            val removed = entries.removeAll { it.preloadId == preloadId }
            if (removed) destroyedIds += preloadId
            return removed
        }

        override fun isAdAvailable(preloadId: String): Boolean {
            return entries
                .firstOrNull { it.preloadId == preloadId }
                ?.ads
                ?.isNotEmpty() == true
        }

        override fun getNumAdsAvailable(preloadId: String): Int {
            return entries
                .firstOrNull { it.preloadId == preloadId }
                ?.ads
                ?.size ?: 0
        }
    }

    private data class Entry(
        val preloadId: String,
        val configuration: PreloadConfiguration,
        val callback: PreloadCallback,
        val ads: ArrayDeque<BannerAd> = ArrayDeque(),
    )

    private companion object {
        const val TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
        const val SECOND_TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/2014213617"
    }
}
