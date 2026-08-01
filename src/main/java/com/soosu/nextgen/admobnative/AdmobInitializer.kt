package com.soosu.nextgen.admobnative

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.common.AgeRestrictedTreatment
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
    private val listenerLock = Any()
    private val pendingListeners = mutableListOf<() -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var initialized = false

    suspend fun initialize(context: Context, admobAppId: String) {
        initialize(context, admobAppId, null)
    }

    suspend fun initialize(context: Context, admobAppId: String, admobConfig: AdmobConfig?) {
        if (initialized) return
        mutex.withLock {
            if (initialized) return

            withContext(Dispatchers.IO) {
                suspendCancellableCoroutine { cont ->
                    val config = InitializationConfig.Builder(admobAppId)
                        .apply {
                            if (admobConfig?.nativeValidatorDisabled == true) {
                                setNativeValidatorDisabled()
                            }
                        }
                        .build()
                    MobileAds.initialize(context.applicationContext, config) {
                        Log.d(TAG, "MobileAds initialized")
                        initialized = true
                        if (cont.isActive) {
                            cont.resume(Unit)
                        }
                    }
                }
            }

            admobConfig?.let { config ->
                val builder = RequestConfiguration.Builder()
                var hasConfig = false

                config.maxAdContentRating?.let { rating ->
                    val maxRating = when (rating) {
                        AdmobConfig.MAX_AD_CONTENT_RATING_G -> RequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_G
                        AdmobConfig.MAX_AD_CONTENT_RATING_PG -> RequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_PG
                        AdmobConfig.MAX_AD_CONTENT_RATING_T -> RequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_T
                        AdmobConfig.MAX_AD_CONTENT_RATING_MA -> RequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_MA
                        else -> null
                    }
                    if (maxRating != null) {
                        builder.setMaxAdContentRating(maxRating)
                        hasConfig = true
                        Log.d(TAG, "Set maxAdContentRating: $rating")
                    }
                }

                if (config.tagForChildDirectedTreatment != null ||
                    config.tagForUnderAgeOfConsent != null
                ) {
                    val treatment = when {
                        config.tagForChildDirectedTreatment == true -> AgeRestrictedTreatment.CHILD
                        config.tagForUnderAgeOfConsent == true -> AgeRestrictedTreatment.TEEN
                        else -> AgeRestrictedTreatment.UNSPECIFIED
                    }
                    builder.setAgeRestrictedTreatment(treatment)
                    hasConfig = true
                    Log.d(TAG, "Set ageRestrictedTreatment: $treatment")
                }

                if (hasConfig) {
                    MobileAds.setRequestConfiguration(builder.build())
                }
            }

            notifyInitialized()
        }
    }

    fun isInitialized(): Boolean = initialized

    /**
     * Runs [listener] once the SDK is initialized.
     *
     * If the SDK is already initialized the listener is posted to the main
     * thread immediately; otherwise it is queued and posted after
     * [initialize] completes. Listeners are always invoked on the main thread
     * so they can safely start preloaders.
     *
     * Note: when an app initializes the SDK by calling `MobileAds.initialize`
     * directly instead of [initialize], listeners queued before that point are
     * never invoked.
     */
    fun whenInitialized(listener: () -> Unit) {
        val runNow = synchronized(listenerLock) {
            if (initialized || MobileAds.isInitialized) {
                true
            } else {
                pendingListeners.add(listener)
                false
            }
        }
        if (runNow) {
            mainHandler.post(listener)
        }
    }

    private fun notifyInitialized() {
        val listeners = synchronized(listenerLock) {
            val snapshot = pendingListeners.toList()
            pendingListeners.clear()
            snapshot
        }
        listeners.forEach { mainHandler.post(it) }
    }
}
