package com.ridedecider.app.domain.model

/**
 * Estado comparativo del progreso del conductor frente al objetivo y ritmo necesario.
 */
enum class ProgressStatus {
    /** El objetivo del período ya ha sido alcanzado o superado (>= 100%). */
    TARGET_REACHED,

    /** El conductor va por delante del ritmo requerido para cumplir el objetivo. */
    AHEAD,

    /** El conductor se encuentra dentro del rango de ritmo esperado (±10%). */
    ON_TRACK,

    /** El conductor va por detrás del ritmo requerido o necesita acelerar. */
    BEHIND
}
