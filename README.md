# Aura Weather

Aura Weather is a native Android weather application built with Kotlin, Jetpack Compose, Material 3, and Room. It works anywhere: pick a place (or use your current location) and it shows that place's forecasts, live NWS observations and active alerts where NWS coverage exists, composite radar loops, 60-minute nowcasting, air quality, and solar/lunar dynamics. No place is pre-chosen and no region is baked in.

---

## 🌟 Key Features

1. **Zero-Key, Offline-First Architecture**
   - Operates with zero API keys out-of-the-box.
   - Room local database acts as the single source of truth (`stale-while-revalidate`).
   - Cached weather renders instantly on startup, even in offline or airplane mode. Cached data is real JSON, round-tripped exactly — alerts included — and labelled with its age when stale.

2. **Per-Place NWS Data**
   - For each place, NWS `/points` resolves the nearest observation station, the grid cell, the radar station, the forecast zone, and the place's timezone. There is no default station: a place outside NWS coverage uses Open-Meteo only, never another city's observations.
   - Real-time Active Alerts with severity tagging (Extreme, Severe, Moderate, Advisory) and official emergency instructions. Expired alerts are dropped, never shown as active.

3. **60-Minute Minute-Cast**
   - 60-minute precipitation intensity curve and plain-language summary sentence (*e.g., "Light rain starting in 15 min..."*).
   - Keyless default powered by Open-Meteo 15-minute resolution interpolation, anchored on the current time (not midnight) and converted to mm/h.

4. **Interactive NEXRAD Radar Viewer**
   - 10-frame composite radar loop (~50 minutes of radar history).
   - Dual feeds: Iowa Environmental Mesonet (IEM) NEXRAD composite & NASA GIBS GOES-East ABI Satellite (Visible & Clean IR, dated today).
   - Play/pause animation, frame scrub slider with timestamps, local radar site label, and Picture-in-Picture (PiP) support.

5. **Air Quality & Pollen**
   - US AQI dial with EPA standard color coding (Good, Moderate, USG, Unhealthy, Very Unhealthy, Hazardous).
   - Individual pollutant breakdowns: PM2.5, PM10, and Ozone (O₃). Missing readings show as unavailable, never as invented values.
   - Pollen gauges are present but not yet connected to a live provider: they show no values until a real pollen source is wired up. A Tomorrow.io key stored in Settings does not enable them yet.

6. **Astronomical Solar & Lunar Dynamics**
   - Pure Kotlin NOAA solar position algorithm: Sunrise, Solar Noon, Sunset, Civil Dawn/Dusk, Golden Hour (morning & evening), Blue Hour, day length changes — all rendered in the selected place's timezone.
   - Canvas-rendered lunar phase disk with illumination percentage and days until next New/Full moon.

7. **Adaptive Layout & Theming (Phase 2 redesign, 2026-11-07)**
   - Floating navigation: a pill nav bar on phones and a floating vertical pill rail on wide layouts, over full-bleed content; it hides on scroll with a spring and never hides while an alert banner is visible or on the Alerts screen.
   - Floating top bar on Home (place pill + freshness pill), floating Radar pill cluster + transport pill, and pill segmented controls for layers and tabs.
   - Two palettes: Material You (the default — colours from your phone's wallpaper; light and dark follow the Theme Mode) and Expressive (the condition-aware palettes, 14 day/night key sets, with bouncier springs). The theme mode is Auto / Dark / Light, and the light schemes are real now, not a system-only fallback. The automatic "Auto Weather" palette was removed 2026-11-08.
   - Mesh sky (3×3 colour points from the condition palette, drift + scroll parallax) and real backdrop blur (the sky is recorded to a GraphicsLayer and the glass samples a blurred copy; off on low-RAM/battery saver; Radar opts out, its backdrop is the map). There is no Compose mesh-gradient API in the bumped BOM, so the mesh is a Canvas approximation and the platform APIs are a later swap.
   - Motion: the "expressive" palette mode now selects the bouncier spring set at the animated call sites (material3 1.4.0 keeps MotionScheme internal, so motion is applied per call site, not via the theme). Springs snap when the system's Reduce motion setting is on.

---

## 📡 Data Sources & Attribution

- **National Weather Service (NOAA)**: `https://api.weather.gov/` (Public domain US Government data).
- **Open-Meteo**: `https://api.open-meteo.com/` (Weather data by Open-Meteo.com under CC BY 4.0).
- **Iowa Environmental Mesonet (IEM)**: Iowa State University / NOAA NEXRAD Level III composite tile cache.
- **Esri World Dark Gray Base**: dark basemap under the radar (keyless, `server.arcgisonline.com`). CARTO dark_all now returns an "API KEY REQUIRED" watermark tile and cannot be used.
- **NASA GIBS**: Global Imagery Browse Services (EOSDIS) GOES-East ABI Satellite layers.

---

## 🛠️ Build, Test & Install Commands

### Run Unit Tests
```bash
./gradlew :app:testDebugUnitTest
```
Note: the repo currently ships no `gradlew` wrapper script; use a local Gradle 9.3.1 install until one is added, or run the same task in CI (see `.github/workflows/ci.yml`).

### Build Debug APK
```bash
./gradlew assembleDebug
```
Output path: `app/build/outputs/apk/debug/app-debug.apk`

### Build Release APK
```bash
./gradlew assembleRelease
```
Output path: `app/build/outputs/apk/release/app-release.apk`

### Install over ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔐 Optional API Keys

Core weather runs without keys. You can optionally supply keys in **Settings**:
- **Tomorrow.io**: stored for a future update. It does not unlock anything today — 1-minute precipitation and live pollen are not connected yet.
- **AirNow**: stored in Settings, but not currently used by the app (no AirNow client exists yet).

---

## 📋 Assumptions & Known Limitations

1. **Alert notifications**: there is no background alert polling yet. No WorkManager dependency or worker exists, and the notification permission is declared but unused. Alerts refresh on app open with honest freshness labels. Periodic alert notifications are a planned upgrade (Phase 3).
2. **Nowcast Resolution**: Keyless minute-cast uses smooth interpolation from Open-Meteo's 15-minute precipitation steps.
3. **No invented data**: when a provider returns nothing for a value, the app shows it as unavailable instead of substituting a plausible number. When a load fails with no cache, it shows an error instead of a fabricated forecast.
4. **Emergency Disclaimer**: *Not an official NWS product. In a severe weather emergency or tornado warning, always prioritize official NOAA Weather Radio and local emergency broadcast sirens.*
5. **Phase 2 device checks still owed**: the 60-second scroll test, the contrast check over the brightest and darkest sky states, the blur-seam check, and the battery/frame comparison against the Phase 1 build all need a real device — they are the next UI audit's job, not yet verified. Known gaps in the shipped redesign: the nav bar ↔ rail size does not interpolate on configuration change (the layout switch itself works), the condition-tinted shadow is only on the nav bar/rail and top-bar pills, and there is no mesh bitmap caching for low-end devices.
