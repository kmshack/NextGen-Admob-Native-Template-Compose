package com.soosu.nextgen.admobnative

import android.content.Context
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object AdmobInitializer {

    private const val TAG = "AdmobInitializer"
    private val mutex = Mutex()

    @Volatile
    private var initialized = false

    suspend fun initialize(context: Context, admobAppId: String) {
        if (initialized) return
        mutex.withLock {
            if (initialized) return
            withContext(Dispatchers.IO) {
                suspendCancellableCoroutine { cont ->
                    val config = InitializationConfig.Builder(admobAppId).build()
                    MobileAds.initialize(context.applicationContext, config) {
                        Log.d(TAG, "MobileAds initialized")
                        initialized = true
                        if (cont.isActive) {
                            cont.resume(Unit)
                        }
                    }
                }
            }
        }
    }

    fun isInitialized(): Boolean = initialized
}
