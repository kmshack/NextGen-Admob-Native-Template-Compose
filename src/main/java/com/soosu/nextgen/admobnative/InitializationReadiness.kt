package com.soosu.nextgen.admobnative

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Publishes library readiness only after all initialization work completes. */
internal class InitializationReadiness(private val postListener: (() -> Unit) -> Unit) {
    private val mutex = Mutex()
    private val listenerLock = Any()
    private val pendingListeners = mutableListOf<() -> Unit>()

    @Volatile
    private var initialized = false

    suspend fun initialize(prepare: suspend () -> Unit) {
        if (initialized) return
        mutex.withLock {
            if (initialized) return
            // Once SDK initialization starts, caller cancellation must not skip
            // configuration or strand listeners. Keep the mutex until completion.
            withContext(NonCancellable) {
                prepare()
                val listeners = synchronized(listenerLock) {
                    initialized = true
                    pendingListeners.toList().also { pendingListeners.clear() }
                }
                listeners.forEach(postListener)
            }
        }
    }

    fun isInitialized(): Boolean = initialized

    fun whenInitialized(listener: () -> Unit) {
        val runNow = synchronized(listenerLock) {
            if (initialized) true else {
                pendingListeners.add(listener)
                false
            }
        }
        if (runNow) postListener(listener)
    }
}
