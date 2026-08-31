package com.ridedecider.app.data.repository

import com.ridedecider.app.data.local.room.dao.DriverGoalsDao
import com.ridedecider.app.data.local.room.entity.DriverGoalsEntity
import com.ridedecider.app.domain.model.DriverGoals
import com.ridedecider.app.domain.repository.DriverGoalsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Implementación de [DriverGoalsRepository] respaldada por Room en SQLite.
 * Proporciona acceso síncrono y reactivo a los objetivos del conductor.
 */
class RoomDriverGoalsRepository(
    private val driverGoalsDao: DriverGoalsDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : DriverGoalsRepository {

    private val _goalsFlow = MutableStateFlow(DriverGoals())
    override val goalsFlow: StateFlow<DriverGoals> = _goalsFlow.asStateFlow()

    init {
        scope.launch(ioDispatcher) {
            val persisted = driverGoalsDao.getGoals()
            if (persisted != null) {
                _goalsFlow.value = persisted.toDomain()
            } else {
                val default = DriverGoals()
                driverGoalsDao.insertOrUpdate(DriverGoalsEntity.fromDomain(default))
                _goalsFlow.value = default
            }

            driverGoalsDao.getGoalsFlow().collect { entity ->
                if (entity != null) {
                    _goalsFlow.value = entity.toDomain()
                }
            }
        }
    }

    override fun getGoals(): DriverGoals {
        return _goalsFlow.value
    }

    override suspend fun updateGoals(goals: DriverGoals) = withContext(ioDispatcher) {
        driverGoalsDao.insertOrUpdate(DriverGoalsEntity.fromDomain(goals))
        _goalsFlow.value = goals
        Unit
    }
}
