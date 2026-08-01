package com.soosu.nextgen.admobnative

import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.libraries.ads.mobile.sdk.common.AdChoicesView
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaContent
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView as SdkNativeAdView

/*
 * Compose bindings for the GMA Next-Gen native ad view.
 *
 * These composables follow the official Next-Gen Compose sample (`NativeComposeFragment` /
 * `NativeComposableUtility` in googleads/gma-next-gen-sdk-android-examples): every ad asset is
 * declared as a composable, and each asset composable registers itself with the enclosing
 * NativeAdView so the SDK can track impressions and clicks.
 *
 * Use them to build a fully custom native ad layout in Compose:
 *
 *     NativeAdView(nativeAd) {
 *         Column {
 *             NativeAdHeadlineView { Text(nativeAd.headline.orEmpty()) }
 *             NativeAdMediaView(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
 *             NativeAdCallToActionView { Button(onClick = {}) { Text("Install") } }
 *         }
 *     }
 */

/** Provides the enclosing [SdkNativeAdView] to the ad asset composables. */
internal val LocalNativeAdView = staticCompositionLocalOf<SdkNativeAdView?> { null }

/**
 * Provides a registration callback for the [MediaView] created by [NativeAdMediaView].
 *
 * [NativeAdView] needs the `MediaView` instance to pass it to `registerNativeAd`, but the view is
 * created by a descendant composable. The descendant reports its instance through this callback and
 * the parent re-registers the ad whenever it changes.
 */
internal val LocalMediaViewRegister = staticCompositionLocalOf<(MediaView?) -> Unit> { {} }

/**
 * The Compose wrapper for the SDK's `NativeAdView`.
 *
 * @param nativeAd The ad whose assets are rendered by [content].
 * @param modifier The modifier applied to the native ad container.
 * @param content The composable ad layout. Ad assets declared with the `NativeAd*View` composables
 *   register themselves with this container.
 */
@Composable
fun NativeAdView(
    nativeAd: NativeAd,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val nativeAdViewRef = remember { mutableStateOf<SdkNativeAdView?>(null) }
    val mediaViewRef = remember { mutableStateOf<MediaView?>(null) }

    AndroidView(
        factory = { context ->
            val composeView = ComposeView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            SdkNativeAdView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                addView(composeView)
                nativeAdViewRef.value = this
            }
        },
        modifier = modifier,
        update = { adView ->
            // The child was added by the factory, so it is always a ComposeView.
            val composeView = adView.getChildAt(0) as? ComposeView
            composeView?.setContent {
                val registerMediaView: (MediaView?) -> Unit =
                    remember { { mediaView -> mediaViewRef.value = mediaView } }
                CompositionLocalProvider(
                    LocalNativeAdView provides adView,
                    LocalMediaViewRegister provides registerMediaView,
                ) {
                    content()
                }
            }
        },
    )

    val currentNativeAd by rememberUpdatedState(nativeAd)
    val currentNativeAdView = nativeAdViewRef.value
    val currentMediaView = mediaViewRef.value

    DisposableEffect(currentNativeAd, currentNativeAdView, currentMediaView) {
        // Re-register whenever the ad, the container or the media view changes: the asset views are
        // created asynchronously by the nested composition, so registration has to be re-attempted.
        currentNativeAdView?.registerWhenLaidOut(currentNativeAd, currentMediaView)

        onDispose {
            // No cleanup needed: the SDK unregisters when the view is detached from the window.
        }
    }
}

/**
 * The container for an `advertiserView`. Must be called from within a [NativeAdView].
 *
 * @param modifier The modifier applied to the asset.
 * @param content The composable content of this asset.
 */
@Composable
fun NativeAdAdvertiserView(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    NativeAdAssetView(modifier = modifier, content = content) { adView, view ->
        adView.advertiserView = view
    }
}

/**
 * The container for a `bodyView`. Must be called from within a [NativeAdView].
 *
 * @param modifier The modifier applied to the asset.
 * @param content The composable content of this asset.
 */
@Composable
fun NativeAdBodyView(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    NativeAdAssetView(modifier = modifier, content = content) { adView, view ->
        adView.bodyView = view
    }
}

/**
 * The container for a `callToActionView`. Must be called from within a [NativeAdView].
 *
 * @param modifier The modifier applied to the asset.
 * @param content The composable content of this asset.
 */
@Composable
fun NativeAdCallToActionView(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    NativeAdAssetView(modifier = modifier, content = content) { adView, view ->
        adView.callToActionView = view
    }
}

/**
 * The container for a `headlineView`. Must be called from within a [NativeAdView].
 *
 * @param modifier The modifier applied to the asset.
 * @param content The composable content of this asset.
 */
@Composable
fun NativeAdHeadlineView(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    NativeAdAssetView(modifier = modifier, content = content) { adView, view ->
        adView.headlineView = view
    }
}

/**
 * The container for an `iconView`. Must be called from within a [NativeAdView].
 *
 * @param modifier The modifier applied to the asset.
 * @param content The composable content of this asset.
 */
@Composable
fun NativeAdIconView(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    NativeAdAssetView(modifier = modifier, content = content) { adView, view ->
        adView.iconView = view
    }
}

/**
 * The container for a `priceView`. Must be called from within a [NativeAdView].
 *
 * @param modifier The modifier applied to the asset.
 * @param content The composable content of this asset.
 */
@Composable
fun NativeAdPriceView(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    NativeAdAssetView(modifier = modifier, content = content) { adView, view ->
        adView.priceView = view
    }
}

/**
 * The container for a `starRatingView`. Must be called from within a [NativeAdView].
 *
 * @param modifier The modifier applied to the asset.
 * @param content The composable content of this asset.
 */
@Composable
fun NativeAdStarRatingView(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    NativeAdAssetView(modifier = modifier, content = content) { adView, view ->
        adView.starRatingView = view
    }
}

/**
 * The container for a `storeView`. Must be called from within a [NativeAdView].
 *
 * @param modifier The modifier applied to the asset.
 * @param content The composable content of this asset.
 */
@Composable
fun NativeAdStoreView(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    NativeAdAssetView(modifier = modifier, content = content) { adView, view ->
        adView.storeView = view
    }
}

/**
 * The `adChoicesView` of the ad. Must be called from within a [NativeAdView].
 *
 * @param modifier The modifier applied to the asset.
 */
@Composable
fun NativeAdChoicesView(modifier: Modifier = Modifier) {
    val nativeAdView = LocalNativeAdView.current ?: error("NativeAdChoicesView requires NativeAdView")
    AndroidView(
        factory = { context ->
            AdChoicesView(context).apply {
                minimumWidth = 15
                minimumHeight = 15
            }
        },
        modifier = modifier,
        update = { view -> nativeAdView.adChoicesView = view },
    )
}

/**
 * The `MediaView` of the ad, rendering the video or the main image. Must be called from within a
 * [NativeAdView].
 *
 * The media view has to be at least 120x120dp, so size it with an `aspectRatio` modifier based on
 * [com.google.android.libraries.ads.mobile.sdk.nativead.MediaContent.aspectRatio].
 *
 * @param modifier The modifier applied to the asset.
 * @param scaleType The scale type applied to image content.
 * @param mediaContent The media to render. Registration alone is not enough when the media content
 *   carries a main image that was filled in from another asset, so pass it explicitly.
 */
@Composable
fun NativeAdMediaView(
    modifier: Modifier = Modifier,
    scaleType: ImageView.ScaleType? = null,
    mediaContent: MediaContent? = null,
) {
    val registerMediaView = LocalMediaViewRegister.current
    AndroidView(
        factory = { context -> MediaView(context) },
        modifier = modifier,
        update = { view ->
            registerMediaView(view)
            mediaContent?.let { view.mediaContent = it }
            scaleType?.let { view.imageScaleType = it }
        },
    )

    DisposableEffect(Unit) { onDispose { registerMediaView(null) } }
}

/**
 * The "Ad" attribution badge required next to every native ad.
 *
 * @param modifier The modifier applied to the badge.
 * @param text The string identifying the content as an advertisement.
 * @param shape The shape of the badge.
 * @param containerColor The background color of the badge.
 * @param contentColor The text color of the badge.
 * @param padding The padding around the badge text.
 */
@Composable
fun NativeAdAttribution(
    modifier: Modifier = Modifier,
    text: String = "Ad",
    shape: Shape = MaterialTheme.shapes.extraSmall,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    padding: PaddingValues = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
) {
    Box(modifier = modifier.background(color = containerColor, shape = shape).padding(padding)) {
        Text(text = text, color = contentColor, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * Hosts [content] in a `ComposeView` and registers that view as an ad asset through [register].
 */
@Composable
private fun NativeAdAssetView(
    modifier: Modifier,
    content: @Composable () -> Unit,
    register: (SdkNativeAdView, ComposeView) -> Unit,
) {
    val nativeAdView = LocalNativeAdView.current
        ?: error("Native ad assets must be declared inside a NativeAdView")
    AndroidView(
        factory = { context -> ComposeView(context) },
        modifier = modifier,
        update = { view ->
            register(nativeAdView, view)
            view.setContent(content)
        },
    )
}

/**
 * Registers the ad once the asset views hosted in the nested composition have been laid out.
 *
 * Posting matters for the media view in particular: the SDK validates that it is at least 120x120,
 * which fails while the view is still 0x0.
 */
private fun SdkNativeAdView.registerWhenLaidOut(nativeAd: NativeAd, mediaView: MediaView?) {
    val anchor = mediaView ?: this
    anchor.post { registerNativeAd(nativeAd, mediaView) }
}
