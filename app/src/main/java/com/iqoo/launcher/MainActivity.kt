package com.iqoo.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.BatteryManager
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import android.widget.ImageView
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import java.util.concurrent.Executors

data class DeviceProfile(
    val manufacturer: String, val model: String, val release: String, val sdk: Int,
    val widthPx: Int, val heightPx: Int, val densityDpi: Int, val density: Float,
    val refreshRates: List<Float>, val buildId: String
) {
    val preferredRefreshRate: Float get() = refreshRates.maxOrNull() ?: 60f
    companion object {
        fun read(context: Context): DeviceProfile {
            val metrics = context.resources.displayMetrics
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            val display: Display? = if (Build.VERSION.SDK_INT >= 30) context.display else wm.defaultDisplay
            val rates = display?.supportedModes?.map { it.refreshRate }?.filter { it.isFinite() && it > 0f }?.distinct()?.sorted().orEmpty()
            val size = android.graphics.Point()
            @Suppress("DEPRECATION")
            display?.getRealSize(size)
            return DeviceProfile(Build.MANUFACTURER.orEmpty(), Build.MODEL.orEmpty(), Build.VERSION.RELEASE.orEmpty(),
                Build.VERSION.SDK_INT, size.x.takeIf { it > 0 } ?: metrics.widthPixels,
                size.y.takeIf { it > 0 } ?: metrics.heightPixels, metrics.densityDpi, metrics.density,
                rates, Build.DISPLAY.orEmpty())
        }
    }
}

data class LaunchableApp(val label: String, val packageName: String, val className: String, val revision: Long = 0L) {
    val id: String get() = "$packageName/$className"
}

data class ChargingState(val percent: Int?, val plugged: Boolean, val charging: Boolean) {
    val active: Boolean get() = plugged || charging
    companion object {
        fun from(intent: Intent?): ChargingState {
            if (intent == null) return ChargingState(null, false, false)
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            return fromValues(level, scale, status, intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0))
        }
        fun fromValues(level: Int, scale: Int, status: Int, plugSource: Int): ChargingState {
            val plugged = plugSource != 0
            val percent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else null
            return ChargingState(percent, plugged,
                status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
        }
    }
}

class MainActivity : ComponentActivity() {
    private var apps by mutableStateOf<List<LaunchableApp>>(emptyList())
    private var charging by mutableStateOf(ChargingState(null, false, false))
    private val appLoader = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = Runnable { refreshApps() }
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { charging = ChargingState.from(intent) }
    }
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            mainHandler.removeCallbacks(refreshRunnable)
            mainHandler.postDelayed(refreshRunnable, 350L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profile = DeviceProfile.read(this)
        setContent {
            MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme(primary = Color(0xFFFF492E))) {
                VelocityHome(profile, apps, charging, ::launchApp)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        charging = ChargingState.from(registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, filter)
        refreshApps()
    }

    override fun onStop() {
        unregisterReceiver(batteryReceiver)
        unregisterReceiver(packageReceiver)
        super.onStop()
    }

    private fun refreshApps() {
        appLoader.execute {
            val pm = packageManager
            val revision = System.nanoTime()
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val found = pm.queryIntentActivities(intent, 0)
            val result = found.asSequence().mapNotNull { item ->
                val info = item.activityInfo ?: return@mapNotNull null
                if (info.packageName == packageName) return@mapNotNull null
                val label = runCatching { item.loadLabel(pm)?.toString() }.getOrNull().orEmpty().ifBlank { info.name }
                LaunchableApp(label, info.packageName, info.name, revision)
            }.distinctBy { it.id }.sortedBy { it.label.lowercase() }.toList()
            mainHandler.post { if (!isFinishing && !isDestroyed) apps = result }
        }
    }

    private fun launchApp(app: LaunchableApp) {
        val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
        val explicit = launchIntent ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(app.packageName, app.className)
        try { startActivity(explicit) }
        catch (_: Exception) { android.widget.Toast.makeText(this, "App is no longer available", android.widget.Toast.LENGTH_SHORT).show(); refreshApps() }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(refreshRunnable)
        appLoader.shutdownNow()
        super.onDestroy()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VelocityHome(profile: DeviceProfile, apps: List<LaunchableApp>, charging: ChargingState,
                         onLaunch: (LaunchableApp) -> Unit) {
    var drawer by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    BackHandler(enabled = drawer) { drawer = false; query = "" }
    val background = Brush.verticalGradient(listOf(Color(0xFF191C23), Color(0xFF08090B)))
    Surface(Modifier.fillMaxSize(), color = Color(0xFF08090B)) {
        Column(Modifier.fillMaxSize().background(background).padding(horizontal = 22.dp, vertical = 24.dp)) {
            Text("IQOO  /  PROJECT VELOCITY", color = Color(0xFFB7BAC2), fontSize = 11.sp, letterSpacing = 1.4.sp)
            Spacer(Modifier.height(18.dp))
            PulseCard(charging)
            Spacer(Modifier.height(20.dp))
            AnimatedContent(targetState = drawer, modifier = Modifier.weight(1f),
                transitionSpec = { VelocityMotion.screenTransition(targetState) }, label = "home-drawer") { showingDrawer ->
                if (showingDrawer) {
                    Column(Modifier.fillMaxSize()) {
                        VelocityGlass(Modifier.fillMaxWidth(), radius = 20.dp) {
                            BasicTextField(value = query, onValueChange = { query = it }, singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                decorationBox = { inner -> Box { if (query.isEmpty()) Text("Search apps", color = Color(0xFF969AA3)); inner() } })
                        }
                        Spacer(Modifier.height(14.dp))
                        val filtered = apps.filter { it.label.contains(query, ignoreCase = true) }
                        LazyVerticalGrid(columns = GridCells.Adaptive(76.dp), modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(filtered, key = { it.id }) { app -> AppTile(app, onLaunch) }
                        }
                        if (filtered.isEmpty()) Text("No matching apps", color = Color(0xFF9DA1AA), modifier = Modifier.padding(18.dp))
                    }
                } else {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(18.dp))
                        Text(android.text.format.DateFormat.format("HH:mm", java.util.Date()).toString(), color = Color.White,
                            fontSize = 68.sp, fontWeight = FontWeight.Light)
                        Text("${apps.size} launchable apps", color = Color(0xFF9DA1AA), fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                        VelocityGlass(Modifier.fillMaxWidth().clickable { drawer = true }, radius = 28.dp) {
                            Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) {
                                Text("OPEN APP LIBRARY  ·  ${apps.size}", color = Color.White, fontSize = 13.sp, letterSpacing = 1.sp)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("${profile.model}  ·  Android ${profile.release} / API ${profile.sdk}", color = Color(0xFF8D929B), fontSize = 10.sp)
                        Text("${profile.widthPx}×${profile.heightPx}  ·  ${profile.densityDpi} dpi  ·  ${profile.refreshRates.joinToString(" / ") { "${it.toInt()} Hz" }.ifBlank { "refresh rate unavailable" }}",
                            color = Color(0xFF8D929B), fontSize = 10.sp)
                    }
                }
            }
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { drawer = false; query = "" } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun PulseCard(state: ChargingState) {
    var expanded by remember { mutableStateOf(false) }
    VelocityGlass(Modifier.fillMaxWidth().clickable { expanded = !expanded }
        .animateContentSize(VelocityMotion.container), radius = if (expanded) 30.dp else 24.dp) {
      Column(Modifier.padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (state.active) Color(0xFFFF492E) else Color(0xFF656970)))
            Spacer(Modifier.width(10.dp))
            Text("IQOO PULSE", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Text(if (state.charging) "CHARGING" else if (state.plugged) "CONNECTED" else "POWER", color = Color(0xFFB5B8BE), fontSize = 10.sp)
        }
        if (state.active || expanded) {
            Spacer(Modifier.height(12.dp))
            Text(if (state.active) "${state.percent?.let { "$it%" } ?: "Battery"}  ·  ${if (state.charging) "Charging" else "Power connected"}" else "No active charging event",
                color = Color.White, fontSize = if (expanded) 18.sp else 14.sp)
            if (expanded) Text("Live system battery state", color = Color(0xFF9DA1AA), fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
        }
      }
    }
}

@Composable
private fun AppTile(app: LaunchableApp, onLaunch: (LaunchableApp) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val icon: Drawable? = remember(app.id, app.revision) { runCatching { context.packageManager.getActivityIcon(android.content.ComponentName(app.packageName, app.className)) }.getOrNull() }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onLaunch(app) }
        .background(Color(0x28FFFFFF)).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        AndroidView(factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
            update = { it.setImageDrawable(icon) }, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(5.dp))
        Text(app.label, color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
