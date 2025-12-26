plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("maven-publish")
}

android {
    namespace = "com.soosu.nextgen.admobnative"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        viewBinding = true
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2025.06.00")
    implementation(composeBom)

    // Compose
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-viewbinding")

    // Google AdMob Next-Gen SDK
    implementation("com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:0.22.0-beta04")

    // Palette for color extraction
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Core
    implementation("androidx.core:core-ktx:1.15.0")

    // CardView for XML layouts
    implementation("androidx.cardview:cardview:1.0.0")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = "com.github.kmshack"
                artifactId = "nextgen-admob-native-template-compose"
                version = "1.0.4"

                pom {
                    name.set("NextGen Admob Native Template Compose")
                    description.set("NextGen AdMob Native Ad templates for Jetpack Compose")
                    url.set("https://github.com/kmshack/NextGen-Admob-Native-Template-Compose")

                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }

                    developers {
                        developer {
                            id.set("kmshack")
                            name.set("kmshack")
                        }
                    }

                    scm {
                        connection.set("scm:git:github.com/kmshack/NextGen-Admob-Native-Template-Compose.git")
                        developerConnection.set("scm:git:ssh://github.com/kmshack/NextGen-Admob-Native-Template-Compose.git")
                        url.set("https://github.com/kmshack/NextGen-Admob-Native-Template-Compose")
                    }
                }
            }
        }
    }
}
