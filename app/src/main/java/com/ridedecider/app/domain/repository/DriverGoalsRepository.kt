package com.ridedecider.app.domain.repository

import com.ridedecider.app.domain.model.DriverGoals
import kotlinx.coroutines.flow.StateFlow

/**
 * Contrato de repositorio para consultar y actualizar los objetivos económicos del conductor.
 */
interface DriverGoalsRepository {
    /**
     * Obtiene los objetivos actuales de forma síncrona o reactiva.
     */
    fun getGoals(): DriverGoals

    /**
     * Flujo reactivo con los objetivos del conductor.
     */
    val goalsFlow: StateFlow<DriverGoals>

    /**
     * Actualiza los objetivos del conductor.
     */
    suspend fun updateGoals(goals: DriverGoals)
}
