package com.soosu.nextgen.admobnative

import android.os.Bundle
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.PreloadCallback
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import java.lang.reflect.Proxy
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class InterstitialAdLoadManagerTest {

    @Test
    fun `interstitial preload buffer defaults to one per key`() {
        val gateway = FakeInterstitialAdPreloaderGateway()
        val manager = createManager(gateway)
        val request = adRequest(TEST_AD_UNIT_ID)

        manager.register("game-over", request)

        assertTrue(manager.start("game-over"))
        assertEquals(1, gateway.entries.single().configuration.bufferSize)
        assertSame(request, gateway.entries.single().configuration.request)
    }

    @Test
    fun `invalid configuration is rejected before calling the SDK`() {
        val request = adRequest(TEST_AD_UNIT_ID)

        assertThrows(IllegalArgumentException::class.java) {
            InterstitialAdLoadConfig(request, bufferSize = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            InterstitialAdLoadConfig(request, maxAdAgeMs = 0)
        }
    }

    @Test
    fun `start is idempotent and a configuration replacement restarts the pool`() {
        val gateway = FakeInterstitialAdPreloaderGateway()
        val manager = createManager(gateway)
        val firstRequest = adRequest(TEST_AD_UNIT_ID)
        val secondRequest = adRequest(SECOND_TEST_AD_UNIT_ID)

        manager.register("game-over", firstRequest)
        assertTrue(manager.start("game-over"))
        assertTrue(manager.start("game-over"))
        assertEquals(1, gateway.startCalls)

        val firstEntry = gateway.entries.single()
        manager.register("game-over", secondRequest, bufferSize = 2)

        assertEquals(2, gateway.startCalls)
        assertEquals(listOf(firstEntry.preloadId), gateway.destroyedIds)
        assertSame(secondRequest, gateway.entries.single().configuration.request)
        assertEquals(2, gateway.entries.single().configuration.bufferSize)
    }

    @Test
    fun `manager instances use isolated SDK preload ids`() {
        val gateway = FakeInterstitialAdPreloaderGateway()
        val first = createManager(gateway)
        val second = createManager(gateway)

        first.register("game-over", adRequest(TEST_AD_UNIT_ID))
        second.register("game-over", adRequest(TEST_AD_UNIT_ID))
        first.start("game-over")
        second.start("game-over")

        val preloadIds = gateway.entries.map { it.preloadId }
        assertEquals(2, preloadIds.size)
        assertNotEquals(preloadIds[0], preloadIds[1])

        first.stopAll()
        assertEquals(listOf(preloadIds[0]), gateway.destroyedIds)
        assertTrue(second.isStarted("game-over"))
    }

    @Test
    fun `polled ad ownership is transferred to caller`() {
        val gateway = FakeInterstitialAdPreloaderGateway()
        val manager = createManager(gateway)
        val destroyCalls = AtomicInteger()
        val ad = fakeInterstitialAd(destroyCalls)

        manager.register("game-over", adRequest(TEST_AD_UNIT_ID))
        manager.start("game-over")
        gateway.entries.single().results.add(ad)

        assertSame(ad, manager.pollAd("game-over"))
        manager.stop("game-over")
        assertEquals(0, destroyCalls.get())

        ad.destroy()
        assertEquals(1, destroyCalls.get())
    }

    @Test
    fun `expired tracked ad is discarded instead of returned`() {
        val gateway = FakeInterstitialAdPreloaderGateway()
        var now = 1L
        val manager = InterstitialAdLoadManager.createForTesting(
            gateway = gateway,
            isSdkInitialized = { true },
            elapsedRealtime = { now },
            postToMain = { it() },
        )
        val destroyCalls = AtomicInteger()
        val responseInfo = fakeResponseInfo("expired-response")
        val ad = fakeInterstitialAd(destroyCalls, responseInfo)

        manager.register(
            "game-over",
            InterstitialAdLoadConfig(adRequest(TEST_AD_UNIT_ID), maxAdAgeMs = 1_000L),
        )
        manager.start("game-over")
        gateway.entries.single().results.add(ad)
        gateway.entries.single().callback.onAdPreloaded("sdk-id", responseInfo)

        now += 2_000L

        assertNull(manager.pollAd("game-over"))
        assertEquals(1, destroyCalls.get())
    }

    @Test
    fun `awaitAd resumes when a preload callback delivers an ad`() = runBlocking {
        val gateway = FakeInterstitialAdPreloaderGateway()
        val manager = createManager(gateway)
        val responseInfo = fakeResponseInfo("awaited-response")
        val ad = fakeInterstitialAd(AtomicInteger(), responseInfo)

        manager.register("game-over", adRequest(TEST_AD_UNIT_ID))
        manager.start("game-over")

        val awaiting = async(start = CoroutineStart.UNDISPATCHED) {
            manager.awaitAd("game-over", timeoutMs = 60_000L)
        }

        gateway.entries.single().results.add(ad)
        gateway.entries.single().callback.onAdPreloaded("sdk-id", responseInfo)

        assertSame(ad, awaiting.await())
    }

    @Test
    fun `start before SDK initialization resumes automatically after init`() {
        val gateway = FakeInterstitialAdPreloaderGateway()
        var sdkInitialized = false
        val initListeners = mutableListOf<() -> Unit>()
        val manager = createDeferredInitManager(gateway, initListeners) {
            sdkInitialized
        }

        manager.register("game-over", adRequest(TEST_AD_UNIT_ID))
        assertFalse(manager.start("game-over"))
        assertEquals(0, gateway.startCalls)
        assertEquals(1, initListeners.size)

        sdkInitialized = true
        initListeners.single().invoke()

        assertTrue(manager.isStarted("game-over"))
        assertEquals(1, gateway.startCalls)
    }

    @Test
    fun `stop before initialization cancels the pending start`() {
        val gateway = FakeInterstitialAdPreloaderGateway()
        var sdkInitialized = false
        val initListeners = mutableListOf<() -> Unit>()
        val manager = createDeferredInitManager(gateway, initListeners) {
            sdkInitialized
        }

        manager.register("game-over", adRequest(TEST_AD_UNIT_ID))
        manager.start("game-over")
        manager.stop("game-over")

        sdkInitialized = true
        initListeners.single().invoke()

        assertFalse(manager.isStarted("game-over"))
        assertEquals(0, gateway.startCalls)
    }

    private fun createManager(
        gateway: FakeInterstitialAdPreloaderGateway,
    ): InterstitialAdLoadManager = InterstitialAdLoadManager.createForTesting(
        gateway = gateway,
        isSdkInitialized = { true },
        elapsedRealtime = { 1L },
        postToMain = { it() },
    )

    private fun createDeferredInitManager(
        gateway: FakeInterstitialAdPreloaderGateway,
        initListeners: MutableList<() -> Unit>,
        isSdkInitialized: () -> Boolean,
    ): InterstitialAdLoadManager = InterstitialAdLoadManager.createForTesting(
        gateway = gateway,
        isSdkInitialized = isSdkInitialized,
        elapsedRealtime = { 1L },
        postToMain = { it() },
        registerInitListener = { initListeners.add(it) },
    )

    private fun adRequest(adUnitId: String): AdRequest {
        return AdRequest.Builder(adUnitId).build()
    }

    private fun fakeInterstitialAd(
        destroyCalls: AtomicInteger,
        responseInfo: ResponseInfo? = null,
    ): InterstitialAd {
        @Suppress("UNCHECKED_CAST")
        return Proxy.newProxyInstance(
            InterstitialAd::class.java.classLoader,
            arrayOf(InterstitialAd::class.java),
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
                method.returnType == Void.TYPE -> Unit
                else -> null
            }
        } as InterstitialAd
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

    private class FakeInterstitialAdPreloaderGateway : InterstitialAdPreloaderGateway {
        val entries = mutableListOf<Entry>()
        val destroyedIds = mutableListOf<String>()
        var startCalls = 0

        override fun start(
            preloadId: String,
            configuration: PreloadConfiguration,
            callback: PreloadCallback,
        ): Boolean {
            startCalls++
            if (entries.any { it.preloadId == preloadId }) return false
            entries += Entry(preloadId, configuration, callback)
            return true
        }

        override fun pollAd(preloadId: String): InterstitialAd? {
            val results = entries.firstOrNull { it.preloadId == preloadId }?.results
                ?: return null
            return if (results.isEmpty()) null else results.removeFirst()
        }

        override fun peekAdResponseInfo(preloadId: String): ResponseInfo? = null

        override fun destroy(preloadId: String): Boolean {
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
        val results: ArrayDeque<InterstitialAd> = ArrayDeque(),
    )

    private companion object {
        const val TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
        const val SECOND_TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/8691691433"
    }
}
