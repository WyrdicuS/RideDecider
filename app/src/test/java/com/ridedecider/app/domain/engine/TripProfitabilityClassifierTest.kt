package com.ridedecider.app.domain.engine

import com.ridedecider.app.domain.model.Decision
import com.ridedecider.app.domain.model.EvaluationMetrics
import com.ridedecider.app.domain.model.ProfitabilityConfig
import com.ridedecider.app.domain.model.TripProfitabilityLevel
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TripProfitabilityClassifierTest {

    private lateinit var classifier: TripProfitabilityClassifier
    private lateinit var config: ProfitabilityConfig

    @Before
    fun setUp() {
        classifier = TripProfitabilityClassifier()
        config = ProfitabilityConfig(
            costPerKm = 0.20,
            costPerHour = 2.0,
            minGrossPerKmRate = 1.0,
            minGrossHourlyRate = 20.0,
            minNetHourlyRate = 15.0,
            minNetTripProfit = 2.0,
            maxPickupDistanceKm = 4.0,
            maxPickupTimeMinutes = 8.0
        )
    }

    @Test
    fun classify_nullMetricsOrUnknown_shouldReturnBad() {
        val result = classifier.classify(
            metrics = null,
            config = config,
            decision = Decision.UNKNOWN,
            pickupDistanceKm = 2.0
        )
        assertEquals(TripProfitabilityLevel.BAD, result)
    }

    @Test
    fun classify_rejectedWithPoorRates_shouldReturnBad() {
        val metrics = EvaluationMetrics(
            totalDistanceKm = 10.0,
            totalDurationMinutes = 20.0,
            estimatedOperatingCost = 2.67,
            grossProfit = 5.0,
            netProfit = 2.33,
            grossPerKm = 0.50, // Muy bajo vs 1.0 min
            grossPerHour = 15.0, // Muy bajo vs 20.0 min
            netPerHour = 7.0
        )

        val result = classifier.classify(
            metrics = metrics,
            config = config,
            decision = Decision.REJECT,
            pickupDistanceKm = 2.0
        )
        assertEquals(TripProfitabilityLevel.BAD, result)
    }

    @Test
    fun classify_rejectedMarginalPickup_withHighHourlyPay_shouldReturnAcceptable() {
        // Rechazado solo porque la recogida fue 4.5 km (max 4.0), pero paga 35 €/h y beneficio neto positivo
        val metrics = EvaluationMetrics(
            totalDistanceKm = 10.0,
            totalDurationMinutes = 20.0,
            estimatedOperatingCost = 2.67,
            grossProfit = 12.0,
            netProfit = 9.33,
            grossPerKm = 1.20,
            grossPerHour = 36.0,
            netPerHour = 28.0
        )

        val result = classifier.classify(
            metrics = metrics,
            config = config,
            decision = Decision.REJECT,
            pickupDistanceKm = 4.5
        )
        assertEquals(TripProfitabilityLevel.ACCEPTABLE, result)
    }

    @Test
    fun classify_acceptedStandard_shouldReturnGood() {
        val metrics = EvaluationMetrics(
            totalDistanceKm = 8.0,
            totalDurationMinutes = 24.0,
            estimatedOperatingCost = 2.40,
            grossProfit = 8.50,
            netProfit = 6.10,
            grossPerKm = 1.06, // Cumple umbral 1.0
            grossPerHour = 21.25, // Cumple umbral 20.0
            netPerHour = 15.25
        )

        val result = classifier.classify(
            metrics = metrics,
            config = config,
            decision = Decision.ACCEPT,
            pickupDistanceKm = 2.5
        )
        assertEquals(TripProfitabilityLevel.GOOD, result)
    }

    @Test
    fun classify_acceptedHighPureProfitability_shouldReturnExcellent() {
        val metrics = EvaluationMetrics(
            totalDistanceKm = 6.0,
            totalDurationMinutes = 15.0,
            estimatedOperatingCost = 1.70,
            grossProfit = 12.0,
            netProfit = 10.30,
            grossPerKm = 2.00, // 200% del umbral de 1.0 €/km
            grossPerHour = 48.0, // 240% del umbral de 20 €/h
            netPerHour = 41.20
        )

        val result = classifier.classify(
            metrics = metrics,
            config = config,
            decision = Decision.ACCEPT,
            pickupDistanceKm = 1.0 // Recogida muy corta y eficiente (1.0 / 4.0 = 0.25 <= 0.85)
        )
        assertEquals(TripProfitabilityLevel.EXCELLENT, result)
    }

    @Test
    fun classify_sameOffer_isIndependentOfEconomicContext_shouldReturnGood() {
        // R4.10: misma oferta del antiguo caso de contextual boosting. Sin economicContext,
        // classify() ya no acepta ese parámetro: el resultado debe ser el nivel intrínseco (GOOD),
        // no EXCELLENT, sin importar los objetivos o el progreso del conductor.
        val metrics = EvaluationMetrics(
            totalDistanceKm = 10.0,
            totalDurationMinutes = 20.0,
            estimatedOperatingCost = 2.67,
            grossProfit = 11.50,
            netProfit = 8.83,
            grossPerKm = 1.15,
            grossPerHour = 34.50,
            netPerHour = 26.50
        )

        val result = classifier.classify(
            metrics = metrics,
            config = config,
            decision = Decision.ACCEPT,
            pickupDistanceKm = 2.0
        )
        assertEquals(TripProfitabilityLevel.GOOD, result)
    }

    @Test
    fun classify_acceptedWithTightMargin_shouldReturnAcceptable() {
        val metrics = EvaluationMetrics(
            totalDistanceKm = 10.0,
            totalDurationMinutes = 30.0,
            estimatedOperatingCost = 3.00,
            grossProfit = 10.0,
            netProfit = 7.00,
            grossPerKm = 1.00,
            grossPerHour = 20.00,
            netPerHour = 14.00 // Ligeramente por debajo del net hourly 15.0
        )

        val result = classifier.classify(
            metrics = metrics,
            config = config,
            decision = Decision.ACCEPT,
            pickupDistanceKm = 3.8
        )
        assertEquals(TripProfitabilityLevel.ACCEPTABLE, result)
    }
}
