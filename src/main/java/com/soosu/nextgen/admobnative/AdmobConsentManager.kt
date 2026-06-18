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
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class AdmobConsentManager(context: Context) {

    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context)

    val canRequestAds: Boolean
        get() = consentInformation.canRequestAds()

    val isPrivacyOptionsRequired: Boolean
        get() = consentInformation.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /**
     * 동의 정보를 갱신하고, 필요 시 동의 폼을 표시한다.
     *
     * @param timeoutMs 동의 정보 갱신(네트워크) 단계의 타임아웃. 0이면 무제한.
     *                  타임아웃은 정보 갱신 단계에만 적용되며, 사용자가 동의 폼을
     *                  읽고 조작하는 단계에는 적용되지 않는다.
     * @return 광고 요청 가능 여부 (consentInformation.canRequestAds())
     */
    suspend fun gatherConsent(
        activity: Activity,
        tagForUnderAgeOfConsent: Boolean = false,
        timeoutMs: Long = 10_000L,
    ): Boolean {
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(tagForUnderAgeOfConsent)
            .build()

        val updated = if (timeoutMs > 0) {
            withTimeoutOrNull(timeoutMs) { requestInfoUpdate(activity, params) }
        } else {
            requestInfoUpdate(activity, params)
        }

        // 타임아웃 또는 갱신 실패 시 현재까지의 동의 상태를 best-effort로 반환
        if (updated != true) {
            return consentInformation.canRequestAds()
        }

        // 폼 표시는 사용자 상호작용 단계이므로 타임아웃을 적용하지 않는다.
        loadAndShowFormIfRequired(activity)
        return consentInformation.canRequestAds()
    }

    private suspend fun requestInfoUpdate(
        activity: Activity,
        params: ConsentRequestParameters,
    ): Boolean = suspendCancellableCoroutine { cont ->
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                if (cont.isActive) cont.resume(true)
            },
            { error ->
                Log.w(TAG, "Consent info update failed: ${error.message}")
                if (cont.isActive) cont.resume(false)
            }
        )
    }

    private suspend fun loadAndShowFormIfRequired(
        activity: Activity,
    ): Unit = suspendCancellableCoroutine { cont ->
        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
            if (error != null) {
                Log.w(TAG, "Consent form error: ${error.message}")
            }
            if (cont.isActive) cont.resume(Unit)
        }
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
