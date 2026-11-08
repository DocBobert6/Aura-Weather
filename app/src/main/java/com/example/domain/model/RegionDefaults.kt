package com.example.domain.model

object RegionDefaults {
    val HOME_POINT = GeoPoint(38.9, -84.5)

    val PRESET_PLACES = listOf(
        Place(
            id = "florence_ky",
            name = "Florence",
            stateCode = "KY",
            point = GeoPoint(38.9989, -84.6266),
            isPreset = true
        ),
        Place(
            id = "covington_ky",
            name = "Covington",
            stateCode = "KY",
            point = GeoPoint(39.0837, -84.5086),
            isPreset = true
        ),
        Place(
            id = "cincinnati_oh",
            name = "Downtown Cincinnati",
            stateCode = "OH",
            point = GeoPoint(39.1031, -84.5120),
            isPreset = true
        ),
        Place(
            id = "newport_ky",
            name = "Newport",
            stateCode = "KY",
            point = GeoPoint(39.0917, -84.4922),
            isPreset = true
        ),
        Place(
            id = "cvg_airport",
            name = "Hebron / CVG Airport",
            stateCode = "KY",
            point = GeoPoint(39.0488, -84.6678),
            isPreset = true
        ),
        Place(
            id = "crittenden_ky",
            name = "Crittenden",
            stateCode = "KY",
            point = GeoPoint(38.7915, -84.6033),
            isPreset = true
        )
    )

    const val DEFAULT_ZONE_ID = "America/New_York"
    const val WFO_ID = "ILN"
    const val RADAR_SITE = "KILN"
}
