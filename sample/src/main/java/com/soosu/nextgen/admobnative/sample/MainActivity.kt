package com.soosu.nextgen.admobnative.sample

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.*
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.soosu.nextgen.admobnative.*

private const val QA_TAG = "AdSampleQA"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        setContent { AdMobNativeSampleTheme { MainScreen() } }
    }
}

@Composable
fun AdMobNativeSampleTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(), content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var screen by rememberSaveable { mutableStateOf("Native") }
    val activity = LocalContext.current as MainActivity
    Scaffold(topBar = { TopAppBar(title = { Text("AdMob device QA") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
                listOf("Native", "Banner", "Fullscreen", "Adapters").forEach { label ->
                    TextButton(onClick = { screen = label }) { Text(label) }
                }
            }
            key(screen) {
                when (screen) {
                    "Native" -> NativeScreen(activity)
                    "Banner" -> BannerScreen(activity)
                    "Fullscreen" -> InterstitialScreen(activity)
                    else -> AdapterScreen()
                }
            }
        }
    }
}

private val templates = listOf("Full width", "Content", "Install", "Headline", "Small", "Icon", "Medium", "Large", "SDK view")

@Composable
private fun NativeScreen(activity: MainActivity) {
    var selected by rememberSaveable { mutableStateOf("Full width") }
    var video by rememberSaveable { mutableStateOf(false) }
    var alternate by rememberSaveable { mutableStateOf(false) }
    var optionsExpanded by rememberSaveable { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { optionsExpanded = !optionsExpanded }) {
                Text(if (optionsExpanded) "Hide options" else "Templates · $selected")
            }
            Button(onClick = { reload++ }) { Text("Reload native") }
        }
        if (optionsExpanded) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                templates.forEach { name ->
                    FilterChip(selected = selected == name, onClick = {
                        selected = name
                        optionsExpanded = false
                        reload++
                    }, label = { Text(name) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = video, onCheckedChange = { video = it; optionsExpanded = false },
                        modifier = Modifier.semantics { contentDescription = "Video creative toggle" })
                    Text("Video creative")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = alternate, onCheckedChange = { alternate = it; optionsExpanded = false },
                        modifier = Modifier.semantics { contentDescription = "Variant toggle" })
                    Text("Variant")
                }
            }
        }
        // Each render owns a distinct ad. Never attach one ad to two NativeAdViews.
        key(selected, video, alternate, reload) {
            NativeCard(activity, selected, video, alternate)
        }
    }
}

@Composable
private fun NativeCard(activity: MainActivity, template: String, video: Boolean, alternate: Boolean) {
    val app = activity.application as SampleApplication
    var ad by remember { mutableStateOf<NativeAd?>(null) }
    var status by remember { mutableStateOf("Loading native…") }
    DisposableEffect(Unit) { onDispose { ad?.destroy() } }
    LaunchedEffect(Unit) {
        val pool = if (video) SampleApplication.NATIVE_VIDEO_POOL else SampleApplication.NATIVE_FEED_POOL
        app.nativeAdLoadManager.start(pool)
        val loaded = app.nativeAdLoadManager.awaitNativeAd(pool, timeoutMs = 30_000L)
        if (loaded == null) {
            status = "Native failed: ${app.nativeAdLoadManager.getState(pool)?.lastError ?: "timeout"}"
            Log.e(QA_TAG, status)
        } else {
            loaded.adEventCallback = object : NativeAdEventCallback {
                override fun onAdImpression() { Log.i(QA_TAG, "Native impression: $template video=$video variant=$alternate") }
                override fun onAdClicked() { Log.i(QA_TAG, "Native clicked: $template video=$video variant=$alternate") }
            }
            ad = loaded
            status = "Loaded $template · video=${loaded.mediaContent?.hasVideoContent}"
            Log.i(QA_TAG, "$status ${loaded.getResponseInfo()}")
        }
    }
    Text(status, style = MaterialTheme.typography.bodySmall)
    ad?.let { loaded ->
        val modifier = Modifier.fillMaxWidth()
        val bg = if (alternate) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
        when (template) {
            "Full width" -> NativeAdFullWidthMediaBox(loaded, modifier = modifier,
                ctaButtonColor = if (alternate) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                ctaTextColor = Color.White)
            "Content" -> NativeAdContentBox(loaded, modifier = modifier, backgroundColor = bg)
            "Install" -> NativeAdAppInstallBox(loaded, modifier = modifier,
                ctaButtonColor = if (alternate) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary)
            "Headline" -> NativeAdHeadlineBox(loaded, modifier = modifier, showImage = !alternate,
                contentsPaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp))
            "Small" -> NativeAdSmallBox(loaded, modifier = modifier, backgroundColor = bg)
            "Icon" -> NativeAdIconSmallBox(loaded, modifier = modifier, backgroundColor = bg)
            "Medium" -> NativeAdMediumBox(loaded, modifier = modifier, backgroundColor = bg)
            "Large" -> NativeAdLargeBox(loaded, modifier = modifier, backgroundColor = bg)
            "SDK view" -> SdkNativeReference(loaded)
        }
        Text("Source: ${loaded.getResponseInfo().adapterClassName?.substringAfterLast('.')}", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SdkNativeReference(ad: NativeAd) {
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { context ->
            val sdkView = com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView(context)
            val column = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
            }
            sdkView.addView(column)
            fun text(value: String) = android.widget.TextView(context).apply {
                text = value
                textSize = 16f
                setTextColor(android.graphics.Color.BLACK)
            }
            column.addView(text("AD"))
            val media = com.google.android.libraries.ads.mobile.sdk.nativead.MediaView(context).apply {
                ad.mediaContent?.let { mediaContent = it }
            }
            column.addView(media, android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                (220 * context.resources.displayMetrics.density).toInt(),
            ))
            val headline = text(ad.headline.orEmpty())
            column.addView(headline)
            sdkView.headlineView = headline
            val body = text(ad.body.orEmpty())
            column.addView(body)
            sdkView.bodyView = body
            val cta = android.widget.Button(context).apply { text = ad.callToAction.orEmpty() }
            column.addView(cta)
            sdkView.callToActionView = cta
            sdkView.post { sdkView.registerNativeAd(ad, media) }
            sdkView
        },
        onRelease = { it.destroy() },
    )
}

@Composable
private fun BannerScreen(activity: MainActivity) {
    var reload by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Adaptive banner", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp))
                Button(onClick = { reload++ }) { Text("Reload banner") }
            }
        }
        key(reload) { AdaptiveBanner(activity) }
    }
}

@Composable
private fun AdaptiveBanner(activity: MainActivity) {
    var status by remember { mutableStateOf("Loading banner…") }
    val adView = remember { AdView(activity) }
    DisposableEffect(adView) { onDispose { adView.destroy() } }
    Text(status, style = MaterialTheme.typography.bodySmall)
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val widthDp = maxWidth.value.toInt()
        val size = remember(widthDp) { AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, widthDp) }
        val height = with(LocalDensity.current) { size.getHeightInPixels(activity).toDp() }
        // Reserve the SDK's requested height before loading; an empty wrap-content AdView
        // measures as zero and otherwise clips the banner after the load completes.
        AndroidView(factory = { adView }, modifier = Modifier.fillMaxWidth().height(height))
        LaunchedEffect(widthDp) {
            adView.loadAd(BannerAdRequest.Builder(SampleApplication.TEST_BANNER_AD_UNIT_ID, size).build(),
                object : AdLoadCallback<BannerAd> {
                    override fun onAdLoaded(ad: BannerAd) {
                        ad.adEventCallback = object : BannerAdEventCallback {
                            override fun onAdImpression() { Log.i(QA_TAG, "Banner impression") }
                            override fun onAdClicked() { Log.i(QA_TAG, "Banner clicked") }
                        }
                        activity.runOnUiThread { status = "Banner loaded: ${ad.getResponseInfo().adapterClassName?.substringAfterLast('.')}" }
                        Log.i(QA_TAG, "Banner loaded: ${ad.getResponseInfo()}")
                    }
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        activity.runOnUiThread { status = "Banner failed: $error" }
                        Log.e(QA_TAG, "Banner failed: $error")
                    }
                })
        }
    }
}

@Composable
private fun InterstitialScreen(activity: MainActivity) {
    val app = activity.application as SampleApplication
    val manager = remember { InterstitialAdLoadManager() }
    var ad by remember { mutableStateOf<InterstitialAd?>(null) }
    var showingAd by remember { mutableStateOf<InterstitialAd?>(null) }
    var active by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Loading interstitial…") }
    var shown by remember { mutableIntStateOf(0) }
    var dismissed by remember { mutableIntStateOf(0) }
    val pool = "sample-interstitial"
    DisposableEffect(manager) {
        manager.register(pool, AdRequest.Builder(SampleApplication.TEST_INTERSTITIAL_AD_UNIT_ID).build(), bufferSize = 1)
        manager.start(pool)
        onDispose {
            active = false
            ad?.destroy()
            showingAd?.destroy()
            app.fullScreenAdVisible = false
            manager.unregister(pool)
        }
    }
    LaunchedEffect(reload) {
        status = "Loading interstitial…"
        val previous = ad
        ad = null
        previous?.destroy()
        ad = manager.awaitAd(pool, timeoutMs = 30_000L)
        status = if (ad == null) "Interstitial failed: ${manager.getState(pool)?.lastError ?: "timeout"}" else "Interstitial ready"
        Log.i(QA_TAG, "$status ${ad?.getResponseInfo()}")
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("전면 배너 / Interstitial", style = MaterialTheme.typography.titleLarge)
        Text(status)
        Text("Shown: $shown · Dismissed: $dismissed")
        Button(enabled = ad != null, onClick = {
            val showing = ad ?: return@Button
            ad = null
            showingAd = showing
            app.fullScreenAdVisible = true
            app.adLifecycleObserver.ignoreNextForegroundAd()
            showing.adEventCallback = object : InterstitialAdEventCallback {
                override fun onAdShowedFullScreenContent() {
                    activity.runOnUiThread { if (active) { shown++; status = "Interstitial showing" } }
                    Log.i(QA_TAG, "Interstitial showed")
                }
                override fun onAdImpression() { Log.i(QA_TAG, "Interstitial impression") }
                override fun onAdDismissedFullScreenContent() {
                    showing.destroy()
                    app.fullScreenAdVisible = false
                    activity.runOnUiThread {
                        showingAd = null
                        if (active) { dismissed++; status = "Interstitial dismissed"; reload++ }
                    }
                    Log.i(QA_TAG, "Interstitial dismissed")
                }
                override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                    showing.destroy()
                    app.fullScreenAdVisible = false
                    activity.runOnUiThread {
                        showingAd = null
                        if (active) status = "Interstitial show failed: $error"
                    }
                    Log.e(QA_TAG, "Interstitial show failed: $error")
                }
            }
            try {
                showing.show(activity)
            } catch (error: RuntimeException) {
                showing.destroy()
                app.fullScreenAdVisible = false
                showingAd = null
                status = "Interstitial show failed: ${error.message}"
                Log.e(QA_TAG, status, error)
            }
        }) { Text("Show interstitial") }
        Button(onClick = { reload++ }) { Text("Reload interstitial") }
        Text("Check close button, system bars, rotation, repeated show/dismiss and background return.")
    }
}

@Composable
private fun AdapterScreen() {
    val adapters = remember {
        mapOf(
            "Meta 6.22.0.1" to "com.google.ads.mediation.facebook.FacebookMediationAdapter",
            "Pangle 8.3.0.4.0" to "com.google.ads.mediation.pangle.PangleMediationAdapter",
            "Vungle 7.7.8.1" to "com.google.ads.mediation.vungle.VungleMediationAdapter",
            "Unity 4.21.0.0" to "com.google.ads.mediation.unity.UnityMediationAdapter",
            "InMobi 11.5.0.0" to "com.google.ads.mediation.inmobi.InMobiMediationAdapter",
        ).map { (name, cls) ->
            val present = runCatching { Class.forName(cls) }.isSuccess
            "$name: ${if (present) "present" else "MISSING"}".also { Log.i(QA_TAG, it) }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("SDK ${MobileAds.getVersion()}", style = MaterialTheme.typography.titleLarge)
        adapters.forEach { Text(it) }
        MobileAds.getInitializationStatus().adapterStatusMap.forEach { (name, state) ->
            Text("${name.substringAfterLast('.')}: ${state.initializationState} · ${state.description}",
                style = MaterialTheme.typography.bodySmall)
        }
        Text("Google demo units only serve Google ads. Network delivery requires your mediation app/unit mappings and each network's test mode.")
        Button(onClick = {
            Log.i(QA_TAG, "Adapter initialization: ${MobileAds.getInitializationStatus()}")
            MobileAds.openAdInspector { error -> Log.i(QA_TAG, "Ad inspector closed: $error") }
        }) { Text("Ad inspector") }
    }
}
