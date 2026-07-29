# BannerAdLoadManager 마이그레이션 가이드

> 배포 상태: `BannerAdLoadManager`는 `1.7.4` 릴리스부터 포함됩니다.
> `1.7.3` 이하 artifact에는 이 클래스가 없습니다.

## 결론

`BannerAdLoadManager`는 앱마다 반복 구현되던 배너 광고의 preload, 버퍼 관리,
만료 판정, SDK 초기화 대기, callback thread 전환을 Next-Gen SDK의
`BannerAdPreloader` 기반으로 통합합니다.

권장 경계는 `NativeAdLoadManager`, `InterstitialAdLoadManager`와 동일합니다.

- 라이브러리: key별 버퍼, 자동 refill, SDK 초기화 전 호출의 자동 예약과 재개,
  만료 폐기, 대기(await), callback thread 전환, 소유권 이전
- 앱: UMP consent, 유료 사용자, Remote Config, 배치, 갱신 주기,
  뷰 attach/detach, impression/click 분석

배너는 네이티브·전면과 달리 **뷰에 붙여야** 노출됩니다. `getView(activity)`
호출과 뷰 계층 관리, `destroy()` 호출은 앱 몫입니다.

## 핵심 API

| API | 역할 |
|---|---|
| `register(key, request)` / `register(key, adUnitId, adSize)` | 독립 pool 등록 또는 **교체(파괴적)** |
| `registerIfAbsent(key, ...)` | 없을 때만 등록. 이미 도는 pool은 건드리지 않음 |
| `start(key)` / `startAll()` | preload 시작. SDK 미초기화 시 자동 예약 후 초기화 완료 시 재개 |
| `pollAd(key)` | 준비된 `BannerAd`를 즉시 소비 (소유권 이전) |
| `awaitAd(key, timeoutMs)` | 시작된 pool에 광고가 없으면 timeout까지 대기 후 소비 |
| `isAdAvailable(key)` / `getNumAdsAvailable(key)` / `getState(key)` | 부작용 없는 순수 조회 |
| `pruneExpired(key)` | 만료된 광고를 명시적으로 정리 |
| `addListener(key, listener)` | 상태, preload 성공, 소진, 원본 `LoadAdError` 수신 |
| `refresh(key)` / `stop(key)` / `unregister(key)` | 재시작 / 중단(설정 유지) / 완전 제거 |

`bufferSize` 기본값은 `1`입니다. **지정하지 않고 SDK에 위임하면 현재 기본값은
2**입니다(실기기 측정). 배너 슬롯 하나는 한 번에 광고 하나만 소비하므로,
버퍼 2는 노출되지 않는 요청을 한 건 더 만듭니다.

## 1. 크기는 요청에 포함된다

`BannerAdRequest`가 `AdSize`를 들고 있으므로, **하나의 pool은 (광고 유닛, 크기)
조합 하나**를 담당합니다. 크기가 다르면 key를 나눠야 합니다.

```kotlin
class MyApplication : Application() {
    val bannerAdLoadManager = BannerAdLoadManager()

    override fun onCreate() {
        super.onCreate()

        // register 는 SDK 초기화 전에도 가능합니다.
        bannerAdLoadManager.registerIfAbsent(
            key = HOME_MREC,
            adUnitId = BuildConfig.BANNER_HOME_AD_UNIT_ID,
            adSize = AdSize.MEDIUM_RECTANGLE,
        )
        bannerAdLoadManager.registerIfAbsent(
            key = LIST_INLINE,
            request = BannerAdRequest.Builder(
                BuildConfig.BANNER_LIST_AD_UNIT_ID,
                AdSize.getInlineAdaptiveBannerAdSize(widthDp, widthDp),
            ).apply {
                keywords.forEach(::addKeyword)
            }.build(),
        )
    }

    companion object {
        const val HOME_MREC = "home-mrec"
        const val LIST_INLINE = "list-inline"
    }
}
```

## 2. Consent와 SDK 초기화 후 시작

`register*()`는 설정만 저장합니다. 실제 로드는 UMP consent 처리 및
`MobileAds.initialize()`가 끝난 뒤 시작합니다.

```kotlin
fun updateBannerAdPolicy(
    manager: BannerAdLoadManager,
    canRequestAds: Boolean,
    isPremium: Boolean,
    remoteConfigEnabled: Boolean,
) {
    if (canRequestAds && !isPremium && remoteConfigEnabled) {
        manager.start(MyApplication.HOME_MREC)
    } else {
        manager.stop(MyApplication.HOME_MREC)
    }
}
```

`start()`를 SDK 초기화 전에 호출해도 예약되었다가 초기화 완료 시 자동으로
재개됩니다. `awaitAd()`도 그 예약 상태를 인지하므로, 초기화와 경쟁해도 즉시
실패로 끝나지 않고 timeout까지 기다립니다.

## 3. Compose 슬롯 예시

```kotlin
@Composable
fun HomeBannerSlot(modifier: Modifier = Modifier) {
    val activity = LocalActivity.current ?: return
    val manager = (activity.application as MyApplication).bannerAdLoadManager
    var bannerAd by remember { mutableStateOf<BannerAd?>(null) }

    LaunchedEffect(Unit) {
        if (bannerAd == null) {
            bannerAd = manager.awaitAd(MyApplication.HOME_MREC)
        }
    }

    // 화면이 사라질 때 이 화면이 소유한 광고만 정리한다.
    val currentAd = bannerAd
    DisposableEffect(currentAd) {
        onDispose { currentAd?.destroy() }
    }

    currentAd?.let { ad ->
        AndroidView(
            factory = { ad.getView(activity) },
            modifier = modifier,
        )
    }
}
```

### 하지 말 것: 화면 dispose 에서 pool destroy

```kotlin
// ── 안티패턴 ──
onDispose {
    bannerAd?.destroy()
    manager.unregister(HOME_MREC)   // ← pool 까지 없앰
}
```

`unregister`/`stop`은 아직 노출되지 않은 **버퍼 광고까지 폐기**합니다. 다음
화면 진입 때 버퍼를 통째로 다시 요청하므로, 화면을 오갈 때마다 노출 없는
요청이 쌓입니다. pool은 application 또는 광고 controller 범위에서 유지하고,
화면은 자기가 받아 간 `BannerAd`만 destroy 하세요.

`stop`/`unregister`는 프리미엄 전환, consent 철회, Remote Config off처럼
**더 이상 광고를 요청하면 안 되는 시점**에만 호출합니다.

## 4. 기존 직접 preload 코드 대체

### 변경 전

```kotlin
val preloadId = "$adUnitId:${adSize.width}x${adSize.height}"
BannerAdPreloader.start(
    preloadId,
    PreloadConfiguration(request),   // bufferSize 미지정 → 기본 2
    object : PreloadCallback {
        override fun onAdPreloaded(preloadId: String, responseInfo: ResponseInfo) { ... }
        override fun onAdFailedToPreload(preloadId: String, adError: LoadAdError) { ... }
        override fun onAdsExhausted(preloadId: String) { ... }
    },
)
val ad = BannerAdPreloader.pollAd(preloadId)
```

### 변경 후

```kotlin
manager.registerIfAbsent(key = HOME_MREC, request = request, bufferSize = 1)
manager.addListener(HOME_MREC, object : BannerAdLoadListener {
    override fun onAdFailedToPreload(key: String, error: LoadAdError) {
        analytics.logNoFill(key, error)
    }
})
manager.start(HOME_MREC)

val ad = manager.awaitAd(HOME_MREC)
```

preloadId 문자열 조합, SDK 초기화 전 `IllegalStateException` 방어,
callback thread 처리, 만료 판정이 전부 라이브러리로 넘어갑니다.

## 5. 만료 처리

`maxAdAgeMs` 기본값은 1시간입니다. 만료된 광고를 poll하면 **그 광고만** 버리고
pool은 그대로 둡니다. SDK가 소비한 만큼만 refill 하므로 재시작 비용이 없습니다.

광고를 소비하지 않고 오래된 재고만 정리하고 싶으면 `pruneExpired(key)`를
직접 호출하세요. `isAdAvailable`, `getNumAdsAvailable`, `getState`는 부작용이
없는 순수 조회라서 자동으로 정리하지 않습니다.

## 6. 주의할 점

- **같은 `BannerAd`를 두 뷰에 붙이지 마세요.** 하나의 슬롯이 하나의 광고를
  소유합니다.
- **같은 key에 대해 `awaitAd()`를 동시에 여러 번 호출하면 각 호출이 서로 다른
  광고를 소비합니다.** 논리적으로 한 슬롯이라면 앱 계층에서 single-flight로
  묶으세요.
- `register()`는 설정이 같아도 **무조건** stop → destroy → start 합니다.
  화면 진입마다 호출되는 자리에는 `registerIfAbsent()`를 쓰세요.
- collapsible 배너나 mediation extras가 필요하면 `BannerAdRequest`를 직접
  만들어 넘기세요. `(adUnitId, adSize)` 오버로드는 기본 요청만 만듭니다.
- 자동 갱신 주기는 AdMob 권장 최소 60초를 지키고, 이전 광고가 실제로 노출된
  뒤에 갱신하세요. 노출 전에 교체하면 요청만 늘고 수익은 늘지 않습니다.

## 적용 체크리스트

- [ ] Manager를 Application 또는 app-scoped controller에 한 번 생성
- [ ] (광고 유닛, 크기) 조합별 안정적인 key 정의
- [ ] `bufferSize = 1` 명시 (미지정 시 SDK 기본 2)
- [ ] UMP consent와 SDK 초기화 후 `start`
- [ ] premium/consent 철회/Remote Config off 시 `stop` 또는 `stopAll`
- [ ] 화면 dispose 에서 pool 이 아니라 `BannerAd`만 destroy
- [ ] 논리 슬롯당 single-flight 보장
- [ ] 갱신 주기 60초 이상, 노출 확인 후 갱신

## 공식 자료

- [BannerAdPreloader API](https://developers.google.com/admob/android/next-gen/reference/com/google/android/libraries/ads/mobile/sdk/banner/BannerAdPreloader)
- [PreloadConfiguration API](https://developers.google.com/admob/android/next-gen/reference/com/google/android/libraries/ads/mobile/sdk/common/PreloadConfiguration)
- [Next-Gen callback thread 처리](https://developers.google.com/admob/android/next-gen/migration/handle-callbacks)
