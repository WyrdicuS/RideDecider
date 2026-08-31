package com.ridedecider.app.domain.model

/**
 * Representa el tipo de oferta mostrada en la aplicación de Uber.
 */
enum class TripOfferType {
    /**
     * Oferta exclusiva asignada directamente al conductor ("Aceptar").
     */
    TRIP_OFFER,

    /**
     * Oferta compartida de radar de viajes ("Emparejar").
     */
    RADAR_OFFER
}
