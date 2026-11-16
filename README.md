# Aura Weather

Aura Weather is a native Android weather application built with Kotlin, Jetpack Compose, Material 3, and Room. It works anywhere: pick a place (or use your current location) and it shows that place's forecasts, live NWS observations and active alerts where NWS coverage exists, composite radar loops, 60-minute nowcasting, air quality, and solar/lunar dynamics. No place is pre-chosen and no region is baked in.

The look is the classic late-90s / early-2000s Weather Channel local forecast — solid navy panels, a red/orange header bar with a yellow title and an "Aura Weather" logo box, yellow temperatures and white text in the Star4000 faces, a bottom ticker, and a regional map forecast. There is no theme picker and no light variant: the retro look is the only UI.

## Status

0.1.2 released — tag `v0.1.2` (`versionName "0.1.2"` / `versionCode 7` in
`app/build.gradle.kts`), arm64-only APK. The hero no longer invents a
day's high from the current temperature when the real high is missing —
it shows "H: --" instead — and the 14-day outlook starts at today, so
TODAY is actually today. The in-app updater (see below) finds that
release. Release builds are cut from a `v*` tag by
`.github/workflows/release.yml`, which signs the arm64 APK with the
release keystore and attaches it to the GitHub release.

---

## 🌟 Key Features

1. **Zero-Key Core, Offline-First**
   - The core runs with zero API keys out-of-the-box. The one exception is optional and user-supplied: a Pirate Weather key, entered in Settings, which enables true 1-minute precipitation (see §4 and §API Keys).
   - Room local database acts as the single source of truth (`stale-while-revalidate`).
   - Cached weather renders instantly on startup, even in offline or airplane mode. Cached data is real JSON, round-tripped exactly — alerts included — and labelled with its age when stale.

2. **Retro WeatherStar 4000-style UI**
   - Solid navy panels, a red/orange header (logo box, tab title, live clock in the place's timezone), and a solid navy bottom bar with the same five tabs (Forecast, Radar, Alerts, Air Quality, Settings) and a yellow active indicator.
   - Star4000 Large / Small type throughout — the WeatherStar 4000 faces, originally created by Nick Smith (twcclassics.com), redistributed in the MIT-licensed ws4kp / FUN-WeatherStar-4000 fan projects. Large is the display face (headings, big temperatures); Small is the label/body face. Text is rendered in the retro all-caps style.
   - A bottom ticker above the nav bar rotates visibility, pressure, wind, and dew point (6 seconds per item; missing facts are skipped). Humidity is not in the ticker — it already lives in the detail grid. There is no ceiling field in the data model, so the ticker does not show one.
   - Background music: four WeatherStar-inspired smooth-jazz tracks from the ws4kp project (netbymatt/ws4kp), AI-generated and unencumbered by copyright, played in random order at a fixed low volume (15%) only while the app is open — no foreground service, no notification. A small music-note toggle on the Home header turns it on/off (off by default, remembered).

3. **Per-Place NWS Data**
   - For each place, NWS `/points` resolves the nearest observation station, the grid cell, the radar station, the forecast zone, and the place's timezone. There is no default station: a place outside NWS coverage uses Open-Meteo only, never another city's observations.
   - Real-time Active Alerts with severity tagging (Extreme, Severe, Moderate, Advisory) and official emergency instructions. Expired alerts are dropped, never shown as active. The most severe alert is expanded first, and the Home banner shows the event and its end time.
   - The UV Index comes from Open-Meteo's `uv_index_max`, merged into the NWS daily forecast by date (NWS daily carries no UV index).

4. **60-Minute Minute-Cast**
   - 60-minute precipitation intensity curve and plain-language summary sentence (*e.g., "Light rain starting in 15 min..."*).
   - With a Pirate Weather key entered in Settings, the minute-cast is true 1-minute precipitation. Without a key it falls back to Open-Meteo 15-minute resolution interpolation, anchored on the current time (not midnight) and converted to mm/h.

5. **Interactive Radar Viewer** (rebuilt on MapLibre Native 13.7.1, Vulkan single-backend — the old tile screen is gone)
   - Live: 10-frame composite radar loop (~50 minutes of radar history). MRMS is the primary source — wide-margin (1.5×) single images, decoded on demand and held in memory across the 5-minute refresh; LibreWXR 512-px tiles are the next fallback and IEM NEXRAD tiles the last. The loop is source-locked: a fallback stays fallen-back until RETRY.
   - Ranges: LIVE (observed NEXRAD), IN 24H and IN 48H (HRRR simulated reflectivity in its live `-0` run form — a forecast, labelled FORECAST, never mixed with the live loop; about 74 frames for 24h and about 97 for 48h, with a probe-driven tail cap and a "FORECAST ENDS ABOUT … UTC" note when the tail is cut). Future 72h is not offered: HRRR caps at 48h.
   - Layers: Composite Radar, and NASA GIBS GOES-East ABI Satellite — Visible GeoColor and Clean Infrared. There is no Local Site layer: the place drives the map centre instead. Satellite is one WMS JPEG per visit (viewport × 1.5, scan time probed live, blank-checked before display), with GIBS WMTS tiles as the fallback.
   - Play/pause animation, frame scrub slider with timestamps, and a source + data-cost panel naming the loop's locked source (MRMS / LIBREWXR / IEM / HRRR) with the actual bytes fetched — never a fixed estimate. Frames crossfade over 150 ms. The dBZ legend and the transport pill are radar-only: satellite is a single image, so they are hidden on the satellite layers.
   - The map is clipped to the content area, so it cannot paint over the header. There is no Picture-in-Picture support.
   - 90s CRT filter: the radar map carries a period-CRT treatment — scanlines, aperture grille, warm phosphor tint and vignette (no glow, no curvature, and it never touches the radar pixels). On by default; Settings has a CRT FILTER toggle to turn it off.

6. **Home: 48-Hour Strip, 14-Day Outlook, Regional Map**
   - A 48-hour hourly strip (five cells across, scrollable by swiping) below the hero temperature.
   - A 14-day daily outlook with rain/snow amounts and precipitation probabilities.
   - A static Esri map (zoom 8) below the outlook, centred on the current place, with the place and its 8 neighbours at ~80 km plotted as dots with city names and temperatures. Neighbour names come from NWS `/points` relative locations, with a compass label (N, NE, E…) as the fallback — never invented. One Open-Meteo multi-location call fetches the neighbours' conditions. The map shows the Esri World Dark Gray Base only; its place-name layer is left off so the map's own labels cannot collide with the overlay labels.

7. **Air Quality**
   - US AQI dial with EPA standard color coding (Good, Moderate, USG, Unhealthy, Very Unhealthy, Hazardous).
   - Individual pollutant breakdowns: PM2.5, PM10, and Ozone (O₃), rounded to whole numbers. Missing readings show as unavailable, never as invented values.
   - AQI comes from Open-Meteo Air Quality — modelled CAMS data, not the official EPA station number.
   - The allergy index runs on air quality alone, and is labelled NOT A MEDICAL FORECAST. Pollen counts are unavailable: there is no free US pollen source (the Open-Meteo pollen model is Europe-only), so no pollen panel is shown.

8. **Astronomical Solar & Lunar Dynamics**
   - Pure Kotlin NOAA solar position algorithm: Sunrise, Solar Noon, Sunset, Civil Dawn/Dusk, Golden Hour (morning & evening), Blue Hour, day length changes — all rendered in the selected place's timezone.
   - Canvas-rendered lunar phase disk with illumination percentage, moonrise/moonset times, and days until next New/Full moon.

9. **Alert Notifications (opt-in)**
   - A background worker checks the selected place every 15 minutes for new or escalated NWS alerts. Tapping a notification deep-links to the Alerts screen, with the notified alert expanded. Extreme/Severe alerts use high importance with sound and vibration.
   - Rain-starting alerts are a separate opt-in: one notification per rain event (rain above 0.1 mm/h), cleared when no rain is found for 60 minutes.
   - Both are off by default. Turning notifications off cancels the worker.

10. **Home-Screen Widgets**
    - A Glance main widget and an hourly-strip widget. Both are cache-only (they show the last fetched data, never fetch themselves) and use the system font — Star4000 cannot be used in widgets. The strip widget shows 4, 5, or 6 hours depending on its width.

11. **In-App Updater (manual check only)**
    - Settings has an APP UPDATES panel with a CHECK FOR UPDATES button. There is no launch check and no background check.
    - When an update is found, the app downloads the release APK and installs it only if its SHA-256 digest matches the digest GitHub reported for the asset and its signing certificate matches the installed app. With no digest reported, the install is refused outright.
    - The updater finds the first GitHub release (0.1.0, tag `v0.1.0`) and any later stable release; with no releases published, it correctly reports "up to date".

---

## 📡 Data Sources & Attribution

- **National Weather Service (NOAA)**: `https://api.weather.gov/` (Public domain US Government data). Requests carry the User-Agent `AuraWeather/1.0 (https://github.com/DocBobert6/Aura-Weather)`.
- **Open-Meteo**: `https://api.open-meteo.com/` (Weather data by Open-Meteo.com under CC BY 4.0) — forecast, hourly/daily, and air quality.
- **Pirate Weather**: `https://api.pirateweather.net/` — true 1-minute precipitation for the minute-cast, when the user has entered their own key in Settings. Free tier, no card required.
- **Iowa Environmental Mesonet (IEM)**: Iowa State University / NOAA NEXRAD Level III composite tile cache — the last-resort live-radar fallback, and the tile host for HRRR simulated reflectivity in the IN 24H / IN 48H forecast ranges.
- **Esri World Dark Gray Base**: dark basemap under the radar and the regional map (keyless, `server.arcgisonline.com`). CARTO dark_all now returns an "API KEY REQUIRED" watermark tile and cannot be used.
- **NASA GIBS**: Global Imagery Browse Services (EOSDIS) GOES-East ABI Satellite layers.
- **Display font**: Star4000 Large / Small, originally created by Nick Smith (twcclassics.com), redistributed in the MIT-licensed ws4kp / FUN-WeatherStar-4000 fan projects.
- **Background music**: WeatherStar-inspired smooth-jazz tracks from the ws4kp project (netbymatt/ws4kp), AI-generated and unencumbered by copyright.

Removed sources: Tomorrow.io and AirNow were removed in the 2026-11-10 audit build — their free plans cannot deliver 1-minute precipitation, pollen, or a station AQI. Rainbow was also removed (its free plan requires a card).

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
Output path: `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`

### Build Release APK
```bash
gradle assembleRelease
```
Output path: `app/build/outputs/apk/release/app-arm64-v8a-release.apk` (arm64-only; there is no universal APK)

### Install over ADB
```bash
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

The CI debug APK is the installable one for branch builds: the branch CI release APK is built unsigned (branch CI has no release keystore) as the R8 check. A `v*` tag push runs `.github/workflows/release.yml` instead, which signs the arm64 release APK with the release keystore, verifies it, and attaches it to the GitHub release. A pure-Kotlin subset of the logic (mappers, solar/lunar calculators, unit formatting, music picker) can also be verified without Gradle via `../run-pure-tests.sh` (kotlinc + JUnit 4; needs `java` on `PATH`).

---

## 🔐 API Keys

No keys are needed for the core: forecast, alerts, radar, satellite, and AQI all run keyless, and no keys are shipped in the APK. One key is accepted, supplied by the user in Settings:

- **Pirate Weather** — paste your own key in Settings (Pirate Weather API Key section). It enables true 1-minute precipitation for the minute-cast. The Settings pill shows KEY ACTIVE only after the API has returned actual usable data; KEY FAILED means it was attempted and did not deliver.

---

## 📋 Assumptions & Known Limitations

1. **First release is 0.1.0**: tag `v0.1.0`, arm64-only APK, signed with the release keystore. The app is verified in CI (unit tests + debug and release builds) and by device screenshot/video audits, but on-device confirmation of each fix is the owner's call. The signed release APK will not install over a debug build (different signing key) — uninstall first wipes app data, including places and settings, which need re-adding afterwards.
2. **Radar on a cold start**: the live loop fetches one wide-margin MRMS image per frame (a tile proving set on a tiled fallback), so the loop can take a while to fill in the first time a range is opened. Repeat visits within a session replay from memory — MRMS bytes are held in memory and tiled frames replay from the map's in-memory tiles; the radar fetcher keeps no disk cache.
3. **No invented data**: when a provider returns nothing for a value, the app shows it as unavailable instead of substituting a plausible number. When a load fails with no cache, it shows an error instead of a fabricated forecast.
4. **Emergency Disclaimer**: *Not an official NWS product. In a severe weather emergency or tornado warning, always prioritize official NOAA Weather Radio and local emergency broadcast sirens.*
5. **AQI is modelled, not official**: the AQI number is Open-Meteo's modelled CAMS value, not the EPA station reading for the area.
6. **Pollen is unavailable**: there is no free US pollen source, so the app shows no pollen data and the allergy index runs on air quality alone.
