package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WeatherCacheDao {
    @Query("SELECT * FROM weather_cache WHERE placeId = :placeId LIMIT 1")
    suspend fun getCache(placeId: String): WeatherCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCache(cache: WeatherCacheEntity)

    @Query("DELETE FROM weather_cache WHERE placeId = :placeId")
    suspend fun deleteCache(placeId: String)
}

@Dao
interface SavedPlaceDao {
    @Query("SELECT * FROM saved_places ORDER BY isPreset DESC, name ASC")
    fun getAllPlaces(): Flow<List<SavedPlaceEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPresets(places: List<SavedPlaceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlace(place: SavedPlaceEntity)

    @Delete
    suspend fun deletePlace(place: SavedPlaceEntity)

    @Query("SELECT * FROM saved_places WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedPlace(): SavedPlaceEntity?

    @Query("UPDATE saved_places SET isSelected = CASE WHEN id = :placeId THEN 1 ELSE 0 END")
    suspend fun setSelectedPlace(placeId: String)
}
