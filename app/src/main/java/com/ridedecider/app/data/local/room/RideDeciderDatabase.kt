package com.ridedecider.app.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ridedecider.app.data.local.room.dao.DriverGoalsDao
import com.ridedecider.app.data.local.room.dao.RecordedTripDao
import com.ridedecider.app.data.local.room.entity.DriverGoalsEntity
import com.ridedecider.app.data.local.room.entity.RecordedTripEntity

/**
 * Base de datos Room principal de RideDecider.
 * Persiste de forma fiable el historial de viajes y los objetivos del conductor.
 */
@Database(
    entities = [
        RecordedTripEntity::class,
        DriverGoalsEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class RideDeciderDatabase : RoomDatabase() {

    abstract fun recordedTripDao(): RecordedTripDao
    abstract fun driverGoalsDao(): DriverGoalsDao

    companion object {
        private const val DATABASE_NAME = "ridedecider_database.db"

        @Volatile
        private var INSTANCE: RideDeciderDatabase? = null

        fun getInstance(context: Context): RideDeciderDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RideDeciderDatabase::class.java,
                    DATABASE_NAME
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
