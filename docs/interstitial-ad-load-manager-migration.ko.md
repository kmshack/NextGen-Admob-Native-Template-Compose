# InterstitialAdLoadManager 마이그레이션 가이드

> 배포 상태: `InterstitialAdLoadManager`는 `1.7.3` 릴리스부터 포함됩니다.
> `1.7.2` 이하 artifact에는 이 클래스가 없습니다.

## 결론

`InterstitialAdLoadManager`는 앱마다 반복 구현되던 전면 광고의 로드, 보관,
재로드, 만료, 표시 후 정리를 Next-Gen SDK의 `InterstitialAdPreloader` 기반으로
통합합니다.

권장 경계는 다음과 같습니다.

- 라이브러리: key별 버퍼, 자동 refill, SDK 초기화 전 호출의 자동 재개, 만료
  폐기, 대기(await), callback thread 전환, 표시 후 destroy
- 앱: UMP consent, 유료 사용자, Remote Config, 노출 주기(interval/cap),
  표시 지점 선택, impression/click 분석

즉 "게임 오버마다 보여주고 닫히면 다시 로드"류의 개별 구현 코드는 전부
지워지고, 앱에는 **언제 보여줄지**에 대한 정책 코드만 남습니다.

## 핵심 API

| API | 역할 |
|---|---|
| `register(key, adUnitId)` / `register(key, request)` | 독립 pool 등록 또는 교체 |
| `start(key)` / `startAll()` | preload 시작. SDK 미초기화 시 자동 예약 후 초기화 완료 시 재개 |
| `showAdIfAvailable(activity, key) { }` | 준비된 광고를 표시하고 dismiss 후 destroy까지 처리 |
| `pollAd(key)` | 준비된 `InterstitialAd`를 직접 소비 (소유권 이전) |
| `awaitAd(key, timeoutMs)` | 시작된 pool에 광고가 없으면 timeout까지 대기 후 소비 |
| `isAdAvailable(key)` / `getNumAdsAvailable(key)` | 유효한 광고 준비 여부/수 확인 |
| `addListener(key, listener)` | 상태, preload 성공, 소진, 원본 `LoadAdError` 수신 |
| `refresh(key)` / `stop(key)` / `unregister(key)` | 재시작 / 중단(설정 유지) / 완전 제거 |

`bufferSize` 기본값은 `1`입니다. 전면 광고는 보통 한 전환 지점에서 한 개만
소비되므로 대부분의 앱에서 그대로 두면 됩니다.

## 1. 기존 개별 구현의 전형적인 모습

대부분의 앱은 legacy SDK 시절 패턴을 Next-Gen SDK로 옮기면서 아래와 같은
코드를 가지고 있습니다.

```kotlin
// ── 삭제 대상: 전형적인 개별 구현 ──
class InterstitialAdController(private val context: Context) {
    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false
    private var loadedAt = 0L

    fun load() {
        if (isLoading || interstitialAd != null) return
        isLoading = true
        InterstitialAd.load(
            AdRequest.Builder(AD_UNIT_ID).build(),
            object : AdLoadCallback<InterstitialAd> {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    loadedAt = SystemClock.elapsedRealtime()
                    isLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading = false
                    // 재시도 타이머, 백오프...
                }
            },
        )
    }

    fun show(activity: Activity, onDone: () -> Unit) {
        val ad = interstitialAd
        if (ad == null || SystemClock.elapsedRealtime() - loadedAt > ONE_HOUR) {
            interstitialAd = null
            onDone()
            load()
            return
        }
        ad.adEventCallback = object : InterstitialAdEventCallback {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                ad.destroy()
                onDone()
                load() // 닫힐 때마다 수동 재로드
            }

            override fun onAdFailedToShowFullScreenContent(e: FullScreenContentError) {
                interstitialAd = null
                ad.destroy()
                onDone()
            }
        }
        interstitialAd = null
        ad.show(activity)
    }
}
```

이 구현이 감추고 있는 문제들:

- SDK 초기화 전 `load()` 호출은 그냥 실패하거나 크래시 위험이 있습니다.
- 실패 재시도, 만료, 재로드 시점을 앱이 직접 관리해야 합니다.
- `interstitialAd` 필드의 동시성 처리가 대부분 불완전합니다.
- 화면마다 controller를 만들면 광고 인스턴스가 누수되기 쉽습니다.

## 2. Application 범위에 Manager 생성

Manager는 화면마다 만들지 말고 앱 또는 광고 controller 범위에서 한 번
생성합니다.

```kotlin
class MyApplication : Application() {
    val interstitialAdLoadManager = InterstitialAdLoadManager()

    override fun onCreate() {
        super.onCreate()

        interstitialAdLoadManager.register(
            key = GAME_OVER_INTERSTITIAL,
            adUnitId = INTERSTITIAL_AD_UNIT_ID,
        )
    }

    companion object {
        const val GAME_OVER_INTERSTITIAL = "game-over"
        const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-XXXX/YYYY"
    }
}
```

keywords나 mediation extras가 필요하면 완성된 `AdRequest`를 등록하세요.

```kotlin
interstitialAdLoadManager.register(
    key = GAME_OVER_INTERSTITIAL,
    request = AdRequest.Builder(INTERSTITIAL_AD_UNIT_ID)
        .addKeyword("game")
        .build(),
)
```

## 3. 로드 시작: `load()` 호출부 → `start(key)`

기존 `load()` 호출 지점(스플래시 이후, consent 완료 이후 등)을 `start(key)`
하나로 바꿉니다.

```kotlin
// consent + 초기화가 끝나는 지점 (예: SplashActivity)
lifecycleScope.launch {
    SplashAdLoader.execute(this@SplashActivity, app.admobConfig)
    app.interstitialAdLoadManager.start(MyApplication.GAME_OVER_INTERSTITIAL)
    // ...
}
```

**초기화 전에 호출해도 안전합니다.** `start()`가 SDK 초기화 전에 호출되면
예약만 해두고, `AdmobInitializer.initialize()`(또는 이를 내부적으로 사용하는
`SplashAdLoader.execute()`)가 끝나는 순간 자동으로 시작됩니다. 그 사이에
`stop()`/`unregister()`를 호출하면 예약이 취소됩니다. 단, 앱이
`MobileAds.initialize()`를 직접 호출하는 경우에는 이 자동 재개가 동작하지
않으므로 초기화 후 `start()`를 호출해야 합니다.

**"닫힐 때마다 재로드" 코드는 삭제합니다.** SDK preloader가 버퍼를 자동으로
다시 채우고, 실패 시 재시도도 SDK가 관리합니다.

## 4. 표시 지점 마이그레이션

### 4-a. 대부분의 경우: `showAdIfAvailable`

기존 `show(activity, onDone)` 류의 메서드는 그대로 대체됩니다.

```kotlin
app.interstitialAdLoadManager.showAdIfAvailable(
    activity = activity,
    key = MyApplication.GAME_OVER_INTERSTITIAL,
) {
    // 광고가 닫혔거나, 표시 실패했거나, 준비된 광고가 없을 때 정확히 1회 호출
    navigateToNextScreen()
}
```

- 광고가 없으면 즉시 완료 콜백이 호출되므로 흐름이 막히지 않습니다.
- dismiss/실패 후 `destroy()`까지 manager가 처리합니다.
- 같은 manager에서 이미 광고가 표시 중이면 중복 표시하지 않습니다.
- 어느 thread에서 호출해도 main thread로 전환됩니다.

노출 주기(interval, cap)는 의도적으로 manager에 없습니다. 기존 구현의
"마지막 표시 후 N분" 같은 정책은 앱 코드에서 `showAdIfAvailable` 호출 여부로
결정하세요.

### 4-b. 표시를 직접 제어해야 하는 경우: `pollAd`

`onAdPaid` 수익 추적, `setImmersiveMode` 등 광고 객체를 직접 다뤄야 하면
`pollAd`를 사용합니다. **이때부터 소유권은 앱에 있으며 `destroy()` 책임도
앱에 있습니다.**

```kotlin
val ad = app.interstitialAdLoadManager.pollAd(MyApplication.GAME_OVER_INTERSTITIAL)
if (ad != null) {
    ad.adEventCallback = object : InterstitialAdEventCallback {
        override fun onAdPaid(value: AdValue) { /* 수익 분석 */ }

        override fun onAdDismissedFullScreenContent() {
            ad.destroy() // 소유권이 앱에 있으므로 직접 destroy
            navigateToNextScreen()
        }

        override fun onAdFailedToShowFullScreenContent(e: FullScreenContentError) {
            ad.destroy()
            navigateToNextScreen()
        }
    }
    ad.show(activity)
} else {
    navigateToNextScreen()
}
```

### 4-c. 잠깐 기다렸다 보여주고 싶은 경우: `awaitAd`

"로딩 스피너를 잠깐 보여주고 광고가 준비되면 표시" 패턴은 `awaitAd`로
대체합니다. `awaitAd`는 시작된 pool에서만 기다립니다.

```kotlin
lifecycleScope.launch {
    val ad = app.interstitialAdLoadManager.awaitAd(
        key = MyApplication.GAME_OVER_INTERSTITIAL,
        timeoutMs = 5_000L,
    )
    // 이후는 pollAd와 동일 (소유권 앱, destroy 책임 앱)
}
```

## 5. 마이그레이션 매핑 요약

| 기존 개별 구현 | InterstitialAdLoadManager |
|---|---|
| `InterstitialAd.load(request, callback)` | `register(key, ...)` + `start(key)` |
| `interstitialAd` 필드 보관 | 삭제 (SDK 버퍼가 보관) |
| `isLoading` 플래그 | 삭제 (`getState(key).isLoading`) |
| dismiss 후 수동 재로드 | 삭제 (SDK 자동 refill) |
| 실패 재시도 타이머/백오프 | 삭제 (SDK 관리, 오류는 listener로 관찰) |
| 만료 시간 비교 후 폐기 | 삭제 (`maxAdAgeMs`, 기본 1시간) |
| `ad != null` 체크 | `isAdAvailable(key)` |
| `show(activity) { }` 헬퍼 | `showAdIfAvailable(activity, key) { }` |
| 초기화 여부 체크 후 로드 | 삭제 (`start()`가 초기화를 기다렸다 자동 재개) |
| 노출 주기 정책 | 유지 (앱 정책으로 남김) |

## 6. 주의 사항

- **`showAdIfAvailable`과 `pollAd`의 소유권이 다릅니다.** 전자는 manager가
  destroy까지 책임지고, 후자는 poll한 순간부터 앱 책임입니다.
- `stop(key)`은 SDK queue에 남아 있는 광고만 destroy합니다. 이미 poll/await로
  꺼내 간 광고는 앱이 정리해야 합니다.
- 여러 placement가 있으면 key를 나눠 등록하세요. key마다 버퍼와 상태가
  독립적입니다.
- `InterstitialAdPreloader.destroyAll()`은 사용하지 마세요. 다른 manager나
  라이브러리가 소유한 버퍼까지 파괴됩니다. manager의 `stopAll()`은 자신이
  등록한 preload ID만 정리합니다.
- 테스트 광고 단위 ID: `ca-app-pub-3940256099942544/1033173712` (Google 공식
  interstitial 테스트 ID).
