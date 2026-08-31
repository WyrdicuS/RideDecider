package com.ridedecider.app.domain.usecase

import com.ridedecider.app.domain.engine.DecisionEngine
import com.ridedecider.app.domain.model.ProfitabilityConfig
import com.ridedecider.app.domain.model.Trip
import com.ridedecider.app.domain.model.TripEvaluation

/**
 * Caso de uso responsable de coordinar la evaluación económica de una oferta de viaje.
 *
 * Actúa como punto de entrada de la lógica de negocio en la capa Domain,
 * delegando el análisis matemático y las reglas de decisión en [DecisionEngine].
 */
class EvaluateIncomingTripUseCase(
    private val decisionEngine: DecisionEngine
) {

    /**
     * Evalúa una oferta de viaje utilizando la configuración de rentabilidad del conductor.
     *
     * @param trip Oferta de viaje normalizada en modelo de dominio.
     * @param config Parámetros y umbrales económicos del conductor.
     * @return [TripEvaluation] con el veredicto, métricas y razones justificadas.
     */
    operator fun invoke(
        trip: Trip,
        config: ProfitabilityConfig,
        economicContext: com.ridedecider.app.domain.model.DriverEconomicContext? = null
    ): TripEvaluation {
        return decisionEngine.evaluate(trip, config, economicContext = economicContext)
    }
}
