package com.example

import android.app.Application

class AuraWeatherApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // No seeded places: on first launch the user picks a place (or uses
        // their current location) and the app loads weather for that place.
    }
}
