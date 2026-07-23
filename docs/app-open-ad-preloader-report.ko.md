# AppOpenAd Preloader 적용 검토 리포트

- 작성 기준: 2026-07-23
- 대상: `NextGen-Admob-Native-Template-Compose`
- 적용 예정 라이브러리 버전: `1.7.0`
- GMA Next-Gen SDK: `1.3.0`

## 1. 결론

Google Mobile Ads SDK의 버전만 올리면 `AppOpenAdPreloader`가 자동으로
활성화되는 것은 아니다. 앱 또는 래퍼 라이브러리가 다음 호출 흐름을 직접
구현해야 한다.

1. `AdRequest`와 `PreloadConfiguration` 생성
2. `AppOpenAdPreloader.start(preloadId, configuration)` 호출
3. 표시 시 `AppOpenAdPreloader.pollAd(preloadId)` 호출
4. 반환된 `AppOpenAd`에 이벤트 콜백을 연결한 뒤 `show(activity)` 호출

이번 변경에서는 이 흐름을 기존 `AppOpenAdManager` 내부에 넣었다. 따라서
기존 앱이 이 프로젝트의 `AppOpenAdManager`와
`AppOpenAdLifecycleObserver`를 문서대로 사용하고 있다면, 앱 코드를
수정하지 않고 이 라이브러리의 버전만 `1.7.0`으로 올려 Preloader를 적용할
수 있다.

단, 앱이 Google의 `AppOpenAd.load()`를 직접 호출하거나
`SplashAdLoader`만 사용하는 경우에는 자동 적용되지 않는다.

## 2. 자동 적용 범위

| 기존 앱의 사용 방식 | 라이브러리 버전만 업데이트 | 결과 |
|---|---:|---|
| `AppOpenAdManager` + `AppOpenAdLifecycleObserver` 사용 | 가능 | 기존 `loadAd()`/`showAdIfAvailable()`가 내부적으로 Preloader 사용 |
| `AppOpenAdManager`를 사용하고 수동으로 `loadAd()`/`showAdIfAvailable()` 호출 | 가능 | 공개 API 변경 없이 Preloader 사용 |
| Google `ads-mobile-sdk` 의존성만 업데이트 | 불가능 | `start()`/`pollAd()` 통합 코드가 없으므로 기존 one-shot 로드 유지 |
| 앱에서 Google `AppOpenAd.load()`를 직접 호출 | 불가능 | 이 라이브러리의 Manager를 거치지 않음 |
| `SplashAdLoader.execute()`의 콜드 스타트 광고 | 적용 안 됨 | 제한 시간 내 1회 로드 후 표시하는 기존 흐름 유지 |
| 앱이 광고 SDK를 초기화하지 않음 | 불가능 | Next-Gen SDK는 광고 요청 전에 초기화 필수 |
| 소비 앱이 아주 오래된 Ads SDK를 강제로 고정 | 보장 안 됨 | Preloader 클래스 부재 시 런타임 링크 오류 가능 |

여기서 “버전만 업데이트”는 Google SDK 단독 업데이트가 아니라, 이
래퍼 라이브러리를 `1.7.0`으로 업데이트하는 것을 뜻한다. 이 프로젝트가
GMA SDK를 `api` 의존성으로 제공하므로 일반적인 Gradle 구성에서는
`ads-mobile-sdk:1.3.0`도 전이 의존성으로 함께 적용된다.

## 3. 구현 내용

### 기존 API 유지

다음 공개 진입점은 변경하지 않았다.

- `AppOpenAdManager(AdmobConfig)`
- `loadAd(Context)`
- `showAdIfAvailable(Activity, onShowAdComplete, loadAndShowIfMissing)`
- `isAdAvailable()`
- 키워드 추가·삭제 API
- `AppOpenAdLifecycleObserver`의 생성 및 사용 방식

새 설정은 모두 기본값이 있으므로 기존 `AdmobConfig.Builder` 호출은 그대로
컴파일된다.

```kotlin
val config = AdmobConfig.Builder(appId)
    .foregroundAdUnitId(appOpenAdUnitId)
    // 생략해도 아래 값이 기본 적용됨
    .useAppOpenAdPreloader(true)
    .appOpenAdPreloadBufferSize(1)
    .build()
```

### 기본 정책

- `useAppOpenAdPreloader`: 기본 `true`
- `appOpenAdPreloadBufferSize`: 기본 `1`
- 버퍼 크기를 `null`로 설정하면 Google SDK가 크기를 선택한다. 공식 문서상
  현재 선택값은 2이며, 시스템 상태에 따라 제한될 수 있다.
- 광고를 `pollAd()`로 소비하면 SDK가 버퍼를 다시 채운다.
- 로드 실패 재시도와 버퍼 보충 스케줄은 SDK가 관리한다.
- `foregroundAdExpirationMs` 계약을 유지하기 위해 `ResponseInfo.responseId`별
  프리로드 시각을 기록하고, `pollAd()` 전에 만료 여부를 확인한다. 응답 ID가
  없거나 콜백보다 먼저 조회된 광고에는 해당 preload 세대의 시작 시각을
  보수적인 하한으로 사용한다.
- 런타임 키워드가 바뀌면 이전 요청으로 만들어진 버퍼를 폐기하고 새
  요청으로 즉시 다시 시작한다.
- `shouldSuppressAds()`가 `true`이면 표시하지 않을 뿐 아니라 기존 버퍼도
  중지해 추가 요청을 막는다.
- SDK의 광고 로드·이벤트 콜백은 백그라운드 스레드에서 올 수 있으므로,
  광고 완료 콜백과 후속 UI 흐름은 메인 스레드에서 실행한다.

### `loadAndShowIfMissing` 호환

`loadAndShowIfMissing = true`는 기존처럼 “광고가 없으면 로드를 기다렸다가
표시”하는 계약을 유지한다. Preloader가 활성화된 경우에는 별도 one-shot
요청을 중복으로 만들지 않고 프리로드 성공 콜백을 기다린 뒤 버퍼에서 광고를
꺼내 표시한다. 첫 프리로드 실패 시 완료 콜백을 호출하고, SDK의 자동 재시도는
다음 표시 기회를 위해 계속된다.

기존 동작으로 완전히 되돌려야 하는 앱은 다음과 같이 옵트아웃할 수 있다.

```kotlin
AdmobConfig.Builder(appId)
    .useAppOpenAdPreloader(false)
    .build()
```

## 4. 기대 이점

### 4.1 Warm foreground의 광고 준비율 개선

기존 방식은 단일 광고를 앱 코드가 직접 보관하고, 소진 또는 실패 후 다시
로드했다. Preloader는 세션 동안 목표 버퍼를 유지하므로 앱이 백그라운드에서
돌아오는 시점에 광고가 이미 준비되어 있을 가능성이 높다.

그 결과 표시 직전 네트워크 요청을 기다리는 경우와 “아직 광고가 없음”으로
표시 기회를 놓치는 경우를 줄일 수 있다.

### 4.2 자동 보충과 자동 재시도

`pollAd()`로 광고를 꺼내면 SDK가 목표 버퍼 크기까지 다시 채운다. 실패한
프리로드 요청도 SDK가 재시도하므로 다음 코드를 앱에서 직접 유지할 필요가
줄어든다.

- 수동 `isLoading` 상태
- 광고 캐시 보관
- 소진 후 재로드
- 실패 후 재시도
- 버퍼 개수 관리

이 라이브러리는 표시 중복 방지, Activity 상태, 노출 간격과 같은 앱 정책만
계속 담당한다.

### 4.3 반복되는 복귀 기회 대응

한 세션에서 앱 전환이 여러 번 발생해도 버퍼가 자동으로 보충된다. 버퍼를
2개 이상으로 설정한 앱은 짧은 시간 안에 여러 적절한 표시 기회가 생기는
경우에도 두 번째 광고가 준비되어 있을 가능성을 높일 수 있다.

다만 App Open 광고는 사용자 대기 구간에서만 자연스럽게 보여야 하며, 버퍼가
있다는 이유로 표시 빈도를 높여서는 안 된다.

### 4.4 관측성 개선

Preloader는 다음 정보를 제공한다.

- 현재 사용 가능한 광고 수
- 광고 프리로드 성공·실패
- 버퍼 소진 이벤트
- 다음 광고의 `ResponseInfo`

이번 라이브러리는 `getNumPreloadedAds()`를 공개해 기본적인 버퍼 상태를
확인할 수 있게 했다.

## 5. 한계와 비용

### 진짜 콜드 스타트는 해결하지 않음

`AppOpenAdPreloader`는 현재 프로세스 세션의 메모리 버퍼를 관리한다. 따라서
프로세스가 종료된 뒤의 진짜 콜드 스타트에는 이전 세션 광고가 남아 있다고
가정할 수 없다. 첫 실행 또는 프로세스 재시작 시 광고가 준비되지 않았다면
앱 콘텐츠 진행을 막지 않는 기존 원칙을 유지해야 한다.

디스크 기반 콜드 스타트 캐시는 별도의
`AppOpenAdRequest.Builder.setAdPersistenceEnabled()` 기능이다. 이 기능은
`@ExperimentalApi`이고 허용 목록에 포함된 계정만 사용할 수 있으므로 이번
범위에는 포함하지 않았다.

### 메모리와 네트워크 사용

버퍼가 커질수록 동시에 메모리에 보관하는 광고와 미리 수행하는 네트워크
요청이 늘 수 있다. 이 변경안은 초기 자원 비용과 불필요한 선요청을 줄이기
위해 기본값을 1로 선택했다. 이는 Google이 버퍼 크기를 자동 선택할 때의 현재
값 2보다 보수적인 설정이다. 반복되는 warm foreground 기회가 많은 앱은
ready rate와 자원 사용량을 비교한 뒤 2 또는 3으로 늘릴 수 있다.

### 4시간 유효성 규칙

공식 App Open 가이드는 요청 후 4시간이 지난 광고를 표시하지 않도록
안내한다. 이 규칙은 Preloader를 사용해도 그대로 고려해야 한다. 다만
Preloader가 큐 안의 만료 광고를 내부적으로 어떻게 제거하는지는 공식
레퍼런스에 명시되어 있지 않다. 이 라이브러리는 프리로드 콜백의
`ResponseInfo.responseId`별 시각을 기록하고, 다음 광고를 꺼내기 전에 만료
여부를 확인한다. ID로 추적 가능한 만료 광고는 해당 광고만 폐기한다. SDK가
nullable 응답 ID를 반환하거나 콜백 기록 전에 광고가 조회된 경우에는 preload
세대의 시작 시각을 보수적인 하한으로 사용하며, 그 하한까지 만료되면 오래된
광고를 잘못 표시하지 않도록 해당 버퍼를 폐기하고 새 세대로 다시 시작한다.

### SDK가 재시도 주기를 관리

Preloader 활성화 시 `foregroundAdCooldownMs`가 SDK 내부 자동 재시도 간격을
제어하지는 못한다. 이 값은 Preloader를 옵트아웃했을 때 사용하는 one-shot
로더에만 적용된다.

### 수익 상승은 보장할 수 없음

준비율이 개선되면 적절한 광고 표시 기회를 놓치는 비율이 낮아질 수 있지만,
Google은 App Open Preloader 적용에 따른 수익 또는 노출률 상승 수치를
공식적으로 제시하지 않았다. 따라서 “수익 증가 보장”이 아니라 “광고 준비
상태와 표시 응답성 개선”으로 평가해야 한다.

## 6. 권장 검증 지표

릴리스 전후 또는 A/B 테스트에서 다음 지표를 함께 비교하는 것이 좋다.

| 지표 | 목적 |
|---|---|
| 포그라운드 표시 시점의 광고 ready rate | Preloader의 직접 효과 확인 |
| 표시 요청부터 `onAdShowed`까지의 시간 | 체감 지연 개선 확인 |
| 광고 없음으로 건너뛴 횟수 | 놓친 표시 기회 감소 확인 |
| preload 성공·실패 및 버퍼 소진 횟수 | 네트워크·fill 상태 진단 |
| 세션당 App Open 노출 수 | 의도하지 않은 빈도 증가 감시 |
| 사용자 유지율·이탈률 | UX 악화 여부 확인 |
| 세션당 광고 수익 | 준비율 개선의 사업 효과 확인 |
| 메모리·데이터 사용량 | 버퍼 크기의 비용 확인 |

기본 버퍼 1 배포 후 ready rate와 메모리·데이터 사용량을 함께 확인하고,
준비율이 부족하다면 버퍼 2 또는 3과 A/B 비교하는 것을 권장한다.

## 7. SDK 버전과 요구사항

- Preloader API 최초 추가: GMA Next-Gen SDK `0.13.0-alpha01`
- 안정 버전 사용 가능: `1.0.0` 이상
- `destroyAll()`·`peekAdResponseInfo()` 추가: `1.1.0`
- 이 변경에서 사용하는 버전: `1.3.0`
- `minSdk`: 24 이상
- `compileSdk`: 34 이상
- Kotlin: 1.9 이상
- 광고 요청 전에 GMA Next-Gen SDK 초기화 필수

현재 프로젝트의 `minSdk 24`, `compileSdk 36`, Kotlin 2.1, Java 17 구성은
요구사항을 충족한다.

## 8. 공식 근거

- [AppOpenAdPreloader API](https://developers.google.com/admob/android/next-gen/reference/com/google/android/libraries/ads/mobile/sdk/appopen/AppOpenAdPreloader)
- [PreloadConfiguration API](https://developers.google.com/admob/android/next-gen/reference/com/google/android/libraries/ads/mobile/sdk/common/PreloadConfiguration)
- [PreloadCallback API](https://developers.google.com/admob/android/next-gen/reference/com/google/android/libraries/ads/mobile/sdk/common/PreloadCallback)
- [GMA Next-Gen SDK 릴리스 노트](https://developers.google.com/admob/android/next-gen/rel-notes)
- [GMA Next-Gen SDK 설정 및 마이그레이션](https://developers.google.com/admob/android/next-gen/migration)
- [App Open 광고 가이드](https://developers.google.com/admob/android/next-gen/app-open)
- [백그라운드 콜백 처리](https://developers.google.com/admob/android/next-gen/migration/handle-callbacks)
- [App Open 영속 캐시 API](https://developers.google.com/admob/android/next-gen/reference/com/google/android/libraries/ads/mobile/sdk/appopen/AppOpenAdRequest.Builder)
