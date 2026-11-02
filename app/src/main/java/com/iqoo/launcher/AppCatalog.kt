package com.iqoo.launcher

/** Pure catalog operations kept separate from Compose so discovery/search stays cheap and testable. */
object AppCatalog {
    fun normalizeQuery(value: String): String = value.trim().lowercase()

    fun filter(apps: List<LaunchableApp>, query: String): List<LaunchableApp> {
        val normalized = normalizeQuery(query)
        if (normalized.isEmpty()) return apps
        return apps.filter { app -> app.label.lowercase().contains(normalized) || app.packageName.lowercase().contains(normalized) }
    }

    fun stableApps(apps: List<LaunchableApp>): List<LaunchableApp> = apps.distinctBy(LaunchableApp::id).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
}
