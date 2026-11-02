package com.iqoo.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCatalogTest {
    private val apps = listOf(LaunchableApp("YouTube", "com.google.youtube", "Main"), LaunchableApp("Settings", "com.android.settings", "Settings"), LaunchableApp("YouTube", "com.google.youtube", "Main"))

    @Test fun stableApps_deduplicatesAndSortsWithoutChangingIds() {
        assertEquals(listOf("com.android.settings/Settings", "com.google.youtube/Main"), AppCatalog.stableApps(apps).map { it.id })
    }

    @Test fun filter_matchesLabelsAndPackageNames_caseInsensitively() {
        assertEquals(1, AppCatalog.filter(apps, "SET").size)
        assertEquals(2, AppCatalog.filter(apps, "youtube").size)
        assertTrue(AppCatalog.filter(apps, "  ").size == apps.size)
    }
}
