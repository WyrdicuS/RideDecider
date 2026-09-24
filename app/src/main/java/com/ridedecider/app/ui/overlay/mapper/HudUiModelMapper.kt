package com.ridedecider.app.ui.overlay.mapper

import com.ridedecider.app.domain.model.Decision
import com.ridedecider.app.domain.model.DecisionMode
import com.ridedecider.app.domain.model.DecisionReason
import com.ridedecider.app.domain.model.GoalContextMetrics
import com.ridedecider.app.domain.model.ProgressStatus
import com.ridedecider.app.domain.model.TripEvaluation
import com.ridedecider.app.domain.model.TripOfferType
import com.ridedecider.app.domain.model.opportunity.Confidence
import com.ridedecider.app.domain.model.opportunity.OpportunityAssessment
import com.ridedecider.app.domain.model.opportunity.OpportunityQuality
import com.ridedecider.app.domain.model.opportunity.Recommendation
import com.ridedecider.app.ui.overlay.model.HudUiModel
import com.ridedecider.app.ui.overlay.model.HudVisualTier
import java.util.Locale

/**
 * Mapper puro para transformar [TripEvaluation] (+ opcionalmente [OpportunityAssessment])
 * en [HudUiModel] segun el [DecisionMode] activo.
 *
 * MANUAL: profitabilityLevel/HudVisualTier es la semantica primaria (comportamiento previo a R5,
 * sin cambios). GoalContext se muestra como contexto separado. OpportunityAssessment es
 * enriquecimiento opcional, nunca reemplaza profitabilityLevel.
 *
 * AUTOMATIC: OpportunityAssessment (Recommendation/Quality/Confidence) es la semantica primaria.
 * GoalContext nunca se consulta ni se muestra en este modo.
 */
object HudUiModelMapper {

    private val SPANISH_LOCALE = Locale.forLanguageTag("es-ES")

    fun map(
        evaluation: TripEvaluation,
        assessment: OpportunityAssessment? = null,
        mode: DecisionMode = DecisionMode.MANUAL
    ): HudUiModel {
        val trip = evaluation.trip
        val metrics = evaluation.metrics

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

        val shared = SharedFields(
            fareText = fareText,
            grossPerKmText = grossPerKmText,
            grossPerHourText = grossPerHourText,
            totalDistanceText = totalDistanceText,
            totalDurationText = totalDurationText,
            pickupSummary = pickupSummary,
            tripSummary = tripSummary,
            formattedReasons = formattedReasons,
            mainReasonText = mainReasonText,
            offerTypeText = offerTypeText,
            passengerRatingText = passengerRatingText
        )

        return when (mode) {
            DecisionMode.MANUAL -> buildManualModel(evaluation, shared)
            DecisionMode.AUTOMATIC -> buildAutomaticModel(evaluation, assessment, shared)
        }
    }

    private data class SharedFields(
        val fareText: String,
        val grossPerKmText: String,
        val grossPerHourText: String,
        val totalDistanceText: String,
        val totalDurationText: String,
        val pickupSummary: String,
        val tripSummary: String,
        val formattedReasons: List<String>,
        val mainReasonText: String?,
        val offerTypeText: String,
        val passengerRatingText: String?
    )

    private fun buildManualModel(evaluation: TripEvaluation, shared: SharedFields): HudUiModel {
        val trip = evaluation.trip
        val tier = determineVisualTier(evaluation)

        val goalContext = evaluation.goalContext
        val goalPaceText = formatGoalPace(goalContext)
        val goalContributionText = formatGoalContribution(goalContext)
        val goalStatusText = formatGoalStatus(goalContext)

        return HudUiModel(
            tier = tier,
            decision = evaluation.decision,
            decisionText = tier.label,
            fareText = shared.fareText,
            grossPerKmText = shared.grossPerKmText,
            grossPerHourText = shared.grossPerHourText,
            totalDistanceText = shared.totalDistanceText,
            totalDurationText = shared.totalDurationText,
            pickupSummaryText = shared.pickupSummary,
            tripSummaryText = shared.tripSummary,
            mainReasonText = shared.mainReasonText,
            reasons = shared.formattedReasons,
            offerType = trip.offerType,
            offerTypeText = shared.offerTypeText,
            isCashPayment = trip.isCashPayment,
            passengerRating = shared.passengerRatingText,
            goalPaceText = goalPaceText,
            goalContributionText = goalContributionText,
            goalStatusText = goalStatusText,
            decisionMode = DecisionMode.MANUAL
        )
    }

    private fun buildAutomaticModel(
        evaluation: TripEvaluation,
        assessment: OpportunityAssessment?,
        shared: SharedFields
    ): HudUiModel {
        val trip = evaluation.trip
        val tier = determineAutomaticTier(evaluation, assessment)
        val recommendationText = determineRecommendationText(evaluation, assessment)
        val qualityText = assessment?.quality?.let { formatQuality(it) }
        val confidenceText = assessment?.confidence?.let { formatConfidence(it) }
        val isOverride = assessment?.speOverridden ?: false
        val overrideText = if (isOverride) assessment?.overrideJustification else null

        return HudUiModel(
            tier = tier,
            decision = evaluation.decision,
            decisionText = recommendationText ?: tier.label,
            fareText = shared.fareText,
            grossPerKmText = shared.grossPerKmText,
            grossPerHourText = shared.grossPerHourText,
            totalDistanceText = shared.totalDistanceText,
            totalDurationText = shared.totalDurationText,
            pickupSummaryText = shared.pickupSummary,
            tripSummaryText = shared.tripSummary,
            mainReasonText = shared.mainReasonText,
            reasons = shared.formattedReasons,
            offerType = trip.offerType,
            offerTypeText = shared.offerTypeText,
            isCashPayment = trip.isCashPayment,
            passengerRating = shared.passengerRatingText,
            goalPaceText = null,
            goalContributionText = null,
            goalStatusText = null,
            decisionMode = DecisionMode.AUTOMATIC,
            recommendationText = recommendationText,
            qualityText = qualityText,
            confidenceText = confidenceText,
            isOverride = isOverride,
            overrideText = overrideText
        )
    }

    /**
     * Texto de accion principal para AUTOMATIC. Fallback conservador cuando assessment == null:
     * nunca se presenta como TAKE, nunca se inventa Quality/Confidence.
     */
    private fun determineRecommendationText(
        evaluation: TripEvaluation,
        assessment: OpportunityAssessment?
    ): String {
        if (assessment == null) {
            return when (evaluation.decision) {
                Decision.ACCEPT -> "OPORTUNIDAD DETECTADA"
                Decision.REJECT -> "PASAR"
                Decision.UNKNOWN -> "SIN DATOS SUFICIENTES"
            }
        }

        if (assessment.speDecision == Decision.UNKNOWN) {
            return "SIN DATOS SUFICIENTES"
        }

        val base = when (assessment.recommendation) {
            Recommendation.TAKE -> when (assessment.quality) {
                OpportunityQuality.EXCEPTIONAL -> "OPORTUNIDAD EXCEPCIONAL"
                else -> "BUENA OPORTUNIDAD"
            }
            Recommendation.EVALUATE -> "VALORAR"
            Recommendation.SKIP -> "PASAR"
        }

        return if (assessment.confidence == Confidence.LOW) {
            "$base — datos limitados"
        } else {
            base
        }
    }

    private fun formatQuality(quality: OpportunityQuality): String = when (quality) {
        OpportunityQuality.EXCEPTIONAL -> "EXCEPCIONAL"
        OpportunityQuality.GOOD -> "BUENA"
        OpportunityQuality.MARGINAL -> "MARGINAL"
        OpportunityQuality.POOR -> "POBRE"
    }

    private fun formatConfidence(confidence: Confidence): String? = when (confidence) {
        Confidence.HIGH -> null
        Confidence.MEDIUM -> "Confianza media"
        Confidence.LOW -> "Confianza baja"
    }

    /**
     * Tier visual para AUTOMATIC: deriva de OpportunityQuality (nunca de profitabilityLevel).
     * Fallback conservador cuando assessment == null.
     */
    private fun determineAutomaticTier(
        evaluation: TripEvaluation,
        assessment: OpportunityAssessment?
    ): HudVisualTier {
        if (assessment == null) {
            return when (evaluation.decision) {
                Decision.ACCEPT -> HudVisualTier.ACCEPTABLE
                Decision.REJECT -> HudVisualTier.BAD
                Decision.UNKNOWN -> HudVisualTier.BAD
            }
        }
        return when (assessment.quality) {
            OpportunityQuality.EXCEPTIONAL -> HudVisualTier.EXCELLENT
            OpportunityQuality.GOOD -> HudVisualTier.GOOD
            OpportunityQuality.MARGINAL -> HudVisualTier.ACCEPTABLE
            OpportunityQuality.POOR -> HudVisualTier.BAD
        }
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

    private fun formatGoalPace(ctx: GoalContextMetrics?): String? {
        val ratio = ctx?.targetPaceRatio ?: return null
        val pct = String.format(SPANISH_LOCALE, "%.0f", ratio * 100)
        return "$pct% ritmo"
    }

    private fun formatGoalContribution(ctx: GoalContextMetrics?): String? {
        val contribution = ctx?.estimatedGoalContribution ?: return null
        val pct = String.format(SPANISH_LOCALE, "%.1f", contribution * 100)
        return "$pct% del restante"
    }

    private fun formatGoalStatus(ctx: GoalContextMetrics?): String? {
        ctx ?: return null
        return when (ctx.progressStatus) {
            ProgressStatus.TARGET_REACHED -> "Objetivo alcanzado"
            ProgressStatus.AHEAD -> "Adelantado"
            ProgressStatus.ON_TRACK -> "En ritmo"
            ProgressStatus.BEHIND -> "Retrasado"
        }
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
