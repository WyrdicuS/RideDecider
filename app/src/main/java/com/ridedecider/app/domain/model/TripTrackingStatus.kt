package com.ridedecider.app.domain.model

/**
 * Estado de seguimiento de un viaje en el sistema histórico.
 * Garantiza que únicamente los viajes completados computen para ganancias reales.
 */
enum class TripTrackingStatus {
    /** La oferta fue evaluada por el DecisionEngine. */
    EVALUATED,

    /** El conductor aceptó la oferta o fue asignada por Uber. */
    ACCEPTED_BY_DRIVER,

    /** El viaje fue efectivamente realizado y completado. */
    COMPLETED,

    /** El viaje fue cancelado por el pasajero. */
    CANCELLED_BY_RIDER,

    /** El viaje fue cancelado por el conductor. */
    CANCELLED_BY_DRIVER,

    /** El viaje fue cancelado por el sistema de Uber. */
    CANCELLED_BY_UBER,

    /** El viaje fue cancelado por no presentación del pasajero (No-Show). */
    CANCELLED_NO_SHOW,

    /** El viaje fue cancelado pero el motivo no pudo determinarse con suficiente evidencia. */
    CANCELLED_UNKNOWN
}
