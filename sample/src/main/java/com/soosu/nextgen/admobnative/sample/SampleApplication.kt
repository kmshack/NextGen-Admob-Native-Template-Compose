package com.soosu.nextgen.admobnative.sample

import android.app.Application
import com.soosu.nextgen.admobnative.AdmobConfig
import com.soosu.nextgen.admobnative.AppOpenAdLifecycleObserver
import com.soosu.nextgen.admobnative.AppOpenAdManager

class SampleApplication : Application() {

    lateinit var admobConfig: AdmobConfig
        private set
    lateinit var appOpenAdManager: AppOpenAdManager
        private set
    lateinit var adLifecycleObserver: AppOpenAdLifecycleObserver
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

        // 3. 포그라운드 광고 라이프사이클 옵저버 등록
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
    }
}
