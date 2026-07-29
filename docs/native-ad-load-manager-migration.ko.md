# NativeAdLoadManager 마이그레이션 가이드

> 배포 상태: `NativeAdLoadManager`는 `1.7.0` 태그 이후에 추가되었습니다.
> 현재 소스 브랜치 또는 이 기능을 포함해 새로 태깅한 후속 버전을 사용해야
> 합니다. 배포된 `1.7.0` artifact에는 이 클래스가 없습니다.

## 결론

`NativeAdLoadManager`는 앱마다 반복되던 Native 광고의 로드, 재시도,
메모리 캐시, 동시성 처리를 Next-Gen SDK의 `NativeAdPreloader` 기반으로
통합합니다.

권장 경계는 다음과 같습니다.

- 라이브러리: 광고 요청 등록, key별 버퍼, 자동 refill, 대기, 만료,
  callback thread 전환, 소유권 이전
- 앱: UMP consent, 유료 사용자, Remote Config, 네트워크 정책,
  placement/slot 배정, 노출 주기, impression/click 분석

따라서 라이브러리를 업데이트하는 것만으로 기존 `NativeAdLoader.load()` 호출이
자동으로 바뀌지는 않습니다. 각 앱에서 기존 로드 지점을 Manager API로 한 번
마이그레이션해야 합니다.

## 핵심 API

| API | 역할 |
|---|---|
| `register(key, request, bufferSize, maxAdAgeMs)` | 완성된 요청을 독립 pool로 등록 또는 교체 |
| `start(key)` | SDK 초기화와 광고 허용 여부를 확인한 뒤 앱이 명시적으로 preload 시작 |
| `pollNativeAd(key)` | 준비된 표준 `NativeAd`를 즉시 소비 |
| `awaitNativeAd(key, timeoutMs)` | 시작된 pool에 광고가 없으면 timeout까지 기다린 뒤 소비 |
| `pollResult(key)` / `awaitResult(key)` | Native, custom native, banner 혼합 결과를 그대로 소비 |
| `isAdAvailable(key)` | 현재 유효한 광고가 준비됐는지 확인 |
| `getNumAdsAvailable(key)` | 해당 key에 준비된 광고 수 확인 |
| `addListener(key, listener)` | 상태, preload 성공, 소진, 원본 `LoadAdError` 수신 |
| `refresh(key)` | 현재 설정으로 pool 전체 재시작 |
| `stop(key)` | 설정은 유지하고 SDK queue만 중단/정리 |
| `unregister(key)` | queue, 설정, listener 제거 |

`bufferSize`는 전체 앱 공용 수치가 아니라 **key별 pool 수치**입니다. 기본값은
Native 광고의 메모리 사용량을 고려해 `1`입니다.

## 1. Application 범위에 Manager 생성

Manager는 화면마다 만들지 말고 앱 또는 광고 controller 범위에서 한 번
생성합니다.

```kotlin
class MyApplication : Application() {
    val nativeAdLoadManager = NativeAdLoadManager()

    override fun onCreate() {
        super.onCreate()

        // register는 SDK 초기화 전에도 가능합니다.
        nativeAdLoadManager.register(
            key = HOME_FEED,
            request = createNativeAdRequestWithDefaults(
                adUnitId = BuildConfig.NATIVE_HOME_AD_UNIT_ID,
            ),
            bufferSize = 1,
        )
    }

    companion object {
        const val HOME_FEED = "home-feed"
    }
}
```

Manager는 `adUnitId`만 받지 않고 완성된 `NativeAdRequest`를 받습니다. 그래야
다음과 같은 기존 앱별 설정이 유실되지 않습니다.

- `NativeMediaAspectRatio.SQUARE` / `ANY`
- keyword와 mediation extras
- custom format ID
- banner size가 포함된 Native 요청
- video, AdChoices, image, custom gesture 옵션

## 2. Consent와 SDK 초기화 후 시작

`register()`는 설정만 저장합니다. 실제 로드는 UMP consent 처리 및
`MobileAds.initialize()`가 끝난 뒤 시작합니다.

```kotlin
suspend fun updateNativeAdPolicy(
    manager: NativeAdLoadManager,
    canRequestAds: Boolean,
    isPremium: Boolean,
    remoteConfigEnabled: Boolean,
) {
    val enabled = canRequestAds && !isPremium && remoteConfigEnabled

    if (enabled) {
        manager.start(MyApplication.HOME_FEED)
    } else {
        manager.stop(MyApplication.HOME_FEED)
    }
}
```

Manager 내부에는 특정 앱의 consent, premium, Remote Config 판단을 넣지
않습니다. 앱의 기존 정책을 위와 같이 `start/stop` 경계에 유지합니다.
`poll*`과 `await*`는 중지된 pool을 자동으로 시작하지 않으므로 이 정책을
우회하지 않습니다.

## 3. 직접 로드 코드를 변경

### 변경 전

```kotlin
val request = NativeAdRequest.Builder(
    adUnitId,
    listOf(NativeAd.NativeAdType.NATIVE),
).build()

NativeAdLoader.load(request, object : NativeAdLoaderCallback {
    override fun onNativeAdLoaded(ad: NativeAd) {
        nativeAd = ad
    }

    override fun onAdFailedToLoad(error: LoadAdError) {
        // 앱의 retry
    }
})
```

### 변경 후: 즉시 소비

```kotlin
val ad = nativeAdLoadManager.pollNativeAd(MyApplication.HOME_FEED)
if (ad != null) {
    nativeAd = ad
}
```

### 변경 후: 준비될 때까지 대기

아래 호출 전에 해당 key의 `start()`가 성공했어야 합니다. key가 등록되지
않았거나 pool이 중지된 상태라면 timeout을 기다리지 않고 `null`을 반환합니다.

```kotlin
val ad = nativeAdLoadManager.awaitNativeAd(
    key = MyApplication.HOME_FEED,
    timeoutMs = 8_000L,
)

if (ad != null) {
    nativeAd = ad
} else {
    // 광고 영역 숨김 또는 placeholder 유지
}
```

SDK preloader가 자동 refill과 실패 후 재시도를 담당하므로 기존
`maxRetries`, exponential backoff, `NO_FILL` 재시도 loop는 제거할 수
있습니다. `onAdFailedToPreload`는 최종 실패가 아니라 상태/분석 이벤트입니다.

## 4. Compose 화면

```kotlin
@Composable
fun HomeNativeAd(
    manager: NativeAdLoadManager,
) {
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }

    LaunchedEffect(manager) {
        nativeAd = manager.awaitNativeAd(
            key = MyApplication.HOME_FEED,
            timeoutMs = 8_000L,
        )
    }

    DisposableEffect(nativeAd) {
        val ownedAd = nativeAd
        onDispose {
            ownedAd?.destroy()
        }
    }

    nativeAd?.let {
        NativeAdSmallBox(nativeAd = it)
    }
}
```

`awaitNativeAd()`는 coroutine 취소 시 listener를 해제합니다. 취소와 광고
도착이 경합해 호출자에게 전달되지 못한 광고도 Manager가 정리합니다.

주의:

- `register()`를 Composable 본문에서 호출하지 않습니다.
- 같은 `NativeAd`를 동시에 두 개의 `NativeAdView`에 등록하지 않습니다.
- 화면에서 받은 광고는 `DisposableEffect`, `ViewModel.onCleared()`,
  `Activity.onDestroy()` 등 실제 소유자의 lifecycle에서 `destroy()`합니다.

## 5. View / Activity

```kotlin
class DetailActivity : ComponentActivity() {
    private val nativeAdManager
        get() = (application as MyApplication).nativeAdLoadManager
    private lateinit var nativeAdTemplateView: NativeAdTemplateView
    private var nativeAd: NativeAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)
        nativeAdTemplateView = findViewById(R.id.native_ad)

        lifecycleScope.launch {
            nativeAd = nativeAdManager.awaitNativeAd("detail", 8_000L)
            nativeAd?.let(nativeAdTemplateView::setNativeAd)
        }
    }

    override fun onDestroy() {
        nativeAd?.destroy()
        nativeAd = null
        super.onDestroy()
    }
}
```

Manager는 raw Next-Gen `NativeAd`를 반환하므로 Compose template,
`ComposeView`, `NativeAdTemplateView`, 직접 만든 `NativeAdView` 모두 같은
방식으로 사용할 수 있습니다.

## 6. ViewModel과 StateFlow

라이브러리는 앱의 상태 모델을 강제하지 않기 위해 public `StateFlow` 대신
listener를 제공합니다. 앱 ViewModel에서 필요한 형태로 변환합니다. Kotlin은
`NativeAdLoadListener`를 직접 구현하고, Java는
`NativeAdLoadListenerAdapter`를 상속하면 필요한 callback만 override할 수
있습니다.

```kotlin
class HomeAdViewModel(
    private val manager: NativeAdLoadManager,
) : ViewModel(), NativeAdLoadListener {
    private val _state = MutableStateFlow<NativeAdLoadState?>(null)
    val state = _state.asStateFlow()
    private val _nativeAd = MutableStateFlow<NativeAd?>(null)
    val nativeAd = _nativeAd.asStateFlow()

    init {
        manager.addListener(MyApplication.HOME_FEED, this)
    }

    override fun onStateChanged(
        key: String,
        state: NativeAdLoadState,
    ) {
        _state.value = state
    }

    override fun onAdPreloaded(
        key: String,
        availableCount: Int,
        responseInfo: ResponseInfo,
    ) {
        acquireIfNeeded()
    }

    override fun onAdFailedToPreload(
        key: String,
        error: LoadAdError,
    ) {
        // 앱 analytics가 있다면 여기서 원본 error를 기록합니다.
    }

    fun acquireIfNeeded() {
        if (_nativeAd.value != null) return
        _nativeAd.value = manager.pollNativeAd(MyApplication.HOME_FEED)
    }

    fun releaseCurrentAd() {
        _nativeAd.value?.destroy()
        _nativeAd.value = null
        acquireIfNeeded()
    }

    override fun onCleared() {
        manager.removeListener(MyApplication.HOME_FEED, this)
        _nativeAd.value?.destroy()
        _nativeAd.value = null
        super.onCleared()
    }
}
```

Callback은 SDK callback stack이 끝난 다음 main thread로 전달됩니다.
`onAdPreloaded`의 `ResponseInfo` 순서와 `pollResult()`가 반환하는 광고 순서는
일치한다고 보장되지 않으므로 둘을 위치 기반으로 연결하면 안 됩니다.

## 7. 여러 광고 유닛과 요청 옵션

광고 유닛 또는 요청 옵션이 다르면 key도 나눕니다.

```kotlin
manager.register(
    key = "home-compact",
    request = nativeAdRequestBuilder(homeAdUnitId)
        .setMediaAspectRatio(NativeAd.NativeMediaAspectRatio.ANY)
        .build(),
)

manager.register(
    key = "alarm-square",
    request = nativeAdRequestBuilder(alarmAdUnitId)
        .setMediaAspectRatio(NativeAd.NativeMediaAspectRatio.SQUARE)
        .build(),
)

manager.startAll()
```

같은 광고 유닛이라도 aspect ratio, keyword, custom format 등 요청이
다르면 별도 key가 안전합니다.

### Remote Config로 광고 유닛 또는 keyword 변경

같은 key를 다시 `register()`하면 active queue를 정리하고 새 요청으로
재시작합니다. 기존 listener는 유지됩니다.

```kotlin
manager.register(
    key = "home-compact",
    request = nativeAdRequestBuilder(remoteAdUnitId)
        .addKeyword(currentCategory)
        .build(),
)
```

요청이 바뀌지 않았는데 화면 재구성마다 다시 등록하면 불필요한 restart가
발생합니다. Remote Config 값이 실제로 변경된 시점에만 재등록합니다.

## 8. 리스트와 여러 slot

리스트 첫 화면에 서로 다른 광고 두 개가 즉시 필요할 때만 해당 key의
버퍼를 늘립니다.

> Mediation 주의: 공식 가이드의 다중 Native 로드 API에는 mediated ad unit
> 제한이 있고 `NativeAdPreloader` 문서는 adapter별 다중 버퍼 동작을 별도로
> 보장하지 않습니다. mediation은 buffer 1을 기본으로 두고, 2 이상은 실제
> ad unit과 adapter 조합을 실기기에서 검증한 뒤 사용하세요.

```kotlin
manager.register(
    key = "inline-list",
    request = request,
    bufferSize = 2,
)
```

```kotlin
val first = manager.pollNativeAd("inline-list")
val second = manager.pollNativeAd("inline-list")
```

`pollNativeAd()`를 호출한 순간 각 광고의 소유권은 호출자로 이전됩니다.
SDK가 소비된 수만큼 pool을 다시 채웁니다.

`slotKey -> NativeAd`의 안정적인 배정은 화면/목록 정책이므로 앱에 둡니다.

```kotlin
private val slotAds = mutableMapOf<String, NativeAd>()

fun acquire(slotKey: String): NativeAd? {
    return slotAds.getOrPut(slotKey) {
        manager.pollNativeAd("inline-list") ?: return null
    }
}

fun release(slotKey: String) {
    slotAds.remove(slotKey)?.destroy()
}
```

이 방식은 같은 광고 인스턴스가 동시에 여러 slot에 배정되는 문제를
방지합니다.

## 9. Custom Native와 Banner 결과

`NativeAdPreloader.pollAd()`는 세 종류를 반환할 수 있습니다.

- `NativeAdLoadResult.NativeAdSuccess`
- `NativeAdLoadResult.CustomNativeAdSuccess`
- `NativeAdLoadResult.BannerAdSuccess`

혼합 요청은 `pollNativeAd()`가 아니라 `pollResult()`를 사용합니다.

```kotlin
when (val result = manager.pollResult("mixed")) {
    is NativeAdLoadResult.NativeAdSuccess -> render(result.ad)
    is NativeAdLoadResult.CustomNativeAdSuccess -> renderCustom(result.ad)
    is NativeAdLoadResult.BannerAdSuccess -> renderBanner(result.ad)
    null -> Unit
}
```

각 분기에서 받은 광고도 사용 종료 시 `destroy()`해야 합니다.

## 10. 광고 수명과 refresh

Native 광고는 장시간 캐시하지 않습니다. Manager가 queue에서 호출자에게
반환하는 광고의 기본 최대 age는 공식 권장에 맞춘 1시간이며
`SystemClock.elapsedRealtime()` 기준으로 검사합니다. 이 검사는
`poll*`, `isAdAvailable`, `getState`처럼 pool에 접근할 때 지연 수행됩니다.
이미 `poll*`/`await*`로 전달된 광고의 age와 교체 주기는 호출 앱이
관리해야 합니다.

10분처럼 더 짧은 앱 정책이 필요하면 key에 설정합니다.

```kotlin
manager.register(
    key = "short-lived-inline",
    request = request,
    maxAdAgeMs = 10 * 60 * 1_000L,
)
```

사용자 이벤트나 Remote Config 갱신 시 queue 전체를 즉시 교체하려면
`refresh(key)`를 호출할 수 있습니다.

## 11. 소유권 규칙

| 상태 | 광고 소유자 | 정리 방법 |
|---|---|---|
| SDK pool에 대기 중 | SDK/Manager | `stop`, `unregister`, `close` |
| `poll*`/`await*` 반환 후 | 호출 앱 | 반드시 `ad.destroy()` |
| coroutine 취소 전에 poll됐지만 전달 실패 | Manager | 자동 정리 |
| 다른 result type을 convenience API로 잘못 poll | Manager | 자동 정리 |

`stop()` 또는 `unregister()`는 이미 화면에 전달한 광고를 destroy하지
않습니다. 화면 소유권과 queue 소유권을 섞지 않기 위한 동작입니다.

## 12. 기존 프로젝트 패턴별 대응

| 기존 구현 | 마이그레이션 |
|---|---|
| 단일 `NativeAd?` 수동 캐시 | buffer 1 + `start` + `pollNativeAd` |
| `takeOrLoad()` | `awaitNativeAd(timeoutMs)` |
| placement별 map | placement를 key로 등록 |
| 두 광고 inline pool | 해당 key만 buffer 2 |
| ViewModel `StateFlow` | listener를 앱의 StateFlow로 변환 |
| Activity 간 광고 전달 | Application manager에서 poll 후 명시적 소유권 전달 |
| Compose별 직접 로드 | Application register + Composable await + DisposableEffect destroy |
| SQUARE/ANY 요청 | 완성된 요청을 서로 다른 key로 등록 |
| keyword/광고 유닛 동적 변경 | 값 변경 시 같은 key 재등록 |
| 수동 retry/backoff | 제거; SDK refill/retry + listener 분석 |
| 10분 cache | `maxAdAgeMs = 10분` |
| stable list slot | 앱의 `slotKey -> NativeAd` map 유지 |

## 13. 호환성과 단계적 적용

- 기존 `NativeAdLoaderHelper.loadWithRetry()`는 삭제되거나 변경되지
  않았습니다.
- 기존 template과 `NativeAdTemplateView` API도 그대로입니다.
- 따라서 앱별로 한 placement씩 단계적으로 Manager로 전환할 수 있습니다.
- 아직 전환하지 않은 placement는 기존 직접 로드 방식을 계속 사용할 수
  있습니다.
- 구형 `com.google.android.gms.ads.nativead.NativeAd`는 Next-Gen
  `NativeAd`와 타입이 다르므로 먼저 Next-Gen SDK 마이그레이션이 필요합니다.

## 적용 체크리스트

- [ ] Manager를 Application 또는 app-scoped controller에 한 번 생성
- [ ] ad unit/request 옵션별 안정적인 key 정의
- [ ] UMP consent와 SDK 초기화 후 `start`
- [ ] premium/Remote Config off 시 `stop`
- [ ] 화면 직접 로드를 `pollNativeAd` 또는 `awaitNativeAd`로 교체
- [ ] 수동 retry loop 제거
- [ ] poll된 모든 광고의 `destroy()` 경로 확인
- [ ] 동일 광고를 두 `NativeAdView`에서 동시에 사용하지 않음
- [ ] mediation 사용 시 buffer 1부터 실기기 검증
- [ ] 상태/에러 분석이 필요하면 listener 연결
- [ ] Remote Config 요청 변경 시에만 재등록
- [ ] 프로세스 재시작 후 다시 register/start되는지 확인

## 공식 자료

- [NativeAdPreloader API](https://developers.google.com/admob/android/next-gen/reference/com/google/android/libraries/ads/mobile/sdk/nativead/NativeAdPreloader)
- [Native 광고 가이드와 1시간 캐시 권장](https://developers.google.com/admob/android/next-gen/native)
- [Next-Gen callback thread 처리](https://developers.google.com/admob/android/next-gen/migration/handle-callbacks)

## 1.7.4 변경 사항 — 요청 수 과다 수정

실기기 계측(Pixel 9)에서 확인한 과요청 경로를 정리한 릴리스입니다.
`InterstitialAdLoadManager`에도 동일하게 적용됩니다.

### 동작 변경

| 항목 | 이전 | 1.7.4 |
|---|---|---|
| SDK 초기화 전 `await*()` | 즉시 `null` 반환 | `startPending`이면 timeout까지 대기 |
| 만료 광고 poll | 광고 폐기 + **pool 전체 재시작** | 해당 광고만 폐기, SDK 1:1 refill에 위임 |
| 광고 load 시각을 모를 때 | pool 시작 시각으로 대체 판정 → 만료 처리 | age unknown으로 보고 만료로 처리하지 않음 |
| `getState` / `getNumAdsAvailable` / `isAdAvailable` | 내부에서 poll·destroy·restart 발생 | 부작용 없는 순수 조회 |

`await*()`가 즉시 `null`을 반환하던 문제가 가장 파급이 컸습니다. 앱들이 이를
"로드 실패"로 처리해 재시도하는 동안, 정작 pool에 미리 채워둔 광고는 노출 없이
버려졌습니다.

### 추가된 API

- `registerIfAbsent(key, ...)` — 이미 있는 pool은 건드리지 않습니다.
  `register()`는 설정이 같아도 **무조건** stop → destroy → start 하므로
  버퍼를 버리고 `bufferSize`만큼 다시 요청합니다. 화면 진입마다 호출되는
  자리에는 `registerIfAbsent()`를 쓰세요.
- `pruneExpired(key)` — 만료된 광고를 명시적으로 정리합니다. 조회 API가 더 이상
  자동으로 정리하지 않으므로, 필요한 시점에 직접 호출합니다.
- `isStartPending(key)` — SDK 초기화를 기다리는 중인지 확인합니다.

### 앱에서 함께 확인할 것

- `bufferSize`를 지정하지 않으면 **SDK 기본값 2**가 적용됩니다(배너·전면 실측).
  슬롯이 동시에 2개를 소비하지 않는다면 1로 지정하세요.
- preload pool을 화면 dispose마다 `destroy()`하지 마세요. 노출되지 않은 버퍼가
  버려지고 다음 진입 때 통째로 다시 요청됩니다.
- pool key에 사용자 데이터(예: 항목 제목)를 넣으면 항목 수만큼 pool이 늘어나고,
  각 pool이 prefill 요청을 낸 뒤 마지막 refill 광고를 계속 들고 있습니다.
- 같은 논리 슬롯에 대해 `await*()`를 동시에 여러 번 호출하면 각 호출이 서로 다른
  광고를 소비합니다. 앱 계층에서 single-flight로 묶으세요.
- 프리미엄 전환·consent 철회·Remote Config off 시점에 `stopAll()`을 호출하세요.
