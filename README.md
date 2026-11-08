# Aura Weather (Northern Kentucky & Greater Cincinnati)

Aura Weather is a native Android weather application built with Kotlin, Jetpack Compose, Material 3, and Room. It delivers hyper-local forecasts, live NWS observations and active alerts, composite radar loops, 60-minute nowcasting, EPA Air Quality telemetry, and solar/lunar dynamics.

---

## 🌟 Key Features

1. **Zero-Key, Offline-First Architecture**
   - Operates with zero API keys out-of-the-box.
   - Room local database acts as the single source of truth (`stale-while-revalidate`).
   - Cached weather renders instantly on startup, even in offline or airplane mode.

2. **NWS Anchor Observations & Fallback Chain**
   - Primary observation station: **KCVG** (Cincinnati / Northern Kentucky International Airport).
   - Secondary observation station: **KLUK** (Cincinnati Municipal Airport Lunken Field).
   - National Weather Service Weather Forecast Office: **ILN** (Wilmington, OH).
   - Real-time Active Alerts with severity tagging (Extreme, Severe, Moderate, Advisory) and official emergency instructions.

3. **60-Minute Minute-Cast**
   - 60-minute precipitation intensity curve and plain-language summary sentence (*e.g., "Light rain starting in 15 min..."*).
   - Keyless default powered by Open-Meteo 15-minute resolution interpolation.

4. **Interactive NEXRAD Radar Viewer**
   - 10-frame composite radar loop (~50 minutes of radar history).
   - Dual feeds: Iowa Environmental Mesonet (IEM) NEXRAD composite & NASA GIBS GOES-East ABI Satellite (Visible & Clean IR).
   - Play/pause animation, frame scrub slider with timestamps, KILN radar site marker, and Picture-in-Picture (PiP) support.

5. **EPA Air Quality & Pollen Telemetry**
   - US AQI dial with EPA standard color coding (Good, Moderate, USG, Unhealthy, Very Unhealthy, Hazardous).
   - Individual pollutant breakdowns: PM2.5, PM10, and Ozone (O₃).
   - Tree, Grass, and Weed pollen gauges (with support for optional Tomorrow.io key).

6. **Astronomical Solar & Lunar Dynamics**
   - Pure Kotlin NOAA solar position algorithm: Sunrise, Solar Noon, Sunset, Civil Dawn/Dusk, Golden Hour (morning & evening), Blue Hour, day length changes.
   - Canvas-rendered lunar phase disk with illumination percentage and days until next New/Full moon.

7. **Adaptive Layout & Theming**
   - Adaptive navigation: Bottom navigation bar on phones; side NavigationRail on foldables, tablets, and wide windows.
   - Condition-aware M3 Expressive theme (Storm Navy, Clear Gold, Snow Frost, Overcast Slate) and system Monet dynamic color support.

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

---

## 🔐 Optional API Keys (Upgrades)

Core weather runs without keys. You can optionally supply keys in **Settings**:
- **Tomorrow.io**: Unlocks 1-minute resolution minutely precipitation and live Tree/Grass/Weed pollen counts.
- **AirNow**: Alternate EPA ground station air quality measurements.

---

## 📋 Assumptions & Known Limitations

1. **Alert Polling vs Device Push**: The National Weather Service does not provide a mobile push gateway; alerts are polled through the WorkManager background worker and refreshed on app open with honest freshness labels.
2. **Nowcast Resolution**: Keyless minute-cast uses smooth interpolation from Open-Meteo's 15-minute precipitation steps, accurately labeled as "15-minute resolution" in the UI.
3. **Emergency Disclaimer**: *Not an official NWS product. In a severe weather emergency or tornado warning, always prioritize official NOAA Weather Radio and local emergency broadcast sirens.*
