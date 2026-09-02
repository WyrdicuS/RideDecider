package com.ridedecider.app.ui.overlay.mapper

import com.ridedecider.app.domain.model.Decision
import com.ridedecider.app.domain.model.DecisionReason
import com.ridedecider.app.domain.model.TripEvaluation
import com.ridedecider.app.domain.model.TripOfferType
import com.ridedecider.app.ui.overlay.model.HudUiModel
import com.ridedecider.app.ui.overlay.model.HudVisualTier
import java.util.Locale

/**
 * Mapper puro para transformar entidades [TripEvaluation] en [HudUiModel]
 * aplicando formato específico para España (coma decimal, símbolo de divisa al final)
 * y clasificación en 4 niveles visuales según rentabilidad:
 *
 * ◆ EXCELENTE (Púrpura)     -> Rentabilidad muy alta (>30% por encima de objetivos/umbrales)
 * ★ BUENO (Verde claro)     -> Cumple holgadamente todos los criterios del DecisionEngine (ACCEPT)
 * ○ ACEPTABLE (Naranja claro) -> Cumple los criterios básicos mínimos
 * ✕ MALO (Rojo oscuro)      -> Rechazado por rentabilidad insuficiente o inviable (REJECT / UNKNOWN)
 */
object HudUiModelMapper {

    private val SPANISH_LOCALE = Locale.forLanguageTag("es-ES")

    fun map(evaluation: TripEvaluation): HudUiModel {
        val trip = evaluation.trip
        val metrics = evaluation.metrics

        val tier = determineVisualTier(evaluation)

        val fareText = trip.rawFare?.let { formatCurrency(it, trip.currency) } ?: "N/A"
        val grossPerKmText = metrics?.grossPerKm?.let { "${formatDecimal(it)} €/km" } ?: "N/A"
        val grossPerHourText = metrics?.grossPerHour?.let { "${formatDecimal(it)} €/h" } ?: "N/A"

        val totalDist = metrics?.totalDistanceKm ?: trip.pickupDistanceKm?.let { p ->
            trip.tripDistanceKm?.let { t -> p + t }
        }
        val totalDistanceText = totalDist?.let { formatDistance(it) } ?: "N/A"

        val totalDur = metrics?.totalDurationMinutes ?: trip.pickupDurationMinutes?.let { p ->
            trip.tripDurationMinutes?.let { t -> p + t }
        }
        val totalDurationText = totalDur?.let { formatDuration(it) } ?: "N/A"

        val pickupSummary = if (trip.pickupDistanceKm != null && trip.pickupDurationMinutes != null) {
            "${formatDistance(trip.pickupDistanceKm)} (${formatDuration(trip.pickupDurationMinutes)})"
        } else if (trip.pickupDistanceKm != null) {
            formatDistance(trip.pickupDistanceKm)
        } else {
            "N/A"
        }

        val tripSummary = if (trip.tripDistanceKm != null && trip.tripDurationMinutes != null) {
            "${formatDistance(trip.tripDistanceKm)} (${formatDuration(trip.tripDurationMinutes)})"
        } else if (trip.tripDistanceKm != null) {
            formatDistance(trip.tripDistanceKm)
        } else {
            "N/A"
        }

        val formattedReasons = evaluation.reasons.map { mapReason(it) }
        val mainReasonText = formattedReasons.firstOrNull()

        val offerTypeText = when (trip.offerType) {
            TripOfferType.TRIP_OFFER -> "VIAJE DIRECTO"
            TripOfferType.RADAR_OFFER -> "TRIP RADAR"
        }

        val passengerRatingText = trip.passengerRating?.let { formatDecimal(it) }

        return HudUiModel(
            tier = tier,
            decision = evaluation.decision,
            decisionText = tier.label,
            fareText = fareText,
            grossPerKmText = grossPerKmText,
            grossPerHourText = grossPerHourText,
            totalDistanceText = totalDistanceText,
            totalDurationText = totalDurationText,
            pickupSummaryText = pickupSummary,
            tripSummaryText = tripSummary,
            mainReasonText = mainReasonText,
            reasons = formattedReasons,
            offerType = trip.offerType,
            offerTypeText = offerTypeText,
            isCashPayment = trip.isCashPayment,
            passengerRating = passengerRatingText
        )
    }

    fun determineVisualTier(evaluation: TripEvaluation): HudVisualTier {
        return when (evaluation.profitabilityLevel) {
            com.ridedecider.app.domain.model.TripProfitabilityLevel.EXCELLENT -> HudVisualTier.EXCELLENT
            com.ridedecider.app.domain.model.TripProfitabilityLevel.GOOD -> HudVisualTier.GOOD
            com.ridedecider.app.domain.model.TripProfitabilityLevel.ACCEPTABLE -> HudVisualTier.ACCEPTABLE
            com.ridedecider.app.domain.model.TripProfitabilityLevel.BAD -> HudVisualTier.BAD
        }
    }

    private fun formatDecimal(value: Double): String {
        return String.format(SPANISH_LOCALE, "%.2f", value)
    }

    private fun formatDistance(km: Double): String {
        return String.format(SPANISH_LOCALE, "%.1f km", km)
    }

    private fun formatDuration(minutes: Double): String {
        return String.format(SPANISH_LOCALE, "%d min", Math.round(minutes))
    }

    private fun formatCurrency(amount: Double, currency: String): String {
        val formatted = formatDecimal(amount)
        val symbol = when (currency.uppercase()) {
            "USD", "$" -> "$"
            else -> "€"
        }
        return "$formatted $symbol"
    }

    private fun mapReason(reason: DecisionReason): String {
        return when (reason) {
            DecisionReason.ACCEPT_HIGH_PROFITABILITY -> "Oferta altamente rentable"
            DecisionReason.REJECT_LOW_KM_RATE -> "Rentabilidad por km baja"
            DecisionReason.REJECT_LOW_HOURLY_RATE -> "Rentabilidad por hora baja"
            DecisionReason.REJECT_EXCESSIVE_PICKUP_DISTANCE -> "Recogida muy lejana"
            DecisionReason.REJECT_EXCESSIVE_PICKUP_TIME -> "Tiempo de recogida excesivo"
            DecisionReason.REJECT_LOW_EFFECTIVE_KM_RATE -> "Rentabilidad efectiva por km baja"
            DecisionReason.REJECT_LOW_NET_PROFIT -> "Beneficio neto insuficiente"
            DecisionReason.REJECT_LOW_NET_HOURLY_RATE -> "Tarifa horaria neta baja"
            DecisionReason.UNKNOWN_MISSING_FARE -> "Sin importe de tarifa"
            DecisionReason.UNKNOWN_MISSING_PICKUP_DISTANCE -> "Sin distancia de recogida"
            DecisionReason.UNKNOWN_MISSING_PICKUP_TIME -> "Sin tiempo de recogida"
            DecisionReason.UNKNOWN_MISSING_TRIP_DISTANCE -> "Sin distancia de viaje"
            DecisionReason.UNKNOWN_MISSING_TRIP_TIME -> "Sin tiempo de viaje"
            DecisionReason.UNKNOWN_INVALID_DATA -> "Datos incompletos o inconsistentes"
            DecisionReason.CONTEXT_BELOW_REQUIRED_HOURLY_RATE -> "Por debajo del ritmo necesario para el objetivo"
            DecisionReason.CONTEXT_ABOVE_REQUIRED_HOURLY_RATE -> "Por encima del ritmo necesario para el objetivo"
            DecisionReason.CONTEXT_DAILY_TARGET_REACHED -> "Objetivo diario del conductor alcanzado"
        }
    }
}
