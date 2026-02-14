package com.soosu.nextgen.admobnative

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AdmobConsentManager(context: Context) {

    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context)

    val canRequestAds: Boolean
        get() = consentInformation.canRequestAds()

    val isPrivacyOptionsRequired: Boolean
        get() = consentInformation.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    suspend fun gatherConsent(
        activity: Activity,
        tagForUnderAgeOfConsent: Boolean = false,
    ): Boolean = suspendCancellableCoroutine { cont ->
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(tagForUnderAgeOfConsent)
            .build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    if (error != null) {
                        Log.w(TAG, "Consent form error: ${error.message}")
                    }
                    if (cont.isActive) {
                        cont.resume(consentInformation.canRequestAds())
                    }
                }
            },
            { error ->
                Log.w(TAG, "Consent info update failed: ${error.message}")
                if (cont.isActive) {
                    cont.resume(consentInformation.canRequestAds())
                }
            }
        )
    }

    suspend fun showPrivacyOptionsForm(activity: Activity): FormError? =
        suspendCancellableCoroutine { cont ->
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
                if (cont.isActive) {
                    cont.resume(error)
                }
            }
        }

    fun reset() {
        consentInformation.reset()
    }

    companion object {
        private const val TAG = "AdmobConsentManager"
    }
}
