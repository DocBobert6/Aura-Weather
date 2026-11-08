package com.example

import android.content.Context
import com.example.core.ProviderEndpoints
import com.example.data.api.NwsApi
import com.example.data.api.OpenMeteoAirQualityApi
import com.example.data.api.OpenMeteoWeatherApi
import com.example.data.db.WeatherDatabase
import com.example.data.repo.WeatherRepository
import com.example.data.settings.SettingsManager
import com.google.android.gms.location.LocationServices
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class AppContainer(private val context: Context) {

    private val nwsInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("User-Agent", ProviderEndpoints.NWS_USER_AGENT)
            .header("Accept", ProviderEndpoints.NWS_ACCEPT_HEADER)
            .build()
        chain.proceed(request)
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(nwsInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val nwsRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(ProviderEndpoints.NWS_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    private val openMeteoRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(ProviderEndpoints.OPEN_METEO_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    private val openMeteoAqRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(ProviderEndpoints.OPEN_METEO_AQ_BASE_URL)
            .client(okHttpClient)
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

    val database: WeatherDatabase by lazy {
        WeatherDatabase.getInstance(context)
    }

    val settingsManager: SettingsManager by lazy {
        SettingsManager(context)
    }

    val weatherRepository: WeatherRepository by lazy {
        WeatherRepository(
            nwsApi = nwsApi,
            openMeteoWeatherApi = openMeteoWeatherApi,
            openMeteoAirQualityApi = openMeteoAirQualityApi,
            weatherCacheDao = database.weatherCacheDao(),
            savedPlaceDao = database.savedPlaceDao()
        )
    }

    val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }
}
