package com.soosu.nextgen.admobnative.sample

import android.app.Application
import com.soosu.nextgen.admobnative.AdmobConfig
import com.soosu.nextgen.admobnative.AppOpenAdLifecycleObserver
import com.soosu.nextgen.admobnative.AppOpenAdManager
import com.soosu.nextgen.admobnative.NativeAdLoadManager
import com.soosu.nextgen.admobnative.createNativeAdRequestWithDefaults

class SampleApplication : Application() {

    lateinit var admobConfig: AdmobConfig
        private set
    lateinit var appOpenAdManager: AppOpenAdManager
        private set
    lateinit var adLifecycleObserver: AppOpenAdLifecycleObserver
        private set
    lateinit var nativeAdLoadManager: NativeAdLoadManager
        private set

    override fun onCreate() {
        super.onCreate()

        // 1. AdmobConfig 설정
        admobConfig = AdmobConfig.Builder(TEST_APP_ID)
            .splashAdUnitId(TEST_APP_OPEN_AD_UNIT_ID)
            .foregroundAdUnitId(TEST_APP_OPEN_AD_UNIT_ID)
            .consentTimeoutMs(5_000)
            .splashAdLoadTimeoutMs(8_000)
            .foregroundAdCooldownMs(30_000)
            .foregroundAdShowIntervalMs(10_000)
            .preloadOnBackground(true)
            .shouldSuppressAds { false /* isPremiumUser() 등 조건 */ }
            .debugLogging(true)
            .build()

        // 2. AppOpenAdManager 생성
        appOpenAdManager = AppOpenAdManager(admobConfig)

        // 3. 네이티브 광고 풀 등록
        // 실제 로딩 시작은 동의 처리와 SDK 초기화가 끝난 뒤 SplashActivity에서 수행합니다.
        nativeAdLoadManager = NativeAdLoadManager().apply {
            register(
                key = NATIVE_FEED_POOL,
                request = createNativeAdRequestWithDefaults(TEST_NATIVE_AD_UNIT_ID),
                bufferSize = 1,
            )
        }

        // 4. 포그라운드 광고 라이프사이클 옵저버 등록
        adLifecycleObserver = AppOpenAdLifecycleObserver(
            application = this,
            adManager = appOpenAdManager,
            config = admobConfig,
        ).apply {
            // SplashActivity에서는 포그라운드 광고 표시 안 함
            excludedActivities.add(SplashActivity::class.java.name)
        }
    }

    companion object {
        // Google 테스트 광고 ID
        const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"
        const val TEST_APP_OPEN_AD_UNIT_ID = "ca-app-pub-3940256099942544/9257395921"
        const val TEST_NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"
        const val NATIVE_FEED_POOL = "sample-native-feed"
    }
}
