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

7. **Adaptive Layout & Theming**
   - Adaptive navigation: Bottom navigation bar on phones; side NavigationRail on foldables, tablets, and wide windows.
   - Condition-aware Material 3 theme (Storm Navy, Clear Gold, Snow Frost, Overcast Slate) and system Monet dynamic color support. (The "expressive" palette mode in Settings currently has no theme branch and does nothing; a full M3 Expressive upgrade is planned — see Phase 2 of the plan.)

---

## 📡 Data Sources & Attribution

- **National Weather Service (NOAA)**: `https://api.weather.gov/` (Public domain US Government data).
- **Open-Meteo**: `https://api.open-meteo.com/` (Weather data by Open-Meteo.com under CC BY 4.0).
- **Iowa Environmental Mesonet (IEM)**: Iowa State University / NOAA NEXRAD Level III composite tile cache.
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
