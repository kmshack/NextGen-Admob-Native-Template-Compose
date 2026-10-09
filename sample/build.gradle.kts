plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.soosu.nextgen.admobnative.sample"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "com.soosu.nextgen.admobnative.sample"
        minSdk = 24
        targetSdk = 36
        versionCode = 101000
        versionName = "1.1.5"

        // Override only for a registered mediation app; demo IDs are the safe default.
        val testDeviceIds = providers.gradleProperty("sampleTestDeviceIds").orElse("").get()
        val qaAppId = providers.gradleProperty("sampleAdMobAppId")
            .orElse("ca-app-pub-3940256099942544~3347511713").get()
        require(qaAppId == "ca-app-pub-3940256099942544~3347511713" || testDeviceIds.isNotBlank()) {
            "Mediation QA requires sampleTestDeviceIds and each network's test mode"
        }
        require(qaAppId.matches(Regex("ca-app-pub-[0-9]+~[0-9]+")))
        require(testDeviceIds.matches(Regex("[A-Fa-f0-9,]*")))
        buildConfigField("String", "QA_APP_ID", "\"$qaAppId\"")
        buildConfigField("String", "QA_TEST_DEVICE_IDS", "\"$testDeviceIds\"")

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Local library
    implementation(project(":"))

    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")

    // Core
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")

    // Google AdMob Next-Gen SDK
    implementation("com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.5.0")

    // Same networks used by the service apps. Unity's adapter does not bundle its SDK.
    implementation("com.google.ads.mediation:facebook:6.22.0.1")
    implementation("com.google.ads.mediation:pangle:8.3.0.4.0")
    implementation("com.google.ads.mediation:vungle:7.7.8.1")
    implementation("com.google.ads.mediation:unity:4.21.0.0")
    implementation("com.unity3d.ads:unity-ads:4.21.0")
    implementation("com.google.ads.mediation:inmobi:11.5.0.0")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Next-Gen supplies the compatibility bridge; the legacy SDK duplicates its classes.
configurations.configureEach {
    exclude(group = "com.google.android.gms", module = "play-services-ads")
    exclude(group = "com.google.android.gms", module = "play-services-ads-lite")
}
