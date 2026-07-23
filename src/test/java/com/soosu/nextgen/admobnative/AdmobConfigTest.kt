package com.soosu.nextgen.admobnative

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AdmobConfigTest {

    @Test
    fun `app open preloader is enabled with a one-ad buffer by default`() {
        val config = AdmobConfig.Builder(TEST_APP_ID).build()

        assertTrue(config.useAppOpenAdPreloader)
        assertEquals(1, config.appOpenAdPreloadBufferSize)
    }

    @Test
    fun `app open preloader can be disabled`() {
        val config = AdmobConfig.Builder(TEST_APP_ID)
            .useAppOpenAdPreloader(false)
            .build()

        assertFalse(config.useAppOpenAdPreloader)
    }

    @Test
    fun `null preload buffer delegates sizing to the SDK`() {
        val config = AdmobConfig.Builder(TEST_APP_ID)
            .appOpenAdPreloadBufferSize(null)
            .build()

        assertNull(config.appOpenAdPreloadBufferSize)
    }

    @Test
    fun `preload buffer must contain at least one ad`() {
        assertThrows(IllegalArgumentException::class.java) {
            AdmobConfig.Builder(TEST_APP_ID).appOpenAdPreloadBufferSize(0)
        }
    }

    private companion object {
        const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"
    }
}
