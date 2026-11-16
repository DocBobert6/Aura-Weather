# Aura Weather 0.1.1

Fixes the first-launch crash in 0.1.0. That release crashed every time it
was opened: after the first forecast loaded, the app tried to save it to
its offline cache and crashed, so it never got past the first load. The
cause was the release build's code shrinker (R8) renaming the
weather-condition names — ClearDay, Rain, and the rest — that Moshi reads
when saving the forecast cache. The build now tells R8 to leave those
names alone, and to leave the forecast-cache models' fields alone too, so
the cache save works and the app stays open.

## Notes

- This is an arm64-only APK (`arm64-v8a`); there is no universal build.
- The signed release APK will not install over a debug build (different
  signing key). Uninstall the debug build first — that wipes app data,
  including places and settings, which need re-adding afterwards.

---

# Aura Weather 0.1.0

First release. The radar tab is rebuilt from the ground up, the retro look
gets its finishing touch, and the in-app updater goes live with this release.

## Rebuilt radar

- New MapLibre Native engine (Vulkan) replaces the old tile screen entirely.
- Live 10-frame composite radar loop (~50 minutes of history). MRMS is the
  primary source, with LibreWXR tiles and IEM NEXRAD tiles as fallbacks; the
  loop stays source-locked until RETRY.
- Satellite layers: NASA GIBS GOES-East ABI — Visible GeoColor and Clean
  Infrared, one WMS image per visit with a WMTS fallback.
- Future radar: IN 24H and IN 48H ranges show HRRR simulated reflectivity,
  labelled FORECAST and never mixed with the live loop. Future 72h is not
  offered — HRRR caps at 48h.
- Play/pause, frame scrub with timestamps, 150 ms crossfades, and a
  source + data-cost panel naming the loop's locked source with the actual
  bytes fetched.

## 90s CRT filter

- The radar map gets a period-CRT treatment: scanlines, aperture grille,
  warm phosphor tint and vignette — no glow, no curvature, and it never
  touches the radar pixels themselves.
- On by default; Settings has a CRT FILTER toggle to turn it off.

## Fixes

- Radar tab bottom chrome no longer floats mid-screen; it sits at the
  bottom of the screen where it belongs.
- Pinch-out no longer triggers an unwanted auto-zoom on the radar map.

## Notes

- This is an arm64-only APK (`arm64-v8a`); there is no universal build.
- The signed release APK will not install over a debug build (different
  signing key). Uninstall the debug build first — that wipes app data,
  including places and settings, which need re-adding afterwards.
- The in-app updater (Settings → APP UPDATES → CHECK FOR UPDATES) now
  finds this release; it installs only a release whose SHA-256 digest
  matches the one GitHub reports and whose signing certificate matches
  the installed app.
