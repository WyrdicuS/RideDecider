package com.ridedecider.app.data.di

import android.content.Context
import com.ridedecider.app.data.local.room.RideDeciderDatabase
import com.ridedecider.app.data.repository.RoomDriverGoalsRepository
import com.ridedecider.app.data.repository.RoomEarningsRepository
import com.ridedecider.app.domain.engine.DecisionEngine
import com.ridedecider.app.domain.engine.EarningsTracker
import com.ridedecider.app.domain.repository.DriverGoalsRepository
import com.ridedecider.app.domain.repository.EarningsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * ServiceLocator simple y robusto para inyección de dependencias en RideDecider.
 * Centraliza la base de datos Room y los repositorios persistentes.
 */
object ServiceLocator {

    @Volatile
    private var database: RideDeciderDatabase? = null

    @Volatile
    private var earningsRepository: EarningsRepository? = null

    @Volatile
    private var driverGoalsRepository: DriverGoalsRepository? = null

    @Volatile
    private var earningsTracker: EarningsTracker? = null

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun getDatabase(context: Context): RideDeciderDatabase {
        return database ?: synchronized(this) {
            database ?: RideDeciderDatabase.getInstance(context).also { database = it }
        }
    }

    fun getEarningsRepository(context: Context): EarningsRepository {
        return earningsRepository ?: synchronized(this) {
            earningsRepository ?: RoomEarningsRepository(
                recordedTripDao = getDatabase(context).recordedTripDao(),
                ioDispatcher = Dispatchers.IO
            ).also { earningsRepository = it }
        }
    }

    fun getDriverGoalsRepository(context: Context): DriverGoalsRepository {
        return driverGoalsRepository ?: synchronized(this) {
            driverGoalsRepository ?: RoomDriverGoalsRepository(
                driverGoalsDao = getDatabase(context).driverGoalsDao(),
                ioDispatcher = Dispatchers.IO,
                scope = applicationScope
            ).also { driverGoalsRepository = it }
        }
    }

    fun getEarningsTracker(context: Context): EarningsTracker {
        return earningsTracker ?: synchronized(this) {
            earningsTracker ?: EarningsTracker(
                goalsRepository = getDriverGoalsRepository(context),
                earningsRepository = getEarningsRepository(context)
            ).also { earningsTracker = it }
        }
    }

    @Volatile
    private var profitabilityConfigProvider: com.ridedecider.app.data.accessibility.uber.InMemoryProfitabilityConfigProvider? = null

    fun getProfitabilityConfigProvider(context: Context): com.ridedecider.app.data.accessibility.uber.InMemoryProfitabilityConfigProvider {
        return profitabilityConfigProvider ?: synchronized(this) {
            profitabilityConfigProvider ?: com.ridedecider.app.data.accessibility.uber.InMemoryProfitabilityConfigProvider().also {
                profitabilityConfigProvider = it
            }
        }
    }

    @Volatile
    private var appUpdateManager: com.ridedecider.app.domain.manager.AppUpdateManager? = null

    fun getAppUpdateManager(context: Context): com.ridedecider.app.domain.manager.AppUpdateManager {
        return appUpdateManager ?: synchronized(this) {
            appUpdateManager ?: com.ridedecider.app.domain.manager.AppUpdateManager(
                context = context.applicationContext
            ).also { appUpdateManager = it }
        }
    }
}
