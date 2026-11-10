package com.example.core

/**
 * Centralized constant endpoints for weather and radar providers.
 * All providers have keyless, zero-configuration defaults.
 */
object ProviderEndpoints {
    // National Weather Service (US Government, Keyless)
    const val NWS_BASE_URL = "https://api.weather.gov/"
    const val NWS_USER_AGENT = "AuraWeather/1.0 (https://github.com/DocBobert6/Aura-Weather)"
    const val NWS_ACCEPT_HEADER = "application/geo+json"

    // Open-Meteo Weather & Air Quality (Free, Keyless, CC BY 4.0)
    const val OPEN_METEO_BASE_URL = "https://api.open-meteo.com/"
    const val OPEN_METEO_AQ_BASE_URL = "https://air-quality-api.open-meteo.com/"

    // Tomorrow.io Timelines (free API key, 500 calls/day): 1-minute
    // precipitation for the minute-cast and the live pollen index.
    // Sub-hourly timesteps (1m/5m/15m/30m) are Enterprise in
    // Tomorrow.io's docs, so a free key may return no 1m timeline.
    const val TOMORROW_BASE_URL = "https://api.tomorrow.io/"

    // AirNow (US EPA, free API key): current AQI observations by lat/lon.
    // AQI + category only, no concentrations.
    const val AIRNOW_BASE_URL = "https://www.airnowapi.org/"

    // Pirate Weather (free API key, 10,000 calls/month, no card):
    // true 1-minute precipitation for the next 60 minutes, Dark Sky
    // format. The minute-cast's first-choice source when a key is set.
    // Auth is the `apikey` header (the key must NOT travel in the URL
    // path, where it would appear in logs); the path still needs a
    // dummy key-shaped segment. Units are requested as `si` so
    // precipIntensity is mm/h (the default `us` units report inches
    // per hour). Community project, best-effort, no SLA; a new key
    // can take ~20 minutes to go live. Endpoint verified live (401
    // without a key); the response shape is from the Pirate Weather
    // API docs and is NOT live-verified yet (no key exists to fetch
    // with).
    const val PIRATE_WEATHER_BASE_URL = "https://api.pirateweather.net/"

    // Iowa Environmental Mesonet (IEM) NEXRAD Radar Tiles (Keyless)
    const val IEM_TILE_BASE = "https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/"
    const val IEM_COMPOSITE_LAYER = "nexrad-n0q-900913"

    // NASA GIBS Satellite WMTS Tiles (Keyless)
    const val NASA_GIBS_BASE = "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/"

    // No default observation or radar station: the nearest station and the
    // radar site are resolved per place from NWS /points (see WeatherRepository).
    // There is deliberately no fallback station — a place outside NWS coverage
    // uses Open-Meteo only, never another city's observations.
}
