# Permissions and platform access

Phase 1 requests no runtime or special permissions.

- Package discovery uses the manifest `<queries>` intent for `MAIN` / `LAUNCHER`; it does not request broad package visibility.
- Battery state is read from the protected sticky system broadcast using a dynamically registered receiver; no battery permission is required.
- App launch uses the system launch intent, with an explicit activity fallback for launchable components.
- Not requested yet: `SYSTEM_ALERT_WINDOW` (future opt-in PULSE overlay), notification listener access (future notifications/media sessions), camera, contacts, location, or accessibility service.
