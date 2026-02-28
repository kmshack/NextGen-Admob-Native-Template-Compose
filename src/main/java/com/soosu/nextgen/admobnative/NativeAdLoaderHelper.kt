package com.soosu.nextgen.admobnative

import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object NativeAdLoaderHelper {

    private const val TAG = "NativeAdLoaderHelper"

    /**
     * AdMob SDK 초기화를 최대 [timeoutMs]ms 대기.
     * @return 초기화 완료 여부
     */
    suspend fun waitForInitialization(timeoutMs: Long = 10_000L): Boolean {
        val startTime = System.currentTimeMillis()
        while (!AdmobInitializer.isInitialized()) {
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                Log.w(TAG, "SDK initialization timeout after ${timeoutMs}ms")
                return false
            }
            delay(100)
        }
        return true
    }

    /**
     * 네이티브 광고를 재시도 로직과 함께 로드.
     *
     * @param adUnitId 광고 단위 ID
     * @param maxRetries 최대 재시도 횟수 (기본 3)
     * @param initialDelayMs 첫 재시도 대기 시간 (기본 2초, 지수 백오프)
     * @param retryOnNoFill NO_FILL 에러 시 재시도 여부 (기본 true)
     * @param noFillDelayMs NO_FILL 재시도 대기 시간 (기본 30초)
     * @param noFillMaxRetries NO_FILL 최대 재시도 횟수 (기본 1)
     * @param mediaAspectRatio 미디어 종횡비 (기본 ANY)
     * @param logTag 로그 태그 (디버그용)
     * @return 로드된 NativeAd 또는 null
     */
    suspend fun loadWithRetry(
        adUnitId: String,
        maxRetries: Int = 3,
        initialDelayMs: Long = 2_000L,
        retryOnNoFill: Boolean = true,
        noFillDelayMs: Long = 30_000L,
        noFillMaxRetries: Int = 1,
        mediaAspectRatio: NativeAd.NativeMediaAspectRatio = NativeAd.NativeMediaAspectRatio.ANY,
        logTag: String = TAG,
    ): NativeAd? {
        var noFillRetryCount = 0

        for (attempt in 0..maxRetries) {
            val result = loadOnce(adUnitId, mediaAspectRatio)

            when {
                result.ad != null -> {
                    Log.d(logTag, "Ad loaded on attempt $attempt")
                    return result.ad
                }

                result.error?.code == LoadAdError.ErrorCode.NO_FILL -> {
                    if (retryOnNoFill && noFillRetryCount < noFillMaxRetries) {
                        noFillRetryCount++
                        Log.d(logTag, "NO_FILL, retrying in ${noFillDelayMs}ms (noFill retry $noFillRetryCount/$noFillMaxRetries)")
                        delay(noFillDelayMs)
                        continue
                    }
                    Log.d(logTag, "NO_FILL, no more retries")
                    return null
                }

                result.isRetryable && attempt < maxRetries -> {
                    val delayMs = initialDelayMs * (1L shl attempt)
                    Log.d(logTag, "Error ${result.error?.code}, retrying in ${delayMs}ms (attempt ${attempt + 1}/$maxRetries)")
                    delay(delayMs)
                }

                else -> {
                    Log.d(logTag, "Failed after attempt $attempt: ${result.error?.message}")
                    return null
                }
            }
        }
        return null
    }

    private suspend fun loadOnce(
        adUnitId: String,
        mediaAspectRatio: NativeAd.NativeMediaAspectRatio,
    ): LoadResult = suspendCancellableCoroutine { cont ->
        try {
            val adRequest = NativeAdRequest.Builder(
                adUnitId,
                listOf(NativeAd.NativeAdType.NATIVE)
            )
                .setMediaAspectRatio(mediaAspectRatio)
                .build()

            NativeAdLoader.load(adRequest, object : NativeAdLoaderCallback {
                override fun onNativeAdLoaded(nativeAd: NativeAd) {
                    if (cont.isActive) cont.resume(LoadResult(ad = nativeAd))
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    // NO_FILL은 별도 경로에서 처리하므로 retryable에서 제외
                    val retryable = adError.code == LoadAdError.ErrorCode.NETWORK_ERROR ||
                        adError.code == LoadAdError.ErrorCode.TIMEOUT ||
                        adError.code == LoadAdError.ErrorCode.INTERNAL_ERROR
                    if (cont.isActive) cont.resume(LoadResult(error = adError, isRetryable = retryable))
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Load exception: ${e.message}")
            if (cont.isActive) cont.resume(LoadResult(isRetryable = false))
        }
    }

    private data class LoadResult(
        val ad: NativeAd? = null,
        val error: LoadAdError? = null,
        val isRetryable: Boolean = false,
    )
}
