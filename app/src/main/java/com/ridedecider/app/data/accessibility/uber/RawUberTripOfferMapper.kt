package com.ridedecider.app.data.accessibility.uber

import com.ridedecider.app.domain.model.Trip
import com.ridedecider.app.domain.model.TripOfferType
import com.ridedecider.app.domain.model.UberCategory

/**
 * Mapper responsable de transformar un [RawUberTripOffer] validado
 * en una entidad de dominio inmutable [Trip].
 *
 * Clase pura de Kotlin independiente de Android, UI y lógica económica.
 */
class RawUberTripOfferMapper {

    /**
     * Mapea un [RawUberTripOffer] a [TripMappingResult].
     *
     * @param rawOffer Oferta en bruto capturada de la interfaz de Uber.
     * @return [TripMappingResult.Success] si los datos requeridos están presentes,
     * o [TripMappingResult.Failure] con la razón específica si falta algún dato crítico.
     */
    fun mapToDomain(rawOffer: RawUberTripOffer): TripMappingResult {
        if (rawOffer.rawFare == null) {
            return TripMappingResult.Failure(TripMappingFailureReason.MISSING_FARE)
        }
        if (rawOffer.currency.isNullOrBlank()) {
            return TripMappingResult.Failure(TripMappingFailureReason.MISSING_CURRENCY)
        }
        if (rawOffer.pickupDistanceKm == null) {
            return TripMappingResult.Failure(TripMappingFailureReason.MISSING_PICKUP_DISTANCE)
        }
        if (rawOffer.pickupDurationMinutes == null) {
            return TripMappingResult.Failure(TripMappingFailureReason.MISSING_PICKUP_DURATION)
        }
        if (rawOffer.tripDistanceKm == null) {
            return TripMappingResult.Failure(TripMappingFailureReason.MISSING_TRIP_DISTANCE)
        }
        if (rawOffer.tripDurationMinutes == null) {
            return TripMappingResult.Failure(TripMappingFailureReason.MISSING_TRIP_DURATION)
        }

        val offerType = when (rawOffer.detectedOfferType) {
            UberOfferScreenType.TRIP_OFFER -> TripOfferType.TRIP_OFFER
            UberOfferScreenType.RADAR_OFFER -> TripOfferType.RADAR_OFFER
            else -> return TripMappingResult.Failure(TripMappingFailureReason.MISSING_OFFER_TYPE)
        }

        val category = parseCategory(rawOffer.category)
        val id = generateDeterministicId(rawOffer, offerType)

        val trip = Trip(
            id = id,
            timestamp = rawOffer.sourceTimestamp,
            offerType = offerType,
            category = category,
            rawFare = rawOffer.rawFare,
            currency = rawOffer.currency,
            pickupDistanceKm = rawOffer.pickupDistanceKm,
            pickupDurationMinutes = rawOffer.pickupDurationMinutes,
            pickupAddress = rawOffer.pickupAddress,
            tripDistanceKm = rawOffer.tripDistanceKm,
            tripDurationMinutes = rawOffer.tripDurationMinutes,
            dropoffAddress = rawOffer.dropoffAddress,
            isCashPayment = rawOffer.isCashPayment,
            passengerRating = rawOffer.passengerRating
        )

        return TripMappingResult.Success(trip)
    }

    private fun parseCategory(rawCategory: String?): UberCategory {
        if (rawCategory.isNullOrBlank()) return UberCategory.UNKNOWN
        val normalized = rawCategory.trim().uppercase()
        return when {
            normalized.contains("COMFORT") -> UberCategory.COMFORT
            normalized.contains("BLACK") -> UberCategory.BLACK
            normalized.contains("GREEN") -> UberCategory.GREEN
            normalized.contains("VAN") -> UberCategory.VAN
            normalized.contains("PACKAGE") || normalized.contains("CONNECT") -> UberCategory.PACKAGE
            normalized.contains("UBERX") || normalized.contains("UBER_X") || normalized == "X" -> UberCategory.UBER_X
            else -> UberCategory.UNKNOWN
        }
    }

    /**
     * Genera un identificador determinista único e invariable para la oferta
     * basado en sus propiedades físicas y direcciones, evitando duplicados por eventos continuos de accesibilidad.
     */
    private fun generateDeterministicId(rawOffer: RawUberTripOffer, offerType: TripOfferType): String {
        val pickup = rawOffer.pickupAddress?.trim()?.lowercase() ?: ""
        val dropoff = rawOffer.dropoffAddress?.trim()?.lowercase() ?: ""
        val signature = "${offerType.name}_${rawOffer.rawFare}_${rawOffer.pickupDistanceKm}_${rawOffer.tripDistanceKm}_${rawOffer.tripDurationMinutes}_${pickup}_${dropoff}"
        return "uber_${Math.abs(signature.hashCode())}"
    }
}
