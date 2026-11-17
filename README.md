# Aura Weather

Aura Weather is a native Android weather application built with Kotlin, Jetpack Compose, Material 3, and Room. It works anywhere: pick a place (or use your current location) and it shows that place's forecasts, live NWS observations and active alerts where NWS coverage exists, composite radar loops, 60-minute nowcasting, air quality, and solar/lunar dynamics. No place is pre-chosen and no region is baked in.

The look is the classic late-90s / early-2000s Weather Channel local forecast — solid navy panels, a navy header bar with a yellow title and a sun-mark logo box, yellow temperatures and white text in the Star4000 faces, a bottom ticker, and a regional map forecast. There is no theme picker and no light variant: the retro look is the only UI.

## Status

0.3.3 — tag `v0.3.3` (`versionName "0.3.3"` / `versionCode 12` in
`app/build.gradle.kts`), arm64-only APK. A UI and font audit: the
header is solid navy now (the orange-red gradient is gone) with our
sun mark in the logo box; the hero temperature is centred under the
moon; pollutant units read "UG/M3" (the µ and ³ characters draw as
nothing in the retro type); the 14-day rows no longer clip long
words; the UV card says NIGHT after dark and TODAY'S MAX in
daylight; the wind arrow points downwind; the air-quality gauge is
an era segmented bar with a CURRENT HOUR label on the pollutant
numbers; and the all-clear alerts card is tighter. It follows 0.3.2,
which did the widget audit, done properly: the widgets now actually
show the Star4000 type (drawn as bitmaps, since the home screen
would not use the bundled font); the hourly strip is the right height
and its first cell agrees with the app about right now; the radar
widget falls back to the most recent frame that works, labelled
honestly, and retries two minutes after a failed first picture;
Settings gains a "Background updates" row that opens the system's
battery-exemption prompt on your tap only; and the weather icons are
fixed (snowflake for snow, cloud-with-lines for fog, moon-with-cloud
for a partly-cloudy night), with the 14-day row's words and icon
always agreeing. It follows 0.3.1, which made today's high honest: the hero no longer invents a day's high from the
current temperature when the real high is missing — it shows "H: --"
instead — and the
14-day outlook starts at today, so TODAY is actually today. It follows
0.3.0, which added IN 6H future radar, the lighter CRT vignette,
RemoteViews widgets with Star4000 and a square 2×2 main widget, and a
4×2 radar widget (15-min refresh, for that widget only). 0.2.0 added
place search and
remove-a-location: Settings → SAVED PLACES → ADD now searches a city,
"City, ST", or a zip code (Open-Meteo Geocoding + Zippopotam.us) and
shows a picker list to tap; entering coordinates stays as a secondary
path. Each saved place has a remove button; Current Location cannot be
removed (it is the device's GPS place). 0.1.1 fixed the 0.1.0
first-launch crash: R8 had renamed the weather-condition names Moshi
reads when saving the forecast cache, so the first cache save crashed
the app. The in-app updater (see below) finds that release.
Release builds are cut from a `v*` tag by
`.github/workflows/release.yml`, which signs the arm64 APK with the
release keystore and attaches it to the GitHub release.

---

## 🌟 Key Features

1. **Zero-Key Core, Offline-First**
   - The core runs with zero API keys out-of-the-box. The one exception is optional and user-supplied: a Pirate Weather key, entered in Settings, which enables true 1-minute precipitation (see §4 and §API Keys).
   - Room local database acts as the single source of truth (`stale-while-revalidate`).
   - Cached weather renders instantly on startup, even in offline or airplane mode. Cached data is real JSON, round-tripped exactly — alerts included — and labelled with its age when stale.

2. **Retro WeatherStar 4000-style UI**
   - Solid navy panels, a navy header (sun-mark logo box, tab title, live clock in the place's timezone), and a solid navy bottom bar with the same five tabs (Forecast, Radar, Alerts, Air Quality, Settings) and a yellow active indicator.
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
   - Ranges: LIVE (observed NEXRAD), IN 6H, IN 24H and IN 48H (HRRR simulated reflectivity in its live `-0` run form — a forecast, labelled FORECAST, never mixed with the live loop; about 24 frames for 6h, about 74 frames for 24h and about 97 for 48h, with a probe-driven tail cap and a "FORECAST ENDS ABOUT … UTC" note when the tail is cut). Future 72h is not offered: HRRR caps at 48h.
   - Layers: Composite Radar, and NASA GIBS GOES-East ABI Satellite — Visible GeoColor and Clean Infrared. There is no Local Site layer: the place drives the map centre instead. Satellite is one WMS JPEG per visit (viewport × 1.5, scan time probed live, blank-checked before display), with GIBS WMTS tiles as the fallback.
   - Play/pause animation, frame scrub slider with timestamps, and a source + data-cost panel naming the loop's locked source (MRMS / LIBREWXR / IEM / HRRR) with the actual bytes fetched — never a fixed estimate. Frames crossfade over 150 ms. The dBZ legend and the transport pill are radar-only: satellite is a single image, so they are hidden on the satellite layers.
   - The map is clipped to the content area, so it cannot paint over the header. There is no Picture-in-Picture support.
   - 90s CRT filter: the radar map carries a period-CRT treatment — scanlines, aperture grille, warm phosphor tint and a very slight vignette, clear until 55% of the way to the corner and then a gentle ramp to the edge (no glow, no curvature, and it never touches the radar pixels). On by default; Settings has a CRT FILTER toggle to turn it off.

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

10. **Home-Screen Widgets** (classic RemoteViews, not Glance — Glance cannot use custom fonts)
    - A main widget and an hourly-strip widget, both in the Star4000 faces like the rest of the app. The main widget is square (2×2) by default and resizes up to 4×2; the strip widget stays 4×1 and shows 4, 5, or 6 hours depending on its width. Both are cache-only (they show the last fetched data, never fetch themselves) and refresh hourly.
    - A 4×2 radar widget: a still radar picture for the selected place (newest MRMS frame over the dark basemap, CRT baked in), refreshed every 15 minutes — for this widget only — stepping through now, +15 and +30 minutes on successive refreshes, with a label saying which frame it is. Tap opens the radar tab. If a refresh fails it keeps the last picture, labelled stale.

11. **In-App Updater (manual check only)**
    - Settings has an APP UPDATES panel with a CHECK FOR UPDATES button. There is no launch check and no background check.
    - When an update is found, the app downloads the release APK and installs it only if its SHA-256 digest matches the digest GitHub reported for the asset and its signing certificate matches the installed app. With no digest reported, the install is refused outright.
    - The updater finds the first GitHub release (0.1.0, tag `v0.1.0`) and any later stable release; with no releases published, it correctly reports "up to date".

12. **Place Search & Remove a Location**
    - Settings → SAVED PLACES → ADD searches instead of asking for latitude/longitude: type a city, "City, ST" (e.g. Crittenden, KY), or a zip code. A 5-digit zip is looked up on Zippopotam.us first (Open-Meteo Geocoding is the fallback); anything else goes to Open-Meteo Geocoding only. Results always show as a list — name, region, country, population when the API gives one — and tapping one adds it; nothing is auto-picked. Typing is debounced (~300 ms) and nothing is searched under 2 characters.
    - Entering coordinates by hand stays available from the search dialog ("ENTER COORDINATES").
    - Every saved place has a remove button, immediate on tap. Removing the selected place selects another saved place, or clears to the empty state when none remains; the removed place's cached weather is deleted too. Current Location cannot be removed.

---

## 📡 Data Sources & Attribution

- **National Weather Service (NOAA)**: `https://api.weather.gov/` (Public domain US Government data). Requests carry the User-Agent `AuraWeather/1.0 (https://github.com/DocBobert6/Aura-Weather)`.
- **Open-Meteo**: `https://api.open-meteo.com/` (Weather data by Open-Meteo.com under CC BY 4.0) — forecast, hourly/daily, and air quality.
- **Open-Meteo Geocoding**: `https://geocoding-api.open-meteo.com/` (Place search by Open-Meteo.com under CC BY 4.0) — city names worldwide, and a US zip code as the query.
- **Zippopotam.us**: `https://api.zippopotam.us/` (US zip-code lookup for place search — first choice for zip queries).
- **Pirate Weather**: `https://api.pirateweather.net/` — true 1-minute precipitation for the minute-cast, when the user has entered their own key in Settings. Free tier, no card required.
- **Iowa Environmental Mesonet (IEM)**: Iowa State University / NOAA NEXRAD Level III composite tile cache — the last-resort live-radar fallback, and the tile host for HRRR simulated reflectivity in the IN 6H / IN 24H / IN 48H forecast ranges.
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
