package com.soosu.nextgen.admobnative

import android.content.Context
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration
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
        initialize(context, admobAppId, null)
    }

    suspend fun initialize(context: Context, admobAppId: String, admobConfig: AdmobConfig?) {
        if (initialized) return
        mutex.withLock {
            if (initialized) return

            admobConfig?.maxAdContentRating?.let { rating ->
                val maxRating = when (rating) {
                    AdmobConfig.MAX_AD_CONTENT_RATING_G -> RequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_G
                    AdmobConfig.MAX_AD_CONTENT_RATING_PG -> RequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_PG
                    AdmobConfig.MAX_AD_CONTENT_RATING_T -> RequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_T
                    AdmobConfig.MAX_AD_CONTENT_RATING_MA -> RequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_MA
                    else -> null
                }
                if (maxRating != null) {
                    val requestConfig = RequestConfiguration.Builder()
                        .setMaxAdContentRating(maxRating)
                        .build()
                    MobileAds.setRequestConfiguration(requestConfig)
                    Log.d(TAG, "Set maxAdContentRating: $rating")
                }
            }

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
