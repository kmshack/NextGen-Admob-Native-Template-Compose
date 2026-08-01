# NextGen AdMob Native Template Compose

<div align="center">

**A modern, declarative AdMob Native Ads library for Jetpack Compose using GMA Next-Gen SDK**

[![](https://jitpack.io/v/kmshack/NextGen-Admob-Native-Template-Compose.svg)](https://jitpack.io/#kmshack/NextGen-Admob-Native-Template-Compose)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=24)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

</div>

---

## Overview

NextGen AdMob Native Template Compose provides ready-to-use, fully customizable native ad templates built specifically for Jetpack Compose using **Google Mobile Ads Next-Gen SDK**. Seamlessly integrate Google AdMob native ads into your modern Android applications with Material 3 theming support and minimal boilerplate.

### Why This Library?

- **Next-Gen SDK** - Built on Google's latest GMA Next-Gen SDK (beta)
- **Zero Boilerplate** - Drop-in composables with sensible defaults
- **Material 3 Integration** - Automatically adapts to your app's theme
- **Auto Color Extraction** - Intelligent color extraction from ad icons for seamless integration
- **Multiple Templates** - 8 layouts: Small, Icon Small, Medium, Large, Headline, App Install, Content Feed, and Full Width Media
- **Type-Safe** - Fully written in Kotlin with null safety
- **Highly Customizable** - Override colors, modifiers, and styling
- **CTR Optimized** - Premium template designed for maximum click-through rates
- **Lightweight** - Minimal dependencies, maximum performance

---

## Table of Contents

- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Native Ad Preloading](#native-ad-preloading)
- [Interstitial Ad Preloading](#interstitial-ad-preloading)
- [Available Templates](#available-templates)
- [API Reference](#api-reference)
- [Advanced Usage](#advanced-usage)
- [AdMob Initialization, UMP Consent & App Open Ads](#admob-initialization-ump-consent--app-open-ads)
- [Custom Layouts](#custom-layouts)
- [Migration from Legacy SDK](#migration-from-legacy-sdk)
- [Sample App](#sample-app)
- [Dependencies](#dependencies)
- [Contributing](#contributing)
- [License](#license)

---

## Requirements

- **Minimum SDK**: 24 (Android 7.0)
- **Compile SDK**: 36+
- **Kotlin**: 2.0.0+
- **Jetpack Compose**: BOM 2025.06.00+
- **GMA Next-Gen SDK**: 1.3.0+

---

## Installation

### Gradle Setup

**Step 1:** Add the JitPack repository to your `settings.gradle.kts` (or project-level `build.gradle`):

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```


**Step 2:** Add the dependency to your app module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.kmshack:NextGen-Admob-Native-Template-Compose:1.8.0")
}
```

> `NativeAdLoadManager` was added in the `1.7.1` release. The examples below
> require the `1.7.1` (or newer) release; do not expect the class in earlier
> published artifacts.

**Step 3:** Sync your project

---

## Quick Start

### 1. Register and Start a Native Ad Pool

Create one application-scoped manager. Registration is allowed before SDK
initialization; start loading only after consent handling and SDK initialization
are complete.

```kotlin
import android.app.Application
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.soosu.nextgen.admobnative.NativeAdLoadManager
import com.soosu.nextgen.admobnative.createNativeAdRequestWithDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyApplication : Application() {
    val nativeAdLoadManager = NativeAdLoadManager()

    override fun onCreate() {
        super.onCreate()

        nativeAdLoadManager.register(
            key = HOME_NATIVE_POOL,
            request = createNativeAdRequestWithDefaults("YOUR_AD_UNIT_ID"),
            bufferSize = 1,
        )

        // In production, complete UMP consent handling before starting the pool.
        CoroutineScope(Dispatchers.IO).launch {
            MobileAds.initialize(
                this@MyApplication,
                InitializationConfig.Builder("YOUR_ADMOB_APP_ID").build(),
            ) {
                nativeAdLoadManager.start(HOME_NATIVE_POOL)
            }
        }
    }

    companion object {
        const val HOME_NATIVE_POOL = "home-native"
    }
}
```

### 2. Consume and Display a Native Ad

`awaitNativeAd()` waits only on an already-started pool. If the key is missing
or stopped, it returns `null` immediately; make SDK initialization and
`start()` part of the app's navigation/readiness gate.

```kotlin
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.soosu.nextgen.admobnative.NativeAdSmallBox

@Composable
fun MyScreen() {
    val app = LocalContext.current.applicationContext as MyApplication
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        nativeAd = app.nativeAdLoadManager.awaitNativeAd(
            key = MyApplication.HOME_NATIVE_POOL,
            timeoutMs = 8_000L,
        )
        isLoading = false
    }

    DisposableEffect(nativeAd) {
        val ownedAd = nativeAd
        onDispose {
            ownedAd?.destroy()
        }
    }

    when {
        isLoading -> CircularProgressIndicator()
        nativeAd != null -> NativeAdSmallBox(
            nativeAd = nativeAd,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}
```

---

## Native Ad Preloading

`NativeAdLoadManager` wraps the Next-Gen SDK `NativeAdPreloader` while keeping
placement policy in the app.

- Each key has an independent buffer; the default is one ad per key.
- A fully built `NativeAdRequest` preserves aspect ratio, keywords, mediation
  extras, custom formats, and banner options.
- The SDK automatically refills the buffer and retries preload failures.
- `pollNativeAd()` returns immediately; `awaitNativeAd()` can wait with a
  coroutine timeout.
- `pollResult()` supports standard native, custom native, and banner success
  results.
- Listener callbacks are delivered on the main thread after the SDK callback
  stack returns.
- Kotlin can implement `NativeAdLoadListener` directly; Java can extend
  `NativeAdLoadListenerAdapter` and override only the needed callbacks.
- `onAdPreloaded` callback order is not guaranteed to match subsequent poll
  order; do not correlate callback `ResponseInfo` by position.
- Ads returned by poll/await belong to the caller and must be destroyed by the
  screen, ViewModel, or Activity that owns them.
- Consent, premium-user, network, and Remote Config decisions stay in the app
  and control `start()` / `stop()`.
- `start()` before SDK initialization is deferred, not dropped: the pool starts
  automatically once `AdmobInitializer.initialize()` completes (this is the
  initializer used internally by `SplashAdLoader.execute()`). A `stop()` or
  `unregister()` before that point cancels the deferred start. Apps that call
  `MobileAds.initialize()` directly must still call `start()` afterwards.

For complete migration patterns—including multiple placements, list slots,
StateFlow, dynamic requests, and ownership—see the
[Korean NativeAdLoadManager migration guide](docs/native-ad-load-manager-migration.ko.md).

---

## Interstitial Ad Preloading

`InterstitialAdLoadManager` wraps the Next-Gen SDK `InterstitialAdPreloader`
with the same pool model as `NativeAdLoadManager`: independent per-key buffers,
automatic SDK refill, expiration handling, deferred start before SDK
initialization, and main-thread listener callbacks.

```kotlin
class MyApplication : Application() {
    val interstitialAdLoadManager = InterstitialAdLoadManager()

    override fun onCreate() {
        super.onCreate()
        interstitialAdLoadManager.register(
            key = GAME_OVER_INTERSTITIAL,
            adUnitId = "YOUR_INTERSTITIAL_AD_UNIT_ID",
        )
        // start() after consent + SDK initialization; a call made earlier is
        // deferred and resumed automatically (see Native Ad Preloading notes).
        interstitialAdLoadManager.start(GAME_OVER_INTERSTITIAL)
    }

    companion object {
        const val GAME_OVER_INTERSTITIAL = "game-over"
    }
}
```

Show at a transition point:

```kotlin
// Simplest: manager polls, shows, and destroys the ad after dismiss.
app.interstitialAdLoadManager.showAdIfAvailable(activity, GAME_OVER_INTERSTITIAL) {
    navigateToNextScreen()
}

// Full control: ownership of the polled ad transfers to the caller.
val ad = app.interstitialAdLoadManager.pollAd(GAME_OVER_INTERSTITIAL)

// Or suspend until an ad is ready (started pools only).
val ad = app.interstitialAdLoadManager.awaitAd(GAME_OVER_INTERSTITIAL, timeoutMs = 5_000L)
```

Show-frequency policy (intervals, caps, premium users) intentionally stays in
the app; the manager only owns loading and show mechanics. For migration from
a hand-rolled `InterstitialAd.load()` implementation, see the
[Korean InterstitialAdLoadManager migration guide](docs/interstitial-ad-load-manager-migration.ko.md).

---

## Banner Ad Preloading

`BannerAdLoadManager` wraps the Next-Gen SDK `BannerAdPreloader` with the same
pool model as the native and interstitial managers. Because `BannerAdRequest`
carries the `AdSize`, one pool serves exactly one (ad unit, size) pair.

```kotlin
class MyApplication : Application() {
    val bannerAdLoadManager = BannerAdLoadManager()

    override fun onCreate() {
        super.onCreate()
        bannerAdLoadManager.registerIfAbsent(
            key = HOME_MREC,
            adUnitId = "YOUR_BANNER_AD_UNIT_ID",
            adSize = AdSize.MEDIUM_RECTANGLE,
        )
        // start() after consent + SDK initialization; a call made earlier is
        // deferred and resumed automatically.
        bannerAdLoadManager.start(HOME_MREC)
    }

    companion object {
        const val HOME_MREC = "home-mrec"
    }
}
```

Consume in a slot. Ownership transfers to the caller, which attaches the ad
with `getView(activity)` and destroys it when the slot goes away:

```kotlin
val ad = app.bannerAdLoadManager.awaitAd(HOME_MREC) ?: return
AndroidView(factory = { ad.getView(activity) })
// later: ad.destroy()
```

Destroy the `BannerAd` a screen owns — not the pool. Calling `stop`/`unregister`
on screen exit throws away buffered ads that were already requested and
re-requests the whole buffer on the next entry. Reserve those for premium
upgrades, consent withdrawal, and Remote Config off.

`bufferSize` defaults to `1`. Leaving it unset delegates to the SDK, whose
current default is `2` — a second ad that a single slot never shows is still a
billed request. See the
[Korean BannerAdLoadManager migration guide](docs/banner-ad-load-manager-migration.ko.md).

---

## Available Templates

The library provides eight pre-built templates optimized for different use cases:

### 1. Small Template - `NativeAdSmallBox`

<img width="536" height="141" alt="Small Template" src="https://github.com/user-attachments/assets/bbc44bb5-38f2-4603-bd98-26f00e8a7b67" />


**Best for:** List items, compact spaces, inline content

**Features:**
- Compact horizontal layout
- Small app icon with headline
- Advertiser name and CTA button
- Ideal for RecyclerView/LazyColumn items

```kotlin
NativeAdSmallBox(
    nativeAd = nativeAd,
    modifier = Modifier.fillMaxWidth()
)
```

### 2. Icon Small Template - `NativeAdIconSmallBox`

<img width="529" height="133" alt="Icon Small Template" src="https://github.com/user-attachments/assets/53d51a82-8583-47ac-9ea8-9fbf56afac72" />

**Best for:** Content feeds, article lists, social media-style layouts

**Features:**
- Large app icon (48dp) with prominent display
- Headline and body text
- Minimal, clean design
- Perfect for content-heavy feeds
- Arrow indicator for engagement

```kotlin
NativeAdIconSmallBox(
    nativeAd = nativeAd,
    modifier = Modifier.fillMaxWidth()
)
```

### 3. Medium Template - `NativeAdMediumBox`

<img width="530" height="729" alt="Medium Template" src="https://github.com/user-attachments/assets/340dffae-825e-49ab-b9be-eaa24e9682cb" />


**Best for:** Cards, featured content, feed items

**Features:**
- Prominent media image (1200x628 recommended)
- Full headline and body text
- Advertiser branding
- Call-to-action button
- Perfect for news feeds or content cards

```kotlin
NativeAdMediumBox(
    nativeAd = nativeAd,
    modifier = Modifier.fillMaxWidth()
)
```

### 4. Headline Template - `NativeAdHeadlineBox`

<img width="515" height="76" alt="Headline Template" src="https://github.com/user-attachments/assets/4cb4845c-c288-4050-8ba3-a2922eb00d2f" />


**Best for:** Minimal spaces, headers, banners

**Features:**
- Ultra-compact design
- Horizontally centered in the space it is given
- Headline only with small icon, which can be turned off
- Minimal visual footprint
- Great for toolbars or between content sections

```kotlin
NativeAdHeadlineBox(
    nativeAd = nativeAd,
    modifier = Modifier.fillMaxWidth()
)

// Text only: the image is neither rendered nor downloaded
NativeAdHeadlineBox(
    nativeAd = nativeAd,
    modifier = Modifier.fillMaxWidth(),
    showImage = false
)
```

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `showImage` | `Boolean` | `true` | Show the ad image between the "AD" badge and the headline |

### 5. Large Template - `NativeAdLargeBox`

<img width="533" height="831" alt="Large Template" src="https://github.com/user-attachments/assets/9006fe2d-064d-4210-b19a-a1cc35a3645c" />


**Best for:** Premium placements, maximum engagement, high CTR campaigns

**Features:**
- Large prominent media image (200dp height)
- Star rating display (when available)
- Price information display (when available)
- Premium CTA button with full width design
- Bold headline (2 lines)
- Detailed body text (3 lines)
- App icon and advertiser branding
- Optimized for maximum click-through rates

```kotlin
NativeAdLargeBox(
    nativeAd = nativeAd,
    modifier = Modifier.fillMaxWidth(),
    ctaButtonColor = Color(0xFF1976D2),
    ctaTextColor = Color.White
)
```

### 6. App Install Template - `NativeAdAppInstallBox`

<img width="527" height="726" alt="App Install Template" src="https://github.com/user-attachments/assets/c3d69cb1-8c90-4bd9-aac4-81eea673c778" />


**Best for:** App promotion campaigns, game advertisements, app store style placements

**Features:**
- App Store style layout with familiar design
- Large app icon with rounded corners
- Star rating display (when available)
- Price/Free indicator
- Prominent "Install" button
- Optional screenshot/media preview

```kotlin
NativeAdAppInstallBox(
    nativeAd = nativeAd,
    modifier = Modifier.fillMaxWidth(),
    ctaButtonColor = Color(0xFF1976D2),
    ctaTextColor = Color.White
)
```

### 7. Content Feed Template - `NativeAdContentBox`

<img width="525" height="740" alt="Content Feed Template" src="https://github.com/user-attachments/assets/3881e988-2d33-4927-98f0-3f4428519a64" />

**Best for:** News feed placements, content discovery feeds, social media style apps

**Features:**
- Social media post-style layout
- Profile icon + advertiser name header
- "Sponsored" label for transparency
- Post-style headline with natural line spacing
- Full-width media with rounded corners
- Engagement-style CTA button
- Dynamic media height based on aspect ratio

```kotlin
NativeAdContentBox(
    nativeAd = nativeAd,
    modifier = Modifier.fillMaxWidth(),
    ctaButtonColor = Color(0xFF1976D2),
    ctaTextColor = Color.White
)
```

### 8. Full Width Media Template - `NativeAdFullWidthMediaBox`

<img width="532" height="396" alt="Full Width Media Template" src="https://github.com/user-attachments/assets/8e3b3fe7-438d-45d5-ba2a-6368f4263f4b" />

**Best for:** Hero placements, full-screen interstitial-style native ads, high-impact banner replacements

**Features:**
- Large media/image display (280dp height)
- Gradient overlay for text readability
- Overlay CTA button for immediate action
- Fallback layout when no media is available

```kotlin
NativeAdFullWidthMediaBox(
    nativeAd = nativeAd,
    modifier = Modifier.fillMaxWidth(),
    ctaButtonColor = Color.White,
    ctaTextColor = Color(0xFF1976D2)
)
```

---

## API Reference

### Common Parameters

All template composables share these parameters:

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `nativeAd` | `NativeAd?` | Required | The loaded AdMob native ad object |
| `modifier` | `Modifier` | `Modifier` | Compose modifier for layout customization |
| `backgroundColor` | `Color` | `MaterialTheme.colorScheme.surfaceVariant` | Background color of the ad container |
| `textColor` | `Color` | `MaterialTheme.colorScheme.onBackground` | Text color for ad content |

### Example: Custom Styling

```kotlin
NativeAdMediumBox(
    nativeAd = nativeAd,
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .shadow(4.dp),
    backgroundColor = Color(0xFFF5F5F5),
    textColor = Color(0xFF333333)
)
```

---

## Advanced Usage

### Handling Ad Load Lifecycle

```kotlin
@Composable
fun AdWithLoadingState(manager: NativeAdLoadManager) {
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }

    LaunchedEffect(manager) {
        nativeAd = manager.awaitNativeAd("home-native", timeoutMs = 8_000L)
        isLoading = false
        isError = nativeAd == null
    }

    DisposableEffect(nativeAd) {
        val ownedAd = nativeAd
        onDispose {
            ownedAd?.destroy()
        }
    }

    when {
        isLoading -> CircularProgressIndicator()
        isError -> Text("Ad failed to load")
        else -> NativeAdSmallBox(nativeAd = nativeAd)
    }
}
```

### Dark Mode Support

The library automatically adapts to Material 3 theme changes:

```kotlin
MaterialTheme(
    colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
) {
    NativeAdMediumBox(
        nativeAd = nativeAd,
        // Automatically uses theme colors
    )
}
```

### Auto Color Extraction

The library provides `NativeAdAutoColorWrapper` that automatically extracts dominant colors from ad icons and applies them to the ad template. This creates a more cohesive, visually appealing ad experience.

**Features:**
- Automatically extracts background color from ad icons using Palette API
- Calculates optimal text color (black/white) based on background brightness
- Asynchronous color extraction on background thread
- Works with all ad templates (Small, Medium, Large)
- Falls back to theme colors if extraction fails

```kotlin
NativeAdAutoColorWrapper(
    nativeAd = nativeAd
) { backgroundColor, textColor ->
    NativeAdSmallBox(
        nativeAd = nativeAd,
        backgroundColor = backgroundColor ?: MaterialTheme.colorScheme.surfaceVariant,
        textColor = textColor ?: MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
    )
}
```

---

## AdMob Initialization, UMP Consent & App Open Ads

The library provides built-in support for **UMP consent management**, **SDK initialization**, **splash screen ads**, and **foreground app open ads** - eliminating boilerplate code that is typically duplicated across projects.

### Setup

#### 1. Create AdmobConfig

`AdmobConfig` holds all settings for your project. Use the Builder pattern:

```kotlin
val config = AdmobConfig.Builder("ca-app-pub-xxx~yyy")
    .splashAdUnitId("ca-app-pub-xxx/splash")         // null to disable splash ads
    .foregroundAdUnitId("ca-app-pub-xxx/foreground")  // null to disable foreground ads
    .consentTimeoutMs(15_000)                         // UMP timeout (default 15s)
    .splashAdLoadTimeoutMs(8_000)                     // Splash ad load timeout (default 8s)
    .foregroundAdCooldownMs(10_000)                   // Foreground ad load cooldown (default 10s)
    .foregroundAdShowIntervalMs(10_000)               // Foreground ad show interval (default 0)
    .preloadOnBackground(true)                        // Preload on entering background
    .useAppOpenAdPreloader(true)                      // SDK-managed preloader (default true)
    .appOpenAdPreloadBufferSize(1)                    // 1 by default; null = SDK optimized
    .shouldSuppressAds { isPremiumUser() }            // Ad suppression condition
    .debugLogging(BuildConfig.DEBUG)
    .build()
```

**AdmobConfig Parameters:**

| Parameter | Default | Description |
|-----------|---------|-------------|
| `splashAdUnitId` | `null` | Splash ad unit ID. `null` to disable |
| `foregroundAdUnitId` | `null` | Foreground ad unit ID. `null` to disable |
| `consentTimeoutMs` | `15000` | UMP consent gathering timeout (ms) |
| `splashAdLoadTimeoutMs` | `8000` | Splash ad load timeout (ms) |
| `foregroundAdExpirationMs` | `4 hours` | Loaded foreground ad expiration time |
| `foregroundAdCooldownMs` | `10000` | Foreground ad load retry cooldown (ms) |
| `foregroundAdShowIntervalMs` | `0` | Minimum interval between foreground ad shows (ms) |
| `preloadOnBackground` | `true` | Whether to preload ads when entering background |
| `useAppOpenAdPreloader` | `true` | Use the SDK-managed `AppOpenAdPreloader` for foreground ads |
| `appOpenAdPreloadBufferSize` | `1` | Maximum queued ads. `null` delegates sizing to the SDK |
| `shouldSuppressAds` | `{ false }` | Ad suppression condition (e.g. premium users) |
| `debugLogging` | `false` | Enable debug logging |

#### 2. Application Setup

```kotlin
class MyApp : Application() {

    lateinit var admobConfig: AdmobConfig
    lateinit var appOpenAdManager: AppOpenAdManager
    lateinit var adLifecycleObserver: AppOpenAdLifecycleObserver

    override fun onCreate() {
        super.onCreate()

        admobConfig = AdmobConfig.Builder("ca-app-pub-xxx~yyy")
            .splashAdUnitId("ca-app-pub-xxx/splash")
            .foregroundAdUnitId("ca-app-pub-xxx/foreground")
            .shouldSuppressAds { isPremiumUser() }
            .debugLogging(BuildConfig.DEBUG)
            .build()

        appOpenAdManager = AppOpenAdManager(admobConfig)

        adLifecycleObserver = AppOpenAdLifecycleObserver(
            application = this,
            adManager = appOpenAdManager,
            config = admobConfig,
        ).apply {
            // Exclude splash from foreground ads
            excludedActivities.add(SplashActivity::class.java.name)
        }
    }
}
```

### Splash Ad (UMP Consent + SDK Init + App Open Ad)

`SplashAdLoader.execute()` handles the entire splash flow in a single `suspend` function:

1. `shouldSuppressAds` check
2. UMP consent gathering (with timeout)
3. GMA SDK initialization
4. App Open Ad loading (with timeout)
5. Show ad and wait for dismiss

```kotlin
class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { /* Splash UI */ }

        val app = application as MyApp

        lifecycleScope.launch {
            // Run the entire flow in a single call
            val result = SplashAdLoader.execute(this@SplashActivity, app.admobConfig)

            when (result) {
                SplashAdResult.AD_SHOWN -> { /* Ad shown successfully */ }
                SplashAdResult.AD_NOT_AVAILABLE -> { /* No ad available */ }
                SplashAdResult.SKIPPED -> { /* Skipped by condition */ }
            }

            // Skip foreground ad once for splash → main transition
            app.adLifecycleObserver.ignoreNextForegroundAd()

            // Preload foreground ad
            app.appOpenAdManager.loadAd(this@SplashActivity)

            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            finish()
        }
    }
}
```

### Foreground App Open Ad

`AppOpenAdLifecycleObserver` automatically detects foreground transitions via `ProcessLifecycleOwner` and `ActivityLifecycleCallbacks`, then shows the ad. Register once in your Application class.

Starting with `1.7.0`, `AppOpenAdManager` uses the SDK-managed
`AppOpenAdPreloader` by default. Existing apps that already use
`AppOpenAdManager` and `AppOpenAdLifecycleObserver` do not need application
code changes: updating this library is enough to route their existing
`loadAd()` and `showAdIfAvailable()` calls through the preloader.

The preloader keeps the in-memory buffer full for the current process session,
automatically retries failed preload requests, and refills after `pollAd()`.
This does not create a disk cache for a true cold start. The library defaults
to a single-ad buffer to minimize memory and network use. Pass `null` to
`appOpenAdPreloadBufferSize()` to let Google choose the size (currently 2), or
set another value of at least 1. Larger buffers can increase memory and network
usage.

**Key Features:**

```kotlin
// Exclude specific activities
adLifecycleObserver.excludedActivities.add(SplashActivity::class.java.name)
adLifecycleObserver.excludedActivities.add(SettingsActivity::class.java.name)

// Skip the next foreground ad once
adLifecycleObserver.ignoreNextForegroundAd()

// Manually show ad (if needed)
appOpenAdManager.showAdIfAvailable(activity) {
    // Called after ad is shown or failed
}

// Manually load ad (if needed)
appOpenAdManager.loadAd(context)

// Inspect or release the SDK-managed buffer
val readyCount = appOpenAdManager.getNumPreloadedAds()
appOpenAdManager.stopPreloading()
```

To retain the pre-`1.7.0` one-shot loader, opt out explicitly:

```kotlin
AdmobConfig.Builder(appId)
    .useAppOpenAdPreloader(false)
    .build()
```

`foregroundAdCooldownMs` controls only the one-shot loader used after opting
out of the preloader. When the preloader is enabled, the Google SDK owns retry
and refill scheduling. `loadAndShowIfMissing` waits for the active preloader
instead of issuing a duplicate one-shot request. Runtime keyword changes
replace and immediately restart the buffer with the updated request.

`loadAd()` before SDK initialization is deferred, not dropped: the call is
replayed automatically once `AdmobInitializer.initialize()` completes (this is
the initializer used internally by `SplashAdLoader.execute()`), re-running the
suppression and configuration gates at that time. `stopPreloading()` cancels a
deferred load. Apps that call `MobileAds.initialize()` directly must still call
`loadAd()` afterwards. `showAdIfAvailable()` is intentionally never deferred —
a foreground ad appearing seconds after the trigger would be a UX bug.

> Updating only Google's `ads-mobile-sdk` dependency does not activate
> preloading. `AppOpenAdPreloader.start()` and `pollAd()` must be integrated,
> which this library now does inside `AppOpenAdManager`. Direct
> `AppOpenAd.load()` calls outside the manager and the cold-start
> `SplashAdLoader` flow remain one-shot loads.

### Gathering Consent

Gather UMP consent before initializing the SDK. Pass `config.consentTimeoutMs` to bound the
consent-info update (network) step — the timeout does **not** apply while the user is interacting
with the consent form:

```kotlin
val consentManager = AdmobConsentManager(activity)
val canRequestAds = consentManager.gatherConsent(
    activity = activity,
    timeoutMs = config.consentTimeoutMs, // 0 = no timeout
)
```

### UMP Privacy Options

Allow users to re-access privacy options from your settings screen:

```kotlin
val consentManager = AdmobConsentManager(context)

// Only show button when privacy options are required
if (consentManager.isPrivacyOptionsRequired) {
    Button(onClick = {
        scope.launch {
            consentManager.showPrivacyOptionsForm(activity)
        }
    }) {
        Text("Privacy Settings")
    }
}
```

### Project-Specific Configuration Examples

Adjust `AdmobConfig` to match each project's requirements:

```kotlin
// Sample 1: consent 5s + ad 8s, foreground 30s cooldown
AdmobConfig.Builder(appId)
    .consentTimeoutMs(5_000)
    .splashAdLoadTimeoutMs(8_000)
    .foregroundAdCooldownMs(30_000)
    .build()

// Sample 2: ad load 5s, no foreground ads
AdmobConfig.Builder(appId)
    .splashAdLoadTimeoutMs(5_000)
    .foregroundAdUnitId(null)  // Disable foreground ads
    .build()

// Sample 3: show interval 10s, background preload
AdmobConfig.Builder(appId)
    .foregroundAdShowIntervalMs(10_000)
    .foregroundAdCooldownMs(5_000)
    .preloadOnBackground(true)
    .build()

// Sample 4: fast load 4s, no cooldown
AdmobConfig.Builder(appId)
    .splashAdLoadTimeoutMs(4_000)
    .foregroundAdCooldownMs(0)
    .build()
```

---

## Custom Layouts

Every template in this library is rendered with pure Compose - there are no XML layouts and no
`AndroidView` inflation of template views. The building blocks follow the official Next-Gen Compose
sample ([`NativeComposeFragment`](https://github.com/googleads/gma-next-gen-sdk-android-examples/blob/main/kotlin/NextGenExample/app/src/main/java/com/example/nextgenexample/native/NativeComposeFragment.kt)):
wrap your layout in `NativeAdView` and declare each ad asset with its matching `NativeAd*View`
composable so the SDK can register the asset and track impressions and clicks.

These composables are public API, so you can build a layout the eight built-in templates do not
cover.

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.soosu.nextgen.admobnative.NativeAdAttribution
import com.soosu.nextgen.admobnative.NativeAdBodyView
import com.soosu.nextgen.admobnative.NativeAdCallToActionView
import com.soosu.nextgen.admobnative.NativeAdHeadlineView
import com.soosu.nextgen.admobnative.NativeAdMediaView
import com.soosu.nextgen.admobnative.NativeAdView

@Composable
fun MyNativeAd(nativeAd: NativeAd) {
    NativeAdView(nativeAd = nativeAd, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Every native ad needs a visible "Ad" attribution.
                NativeAdAttribution()

                NativeAdHeadlineView(modifier = Modifier.padding(start = 8.dp)) {
                    Text(text = nativeAd.headline.orEmpty(), fontWeight = FontWeight.Bold)
                }
            }

            // The MediaView must be at least 120x120dp, so size it with the reported aspect ratio.
            val aspectRatio = nativeAd.mediaContent?.aspectRatio?.takeIf { it > 0f } ?: (16f / 9f)
            NativeAdMediaView(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .heightIn(min = 120.dp)
            )

            nativeAd.body?.let { body ->
                NativeAdBodyView(modifier = Modifier.padding(top = 8.dp)) { Text(text = body) }
            }

            NativeAdCallToActionView(modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = {}) { Text(text = nativeAd.callToAction.orEmpty()) }
            }
        }
    }
}
```

### Ad Asset Composables

| Composable | Registers | Notes |
|------------|-----------|-------|
| `NativeAdView(nativeAd) { }` | The ad container | Required root; registers the ad when the assets are laid out |
| `NativeAdHeadlineView { }` | `headlineView` | Required by the SDK |
| `NativeAdBodyView { }` | `bodyView` | |
| `NativeAdCallToActionView { }` | `callToActionView` | Wrap a button, or the whole card to make it all clickable |
| `NativeAdIconView { }` | `iconView` | Render the icon with `nativeAd.icon?.drawable` / `uri` |
| `NativeAdMediaView(modifier)` | The `MediaView` | Video capable; needs at least 120x120dp |
| `NativeAdAdvertiserView { }` | `advertiserView` | |
| `NativeAdStoreView { }` | `storeView` | |
| `NativeAdPriceView { }` | `priceView` | |
| `NativeAdStarRatingView { }` | `starRatingView` | |
| `NativeAdChoicesView(modifier)` | `adChoicesView` | |
| `NativeAdAttribution(...)` | - | The "Ad" badge, styled with Material 3 colors |

> **Upgrading from 1.7.x**
> The View-based API (`NativeAdTemplateView`, `AdTemplateType` and the `app:adTemplate` XML
> attributes) has been removed together with the `gnt_ad_*` layouts. Host the template composables
> in a `ComposeView` if you still need them inside a View hierarchy, or build your own layout with
> the asset composables above.

---

## Migration from Legacy SDK

> **Looking for Legacy SDK?**
> If you're using the legacy Google Play Services Ads SDK (`com.google.android.gms:play-services-ads`), please use our legacy library instead:
> **[Admob-Native-Template-Compose](https://github.com/kmshack/Admob-Native-Template-Compose)**

If you're migrating from the legacy Google Play Services Ads SDK, here are the key changes:

### SDK Dependency

```kotlin
// Legacy SDK
implementation("com.google.android.gms:play-services-ads:24.x.x")

// Next-Gen SDK
implementation("com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.3.0")
```

### Initialization

```kotlin
// Legacy SDK
MobileAds.initialize(context) { }

// Next-Gen SDK
MobileAds.initialize(
    context,
    InitializationConfig.Builder(appId).build()
) { }
```

### Ad Loading

```kotlin
// Legacy SDK
val adLoader = AdLoader.Builder(context, adUnitId)
    .forNativeAd { ad -> nativeAd = ad }
    .build()
adLoader.loadAd(AdRequest.Builder().build())

// Next-Gen SDK
val adRequest = NativeAdRequest.Builder(
    adUnitId,
    listOf(NativeAd.NativeAdType.NATIVE)
).build()

NativeAdLoader.load(adRequest, object : NativeAdLoaderCallback {
    override fun onNativeAdLoaded(ad: NativeAd) {
        runOnUiThread {
            nativeAd = ad
        }
    }
    override fun onAdFailedToLoad(error: LoadAdError) {
        // Handle error
    }
})
```

### Key API Changes

| Legacy SDK | Next-Gen SDK |
|------------|--------------|
| `com.google.android.gms.ads.nativead.NativeAd` | `com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd` |
| `AdLoader.Builder().forNativeAd()` | `NativeAdLoader.load()` |
| `NativeAdView.setNativeAd()` | `NativeAdView.registerNativeAd()` |
| `MediaView.setMediaContent()` | `MediaView.mediaContent = ` |
| `nativeAd.images` | Not available (use `mediaContent`) |
| `minSdk = 23` | `minSdk = 24` |

After completing the SDK type migration, use the per-format migration guides to
replace direct loads with SDK-managed preload pools:

- [NativeAdLoadManager](docs/native-ad-load-manager-migration.ko.md)
- [InterstitialAdLoadManager](docs/interstitial-ad-load-manager-migration.ko.md)
- [BannerAdLoadManager](docs/banner-ad-load-manager-migration.ko.md)

---

## Sample App

A complete sample application is included in this repository demonstrating all ad templates.

### Running the Sample

```bash
# Clone the repository
git clone https://github.com/kmshack/NextGen-Admob-Native-Template-Compose.git
cd NextGen-Admob-Native-Template-Compose

# Build and run the sample app
./gradlew :sample:installDebug

# Or open in Android Studio and run the 'sample' module
```

### What's Included

The sample app demonstrates:

- **All Eight Templates** - Headline, Small, Icon Small, Medium, Large, App Install, Content Feed, and Full Width Media layouts
- **Auto Color Extraction** - Live demonstration of automatic color extraction from ad icons
- **Live Ad Loading** - Using Google's test ad unit IDs
- **Loading States** - Progress indicators while ads load
- **Error Handling** - Graceful error messages when ads fail
- **Material 3 Theming** - Modern, beautiful UI design
- **Best Practices** - Production-ready implementation patterns
- **CTR Optimization** - Premium template showcasing high-engagement design

### Test Ad Unit IDs

The sample uses Google's official test IDs:
```
App ID: ca-app-pub-3940256099942544~3347511713
Native Ad Unit: ca-app-pub-3940256099942544/2247696110
```

**Note:** These are test IDs and will not generate revenue. Replace with your own IDs for production use.

---

## Dependencies

This library uses the following dependencies:

| Dependency | Version | Purpose |
|------------|---------|---------|
| Jetpack Compose BOM | 2025.06.00 | Compose runtime and UI |
| Material 3 | Compose BOM | Material Design components |
| GMA Next-Gen SDK | 1.3.0 | AdMob SDK (Native, App Open) |
| UMP SDK | 4.0.0 | User consent management (GDPR/CCPA) |
| Lifecycle Process | 2.10.0 | Foreground/background detection |
| Kotlinx Coroutines | 1.10.2 | Coroutines support |
| Palette KTX | 1.0.0 | Auto color extraction from images |

---

## Contributing

Contributions are welcome! Here's how you can help:

1. **Report Bugs**: Open an issue with detailed reproduction steps
2. **Suggest Features**: Propose new templates or improvements
3. **Submit PRs**: Fork, create a feature branch, and submit a pull request

### Development Setup

```bash
git clone https://github.com/kmshack/NextGen-Admob-Native-Template-Compose.git
cd NextGen-Admob-Native-Template-Compose
./gradlew build
```

---

## License

```
Copyright 2025 kmshack

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

## Support

- **Issues**: [GitHub Issues](https://github.com/kmshack/NextGen-Admob-Native-Template-Compose/issues)
- **Discussions**: [GitHub Discussions](https://github.com/kmshack/NextGen-Admob-Native-Template-Compose/discussions)

---

<div align="center">

**Made with love for the Android community**

[Star this repo](https://github.com/kmshack/NextGen-Admob-Native-Template-Compose) if you find it useful!

</div>
