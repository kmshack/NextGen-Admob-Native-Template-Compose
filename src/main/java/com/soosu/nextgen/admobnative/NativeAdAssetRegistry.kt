package com.soosu.nextgen.admobnative

/** Tracks asset view identity so late assets trigger a new SDK registration. */
internal class NativeAdAssetRegistry(private val onChanged: () -> Unit) {
    private val assets = mutableMapOf<String, Any>()

    fun register(name: String, view: Any, apply: () -> Unit) {
        if (assets[name] === view) return
        assets[name] = view
        apply()
        onChanged()
    }

    fun unregister(name: String, view: Any, clear: () -> Unit) {
        // An old AndroidView may be released after its replacement was registered.
        if (assets[name] !== view) return
        assets.remove(name)
        clear()
        onChanged()
    }
}
