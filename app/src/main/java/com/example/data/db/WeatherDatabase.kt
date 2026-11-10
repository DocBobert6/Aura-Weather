package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 2 changes (Phase 1):
 * - saved_places gains zoneId (the place's resolved IANA timezone).
 * - weather_cache gains pollenJson / radarStation / forecastZone columns.
 * - All v1 cache rows are deleted: v1 stored bar/comma-delimited strings
 *   that the v2 JSON parser cannot read, and restoring them would silently
 *   re-invent the dropped/hardcoded fields.
 * - All seeded preset places are deleted: the app no longer ships default
 *   locations. An install whose selected place was a preset comes back with
 *   no place selected and the empty state.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE saved_places ADD COLUMN zoneId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE weather_cache ADD COLUMN pollenJson TEXT NOT NULL DEFAULT 'null'")
        db.execSQL("ALTER TABLE weather_cache ADD COLUMN radarStation TEXT")
        db.execSQL("ALTER TABLE weather_cache ADD COLUMN forecastZone TEXT")
        db.execSQL("DELETE FROM weather_cache")
        db.execSQL("DELETE FROM saved_places WHERE isPreset = 1")
    }
}

/**
 * Version 3 changes (Phase 3.1):
 * - seen_alerts table (alertId, firstSeenAt, severity): the persisted
 *   dedupe set for alert notifications. A fresh install creates it with
 *   the database; an upgrade runs MIGRATION_2_3.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `seen_alerts` (" +
                "`alertId` TEXT NOT NULL, " +
                "`firstSeenAt` INTEGER NOT NULL, " +
                "`severity` TEXT NOT NULL, " +
                "PRIMARY KEY(`alertId`))"
        )
    }
}

@Database(
    entities = [WeatherCacheEntity::class, SavedPlaceEntity::class, SeenAlertEntity::class],
    version = 3,
    exportSchema = false
)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun weatherCacheDao(): WeatherCacheDao
    abstract fun savedPlaceDao(): SavedPlaceDao
    abstract fun seenAlertDao(): SeenAlertDao

    companion object {
        @Volatile
        private var INSTANCE: WeatherDatabase? = null

        fun getInstance(context: Context): WeatherDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WeatherDatabase::class.java,
                    "aura_weather.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
