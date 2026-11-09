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

    @Query("SELECT * FROM saved_places")
    suspend fun getAllPlacesList(): List<SavedPlaceEntity>

    @Query("SELECT * FROM saved_places WHERE id = :placeId LIMIT 1")
    suspend fun getPlaceById(placeId: String): SavedPlaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlace(place: SavedPlaceEntity)

    @Delete
    suspend fun deletePlace(place: SavedPlaceEntity)

    @Query("SELECT * FROM saved_places WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedPlace(): SavedPlaceEntity?

    @Query("UPDATE saved_places SET isSelected = CASE WHEN id = :placeId THEN 1 ELSE 0 END")
    suspend fun setSelectedPlace(placeId: String)

    @Query("UPDATE saved_places SET zoneId = :zoneId WHERE id = :placeId")
    suspend fun updateZoneId(placeId: String, zoneId: String)

    @Query("UPDATE saved_places SET name = :name WHERE id = :placeId")
    suspend fun updateName(placeId: String, name: String)
}

/**
 * The 3.1 seen-set: alerts the worker has already processed. REPLACE on
 * insert, so a re-poll refreshes the severity (and the first-seen clock)
 * for an alert that is still active; rows for alerts that expired stop
 * being refreshed and are pruned by age.
 */
@Dao
interface SeenAlertDao {
    @Query("SELECT * FROM seen_alerts")
    suspend fun getAll(): List<SeenAlertEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(seen: SeenAlertEntity)

    @Query("DELETE FROM seen_alerts WHERE firstSeenAt < :olderThanMillis")
    suspend fun deleteOlderThan(olderThanMillis: Long)
}
