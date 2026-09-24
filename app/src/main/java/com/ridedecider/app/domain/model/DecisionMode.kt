package com.ridedecider.app.domain.model

/**
 * Modo de presentacion y decision del HUD.
 *
 * MANUAL: optimizacion respecto al objetivo personal del conductor (Goals).
 * AUTOMATIC: evaluacion de la oportunidad economica intrinseca, independiente de Goals.
 */
enum class DecisionMode {
    MANUAL,
    AUTOMATIC
}
