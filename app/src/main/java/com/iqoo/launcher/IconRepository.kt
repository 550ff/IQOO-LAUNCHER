package com.iqoo.launcher

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads icons away from the UI thread and keeps a small process-local cache for scroll stability. */
class IconRepository(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val cache = object : LruCache<String, Drawable.ConstantState>(64) {}

    suspend fun load(app: LaunchableApp): Drawable? = withContext(Dispatchers.IO) {
        cache.get(app.id)?.newDrawable(appContext.resources)?.mutate()?.let { return@withContext it }
        val drawable = runCatching { packageManager.getActivityIcon(ComponentName(app.packageName, app.className)) }.getOrNull()
            ?: return@withContext null
        drawable.constantState?.let { cache.put(app.id, it) }
        drawable.mutate()
    }
}
