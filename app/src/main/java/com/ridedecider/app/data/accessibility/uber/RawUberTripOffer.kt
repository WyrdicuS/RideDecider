package com.ridedecider.app.data.accessibility.uber

import java.util.UUID

/**
 * Representa los datos en bruto extraídos directamente de la interfaz de usuario de Uber
 * mediante el servicio de accesibilidad, antes de la validación contextual y del mapeo
 * al modelo de dominio [com.ridedecider.app.domain.model.Trip].
 *
 * Permite datos incompletos o nulos correspondientes a campos aún no detectados en pantalla.
 */
data class RawUberTripOffer(
    val rawFare: Double? = null,
    val currency: String? = null,
    val pickupDistanceKm: Double? = null,
    val pickupDurationMinutes: Double? = null,
    val tripDistanceKm: Double? = null,
    val tripDurationMinutes: Double? = null,
    val pickupAddress: String? = null,
    val dropoffAddress: String? = null,
    val category: String? = null,
    val isCashPayment: Boolean = false,
    val passengerRating: Double? = null,
    val detectedOfferType: UberOfferScreenType? = null,
    val cancellationFee: Double? = null,
    val cancellationReasonText: String? = null,
    val cancellationReason: com.ridedecider.app.domain.model.CancellationReason? = null,
    val finalEarningsEur: Double? = null,
    val sourceTimestamp: Long = System.currentTimeMillis(),
    val kinematicsSource: KinematicsSource = KinematicsSource.LEGACY_UNSPECIFIED,
    val instanceId: String = UUID.randomUUID().toString()
)

