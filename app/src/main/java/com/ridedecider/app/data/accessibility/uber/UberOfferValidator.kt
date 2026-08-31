package com.ridedecider.app.data.accessibility.uber

/**
 * Validador y clasificador determinista de ofertas capturadas de Uber ([RawUberTripOffer]).
 *
 * Clase pura de Kotlin independiente del framework de Android y de cálculos económicos.
 * Su responsabilidad es asegurar que una oferta capturada sea estructuralmente completa,
 * físicamente coherente y contextualmente válida antes de ser mapeada a dominio.
 */
class UberOfferValidator {

    companion object {
        const val MIN_FARE = 0.50
        const val MAX_FARE = 500.00
        const val MIN_DISTANCE_KM = 0.00
        const val MAX_DISTANCE_KM = 300.00
        const val MIN_DURATION_MINUTES = 0.00
        const val MAX_DURATION_MINUTES = 360.00
    }

    /**
     * Valida y clasifica un [RawUberTripOffer].
     *
     * @param rawOffer Datos sin procesar capturados de la interfaz de Uber.
     * @return [UberOfferValidationResult] con la clasificación y razones estructuradas.
     */
    fun validate(rawOffer: RawUberTripOffer): UberOfferValidationResult {
        // 1. Detección explícita de pantalla sin oferta o de navegación activa
        if (rawOffer.detectedOfferType == UberOfferScreenType.NO_OFFER) {
            return UberOfferValidationResult(
                screenType = UberOfferScreenType.NO_OFFER,
                isValidOffer = false,
                reasons = listOf(UberValidationReason.MISSING_OFFER_TYPE)
            )
        }

        if (rawOffer.detectedOfferType == UberOfferScreenType.ACTIVE_TRIP) {
            return UberOfferValidationResult(
                screenType = UberOfferScreenType.ACTIVE_TRIP,
                isValidOffer = false,
                reasons = emptyList()
            )
        }

        if (rawOffer.detectedOfferType == UberOfferScreenType.TRIP_CANCELLED) {
            return UberOfferValidationResult(
                screenType = UberOfferScreenType.TRIP_CANCELLED,
                isValidOffer = false,
                reasons = emptyList()
            )
        }

        if (rawOffer.detectedOfferType == UberOfferScreenType.TRIP_COMPLETED) {
            return UberOfferValidationResult(
                screenType = UberOfferScreenType.TRIP_COMPLETED,
                isValidOffer = false,
                reasons = emptyList()
            )
        }

        // 2. Comprobar si son datos aislados (ej. únicamente un número en pantalla sin tipo de oferta ni estructura)
        val hasAnyKinematicData = rawOffer.pickupDistanceKm != null ||
                rawOffer.pickupDurationMinutes != null ||
                rawOffer.tripDistanceKm != null ||
                rawOffer.tripDurationMinutes != null

        if (rawOffer.detectedOfferType == null && !hasAnyKinematicData) {
            return UberOfferValidationResult(
                screenType = UberOfferScreenType.NO_OFFER,
                isValidOffer = false,
                reasons = listOf(
                    UberValidationReason.MISSING_OFFER_TYPE,
                    UberValidationReason.INCOMPLETE_OFFER
                )
            )
        }

        // 3. Validación de presencia de campos obligatorios
        val missingReasons = mutableListOf<UberValidationReason>()

        if (rawOffer.detectedOfferType == null || rawOffer.detectedOfferType == UberOfferScreenType.UNKNOWN) {
            missingReasons.add(UberValidationReason.MISSING_OFFER_TYPE)
        }
        if (rawOffer.rawFare == null) {
            missingReasons.add(UberValidationReason.MISSING_FARE)
        }
        if (rawOffer.pickupDistanceKm == null) {
            missingReasons.add(UberValidationReason.MISSING_PICKUP_DISTANCE)
        }
        if (rawOffer.pickupDurationMinutes == null) {
            missingReasons.add(UberValidationReason.MISSING_PICKUP_DURATION)
        }
        if (rawOffer.tripDistanceKm == null) {
            missingReasons.add(UberValidationReason.MISSING_TRIP_DISTANCE)
        }
        if (rawOffer.tripDurationMinutes == null) {
            missingReasons.add(UberValidationReason.MISSING_TRIP_DURATION)
        }

        if (missingReasons.isNotEmpty()) {
            return UberOfferValidationResult(
                screenType = UberOfferScreenType.UNKNOWN,
                isValidOffer = false,
                reasons = listOf(UberValidationReason.INCOMPLETE_OFFER) + missingReasons
            )
        }

        // En este punto, todos los campos obligatorios son no nulos
        val fare = rawOffer.rawFare!!
        val pickupDistance = rawOffer.pickupDistanceKm!!
        val pickupDuration = rawOffer.pickupDurationMinutes!!
        val tripDistance = rawOffer.tripDistanceKm!!
        val tripDuration = rawOffer.tripDurationMinutes!!

        // 4. Validaciones de límites numéricos y coherencia física
        val invalidReasons = mutableListOf<UberValidationReason>()

        if (fare.isNaN() || fare < MIN_FARE || fare > MAX_FARE) {
            invalidReasons.add(UberValidationReason.INVALID_FARE)
        }

        if (pickupDistance.isNaN() || pickupDistance < MIN_DISTANCE_KM || pickupDistance > MAX_DISTANCE_KM) {
            invalidReasons.add(UberValidationReason.INVALID_DISTANCE)
        }

        if (tripDistance.isNaN() || tripDistance < MIN_DISTANCE_KM || tripDistance > MAX_DISTANCE_KM) {
            invalidReasons.add(UberValidationReason.INVALID_DISTANCE)
        }

        if (pickupDuration.isNaN() || pickupDuration < MIN_DURATION_MINUTES || pickupDuration > MAX_DURATION_MINUTES) {
            invalidReasons.add(UberValidationReason.INVALID_DURATION)
        }

        if (tripDuration.isNaN() || tripDuration < MIN_DURATION_MINUTES || tripDuration > MAX_DURATION_MINUTES) {
            invalidReasons.add(UberValidationReason.INVALID_DURATION)
        }

        if (invalidReasons.isNotEmpty()) {
            return UberOfferValidationResult(
                screenType = UberOfferScreenType.UNKNOWN,
                isValidOffer = false,
                reasons = invalidReasons
            )
        }

        val positiveReason = when (rawOffer.detectedOfferType) {
            UberOfferScreenType.RADAR_OFFER -> listOf(UberValidationReason.VALID_RADAR_OFFER)
            UberOfferScreenType.TRIP_OFFER -> listOf(UberValidationReason.VALID_TRIP_OFFER)
            else -> emptyList()
        }

        // 5. Oferta válida y consistente
        return UberOfferValidationResult(
            screenType = rawOffer.detectedOfferType ?: UberOfferScreenType.UNKNOWN,
            isValidOffer = true,
            reasons = positiveReason
        )
    }
}
