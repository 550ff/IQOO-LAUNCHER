# Phase 2 architecture

- `MainActivity`: platform lifecycle, receiver registration, asynchronous app discovery, HOME role request, settings shortcuts and launch intents.
- `DeviceProfile`: immutable runtime device/display data, with display-size fallback to resource metrics and 60 Hz informational fallback when modes are unavailable.
- `ChargingState`: pure state conversion plus a lifecycle-scoped dynamic battery receiver; Compose observes Activity state.
- `AppCatalog`: pure stable sorting, deduplication and normalized search used by the drawer and unit tests.
- `IconRepository`: bounded `LruCache` of drawable constant states; icon retrieval runs on `Dispatchers.IO`, so composition and scrolling never call `PackageManager` directly.
- `VelocityShell`: one coherent home/dock/drawer/control-centre surface. Swipe up opens the drawer and swipe down opens the in-launcher control centre.
- `VelocityGlass`: reusable static glass material (gradient and border, no blur).
- `VelocityMotion`: shared spring and screen transition specifications.

App enumeration runs on a single background executor. The drawer uses stable activity IDs, a remembered filtered list, lazy-grid keys and a bounded asynchronous icon cache. Package broadcasts are coalesced before refresh. Battery and package receivers are unregistered at Activity stop; queued refresh work is removed and the executor shut down on destroy.

The dock is intentionally lightweight and launches the real app drawer; it does not pretend to launch a hard-coded app. The control centre opens public Android settings surfaces and requests the Android HOME role. The OS-owned notification shade/Quick Settings remain outside a third-party launcher's authority.
