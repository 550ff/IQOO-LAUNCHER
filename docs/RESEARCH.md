# Phase 1 research notes

## Verified decisions

- Compose springs are interruptible and carry current velocity; use one shared spring family for expandable content and surface transitions. Avoid infinite transitions for static launcher surfaces. [Compose animation guidance](https://developer.android.com/develop/ui/compose/animation/quick-guide)
- The app reads display modes at runtime and presents supported refresh rates. It does not assert 120 Hz on its own: Android retains display scheduling control, and no frame-rate result is claimed.
- `ACTION_BATTERY_CHANGED` is a sticky, protected broadcast and must be registered dynamically. PULSE reads that real event and does not poll. [Android Intent reference](https://developer.android.com/reference/android/content/Intent#ACTION_BATTERY_CHANGED)
- Launchable apps are queried through the `MAIN` / `LAUNCHER` intent pair. The manifest includes that query for package visibility on newer Android releases.
- Static gradient and hairline surfaces are used for glass styling. There is no live blur in this phase; this avoids broad offscreen rendering and has a consistent low-cost fallback.
- Build versions: AGP 8.13.2 with Gradle 8.13; Kotlin and Compose compiler plugin 2.2.20; Compose BOM 2026.09.00. The Android Gradle Plugin compatibility table pairs AGP 8.13 with Gradle 8.13. [AGP compatibility](https://developer.android.com/build/releases/about-agp) [Compose dependencies](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)

## Capability boundaries

- Publicly available now: device/display metadata, battery state, launchable activity discovery, app launching, Compose UI and animation.
- Requires consent and separate implementation: overlay window (special overlay permission), media session access through enabled notification access, notification content through NotificationListenerService.
- Not available to a normal launcher: replacing system status/quick-settings surfaces or reading arbitrary app-screen content. IQOO PULSE is currently in-app only and displays charging data only.
- Exact iQOO Z10x Android build and physical performance have not been verified. Runtime display/profile values are detected on-device rather than assumed.
