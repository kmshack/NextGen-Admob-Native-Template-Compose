package com.soosu.nextgen.admobnative

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InitializationReadinessTest {
    @Test
    fun `configuration and post processing finish before any listener is posted`() = runBlocking {
        val events = mutableListOf<String>()
        lateinit var readiness: InitializationReadiness
        readiness = InitializationReadiness { listener ->
            assertTrue(events.contains("configuration"))
            assertTrue(events.contains("post processing"))
            assertTrue(readiness.isInitialized())
            listener()
        }
        val sdkCallback = CompletableDeferred<Unit>()
        readiness.whenInitialized { events.add("early listener") }
        val initialization = launch(start = CoroutineStart.UNDISPATCHED) {
            readiness.initialize {
                sdkCallback.await()
                // Registration while the SDK is initialized but configuration
                // is still pending must remain queued.
                readiness.whenInitialized { events.add("callback listener") }
                assertFalse(readiness.isInitialized())
                assertTrue(events.isEmpty())
                events.add("configuration")
                events.add("post processing")
            }
        }
        sdkCallback.complete(Unit)
        initialization.join()
        readiness.whenInitialized { events.add("late listener") }
        assertEquals(listOf("configuration", "post processing", "early listener",
            "callback listener", "late listener"), events)
    }

    @Test
    fun `cancellation before SDK callback still configures and releases listeners once`() = runBlocking {
        val events = mutableListOf<String>()
        val readiness = InitializationReadiness { it() }
        val sdkCallback = CompletableDeferred<Unit>()
        readiness.whenInitialized { events.add("listener") }
        val initialization = launch(start = CoroutineStart.UNDISPATCHED) {
            readiness.initialize {
                events.add("SDK started")
                sdkCallback.await()
                events.add("configuration")
            }
        }
        initialization.cancel()
        assertFalse(readiness.isInitialized())
        val retry = launch(start = CoroutineStart.UNDISPATCHED) {
            readiness.initialize { error("SDK initialization must not start twice") }
        }
        assertEquals(listOf("SDK started"), events)
        sdkCallback.complete(Unit)
        initialization.join()
        retry.join()
        assertTrue(initialization.isCancelled)
        assertTrue(readiness.isInitialized())
        readiness.initialize { error("Ready initialization must return immediately") }
        readiness.whenInitialized { events.add("late listener") }
        assertEquals(listOf("SDK started", "configuration", "listener", "late listener"), events)
    }

    @Test
    fun `failed preparation does not publish readiness and queued listeners survive retry`() = runBlocking {
        var notifications = 0
        val readiness = InitializationReadiness { it() }
        readiness.whenInitialized { notifications++ }
        try {
            readiness.initialize { throw IllegalStateException("configuration failed") }
            error("Expected preparation failure")
        } catch (_: IllegalStateException) {
            assertFalse(readiness.isInitialized())
            assertEquals(0, notifications)
        }
        readiness.initialize {}
        assertTrue(readiness.isInitialized())
        assertEquals(1, notifications)
    }

}
