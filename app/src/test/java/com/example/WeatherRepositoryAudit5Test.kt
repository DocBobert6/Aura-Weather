package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.NwsApi
import com.example.data.api.NwsAlertsResponse
import com.example.data.api.NwsForecastPeriod
import com.example.data.api.NwsForecastResponse
import com.example.data.api.NwsObservationResponse
import com.example.data.api.NwsPointsResponse
import com.example.data.api.NwsStationsResponse
import com.example.data.api.OpenMeteoAirQualityApi
import com.example.data.api.OpenMeteoAirQualityResponse
import com.example.data.api.OpenMeteoHourly
import com.example.data.api.OpenMeteoWeatherApi
import com.example.data.api.OpenMeteoWeatherResponse
import com.example.data.api.PirateWeatherApi
import com.example.data.api.PirateWeatherForecastResponse
import com.example.data.db.WeatherCacheEntity
import com.example.data.db.WeatherDatabase
import com.example.data.mapper.InstantJsonAdapter
import com.example.data.mapper.LocalDateJsonAdapter
import com.example.data.repo.WeatherRepository
import com.example.data.repo.hourlyCutoff
import com.example.data.settings.SettingsManager
import com.example.domain.model.CurrentConditions
import com.example.domain.model.DailyPoint
import com.example.domain.model.FullWeatherState
import com.example.domain.model.GeoPoint
import com.example.domain.model.HourlyPoint
import com.example.domain.model.MinutePrecip
import com.example.domain.model.Place
import com.example.domain.model.PollenData
import com.example.domain.model.WeatherAlert
import com.example.domain.model.WeatherCondition
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Robolectric tests for the AUDIT5 repository/data fixes (L-1, L-2, L-3).
 * The parsers under test are private to WeatherRepository, so they are
 * reached by reflection — the same pattern AlertCheckerTest uses to reach
 * the repository through its public API, but the parsers take their
 * inputs directly and have no network path, so reflection keeps these
 * tests hermetic. CI runs these (testDebugUnitTest); they are not pure
 * tests (Room + Retrofit interfaces), so they are not in
 * run-pure-tests-audit5.sh. The pure halves of the same contracts live
 * in AuraWeatherUnitTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WeatherRepositoryAudit5Test {

    private lateinit var db: WeatherDatabase
    private lateinit var repository: WeatherRepository

    private val fakeNwsApi = object : NwsApi {
        override suspend fun getActiveAlerts(point: String): NwsAlertsResponse =
            throw NotImplementedError("not used by the audit5 repository tests")

        override suspend fun getPoints(lat: Double, lon: Double): NwsPointsResponse =
            throw NotImplementedError("not used by the audit5 repository tests")

        override suspend fun getStations(lat: Double, lon: Double): NwsStationsResponse =
            throw NotImplementedError("not used by the audit5 repository tests")

        override suspend fun getLatestObservation(stationId: String): NwsObservationResponse =
            throw NotImplementedError("not used by the audit5 repository tests")

        override suspend fun getForecast(wfo: String, x: Int, y: Int): NwsForecastResponse =
            throw NotImplementedError("not used by the audit5 repository tests")

        override suspend fun getHourlyForecast(wfo: String, x: Int, y: Int): NwsForecastResponse =
            throw NotImplementedError("not used by the audit5 repository tests")
    }

    private val stubOpenMeteo = object : OpenMeteoWeatherApi {
        override suspend fun getForecast(
            lat: Double, lon: Double, current: String, hourly: String,
            minutely15: String, daily: String, forecastDays: Int, timezone: String
        ): OpenMeteoWeatherResponse = throw NotImplementedError("not used by the audit5 repository tests")

        override suspend fun getCurrentMulti(
            latitudes: String, longitudes: String, current: String, timezone: String
        ): List<OpenMeteoWeatherResponse> = throw NotImplementedError("not used by the audit5 repository tests")

        override suspend fun getDailyLocal(
            lat: Double, lon: Double, daily: String, forecastDays: Int, timezone: String
        ): OpenMeteoWeatherResponse = throw NotImplementedError("not used by the audit5 repository tests")
    }

    private val stubAirQuality = object : OpenMeteoAirQualityApi {
        override suspend fun getAirQuality(
            lat: Double, lon: Double, current: String
        ): OpenMeteoAirQualityResponse = throw NotImplementedError("not used by the audit5 repository tests")
    }

    private val stubPirateWeather = object : PirateWeatherApi {
        override suspend fun getForecast(
            dummy: String, latlon: String, apiKey: String, units: String
        ): PirateWeatherForecastResponse = PirateWeatherForecastResponse(minutely = null)
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WeatherDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = WeatherRepository(
            nwsApi = fakeNwsApi,
            openMeteoWeatherApi = stubOpenMeteo,
            openMeteoAirQualityApi = stubAirQuality,
            pirateWeatherApi = stubPirateWeather,
            settingsManager = SettingsManager(context),
            weatherCacheDao = db.weatherCacheDao(),
            savedPlaceDao = db.savedPlaceDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    // --- L-2: hourly lists start at the current hour. ---

    @Test
    fun testHourlyCutoff_wholeHourZone() {
        val zone = ZoneId.of("America/New_York")
        assertEquals(
            Instant.parse("2026-11-13T14:00:00Z"),
            hourlyCutoff(Instant.parse("2026-11-13T14:37:00Z"), zone)
        )
    }

    @Test
    fun testHourlyCutoff_halfHourZone() {
        // 15:37 IST: the local hour started at 09:30Z, not 10:00Z.
        assertEquals(
            Instant.parse("2026-11-13T09:30:00Z"),
            hourlyCutoff(Instant.parse("2026-11-13T10:07:00Z"), ZoneId.of("Asia/Kolkata"))
        )
    }

    @Test
    fun testParseNwsHourly_dropsPreviousHourKeepsCurrentHour() {
        val now = Instant.parse("2026-11-13T14:37:00Z") // 09:37 EST
        val zone = ZoneId.of("America/New_York")
        val periods = listOf(
            nwsPeriod(1, "2026-11-13T13:38:00Z", "2026-11-13T14:38:00Z"),
            nwsPeriod(2, "2026-11-13T14:00:00Z", "2026-11-13T15:00:00Z"),
            nwsPeriod(3, "2026-11-13T15:00:00Z", "2026-11-13T16:00:00Z")
        )
        val result = callParseNwsHourly(periods, now, zone)
        // The 13:38Z entry (59 minutes ago, previous hour) is gone; the
        // first kept entry is the current hour, so the first-hour
        // condition override in fetchFreshWeather stamps "now" onto the
        // current hour, not the previous one.
        assertEquals(2, result.size)
        assertEquals(Instant.parse("2026-11-13T14:00:00Z"), result[0].time)
        assertEquals(Instant.parse("2026-11-13T15:00:00Z"), result[1].time)
    }

    @Test
    fun testParseOpenMeteoHourly_dropsPreviousLocalHourInHalfHourZone() {
        val now = Instant.parse("2026-11-13T10:07:00Z") // 15:37 IST
        val place = Place("p2", "Mumbai", GeoPoint(19.07, 72.87), "", zoneId = "Asia/Kolkata")
        val result = OpenMeteoWeatherResponse(
            latitude = 19.07,
            longitude = 72.87,
            utcOffsetSeconds = 19800,
            current = null,
            hourly = OpenMeteoHourly(
                time = listOf("2026-11-13T09:00", "2026-11-13T10:00", "2026-11-13T11:00"),
                temperature2m = listOf(28.0, 29.0, 30.0),
                precipitationProbability = null,
                precipitation = null,
                snowfall = null,
                weatherCode = listOf(0, 0, 0),
                windSpeed10m = null
            ),
            minutely15 = null,
            daily = null
        )
        val points = callParseOpenMeteoHourly(result, place, ZoneId.of("Asia/Kolkata"), now)
        // 09:00Z is 14:30 IST — the previous local hour — so it is
        // dropped even though it is only 67 minutes old; the first kept
        // entry is the current local hour (10:00Z = 15:30 IST).
        assertEquals(2, points.size)
        assertEquals(Instant.parse("2026-11-13T10:00:00Z"), points[0].time)
        assertEquals(Instant.parse("2026-11-13T11:00:00Z"), points[1].time)
    }

    // --- L-3: a daily bucket with no daytime period is Unknown. ---

    @Test
    fun testParseNwsDaily_nightOnlyBucketIsUnknownNotClear() {
        val zone = ZoneId.of("America/New_York")
        val periods = listOf(
            // 2026-11-13 has a day period (control) and a night period.
            nwsPeriod(
                1, "2026-11-13T13:00:00-05:00", "2026-11-13T18:00:00-05:00",
                isDaytime = true, temperature = 60.0, shortForecast = "Sunny"
            ),
            nwsPeriod(
                2, "2026-11-13T18:00:00-05:00", "2026-11-14T06:00:00-05:00",
                isDaytime = false, temperature = 45.0, shortForecast = "Mostly Clear"
            ),
            // 2026-11-14 has only a night period: no daytime condition.
            nwsPeriod(
                3, "2026-11-14T18:00:00-05:00", "2026-11-15T06:00:00-05:00",
                isDaytime = false, temperature = 44.0, shortForecast = "Mostly Clear"
            )
        )
        val result = callParseNwsDaily(periods, zone)
        assertEquals(2, result.size)
        assertEquals(WeatherCondition.ClearDay, result[0].condition)
        assertTrue("Day bucket keeps its daytime summary", result[0].summary.contains("Sunny"))
        assertEquals(
            "A night-only bucket must not invent Clear",
            WeatherCondition.Unknown, result[1].condition
        )
        assertEquals("Fair", result[1].summary)
    }

    // --- L-1: cache restore recomputes moonrise/moonset. ---

    @Test
    fun testParseCacheToState_restoresMoonriseMoonset() {
        val place = Place(
            "p1", "Test", GeoPoint(38.77, -84.56), "KY",
            zoneId = "America/New_York"
        )
        val now = Instant.now()
        val cache = WeatherCacheEntity(
            placeId = "p1",
            cachedAt = now.toEpochMilli(),
            currentJson = moshi.adapter(CurrentConditions::class.java).toJson(
                CurrentConditions(
                    observedAt = now, station = null, condition = WeatherCondition.ClearDay,
                    description = "Clear", tempC = 10.0, feelsLikeC = 10.0, dewPointC = null,
                    humidityPct = null, windMs = null, windGustMs = null, windDirDeg = null,
                    pressurePa = null, visibilityM = null, cloudCoverPct = null
                )
            ),
            hourlyJson = listAdapter(HourlyPoint::class.java).toJson(
                listOf(
                    HourlyPoint(
                        time = now, tempC = 10.0, popPct = null, precipMm = null,
                        condition = WeatherCondition.ClearDay, windMs = null, isDaytime = true
                    )
                )
            ),
            dailyJson = listAdapter(DailyPoint::class.java).toJson(
                listOf(
                    DailyPoint(
                        date = LocalDate.now(ZoneId.of("America/New_York")),
                        highC = 15.0, lowC = 5.0, popPct = null,
                        condition = WeatherCondition.ClearDay, summary = "Clear",
                        uvIndexMax = null
                    )
                )
            ),
            minutelyJson = listAdapter(MinutePrecip::class.java).toJson(emptyList()),
            alertsJson = listAdapter(WeatherAlert::class.java).toJson(emptyList()),
            aqiJson = moshi.adapter(com.example.domain.model.AirQuality::class.java).toJson(null),
            pollenJson = moshi.adapter(PollenData::class.java).toJson(
                PollenData(null, null, null, "Unavailable", false)
            ),
            radarStation = null,
            forecastZone = null,
            minuteSummary = "No rain expected"
        )
        val restored = callParseCacheToState(place, cache, false)
        assertNotNull("A parseable cache row must restore", restored)
        // The old code left both null on every restore. The recompute
        // fills them from today's date at the place — on the rare day
        // the moon genuinely does not rise or set at this latitude one
        // of the two may legitimately be null, so assert at least one
        // is present rather than pinning both.
        assertTrue(
            "Cache restore must recompute moonrise/moonset, not drop them",
            restored!!.lunar.moonrise != null || restored.lunar.moonset != null
        )
    }

    // --- Reflection helpers (private repository parsers). ---

    private fun nwsPeriod(
        number: Int,
        startTime: String,
        endTime: String,
        isDaytime: Boolean = true,
        temperature: Double? = 50.0,
        shortForecast: String? = "Sunny"
    ) = NwsForecastPeriod(
        number = number,
        name = "Period $number",
        startTime = startTime,
        endTime = endTime,
        isDaytime = isDaytime,
        temperature = temperature,
        temperatureUnit = "F",
        windSpeed = "5 mph",
        windDirection = "NW",
        shortForecast = shortForecast,
        detailedForecast = null,
        probabilityOfPrecipitation = null
    )

    @Suppress("UNCHECKED_CAST")
    private fun callParseNwsHourly(
        periods: List<NwsForecastPeriod>?,
        now: Instant,
        zone: ZoneId
    ): List<HourlyPoint> {
        val method = WeatherRepository::class.java.getDeclaredMethod(
            "parseNwsHourly", List::class.java, Instant::class.java, ZoneId::class.java
        )
        method.isAccessible = true
        return method.invoke(repository, periods, now, zone) as List<HourlyPoint>
    }

    @Suppress("UNCHECKED_CAST")
    private fun callParseOpenMeteoHourly(
        result: OpenMeteoWeatherResponse?,
        place: Place,
        zone: ZoneId,
        now: Instant
    ): List<HourlyPoint> {
        val method = WeatherRepository::class.java.getDeclaredMethod(
            "parseOpenMeteoHourly",
            OpenMeteoWeatherResponse::class.java, Place::class.java,
            ZoneId::class.java, Instant::class.java
        )
        method.isAccessible = true
        return method.invoke(repository, result, place, zone, now) as List<HourlyPoint>
    }

    @Suppress("UNCHECKED_CAST")
    private fun callParseNwsDaily(
        periods: List<NwsForecastPeriod>?,
        zone: ZoneId
    ): List<DailyPoint> {
        val method = WeatherRepository::class.java.getDeclaredMethod(
            "parseNwsDaily", List::class.java, ZoneId::class.java
        )
        method.isAccessible = true
        return method.invoke(repository, periods, zone) as List<DailyPoint>
    }

    private fun callParseCacheToState(
        place: Place,
        cache: WeatherCacheEntity,
        isStale: Boolean
    ): FullWeatherState? {
        val method = WeatherRepository::class.java.getDeclaredMethod(
            "parseCacheToState", Place::class.java, WeatherCacheEntity::class.java,
            java.lang.Boolean.TYPE
        )
        method.isAccessible = true
        return method.invoke(repository, place, cache, isStale) as FullWeatherState?
    }

    private val moshi: Moshi = Moshi.Builder()
        .add(InstantJsonAdapter)
        .add(LocalDateJsonAdapter)
        .add(KotlinJsonAdapterFactory())
        .build()

    private fun <T> listAdapter(clazz: Class<T>) =
        moshi.adapter<List<T>>(Types.newParameterizedType(List::class.java, clazz))
}
