package com.ridedecider.app.domain.model

/**
 * Recomendación final generada por el DecisionEngine para el conductor.
 */
enum class Decision {
    /**
     * La oferta cumple o supera todos los umbrales de rentabilidad y límites operativos.
     */
    ACCEPT,

    /**
     * La oferta incumple uno o más criterios de rentabilidad o límites operativos.
     */
    REJECT,

    /**
     * Faltan datos críticos o los datos extraídos son inválidos/inconsistentes.
     */
    UNKNOWN
}
