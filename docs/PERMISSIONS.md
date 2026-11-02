# Permissions and platform access

Phase 2 requests no runtime or special permissions.

- Package discovery uses the manifest `<queries>` intent for `MAIN` / `LAUNCHER`; it does not request broad package visibility.
- Battery state is read from the protected sticky system broadcast using a dynamically registered receiver; no battery permission is required.
- App launch uses the system launch intent, with an explicit activity fallback for launchable components.
- The manifest declares the standard `HOME` intent filter. The control centre uses the Android 10+ `RoleManager.ROLE_HOME` request flow, with the public HOME settings fallback on older versions.
- Wi-Fi, Bluetooth, display, battery and notification tiles open public Settings actions. They do not claim to toggle protected system state from a third-party app.
- The notification shade and Quick Settings are OS-owned. This app does not use root, hidden APIs, overlays, accessibility abuse or notification-listener access to imitate them.
- Not requested: `SYSTEM_ALERT_WINDOW`, notification listener access, camera, contacts, location or accessibility service.
