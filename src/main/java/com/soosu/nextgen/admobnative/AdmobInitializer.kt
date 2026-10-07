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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object AdmobInitializer {

    private const val TAG = "AdmobInitializer"
    private val mainHandler = Handler(Looper.getMainLooper())

    private val readiness = InitializationReadiness { mainHandler.post(it) }

    suspend fun initialize(context: Context, admobAppId: String) {
        initialize(context, admobAppId, null)
    }

    suspend fun initialize(context: Context, admobAppId: String, admobConfig: AdmobConfig?) {
        readiness.initialize {
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
                        MobileAds.setUserMutedApp(true)
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
        }
    }

    /** True after SDK initialization and library request configuration complete. */
    fun isInitialized(): Boolean = readiness.isInitialized()

    /**
     * Posts [listener] to the main thread once this library is ready, including
     * request configuration and initialization post-processing.
     *
     * Calling `MobileAds.initialize` directly does not make this library ready;
     * call [initialize] to apply its configuration and release queued listeners.
     */
    fun whenInitialized(listener: () -> Unit) = readiness.whenInitialized(listener)
}
