package com.ridedecider.app.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ridedecider.app.data.local.room.dao.DecisionSnapshotDao
import com.ridedecider.app.data.local.room.dao.DriverGoalsDao
import com.ridedecider.app.data.local.room.dao.RecordedTripDao
import com.ridedecider.app.data.local.room.entity.DecisionSnapshotEntity
import com.ridedecider.app.data.local.room.entity.DriverGoalsEntity
import com.ridedecider.app.data.local.room.entity.RecordedTripEntity

/**
 * Base de datos Room principal de RideDecider.
 * Persiste de forma fiable el historial de viajes, los objetivos del conductor y los snapshots de aprendizaje.
 */
@Database(
    entities = [
        RecordedTripEntity::class,
        DriverGoalsEntity::class,
        DecisionSnapshotEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class RideDeciderDatabase : RoomDatabase() {

    abstract fun recordedTripDao(): RecordedTripDao
    abstract fun driverGoalsDao(): DriverGoalsDao
    abstract fun decisionSnapshotDao(): DecisionSnapshotDao

    companion object {
        private const val DATABASE_NAME = "ridedecider_database.db"

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `decision_snapshots` (
                        `snapshotId` TEXT NOT NULL,
                        `instanceId` TEXT NOT NULL,
                        `tripId` TEXT NOT NULL,
                        `recordedTimestamp` INTEGER NOT NULL,
                        `appVersion` TEXT NOT NULL,
                        `engineVersion` TEXT NOT NULL,
                        `offerType` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `estimatedFareEur` REAL,
                        `currency` TEXT NOT NULL,
                        `estimatedPickupDistanceKm` REAL,
                        `estimatedPickupDurationMinutes` REAL,
                        `estimatedTripDistanceKm` REAL,
                        `estimatedTripDurationMinutes` REAL,
                        `estimatedTotalDistanceKm` REAL,
                        `estimatedTotalDurationMinutes` REAL,
                        `grossPerKm` REAL,
                        `grossPerHour` REAL,
                        `effectiveGrossPerKm` REAL,
                        `estimatedOperatingCost` REAL,
                        `estimatedNetProfit` REAL,
                        `netPerHour` REAL,
                        `pickupSpeedKmh` REAL,
                        `pickupDistanceRatio` REAL,
                        `pickupTimeRatio` REAL,
                        `profitabilityScore` INTEGER,
                        `decision` TEXT NOT NULL,
                        `decisionReasonsCommaSeparated` TEXT NOT NULL,
                        `profitabilityLevel` TEXT NOT NULL,
                        `kinematicsSource` TEXT NOT NULL,
                        `dayOfWeek` INTEGER NOT NULL,
                        `hourOfDay` INTEGER NOT NULL,
                        `minuteOfHour` INTEGER NOT NULL,
                        `timeBucket` TEXT NOT NULL,
                        `wazeEstimatedDurationMinutes` REAL,
                        `wazeAvailable` INTEGER NOT NULL,
                        `uberWazeDurationDeltaMinutes` REAL,
                        `wazeEstimateTimestamp` INTEGER,
                        `actualDistanceKm` REAL,
                        `actualDurationMinutes` REAL,
                        `actualPickupDurationMinutes` REAL,
                        `actualBaseFareEur` REAL,
                        `waitingCompensationEur` REAL,
                        `cancellationFeeEur` REAL,
                        `tipEur` REAL,
                        `finalEarningsEur` REAL,
                        PRIMARY KEY(`snapshotId`)
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_decision_snapshots_instanceId` ON `decision_snapshots` (`instanceId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_decision_snapshots_tripId` ON `decision_snapshots` (`tripId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_decision_snapshots_recordedTimestamp` ON `decision_snapshots` (`recordedTimestamp`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_decision_snapshots_decision` ON `decision_snapshots` (`decision`)")
            }
        }

        @Volatile
        private var INSTANCE: RideDeciderDatabase? = null

        fun getInstance(context: Context): RideDeciderDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RideDeciderDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
