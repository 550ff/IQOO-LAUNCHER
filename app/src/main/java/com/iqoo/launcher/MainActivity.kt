package com.iqoo.launcher

import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.provider.Settings
import android.view.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import java.util.*
import java.util.concurrent.Executors

data class DeviceProfile(val manufacturer: String, val model: String, val release: String, val sdk: Int, val widthPx: Int, val heightPx: Int, val densityDpi: Int, val density: Float, val refreshRates: List<Float>, val buildId: String) {
    val preferredRefreshRate get() = refreshRates.maxOrNull() ?: 60f
    companion object { fun read(context: Context): DeviceProfile { val metrics = context.resources.displayMetrics; val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager; @Suppress("DEPRECATION") val display: Display? = if (Build.VERSION.SDK_INT >= 30) context.display else wm.defaultDisplay; val rates = display?.supportedModes?.map { it.refreshRate }?.filter { it.isFinite() && it > 0f }?.distinct()?.sorted().orEmpty(); val size = android.graphics.Point(); @Suppress("DEPRECATION") display?.getRealSize(size); return DeviceProfile(Build.MANUFACTURER.orEmpty(), Build.MODEL.orEmpty(), Build.VERSION.RELEASE.orEmpty(), Build.VERSION.SDK_INT, size.x.takeIf { it > 0 } ?: metrics.widthPixels, size.y.takeIf { it > 0 } ?: metrics.heightPixels, metrics.densityDpi, metrics.density, rates, Build.DISPLAY.orEmpty()) } }
}

data class LaunchableApp(val label: String, val packageName: String, val className: String) { val id get() = "$packageName/$className" }
data class ChargingState(val percent: Int?, val plugged: Boolean, val charging: Boolean) { val active get() = plugged || charging; companion object { fun from(i: Intent?): ChargingState { if (i == null) return ChargingState(null, false, false); return fromValues(i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), i.getIntExtra(BatteryManager.EXTRA_SCALE, -1), i.getIntExtra(BatteryManager.EXTRA_STATUS, 1), i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) }; fun fromValues(level: Int, scale: Int, status: Int, plug: Int): ChargingState { val percent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else null; return ChargingState(percent, plug != 0, status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) } } }

class MainActivity : ComponentActivity() {
    private var apps by mutableStateOf<List<LaunchableApp>>(emptyList()); private var charging by mutableStateOf(ChargingState(null, false, false))
    private val loader = Executors.newSingleThreadExecutor(); private val handler = Handler(Looper.getMainLooper()); private val refresh = Runnable { refreshApps() }
    private val battery = object : BroadcastReceiver() { override fun onReceive(c: Context?, i: Intent?) { charging = ChargingState.from(i) } }
    private val packages = object : BroadcastReceiver() { override fun onReceive(c: Context?, i: Intent?) { handler.removeCallbacks(refresh); handler.postDelayed(refresh, 300) } }
    override fun onCreate(state: Bundle?) { super.onCreate(state); val profile = DeviceProfile.read(this); setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFFF4D32))) { VelocityShell(profile, apps, charging, IconRepository(this), ::launchApp, ::requestDefault, ::systemSettings) } } }
    override fun onStart() { super.onStart(); charging = ChargingState.from(registerReceiver(battery, IntentFilter(Intent.ACTION_BATTERY_CHANGED))); val f = IntentFilter().apply { addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED); addAction(Intent.ACTION_PACKAGE_REPLACED); addDataScheme("package") }; registerReceiver(packages, f); refreshApps() }
    private fun refreshApps() { loader.execute { val pm = packageManager; val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER); val result = AppCatalog.stableApps(pm.queryIntentActivities(intent, PackageManager.MATCH_ALL).mapNotNull { item -> val info = item.activityInfo ?: return@mapNotNull null; if (info.packageName == packageName) return@mapNotNull null; LaunchableApp(runCatching { item.loadLabel(pm).toString() }.getOrDefault(info.name), info.packageName, info.name) }); handler.post { if (!isFinishing && !isDestroyed) apps = result } } }
    private fun launchApp(app: LaunchableApp) { val intent = packageManager.getLaunchIntentForPackage(app.packageName) ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(app.packageName, app.className); runCatching { startActivity(intent) }.onFailure { refreshApps() } }
    private fun requestDefault() { val intent = if (Build.VERSION.SDK_INT >= 29) getSystemService(android.app.role.RoleManager::class.java)?.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME) else null; startActivity(intent ?: Intent(Settings.ACTION_HOME_SETTINGS)) }
    private fun systemSettings(action: String) { runCatching { startActivity(Intent(action)) }.onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    override fun onStop() { unregisterReceiver(battery); unregisterReceiver(packages); super.onStop() }; override fun onDestroy() { handler.removeCallbacks(refresh); loader.shutdownNow(); super.onDestroy() }
}

private enum class Surface { HOME, DRAWER, CONTROL }

@Composable private fun VelocityShell(profile: DeviceProfile, apps: List<LaunchableApp>, charging: ChargingState, icons: IconRepository, launch: (LaunchableApp) -> Unit, requestDefault: () -> Unit, settings: (String) -> Unit) {
    var surface by remember { mutableStateOf(Surface.HOME) }; var query by remember { mutableStateOf("") }; val filtered = remember(apps, query) { AppCatalog.filter(apps, query) }; val lifecycle = LocalLifecycleOwner.current.lifecycle
    BackHandler(surface != Surface.HOME) { surface = Surface.HOME; query = "" }; DisposableEffect(lifecycle) { val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) { surface = Surface.HOME; query = "" } }; lifecycle.addObserver(o); onDispose { lifecycle.removeObserver(o) } }
    Surface(Modifier.fillMaxSize().pointerInput(Unit) { var total = 0f; detectVerticalDragGestures(onDragStart = { total = 0f }, onVerticalDrag = { _, d -> total += d }, onDragEnd = { if (total < -70) surface = Surface.DRAWER; if (total > 70 && surface == Surface.HOME) surface = Surface.CONTROL }) }, color = Color(0xFF08090B)) {
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF171A22), Color(0xFF07080B)))).padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("VELOCITY", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp); Text("${profile.model}  ·  ${profile.preferredRefreshRate.toInt()} Hz target", color = Color(0xFF969AA5), fontSize = 10.sp) }; Text("⚙", color = Color(0xFFBFC3CC), fontSize = 20.sp, modifier = Modifier.clickable { settings(Settings.ACTION_SETTINGS) }.padding(8.dp)) }
            when (surface) { Surface.HOME -> HomeSurface(apps, charging, launch, { surface = Surface.DRAWER }, { surface = Surface.CONTROL }); Surface.DRAWER -> DrawerSurface(filtered, query, { query = it }, icons, launch); Surface.CONTROL -> ControlCentre(charging, requestDefault, settings) }
        }
    }
}

@Composable private fun HomeSurface(apps: List<LaunchableApp>, charging: ChargingState, launch: (LaunchableApp) -> Unit, drawer: () -> Unit, control: () -> Unit) { Column(Modifier.fillMaxSize()) { Spacer(Modifier.height(22.dp)); Text(android.text.format.DateFormat.format("HH:mm", Date()).toString(), color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Light); Text(android.text.format.DateFormat.format("EEEE, d MMMM", Date()).toString(), color = Color(0xFF9EA3AD), fontSize = 13.sp); Spacer(Modifier.height(18.dp)); PulseCard(charging); Spacer(Modifier.weight(1f)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { ActionCard("CONTROL\nCENTRE", "Swipe down", control, Modifier.weight(1f)); ActionCard("APP\nLIBRARY", "${apps.size} apps", drawer, Modifier.weight(1f)) }; Spacer(Modifier.height(12.dp)); VelocityGlass(Modifier.fillMaxWidth().height(72.dp), 24.dp) { Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) { listOf("Phone", "Messages", "Camera", "Browser").forEach { Text(it, color = Color(0xFFDDE0E7), fontSize = 11.sp, modifier = Modifier.clickable { drawer() }.padding(10.dp)) } } }; Spacer(Modifier.height(6.dp)); Text("Swipe up for every app  ·  swipe down for launcher controls", color = Color(0xFF777D88), fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally)) } }
@Composable private fun ActionCard(title: String, subtitle: String, click: () -> Unit, modifier: Modifier) = VelocityGlass(modifier.clickable { click() }, 20.dp) { Column(Modifier.padding(16.dp)) { Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(5.dp)); Text(subtitle, color = Color(0xFF9DA2AE), fontSize = 11.sp) } }

@Composable private fun DrawerSurface(apps: List<LaunchableApp>, query: String, setQuery: (String) -> Unit, icons: IconRepository, launch: (LaunchableApp) -> Unit) { Column(Modifier.fillMaxSize()) { Spacer(Modifier.height(18.dp)); Text("APP LIBRARY", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold); Text("Fast, local search · ${apps.size} results", color = Color(0xFF9EA3AD), fontSize = 12.sp); Spacer(Modifier.height(14.dp)); VelocityGlass(Modifier.fillMaxWidth(), 18.dp) { BasicTextField(query, setQuery, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 16.sp), modifier = Modifier.fillMaxWidth().padding(16.dp), decorationBox = { inner -> Box { if (query.isEmpty()) Text("Search apps", color = Color(0xFF858A95)); inner() } }) }; Spacer(Modifier.height(12.dp)); if (apps.isEmpty()) Text("No matching apps", color = Color(0xFF9DA1AA), modifier = Modifier.padding(18.dp)) else LazyVerticalGrid(columns = GridCells.Adaptive(78.dp), contentPadding = PaddingValues(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) { items(apps, key = { it.id }) { AppTile(it, icons, launch) } } } }
@Composable private fun AppTile(app: LaunchableApp, icons: IconRepository, launch: (LaunchableApp) -> Unit) { var icon by remember(app.id) { mutableStateOf<android.graphics.drawable.Drawable?>(null) }; LaunchedEffect(app.id) { icon = icons.load(app) }; Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { launch(app) }.background(Color(0x20FFFFFF)).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) { AndroidView(factory = { android.widget.ImageView(it).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER } }, update = { it.setImageDrawable(icon) }, modifier = Modifier.size(48.dp)); Spacer(Modifier.height(5.dp)); Text(app.label, color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } }

@Composable private fun ControlCentre(charging: ChargingState, requestDefault: () -> Unit, settings: (String) -> Unit) { Column(Modifier.fillMaxSize()) { Spacer(Modifier.height(20.dp)); Text("CONTROL CENTRE", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold); Text("Shortcuts backed by public Android settings APIs", color = Color(0xFF9EA3AD), fontSize = 12.sp); Spacer(Modifier.height(16.dp)); PulseCard(charging); Spacer(Modifier.height(14.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { SettingTile("Wi‑Fi", Settings.ACTION_WIFI_SETTINGS, settings, Modifier.weight(1f)); SettingTile("Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS, settings, Modifier.weight(1f)) }; Spacer(Modifier.height(10.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { SettingTile("Display", Settings.ACTION_DISPLAY_SETTINGS, settings, Modifier.weight(1f)); SettingTile("Battery", Settings.ACTION_BATTERY_SAVER_SETTINGS, settings, Modifier.weight(1f)) }; Spacer(Modifier.height(10.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { SettingTile("Notifications", "android.settings.NOTIFICATION_SETTINGS", settings, Modifier.weight(1f)); SettingTile("Make default", "default", { requestDefault() }, Modifier.weight(1f)) }; Spacer(Modifier.height(16.dp)); Text("The notification shade and Quick Settings belong to Android and iQOO. A third-party launcher cannot replace or expand them; use the system gesture for the real shade.", color = Color(0xFF858B96), fontSize = 11.sp, lineHeight = 16.sp) } }
@Composable private fun SettingTile(label: String, action: String, settings: (String) -> Unit, modifier: Modifier) = VelocityGlass(modifier.clickable { settings(action) }, 18.dp) { Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(16.dp)) }
@Composable private fun PulseCard(state: ChargingState) { VelocityGlass(Modifier.fillMaxWidth(), 22.dp) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(if (state.active) Color(0xFFFF4D32) else Color(0xFF656970))); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("IQOO PULSE", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp); Text(if (state.active) "${state.percent?.let { "$it%" } ?: "Battery"}  ·  ${if (state.charging) "Charging" else "Power connected"}" else "Ready for your next move", color = Color(0xFF9DA2AE), fontSize = 11.sp) }; Text(if (state.charging) "CHARGING" else "POWER", color = Color(0xFFB5B8BE), fontSize = 10.sp) } } }
