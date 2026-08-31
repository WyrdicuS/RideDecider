package com.ridedecider.app.domain.model

/**
 * Motivo clasificado de la cancelación de un viaje asignado o en curso.
 */
enum class CancellationReason {
    /** Cancelado por el pasajero / usuario. */
    RIDER,

    /** Cancelado por el conductor. */
    DRIVER,

    /** Cancelado por el sistema de Uber (ej. reasignación o problemas de pago). */
    UBER,

    /** Cancelado por no presentación del pasajero tras el tiempo de espera legal (No-Show). */
    NO_SHOW,

    /** Cancelación detectada pero con motivo o responsable indeterminado. */
    UNKNOWN
}
