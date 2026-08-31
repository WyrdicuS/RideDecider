package com.ridedecider.app.data.accessibility.uber

/**
 * Clasificación contextual del tipo de pantalla o estado detectado en la aplicación de Uber.
 */
enum class UberOfferScreenType {
    /**
     * Pantalla normal de Uber sin ninguna oferta activa (mapa libre, espera).
     */
    NO_OFFER,

    /**
     * Oferta exclusiva asignada al conductor ("Aceptar").
     */
    TRIP_OFFER,

    /**
     * Oferta compartida de radar de viajes ("Emparejar" / "Match").
     */
    RADAR_OFFER,

    /**
     * Pantalla de viaje en curso o navegación activa tras haber aceptado un viaje.
     */
    ACTIVE_TRIP,

    /**
     * Pantalla o notificación explícita de cancelación por el pasajero o Uber (ej. "El viaje fue cancelado por el pasajero").
     */
    TRIP_CANCELLED,

    /**
     * Pantalla o recibo de finalización de viaje (ej. "Viaje completado", "Has ganado", "Resumen del viaje").
     */
    TRIP_COMPLETED,

    /**
     * Estado no determinado o datos visuales insuficientes para clasificar la pantalla.
     */
    UNKNOWN
}
