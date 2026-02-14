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
    val shouldSuppressAds: () -> Boolean,
    val debugLogging: Boolean,
) {
    class Builder(private val admobAppId: String) {
        private var splashAdUnitId: String? = null
        private var foregroundAdUnitId: String? = null
        private var consentTimeoutMs: Long = 5_000L
        private var splashAdLoadTimeoutMs: Long = 8_000L
        private var foregroundAdExpirationMs: Long = 4 * 60 * 60 * 1_000L
        private var foregroundAdCooldownMs: Long = 30_000L
        private var foregroundAdShowIntervalMs: Long = 0L
        private var preloadOnBackground: Boolean = false
        private var shouldSuppressAds: () -> Boolean = { false }
        private var debugLogging: Boolean = false

        fun splashAdUnitId(id: String?) = apply { this.splashAdUnitId = id }
        fun foregroundAdUnitId(id: String?) = apply { this.foregroundAdUnitId = id }
        fun consentTimeoutMs(ms: Long) = apply { this.consentTimeoutMs = ms }
        fun splashAdLoadTimeoutMs(ms: Long) = apply { this.splashAdLoadTimeoutMs = ms }
        fun foregroundAdExpirationMs(ms: Long) = apply { this.foregroundAdExpirationMs = ms }
        fun foregroundAdCooldownMs(ms: Long) = apply { this.foregroundAdCooldownMs = ms }
        fun foregroundAdShowIntervalMs(ms: Long) = apply { this.foregroundAdShowIntervalMs = ms }
        fun preloadOnBackground(enabled: Boolean) = apply { this.preloadOnBackground = enabled }
        fun shouldSuppressAds(predicate: () -> Boolean) = apply { this.shouldSuppressAds = predicate }
        fun debugLogging(enabled: Boolean) = apply { this.debugLogging = enabled }

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
            shouldSuppressAds = shouldSuppressAds,
            debugLogging = debugLogging,
        )
    }
}
