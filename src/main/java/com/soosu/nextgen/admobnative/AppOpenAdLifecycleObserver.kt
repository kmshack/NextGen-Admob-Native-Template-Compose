package com.soosu.nextgen.admobnative

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.lang.ref.WeakReference

class AppOpenAdLifecycleObserver(
    application: Application,
    private val adManager: AppOpenAdManager,
    private val config: AdmobConfig,
) : DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {

    val excludedActivities: MutableSet<String> = mutableSetOf()

    private var currentActivityRef: WeakReference<Activity>? = null
    private var ignoreNextForeground = false
    private var isInBackground = false

    init {
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun ignoreNextForegroundAd() {
        ignoreNextForeground = true
    }

    // DefaultLifecycleObserver - process lifecycle

    override fun onStart(owner: LifecycleOwner) {
        if (!isInBackground) return
        isInBackground = false

        if (ignoreNextForeground) {
            ignoreNextForeground = false
            Log.d(TAG, "Ignoring foreground ad (one-time skip)")
            return
        }

        val activity = currentActivityRef?.get() ?: return

        if (excludedActivities.contains(activity::class.java.name)) {
            Log.d(TAG, "Activity excluded: ${activity::class.java.simpleName}")
            return
        }

        adManager.showAdIfAvailable(activity)
    }

    override fun onStop(owner: LifecycleOwner) {
        isInBackground = true

        if (config.preloadOnBackground) {
            val activity = currentActivityRef?.get()
            if (activity != null) {
                adManager.loadAd(activity)
            }
        }
    }

    // Application.ActivityLifecycleCallbacks

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        currentActivityRef = WeakReference(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivityRef = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}

    companion object {
        private const val TAG = "AppOpenAdLifecycle"
    }
}
