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
- [Available Templates](#available-templates)
- [API Reference](#api-reference)
- [Advanced Usage](#advanced-usage)
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
- **GMA Next-Gen SDK**: 0.22.0-beta04+

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
    implementation("com.github.kmshack:NextGen-Admob-Native-Template-Compose:1.0.5")
}
```

**Step 3:** Sync your project

---

## Quick Start

### 1. Initialize AdMob (Next-Gen SDK)

Initialize the SDK in your Application or Activity:

```kotlin
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize GMA Next-Gen SDK on background thread
        val appId = "ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy"

        CoroutineScope(Dispatchers.IO).launch {
            MobileAds.initialize(
                this@MainActivity,
                InitializationConfig.Builder(appId).build()
            ) {
                // Initialization complete
            }
        }
    }
}
```

### 2. Load and Display a Native Ad

```kotlin
import androidx.compose.runtime.*
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.soosu.nextgen.admobnative.NativeAdSmallBox

@Composable
fun MyScreen() {
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val adRequest = NativeAdRequest.Builder(
            "YOUR_AD_UNIT_ID",
            listOf(NativeAd.NativeAdType.NATIVE)
        ).build()

        NativeAdLoader.load(adRequest, object : NativeAdLoaderCallback {
            override fun onNativeAdLoaded(ad: NativeAd) {
                nativeAd = ad
                isLoading = false
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                isLoading = false
            }
        })
    }

    // Display the ad
    if (!isLoading && nativeAd != null) {
        NativeAdSmallBox(
            nativeAd = nativeAd,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
    }
}
```

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
- Headline only with small icon
- Minimal visual footprint
- Great for toolbars or between content sections

```kotlin
NativeAdHeadlineBox(
    nativeAd = nativeAd,
    modifier = Modifier.fillMaxWidth()
)
```

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
fun AdWithLoadingState() {
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val adRequest = NativeAdRequest.Builder(
            "YOUR_AD_UNIT_ID",
            listOf(NativeAd.NativeAdType.NATIVE)
        ).build()

        NativeAdLoader.load(adRequest, object : NativeAdLoaderCallback {
            override fun onNativeAdLoaded(ad: NativeAd) {
                nativeAd = ad
                isLoading = false
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                isLoading = false
                isError = true
            }
        })
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
implementation("com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:0.22.0-beta04")
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
        nativeAd = ad
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
| Material 3 | 1.3.2+ | Material Design components |
| GMA Next-Gen SDK | 0.22.0-beta04 | AdMob SDK |
| Palette KTX | 1.0.0 | Auto color extraction from images |
| AndroidX Core KTX | 1.15.0 | Core Android utilities |
| CardView | 1.0.0 | Card components |

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
