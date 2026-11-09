# Aura Weather

Aura Weather is a native Android weather application built with Kotlin, Jetpack Compose, Material 3, and Room. It works anywhere: pick a place (or use your current location) and it shows that place's forecasts, live NWS observations and active alerts where NWS coverage exists, composite radar loops, 60-minute nowcasting, air quality, and solar/lunar dynamics. No place is pre-chosen and no region is baked in.

The look is the classic late-90s / early-2000s Weather Channel local forecast — solid navy panels, a red/orange header bar with a yellow title and an "Aura Weather" logo box, yellow temperatures and white text in the Star4000 faces, a bottom ticker, and a regional map forecast. There is no theme picker and no light variant: the retro look is the only UI.

---

## 🌟 Key Features

1. **Zero-Key, Offline-First Architecture**
   - Operates with zero API keys out-of-the-box.
   - Room local database acts as the single source of truth (`stale-while-revalidate`).
   - Cached weather renders instantly on startup, even in offline or airplane mode. Cached data is real JSON, round-tripped exactly — alerts included — and labelled with its age when stale.

2. **Retro WeatherStar 4000-style UI**
   - Solid navy panels, a red/orange header (logo box, tab title, live clock in the place's timezone), and a solid navy bottom bar with the same five tabs (Forecast, Radar, Alerts, Air Quality, Settings) and a yellow active indicator.
   - Star4000 Large / Small type throughout — the WeatherStar 4000 faces, originally created by Nick Smith (twcclassics.com), redistributed in the MIT-licensed ws4kp / FUN-WeatherStar-4000 fan projects. Large is the display face (headings, big temperatures); Small is the label/body face. All text is uppercase.
   - A bottom ticker above the nav bar rotates visibility, pressure, wind, humidity, and dew point (6 seconds per item; missing facts are skipped). There is no ceiling field in the data model, so the ticker does not show one.
   - Background music: four WeatherStar-inspired smooth-jazz tracks from the ws4kp project (netbymatt/ws4kp), AI-generated and unencumbered by copyright, played in random order at a fixed low volume (15%) only while the app is open — no foreground service, no notification. A small music-note toggle on the Home header turns it on/off (off by default, remembered).

3. **Per-Place NWS Data**
   - For each place, NWS `/points` resolves the nearest observation station, the grid cell, the radar station, the forecast zone, and the place's timezone. There is no default station: a place outside NWS coverage uses Open-Meteo only, never another city's observations.
   - Real-time Active Alerts with severity tagging (Extreme, Severe, Moderate, Advisory) and official emergency instructions. Expired alerts are dropped, never shown as active. The most severe alert is expanded first, and the Home banner shows the event and its end time.
   - The UV Index comes from Open-Meteo's `uv_index_max`, merged into the NWS daily forecast by date (NWS daily carries no UV index).

4. **60-Minute Minute-Cast**
   - 60-minute precipitation intensity curve and plain-language summary sentence (*e.g., "Light rain starting in 15 min..."*).
   - Keyless default powered by Open-Meteo 15-minute resolution interpolation, anchored on the current time (not midnight) and converted to mm/h.

5. **Interactive NEXRAD Radar Viewer**
   - 10-frame composite radar loop (~50 minutes of radar history).
   - Three feeds: Iowa Environmental Mesonet (IEM) NEXRAD composite, IEM local-site radar, and NASA GIBS GOES-East ABI Satellite (Visible GeoColor and Clean Infrared, at the latest 10-minute GOES scan — not a date-only time, so the visible image is a daytime one at noon).
   - Play/pause animation, frame scrub slider with timestamps, local radar site label, and Picture-in-Picture (PiP) support. The dBZ legend and the transport pill are radar-only: satellite is a single image, so they are hidden on the satellite layers.
   - Tiles are clipped to the content area, so they cannot paint over the header.

6. **Regional Forecast Map (Home)**
   - A static Esri map (zoom 8) below the 7-day forecast, centred on the current place, with the place and its 8 neighbours at 80 km plotted as dots with city names and temperatures. Neighbour names come from NWS `/points` relative locations, with a compass label (N, NE, E…) as the fallback — never invented. One Open-Meteo multi-location call fetches the neighbours' conditions. The map shows the Esri World Dark Gray Base only; its place-name layer is left off so the map's own labels cannot collide with the overlay labels.

7. **Air Quality & Pollen**
   - US AQI dial with EPA standard color coding (Good, Moderate, USG, Unhealthy, Very Unhealthy, Hazardous).
   - Individual pollutant breakdowns: PM2.5, PM10, and Ozone (O₃), rounded to whole numbers. Missing readings show as unavailable, never as invented values.
   - Pollen gauges are present but not yet connected to a live provider: they show no values until a real pollen source is wired up. A Tomorrow.io key stored in Settings does not enable them yet.

8. **Astronomical Solar & Lunar Dynamics**
   - Pure Kotlin NOAA solar position algorithm: Sunrise, Solar Noon, Sunset, Civil Dawn/Dusk, Golden Hour (morning & evening), Blue Hour, day length changes — all rendered in the selected place's timezone.
   - Canvas-rendered lunar phase disk with illumination percentage, moonrise/moonset times, and days until next New/Full moon.

---

## 📡 Data Sources & Attribution

- **National Weather Service (NOAA)**: `https://api.weather.gov/` (Public domain US Government data).
- **Open-Meteo**: `https://api.open-meteo.com/` (Weather data by Open-Meteo.com under CC BY 4.0).
- **Iowa Environmental Mesonet (IEM)**: Iowa State University / NOAA NEXRAD Level III composite tile cache.
- **Esri World Dark Gray Base**: dark basemap under the radar and the regional map (keyless, `server.arcgisonline.com`). CARTO dark_all now returns an "API KEY REQUIRED" watermark tile and cannot be used.
- **Esri Reference / World Boundaries and Places**: city labels and boundaries on the radar map only (keyless). It is deliberately not drawn on the regional map, where its place names would collide with the overlay labels.
- **NASA GIBS**: Global Imagery Browse Services (EOSDIS) GOES-East ABI Satellite layers.
- **Display font**: Star4000 Large / Small, originally created by Nick Smith (twcclassics.com), redistributed in the MIT-licensed ws4kp / FUN-WeatherStar-4000 fan projects.
- **Background music**: WeatherStar-inspired smooth-jazz tracks from the ws4kp project (netbymatt/ws4kp), AI-generated and unencumbered by copyright.

---

## 🛠️ Build, Test & Install Commands

The repo ships no `gradlew` wrapper script; use a local Gradle 9.3.1 install, or run the same tasks in CI (see `.github/workflows/ci.yml`, which installs Gradle 9.3.1 and JDK 21). Local Gradle builds are also blocked in some sandboxed environments — CI is the reliable verification path.

### Run Unit Tests
```bash
gradle :app:testDebugUnitTest
```

### Build Debug APK
```bash
gradle assembleDebug
```
Output path: `app/build/outputs/apk/debug/app-debug.apk`

### Build Release APK
```bash
gradle assembleRelease
```
Output path: `app/build/outputs/apk/release/app-release.apk`

### Install over ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

A pure-Kotlin subset of the logic (mappers, solar/lunar calculators, unit formatting, music picker) can also be verified without Gradle via `../run-pure-tests.sh` (kotlinc + JUnit 4; needs `java` on `PATH`).

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
5. **Device checks still owed**: the retro look is verified in CI (unit tests + debug build) and by a device screenshot audit (14 shots, 2026-11-09) whose 12 findings plus 2 polish items are all fixed on `fix/correctness-phase-1` (batches A–F, all CI-green). The fixes are only truly confirmed when seen on a real device — the next screenshot round is the owner's call. The PR that carries this work (`fix/correctness-phase-1` → `main`) is open and unmerged; merging is the owner's call.
6. **Phase 3 is not started**: alert notifications, a home widget, radar polish, a real contact in the NWS User-Agent, and dependency cleanup are planned but not authorized or built.
