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

    // Rainbow Weather Nowcast (free API key, 5,000 requests/month):
    // minute-by-minute precipitation for up to 4 hours ahead at
    // 1-minute resolution. The minute-cast's first-choice source when
    // a key is set — Tomorrow.io's 1m timestep is Enterprise, so a
    // free Tomorrow.io key may not provide it. Auth is the
    // Ocp-Apim-Subscription-Key header. Endpoint verified live (401
    // without a key); the response shape is from the Nowcast OpenAPI
    // spec and is NOT live-verified yet (no key exists to fetch with).
    const val RAINBOW_BASE_URL = "https://api.rainbow.ai/"

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
