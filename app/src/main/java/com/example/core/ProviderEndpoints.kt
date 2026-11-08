package com.example.core

/**
 * Centralized constant endpoints for weather and radar providers.
 * All providers have keyless, zero-configuration defaults.
 */
object ProviderEndpoints {
    // National Weather Service (US Government, Keyless)
    const val NWS_BASE_URL = "https://api.weather.gov/"
    const val NWS_USER_AGENT = "AuraWeather/1.0 (contact@auraweather.local)"
    const val NWS_ACCEPT_HEADER = "application/geo+json"

    // Open-Meteo Weather & Air Quality (Free, Keyless, CC BY 4.0)
    const val OPEN_METEO_BASE_URL = "https://api.open-meteo.com/"
    const val OPEN_METEO_AQ_BASE_URL = "https://air-quality-api.open-meteo.com/"

    // Iowa Environmental Mesonet (IEM) NEXRAD Radar Tiles (Keyless)
    const val IEM_TILE_BASE = "https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/"
    const val IEM_COMPOSITE_LAYER = "nexrad-n0q-900913"

    // NASA GIBS Satellite WMTS Tiles (Keyless)
    const val NASA_GIBS_BASE = "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/"

    // Regional NEXRAD station for Northern Kentucky & Greater Cincinnati
    const val DEFAULT_RADAR_STATION = "KILN" // Wilmington, OH
    const val PRIMARY_OBS_STATION = "KCVG"  // Cincinnati/Northern Kentucky International Airport
    const val SECONDARY_OBS_STATION = "KLUK" // Cincinnati Municipal Airport Lunken Field
}
