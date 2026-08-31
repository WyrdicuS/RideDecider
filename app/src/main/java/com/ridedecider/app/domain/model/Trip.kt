package com.ridedecider.app.domain.model

/**
 * Representa una oferta de viaje de Uber ya extraída y validada.
 *
 * Contiene únicamente los datos en bruto de la oferta; las métricas derivadas
 * y de rentabilidad son calculadas exclusivamente por el DecisionEngine.
 */
data class Trip(
    val id: String,
    val timestamp: Long,
    val offerType: TripOfferType,
    val category: UberCategory,
    val rawFare: Double?,
    val currency: String,
    val pickupDistanceKm: Double?,
    val pickupDurationMinutes: Double?,
    val pickupAddress: String?,
    val tripDistanceKm: Double?,
    val tripDurationMinutes: Double?,
    val dropoffAddress: String?,
    val isCashPayment: Boolean = false,
    val passengerRating: Double? = null
)

