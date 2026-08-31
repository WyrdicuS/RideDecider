package com.ridedecider.app.data.accessibility.uber

import com.ridedecider.app.domain.model.TripEvaluation

/**
 * Listener abstracto para recibir los resultados de evaluación y cambios de estado de pantalla emitidos
 * por el pipeline de accesibilidad.
 */
interface TripEvaluationListener {

    /**
     * Notificado cuando una oferta válida ha sido completamente analizada por el [com.ridedecider.app.domain.engine.DecisionEngine].
     */
    fun onTripEvaluation(evaluation: TripEvaluation)

    /**
     * Notificado cuando cambia la clasificación de la pantalla de Uber detectada.
     */
    fun onScreenStateChanged(screenType: UberOfferScreenType) {}

    /**
     * Notificado cuando se detecta una cancelación clasificada con su posible compensación.
     */
    fun onTripCancelled(reason: com.ridedecider.app.domain.model.CancellationReason, feeEur: Double?) {}

    /**
     * Notificado cuando se detecta la finalización real de un viaje con su posible tarifa definitiva.
     */
    fun onTripCompleted(finalFareEur: Double?) {}
}
