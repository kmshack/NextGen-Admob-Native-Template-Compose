package com.soosu.nextgen.admobnative

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class NativeAdAssetRegistryTest {
    @Test
    fun `late icon triggers registration after the headline was already registered`() {
        val registrations = mutableListOf<Set<String>>()
        val sdkAssets = mutableMapOf<String, Any>()
        val registry = NativeAdAssetRegistry { registrations += sdkAssets.keys.toSet() }

        val headline = Any()
        registry.register("headline", headline) { sdkAssets["headline"] = headline }
        // A URI-only image finishes downloading after the text has been displayed.
        val icon = Any()
        registry.register("icon", icon) { sdkAssets["icon"] = icon }

        assertEquals(listOf(setOf("headline"), setOf("headline", "icon")), registrations)
    }

    @Test
    fun `stable recomposition does not reset native ad registration`() {
        var changes = 0
        var assetAssignments = 0
        val registry = NativeAdAssetRegistry { changes++ }
        val icon = Any()

        repeat(5) {
            registry.register("icon", icon) { assetAssignments++ }
        }

        assertEquals(1, changes)
        assertEquals(1, assetAssignments)
    }

    @Test
    fun `releasing a replaced asset does not clear its replacement`() {
        var changes = 0
        var sdkIcon: Any? = null
        val registry = NativeAdAssetRegistry { changes++ }
        val oldIcon = Any()
        val newIcon = Any()

        registry.register("icon", oldIcon) { sdkIcon = oldIcon }
        registry.register("icon", newIcon) { sdkIcon = newIcon }
        registry.unregister("icon", oldIcon) { sdkIcon = null }

        assertSame(newIcon, sdkIcon)
        assertEquals(2, changes)

        registry.unregister("icon", newIcon) { sdkIcon = null }
        assertNull(sdkIcon)
        assertEquals(3, changes)
    }
}
