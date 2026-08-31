package com.ridedecider.app.data.accessibility.uber

/**
 * Razones estructuradas de fallo al mapear un [RawUberTripOffer] a una entidad de dominio.
 */
enum class TripMappingFailureReason {
    MISSING_FARE,
    MISSING_CURRENCY,
    MISSING_PICKUP_DISTANCE,
    MISSING_PICKUP_DURATION,
    MISSING_TRIP_DISTANCE,
    MISSING_TRIP_DURATION,
    MISSING_OFFER_TYPE,
    INVALID_DATA
}
