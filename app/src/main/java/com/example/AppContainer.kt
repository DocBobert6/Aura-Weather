package com.example

import android.content.Context
import com.example.core.HttpClients
import com.example.core.ProviderEndpoints
import com.example.data.api.AirNowApi
import com.example.data.api.NwsApi
import com.example.data.api.OpenMeteoAirQualityApi
import com.example.data.api.OpenMeteoWeatherApi
import com.example.data.db.WeatherDatabase
import com.example.data.repo.WeatherRepository
import com.example.data.settings.SettingsManager
import com.google.android.gms.location.LocationServices
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class AppContainer(private val context: Context) {

    // 3.4: two clients. NWS carries the NWS headers (User-Agent with the
    // repo URL + geo+json Accept); Open-Meteo gets a plain client with no
    // NWS headers. The shared client that leaked NWS headers to
    // Open-Meteo is gone.
    private val nwsOkHttpClient: OkHttpClient by lazy {
        HttpClients.nwsClient()
    }

    private val plainOkHttpClient: OkHttpClient by lazy {
        HttpClients.plainClient()
    }

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val nwsRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(ProviderEndpoints.NWS_BASE_URL)
            .client(nwsOkHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    private val openMeteoRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(ProviderEndpoints.OPEN_METEO_BASE_URL)
            .client(plainOkHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    private val openMeteoAqRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(ProviderEndpoints.OPEN_METEO_AQ_BASE_URL)
            .client(plainOkHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    // AirNow gets the plain client (no NWS headers — it is not an NWS
    // endpoint, and its key travels as a query parameter, not a header).
    private val airNowRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(ProviderEndpoints.AIRNOW_BASE_URL)
            .client(plainOkHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    val nwsApi: NwsApi by lazy {
        nwsRetrofit.create(NwsApi::class.java)
    }

    val openMeteoWeatherApi: OpenMeteoWeatherApi by lazy {
        openMeteoRetrofit.create(OpenMeteoWeatherApi::class.java)
    }

    val openMeteoAirQualityApi: OpenMeteoAirQualityApi by lazy {
        openMeteoAqRetrofit.create(OpenMeteoAirQualityApi::class.java)
    }

    val airNowApi: AirNowApi by lazy {
        airNowRetrofit.create(AirNowApi::class.java)
    }

    val database: WeatherDatabase by lazy {
        WeatherDatabase.getInstance(context)
    }

    val seenAlertDao by lazy {
        database.seenAlertDao()
    }

    val settingsManager: SettingsManager by lazy {
        SettingsManager(context)
    }

    val weatherRepository: WeatherRepository by lazy {
        WeatherRepository(
            nwsApi = nwsApi,
            openMeteoWeatherApi = openMeteoWeatherApi,
            openMeteoAirQualityApi = openMeteoAirQualityApi,
            airNowApi = airNowApi,
            settingsManager = settingsManager,
            weatherCacheDao = database.weatherCacheDao(),
            savedPlaceDao = database.savedPlaceDao()
        )
    }

    val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }
}
