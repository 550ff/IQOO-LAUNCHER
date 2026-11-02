# Phase 1 architecture

- `MainActivity`: platform lifecycle, receiver registration, asynchronous app discovery and launch intents.
- `DeviceProfile`: immutable runtime device/display data, with display-size fallback to resource metrics and 60 Hz informational fallback when modes are unavailable.
- `ChargingState`: pure state conversion plus a lifecycle-scoped dynamic battery receiver; Compose observes Activity state.
- `VelocityHome`: home/app-library navigation, search, lazy grid and PULSE rendering.
- `VelocityGlass`: reusable static glass material (gradient and border, no blur).
- `VelocityMotion`: shared spring and screen transition specifications.

App enumeration runs on a single background executor. Only visible lazy-grid cells request icons, keyed by activity and discovery revision. Package broadcasts are coalesced before refresh. Battery and package receivers are unregistered at Activity stop; queued refresh work is removed and the executor shut down on destroy.

The app library is the first navigation surface. Folders, dock management, settings, PULSE overlay, media and notification sources are later milestones, not simulated in this build.
