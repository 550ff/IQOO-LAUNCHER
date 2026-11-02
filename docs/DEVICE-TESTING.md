# Device testing status

## Not yet physically tested

The iQOO Z10x is not connected to this development environment. Its exact Android release, SDK, build display ID, resolution, density and supported modes remain unverified; the app reads profile data at runtime.

## Checklist for device validation

- Confirm iQOO Z10x model/build and runtime profile values.
- Confirm Home selection and return from launched apps.
- Check app drawer with a large installed-app set, search, back and keyboard dismissal.
- Swipe up/down repeatedly while the drawer, keyboard and settings activities are appearing; verify transitions remain responsive and the drawer keeps scroll position.
- Open each control-centre tile and verify it lands in the corresponding Android settings page; use Make default and confirm the Android HOME role flow.
- Connect/disconnect power and verify PULSE percentage/status updates; rotate and background/foreground.
- Rapidly reverse drawer and PULSE transitions.
- Verify supported refresh modes and Android display scheduling; measure frame timing before reporting performance.
- Inspect memory and cold/warm startup under normal and memory-pressure conditions.
- Capture a Perfetto/system trace or Android Studio CPU/memory profile while opening and searching the drawer. Confirm no `PackageManager` icon work occurs on the main thread and record actual frame timing before making any 120 Hz claim.

No 120 FPS or battery-impact claim is made until measured on the device.
