package com.soosu.nextgen.admobnative

class AdmobConfig private constructor(
    val admobAppId: String,
    val splashAdUnitId: String?,
    val foregroundAdUnitId: String?,
    val consentTimeoutMs: Long,
    val splashAdLoadTimeoutMs: Long,
    val foregroundAdExpirationMs: Long,
    val foregroundAdCooldownMs: Long,
    val foregroundAdShowIntervalMs: Long,
    val preloadOnBackground: Boolean,
    val useAppOpenAdPreloader: Boolean,
    val appOpenAdPreloadBufferSize: Int?,
    val shouldSuppressAds: () -> Boolean,
    val debugLogging: Boolean,
    val maxAdContentRating: String?,
    val tagForChildDirectedTreatment: Boolean?,
    val tagForUnderAgeOfConsent: Boolean?,
    val keywords: List<String>,
) {
    class Builder(private val admobAppId: String) {
        private var splashAdUnitId: String? = null
        private var foregroundAdUnitId: String? = null
        private var consentTimeoutMs: Long = 15_000L
        private var splashAdLoadTimeoutMs: Long = 8_000L
        private var foregroundAdExpirationMs: Long = 4 * 60 * 60 * 1_000L
        private var foregroundAdCooldownMs: Long = 10_000L
        private var foregroundAdShowIntervalMs: Long = 0L
        private var preloadOnBackground: Boolean = true
        private var useAppOpenAdPreloader: Boolean = true
        private var appOpenAdPreloadBufferSize: Int? = 1
        private var shouldSuppressAds: () -> Boolean = { false }
        private var debugLogging: Boolean = false
        private var maxAdContentRating: String? = null
        private var tagForChildDirectedTreatment: Boolean? = null
        private var tagForUnderAgeOfConsent: Boolean? = null
        private val keywords: MutableList<String> = mutableListOf()

        fun splashAdUnitId(id: String?) = apply { this.splashAdUnitId = id }
        fun foregroundAdUnitId(id: String?) = apply { this.foregroundAdUnitId = id }
        fun consentTimeoutMs(ms: Long) = apply { this.consentTimeoutMs = ms }
        fun splashAdLoadTimeoutMs(ms: Long) = apply { this.splashAdLoadTimeoutMs = ms }
        fun foregroundAdExpirationMs(ms: Long) = apply { this.foregroundAdExpirationMs = ms }
        fun foregroundAdCooldownMs(ms: Long) = apply { this.foregroundAdCooldownMs = ms }
        fun foregroundAdShowIntervalMs(ms: Long) = apply { this.foregroundAdShowIntervalMs = ms }
        fun preloadOnBackground(enabled: Boolean) = apply { this.preloadOnBackground = enabled }
        fun useAppOpenAdPreloader(enabled: Boolean) = apply {
            this.useAppOpenAdPreloader = enabled
        }

        fun appOpenAdPreloadBufferSize(size: Int?) = apply {
            require(size == null || size >= 1) {
                "App open ad preload buffer size must be null or at least 1"
            }
            this.appOpenAdPreloadBufferSize = size
        }

        fun shouldSuppressAds(predicate: () -> Boolean) = apply { this.shouldSuppressAds = predicate }
        fun debugLogging(enabled: Boolean) = apply { this.debugLogging = enabled }
        fun maxAdContentRating(rating: String?) = apply { this.maxAdContentRating = rating }
        fun tagForChildDirectedTreatment(tag: Boolean?) = apply { this.tagForChildDirectedTreatment = tag }
        fun tagForUnderAgeOfConsent(tag: Boolean?) = apply { this.tagForUnderAgeOfConsent = tag }
        fun addKeyword(keyword: String) = apply { this.keywords.add(keyword) }
        fun addKeywords(keywords: List<String>) = apply { this.keywords.addAll(keywords) }

        fun build() = AdmobConfig(
            admobAppId = admobAppId,
            splashAdUnitId = splashAdUnitId,
            foregroundAdUnitId = foregroundAdUnitId,
            consentTimeoutMs = consentTimeoutMs,
            splashAdLoadTimeoutMs = splashAdLoadTimeoutMs,
            foregroundAdExpirationMs = foregroundAdExpirationMs,
            foregroundAdCooldownMs = foregroundAdCooldownMs,
            foregroundAdShowIntervalMs = foregroundAdShowIntervalMs,
            preloadOnBackground = preloadOnBackground,
            useAppOpenAdPreloader = useAppOpenAdPreloader,
            appOpenAdPreloadBufferSize = appOpenAdPreloadBufferSize,
            shouldSuppressAds = shouldSuppressAds,
            debugLogging = debugLogging,
            maxAdContentRating = maxAdContentRating,
            tagForChildDirectedTreatment = tagForChildDirectedTreatment,
            tagForUnderAgeOfConsent = tagForUnderAgeOfConsent,
            keywords = keywords.toList(),
        )
    }

    companion object {
        const val MAX_AD_CONTENT_RATING_G = "G"
        const val MAX_AD_CONTENT_RATING_PG = "PG"
        const val MAX_AD_CONTENT_RATING_T = "T"
        const val MAX_AD_CONTENT_RATING_MA = "MA"
    }
}
