package com.ridedecider.app.data.accessibility.uber

/**
 * Razones estructuradas generadas durante la validación y clasificación de un [RawUberTripOffer].
 */
enum class UberValidationReason {
    MISSING_OFFER_TYPE,
    MISSING_FARE,
    MISSING_PICKUP_DISTANCE,
    MISSING_PICKUP_DURATION,
    MISSING_TRIP_DISTANCE,
    MISSING_TRIP_DURATION,
    INVALID_FARE,
    INVALID_DISTANCE,
    INVALID_DURATION,
    INCOMPLETE_OFFER,
    VALID_TRIP_OFFER,
    VALID_RADAR_OFFER,
    AMBIGUOUS_OFFER
}
