package com.ridedecider.app.data.repository

import com.ridedecider.app.domain.model.DriverGoals
import com.ridedecider.app.domain.repository.DriverGoalsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Implementación en memoria y reactiva del repositorio de objetivos del conductor.
 * Thread-safe y sin dependencias externas.
 */
class InMemoryDriverGoalsRepository(
    initialGoals: DriverGoals = DriverGoals()
) : DriverGoalsRepository {

    private val _goalsFlow = MutableStateFlow(initialGoals)
    override val goalsFlow: StateFlow<DriverGoals> = _goalsFlow.asStateFlow()

    override fun getGoals(): DriverGoals {
        return _goalsFlow.value
    }

    override suspend fun updateGoals(goals: DriverGoals) {
        _goalsFlow.value = goals
    }
}
