package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weather_cache")
data class WeatherCacheEntity(
    @PrimaryKey val placeId: String,
    val cachedAt: Long,
    val currentJson: String,
    val hourlyJson: String,
    val dailyJson: String,
    val minutelyJson: String,
    val alertsJson: String,
    val aqiJson: String,
    val minuteSummary: String
)

@Entity(tableName = "saved_places")
data class SavedPlaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val stateCode: String,
    val lat: Double,
    val lon: Double,
    val isPreset: Boolean,
    val isSelected: Boolean
)
