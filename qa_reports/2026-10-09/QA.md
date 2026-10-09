# 실기기 광고·UI QA — 2026-10-09

최종 수정 APK를 연결된 Pixel 9에 설치하여 광고 로드·노출, 실제 터치, 외부 화면 이동, 앱 복귀와 UI 배치를 확인했다. 네이티브 8종 × 이미지/동영상 16개 경우 모두 SDK 검증과 클릭을 통과했고, 전면 광고는 표시·닫기 3회가 통과했다. 테스트한 Google 광고에서 추가로 재현되는 클릭 불능이나 버튼 가림은 없었다.

> 이 보고서의 실기기 결과는 `1.9.5` 기준이다. 후속 `1.9.6`에서는 요청에 따라 Headline / Small / Icon 동영상의 Medium 자동 전환 분기를 제거했다. 해당 3종의 동영상 검증·클릭 결과는 전환 분기가 있던 당시의 결과다. 후속 변경은 Debug/Release 컴파일, 샘플 Debug 빌드 및 기존 테스트 74개 통과를 확인했다.

## 기기와 설치 APK

| 항목 | 확인 값 |
|---|---|
| 기기 | Pixel 9 / `49111FDAQ002NS` |
| OS | Android 17 / API 37 |
| 화면 | 1080 × 2424, 기존 density override 460 유지 |
| 패키지 | `com.soosu.nextgen.admobnative.sample` |
| 샘플 버전 | 1.1.5 / 101000, minSdk 24, targetSdk 36 |
| 광고 SDK | Next-Gen 1.5.0 |
| 최종 프로세스 | PID 16448 / MainActivity foreground |
| 설치 APK SHA-256 | `862285210fbb6acad2424626710c886f45613d4b3ecbfb00836ac3140832ac43` |

빌드 APK와 기기 `base.apk`의 SHA-256이 일치한다. APK v2 서명 검증도 통과했다. QA 종료 후 글꼴 1.0, 자동 회전 1, user_rotation 0, rotation free를 복구했다. [최종 기기 상태](device-final-state.txt)

## 발견하여 수정한 문제

| 재현된 문제 | 수정 및 재확인 |
|---|---|
| Compose 동영상 광고의 CTA가 반응하지 않는 경우 | 자산 뷰의 측정 완료 후 SDK에 등록하고, 같은 MediaContent를 재구성 때마다 다시 설정하지 않도록 수정. 최종 8종 동영상 모두 실제 클릭 콜백과 Google Play 이동 확인 |
| Headline / Small / Icon 동영상의 MediaView 누락 경고 | 동영상은 MediaView가 있는 Medium 레이아웃으로 표시. 이미지 광고의 작은 행 레이아웃은 유지 |
| Full Width 동영상과 텍스트·CTA 영역의 간섭 | 동영상의 제목·본문·CTA를 미디어 아래에 배치. 플레이어 영역과 CTA가 각각 표시됨 |
| Medium 이미지 광고의 자산 경계 경고 | CTA 등록을 해당 행으로 한정하고 미디어 좌우·하단에 1dp 여유를 확보. 같은 Google Ads 이미지와 Flood-It 이미지에서 경고 해소 확인 |
| 가로 배너의 잘림 | 로드 전부터 SDK가 요청한 높이를 확보하고 하단 시스템 영역을 반영. 가로 화면에서 광고 제목과 OPEN, 샘플의 Reload 버튼 모두 표시 |
| 큰 글꼴에서 샘플 조작 영역이 광고 표시 공간을 과도하게 차지 | 템플릿 선택 패널을 접을 수 있도록 변경하고 선택 후 자동으로 접음. 버튼은 FlowRow로 줄바꿈 |
| 밝은 화면의 시스템 바 아이콘 대비 | 샘플의 밝은 테마와 시스템 바 스타일을 맞춰 상태·내비게이션 아이콘 식별 가능 |

Medium 경계 문제는 1dp 여유 적용 전후로 SDK validator 결과가 바뀌었다. SDK 내부 원인까지 확정한 것은 아니다. 이전 경고: [MediaView 누락](native-video-validation-issue.png), [자산 경계](native-image-medium-boundary-issue.png).

자산 등록과 클릭 처리는 [Google의 네이티브 통합 문서](https://developers.google.com/admob/android/next-gen/native/advanced) 및 [공식 Compose 예제](https://github.com/googleads/gma-next-gen-sdk-android-examples/blob/main/kotlin/NextGenExample/app/src/main/java/com/example/nextgenexample/native/NativeComposeUtility.kt)를 기준으로 점검했다.

## 네이티브 광고

최종 APK의 검증 시간은 10:45–10:51 KST. 각 경우 별도의 광고 인스턴스를 로드하고 `hasVideoContent` 값으로 이미지/동영상을 구분했다. 광고 노출 → validator 녹색 → 실제 CTA/자산 터치 → `onAdClicked` → Google Play foreground → Back 복귀 순서로 확인했다. Small / Icon 이미지 행은 제목 자산을 터치했다.

| 템플릿 | 이미지 UI·검증·클릭 | 동영상 UI·검증·클릭 |
|---|---|---|
| Full width | 통과 [화면](native-final-image-full-width.png) | 통과 [화면](native-final-video-full-width.png) |
| Content | 통과 [화면](native-final-image-content.png) | 통과 [화면](native-final-video-content.png) |
| Install | 통과 [화면](native-final-image-install.png) | 통과 [화면](native-final-video-install.png) |
| Headline | 통과 [화면](native-final-image-headline.png) | 통과 [화면](native-final-video-headline.png) |
| Small | 통과 [화면](native-final-image-small.png) | 통과 [화면](native-final-video-small.png) |
| Icon | 통과 [화면](native-final-image-icon.png) | 통과 [화면](native-final-video-icon.png) |
| Medium | 통과 [화면](native-final-image-medium.png) | 통과 [화면](native-final-video-medium.png) |
| Large | 통과 [화면](native-final-image-large.png) | 통과 [화면](native-final-video-large.png) |

제목, 광고 표시, CTA, 미디어, 재생 컨트롤의 배치를 캡처로 확인했다. 긴 카드와 큰 글꼴에서는 세로 스크롤 후 CTA 전체를 화면 안에 놓고 터치했다. 텍스트의 설정된 말줄임과 카드의 스크롤을 버튼 잘림으로 판정하지 않았다. Validator 팝업은 닫은 후 클릭을 시험했다. [Validator](https://developers.google.com/admob/android/next-gen/native/validator) 통과와 실제 클릭 성공을 각각 확인했다.

최종 빌드의 Full Width는 글꼴 1.3에서도 [이미지 UI](native-final-font-130-image.png), [동영상 검증](native-final-font-130-video-validator.png), [스크롤 후 CTA](native-final-font-130-video.png)를 확인했다. 11:07:38 KST에 동영상 CTA 클릭 콜백과 Google Play 이동을 추가 확인했다. [큰 글꼴 결과](native-final-font-130-result.json)

16개 경우의 클릭 시간·목적지·콜백은 [결과 JSON](native-final-matrix.json)에 기록되어 있다. 각 `native-final-{image|video}-{template}-validator.png`는 SDK 검증 화면이다.

## 배너·전면 광고·앱 오픈

| 항목 | 결과와 증거 |
|---|---|
| Adaptive banner 세로 / 글꼴 1.3 | 로드·노출, 제목과 Reload / OPEN 표시 확인. [화면](banner-final-font-130-portrait.png) |
| Adaptive banner 가로 / 글꼴 1.3 | SDK 요청 높이 안에서 광고 제목·OPEN·샘플 조작 버튼 표시, Reload 후 재로드 확인. [화면](banner-final-font-130-landscape.png) |
| 배너 실제 클릭 | 10:54:47.930 KST `Banner clicked` 및 Chrome foreground 확인 후 Back 복귀 |
| Interstitial 반복 | 10:59–11:01 KST 표시·노출·닫기 각 3회. `Shown: 3 · Dismissed: 3` 확인. [최종 카운터](interstitial-final-3-dismissed.png), [콜백](interstitial-final-matrix.json) |
| 전면 광고 중 홈 → 최근 앱 → 복귀 | SDK AdActivity로 복귀하며 광고 유지. 추가 앱 오픈 광고가 겹쳐 뜨지 않음. [복귀 화면](interstitial-final-2-background-return.png) |
| 전면 광고 중 회전 요청 | 해당 SDK 광고는 세로 방향 유지. 닫기 버튼 `[974,207][1046,281]`이 화면 안에 있고 터치로 닫힘. [화면](interstitial-final-2-rotation-attempt.png) |
| 전면 광고 Reload 연속 2회 | 로딩 중 이전 광고 참조를 비우고 Show 버튼 비활성화 확인. [화면·UI 트리](interstitial-final-reload.xml) |
| 앱 오픈 | 테스트 광고 표시와 닫은 후 메인 복귀 확인. [화면](app-open-final.png) |

가로 배너의 SDK 테스트 소재는 짧은 형태로 바뀌며 세로 소재의 본문 전체를 표시하지 않는다. 앱의 버튼이나 광고 OPEN이 영역 밖으로 잘리는 현상은 재현되지 않았다. 회전·크기 변경 때 이전 배너 요청에서 `CANCELLED (5)` 1건을 관찰했으며 곧 새 배너 로드·노출이 성공했다. 최종 앱 PID의 AndroidRuntime fatal/crash는 관찰되지 않았다.

## 미디에이션과 의존성

ONEWallet, BusanBus, ONEDiary, NotiAlarm의 Gradle / version catalog에서 사용하는 네트워크를 조사하여 샘플에 추가했다. 라이브러리 소비 앱에 어댑터를 강제로 전파하지 않도록 샘플 의존성으로 추가했다.

| 샘플 어댑터 | 버전 | 실기기 확인 |
|---|---|---|
| Meta | 6.22.0.1 | 클래스 포함 확인. QA에 사용한 ONEWallet 앱 설정의 초기화 목록에는 없음 |
| Pangle | 8.3.0.4.0 | COMPLETE, Inspector에서 초기화 493ms. 런타임 표시 버전은 adapter 8.3.4.0 / SDK 8.3.4 |
| Liftoff / Vungle | 7.7.8.1 | COMPLETE, adapter 초기화 227ms / SDK 7.7.8 |
| Unity | 4.21.0.0 | COMPLETE, adapter 초기화 1108ms / 별도 SDK 4.21.0 포함 |
| InMobi | 11.5.0.0 | COMPLETE, adapter 초기화 507ms / SDK 11.5.0 |

[런타임 클래스·상태](adapter-final-status.png), [Ad Inspector 광고 응답](adapter-final-inspector.png), [Vungle](adapter-final-vungle.png), [Pangle](adapter-final-pangle.png), [InMobi·Unity](adapter-final-unity.png), [상세 UI 데이터](adapter-final-matrix.json).

등록된 서비스 앱 ID와 테스트 기기 설정으로 초기화를 확인했으며 광고 요청은 Google demo unit만 사용했다. 실제 fill source도 AdMob Network였다. **타사 네트워크별 광고 송출·소재별 UI와 클릭은 이번 QA로 검증되지 않았다.** Meta의 초기화도 별도 확인이 필요하다. 해당 검증에는 네트워크 매핑된 테스트 광고 단위와 각 네트워크의 테스트 모드가 필요하다. [Google 테스트 광고 안내](https://developers.google.com/admob/android/next-gen/test-ads)

주요 의존성은 Next-Gen 1.5.0, Compose BOM 2026.09.00, Lifecycle 2.11.0, Coroutines 1.11.0, Core 1.19.1, Activity Compose 1.13.0으로 갱신했다. UMP 4.0.0, Palette 1.0.0, JUnit 4.13.2는 확인한 최신 안정 버전을 유지했다. 빌드 도구는 AGP 9.4.1, Kotlin / Compose compiler 2.4.21, Java 17, compileSdk 37.2다. Gradle은 Kotlin의 지원 범위에 맞춰 9.7.0을 사용한다. [Kotlin 호환 범위](https://kotlinlang.org/docs/gradle-configure-project.html), [광고 SDK 릴리스](https://developers.google.com/admob/android/next-gen/rel-notes)

Next-Gen과 중복되는 legacy `play-services-ads` / `ads-lite`는 어댑터 경로에서 제외했다. Pangle 저장소도 해당 SDK 그룹에 한정했다. [Next-Gen 미디에이션 문서](https://developers.google.com/admob/android/next-gen/mediation)

## 빌드·테스트·Git 상태

- `:testDebugUnitTest` — 74개, 실패·오류·스킵 0. 기존 초기화 / 취소 / 풀 / 라이프사이클 회귀 검증 포함.
- `:assembleRelease`, `:sample:assembleDebug`, `:sample:assembleRelease`, `:publishToMavenLocal` — BUILD SUCCESSFUL.
- 샘플 Release APK는 unsigned QA 빌드이며 배포용 서명 APK가 아니다.
- `git diff --check` — 통과.
- 기존 `1.9.4` 커밋·푸시·태그는 `3a51a2d981f4706b6af040b4ae39a911f9595647` / SDK 1.3.1이다. 원격 main과 태그의 대상 커밋을 다시 확인했다.
- 최신 의존성 갱신과 QA 수정은 `b6b451d`로 main에 반영했다. 후속 릴리스 `1.9.5`에서 Maven publication 버전과 설치 안내를 올리고 같은 이름의 태그로 배포한다. 기존 `1.9.4` 태그는 유지한다.

기기 설정 복구 및 설치 APK 일치 여부는 [기기 상태](device-final-state.txt), 주요 광고 콜백은 [이벤트 기록](device-final-events.txt)에 보관했다. 빌드와 전체 logcat 원본은 같은 폴더의 Git 제외된 `.log` 파일로 남아 있다.
