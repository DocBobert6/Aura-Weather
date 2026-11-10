package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.AirNowApi
import com.example.data.api.AirNowObservation
import com.example.data.api.NwsAlertFeature
import com.example.data.api.NwsAlertProperties
import com.example.data.api.NwsAlertsResponse
import com.example.data.api.NwsApi
import com.example.data.api.NwsForecastResponse
import com.example.data.api.NwsObservationResponse
import com.example.data.api.NwsPointsResponse
import com.example.data.api.NwsStationsResponse
import com.example.data.api.OpenMeteoAirQualityApi
import com.example.data.api.OpenMeteoAirQualityResponse
import com.example.data.api.OpenMeteoWeatherApi
import com.example.data.api.OpenMeteoWeatherResponse
import com.example.data.db.SavedPlaceEntity
import com.example.data.db.WeatherDatabase
import com.example.data.settings.SettingsManager
import com.example.data.repo.WeatherRepository
import com.example.data.work.AlertChecker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Robolectric tests for the 3.1 checker (AlertChecker) — the acceptance
 * behaviour, end to end through the real repository parser and the real
 * Room seen-set, with a fake NWS API and a recording notifier. CI runs
 * these (testDebugUnitTest); they are not pure tests (Room + Retrofit
 * interfaces), so they are not in run-pure-tests.sh.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlertCheckerTest {

    // Times are relative to the real clock: the repository's alert parser
    // uses Instant.now() (not the checker's injected now), so fixed 2026
    // timestamps would go stale and the "active" alerts would read as
    // expired whenever the suite ran after them.
    private val now = Instant.now()

    private lateinit var db: WeatherDatabase
    private lateinit var repository: WeatherRepository
    private lateinit var checker: AlertChecker
    private val notified = mutableListOf<String>()
    private var cannedAlerts: List<NwsAlertFeature> = emptyList()

    private fun alertFeature(
        id: String?,
        severity: String,
        expires: String = now.plusSeconds(8 * 3600).toString(),
        effective: String = now.minusSeconds(2 * 3600).toString()
    ) = NwsAlertFeature(
        NwsAlertProperties(
            id = id,
            areaDesc = "Boone County",
            event = "Severe Thunderstorm Warning",
            headline = "Severe thunderstorm warning",
            description = "Damaging winds expected.",
            instruction = "Seek shelter.",
            severity = severity,
            urgency = "Immediate",
            certainty = "Likely",
            effective = effective,
            expires = expires,
            ends = null,
            senderName = "NWS"
        )
    )

    private val fakeNwsApi = object : NwsApi {
        override suspend fun getActiveAlerts(point: String): NwsAlertsResponse =
            NwsAlertsResponse(features = cannedAlerts)

        override suspend fun getPoints(lat: Double, lon: Double): NwsPointsResponse =
            throw NotImplementedError("not used by the checker test")

        override suspend fun getStations(lat: Double, lon: Double): NwsStationsResponse =
            throw NotImplementedError("not used by the checker test")

        override suspend fun getLatestObservation(stationId: String): NwsObservationResponse =
            throw NotImplementedError("not used by the checker test")

        override suspend fun getForecast(wfo: String, x: Int, y: Int): NwsForecastResponse =
            throw NotImplementedError("not used by the checker test")

        override suspend fun getHourlyForecast(wfo: String, x: Int, y: Int): NwsForecastResponse =
            throw NotImplementedError("not used by the checker test")
    }

    private val stubOpenMeteo = object : OpenMeteoWeatherApi {
        override suspend fun getForecast(
            lat: Double, lon: Double, current: String, hourly: String,
            minutely15: String, daily: String, timezone: String
        ): OpenMeteoWeatherResponse = throw NotImplementedError("not used by the checker test")

        override suspend fun getCurrentMulti(
            latitudes: String, longitudes: String, current: String, timezone: String
        ): List<OpenMeteoWeatherResponse> = throw NotImplementedError("not used by the checker test")
    }

    private val stubAirQuality = object : OpenMeteoAirQualityApi {
        override suspend fun getAirQuality(
            lat: Double, lon: Double, current: String
        ): OpenMeteoAirQualityResponse = throw NotImplementedError("not used by the checker test")
    }

    // AirNow is not used by the checker test (alerts only): the stub
    // returns no observations, so the AQI falls back to Open-Meteo.
    private val stubAirNow = object : AirNowApi {
        override suspend fun getCurrentObservations(
            lat: Double, lon: Double, distance: Int, format: String, apiKey: String
        ): List<AirNowObservation> = emptyList()
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
            airNowApi = stubAirNow,
            settingsManager = SettingsManager(context),
            weatherCacheDao = db.weatherCacheDao(),
            savedPlaceDao = db.savedPlaceDao()
        )
        checker = AlertChecker(
            repository = repository,
            seenAlertDao = db.seenAlertDao(),
            onNotify = { alert, _ -> notified.add(alert.id) }
        )
        runBlocking {
            db.savedPlaceDao().insertPlace(
                SavedPlaceEntity(
                    id = "p1",
                    name = "Crittenden, KY",
                    stateCode = "KY",
                    lat = 38.78,
                    lon = -84.61,
                    isPreset = false,
                    isSelected = true,
                    zoneId = "America/New_York"
                )
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testChecker_sameAlertTwiceNotifiesOnce() = runBlocking {
        cannedAlerts = listOf(alertFeature("https://api.weather.gov/alerts/a1", "Severe"))
        checker.check(now)
        checker.check(now)
        assertEquals(listOf("https://api.weather.gov/alerts/a1"), notified)
    }

    @Test
    fun testChecker_newAlertIdNotifiesAgain() = runBlocking {
        cannedAlerts = listOf(alertFeature("https://api.weather.gov/alerts/a1", "Severe"))
        checker.check(now)
        cannedAlerts = listOf(
            alertFeature("https://api.weather.gov/alerts/a1", "Severe"),
            alertFeature("https://api.weather.gov/alerts/a2", "Moderate")
        )
        checker.check(now)
        assertEquals(
            listOf("https://api.weather.gov/alerts/a1", "https://api.weather.gov/alerts/a2"),
            notified
        )
    }

    @Test
    fun testChecker_expiredAlertNeverNotifies() = runBlocking {
        cannedAlerts = listOf(
            alertFeature("https://api.weather.gov/alerts/a1", "Severe", expires = now.minusSeconds(3600).toString())
        )
        val outcome = checker.check(now)
        assertTrue(notified.isEmpty())
        assertEquals(AlertChecker.Outcome.Checked(notified = 0, active = 0), outcome)
    }

    @Test
    fun testChecker_severityEscalationNotifiesAgain() = runBlocking {
        cannedAlerts = listOf(alertFeature("https://api.weather.gov/alerts/a1", "Moderate"))
        checker.check(now)
        cannedAlerts = listOf(alertFeature("https://api.weather.gov/alerts/a1", "Severe"))
        checker.check(now)
        assertEquals(
            listOf("https://api.weather.gov/alerts/a1", "https://api.weather.gov/alerts/a1"),
            notified
        )
    }

    @Test
    fun testChecker_idlessAlertGetsStableIdAndDedupes() = runBlocking {
        cannedAlerts = listOf(alertFeature(null, "Severe"))
        checker.check(now)
        checker.check(now)
        assertEquals("An id-less alert must notify once, not once per poll", 1, notified.size)
        assertTrue("The derived id carries the alert_ prefix", notified.first().startsWith("alert_"))
    }

    @Test
    fun testChecker_noSelectedPlaceNotifiesNothing() = runBlocking {
        db.savedPlaceDao().deletePlace(
            SavedPlaceEntity(
                id = "p1", name = "Crittenden, KY", stateCode = "KY",
                lat = 38.78, lon = -84.61, isPreset = false, isSelected = true,
                zoneId = "America/New_York"
            )
        )
        cannedAlerts = listOf(alertFeature("https://api.weather.gov/alerts/a1", "Severe"))
        val outcome = checker.check(now)
        assertEquals(AlertChecker.Outcome.NoPlace, outcome)
        assertTrue(notified.isEmpty())
    }
}
