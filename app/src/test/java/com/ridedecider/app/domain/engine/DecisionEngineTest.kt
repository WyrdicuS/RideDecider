package com.ridedecider.app.domain.engine

import com.ridedecider.app.domain.model.Decision
import com.ridedecider.app.domain.model.DecisionReason
import com.ridedecider.app.domain.model.ProfitabilityConfig
import com.ridedecider.app.domain.model.Trip
import com.ridedecider.app.domain.model.TripOfferType
import com.ridedecider.app.domain.model.UberCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios para [DecisionEngine].
 *
 * Verifica la lógica matemática, los filtros de descarte, las validaciones
 * de datos incompletos o inválidos y la inmutabilidad de los modelos de dominio.
 */
class DecisionEngineTest {

    private lateinit var engine: DecisionEngine
    private lateinit var defaultConfig: ProfitabilityConfig

    private val delta = 0.0001

    @Before
    fun setUp() {
        engine = DecisionEngine()
        defaultConfig = ProfitabilityConfig(
            costPerKm = 0.20,
            costPerHour = 4.0,
            minGrossHourlyRate = 20.0,
            minGrossPerKmRate = 1.20,
            minNetTripProfit = 2.0,
            minNetHourlyRate = 15.0,
            maxPickupDistanceKm = 4.0,
            maxPickupTimeMinutes = 10.0
        )
    }

    private fun createSampleTrip(
        id: String = "trip-123",
        timestamp: Long = 1700000000000L,
        offerType: TripOfferType = TripOfferType.TRIP_OFFER,
        category: UberCategory = UberCategory.UBER_X,
        rawFare: Double? = 15.0,
        currency: String = "EUR",
        pickupDistanceKm: Double? = 2.0,
        pickupDurationMinutes: Double? = 4.0,
        pickupAddress: String? = "Calle Gran Vía 1",
        tripDistanceKm: Double? = 3.0,
        tripDurationMinutes: Double? = 8.0,
        dropoffAddress: String? = "Paseo de la Castellana 50"
    ): Trip {
        return Trip(
            id = id,
            timestamp = timestamp,
            offerType = offerType,
            category = category,
            rawFare = rawFare,
            currency = currency,
            pickupDistanceKm = pickupDistanceKm,
            pickupDurationMinutes = pickupDurationMinutes,
            pickupAddress = pickupAddress,
            tripDistanceKm = tripDistanceKm,
            tripDurationMinutes = tripDurationMinutes,
            dropoffAddress = dropoffAddress
        )
    }

    // =========================================================================
    // 1. VIAJE RENTABLE
    // =========================================================================
    @Test
    fun profitableTrip_shouldCalculateCorrectMetricsAndReturnAccept() {
        val trip = createSampleTrip(
            rawFare = 15.0,
            pickupDistanceKm = 2.0,
            pickupDurationMinutes = 4.0,
            tripDistanceKm = 3.0,
            tripDurationMinutes = 8.0
        )

        val evaluation = engine.evaluate(trip, defaultConfig)

        assertEquals(Decision.ACCEPT, evaluation.decision)
        assertTrue(evaluation.reasons.contains(DecisionReason.ACCEPT_HIGH_PROFITABILITY))

        val metrics = evaluation.metrics
        assertNotNull(metrics)
        assertEquals(5.0, metrics!!.totalDistanceKm, delta)
        assertEquals(12.0, metrics.totalDurationMinutes, delta)
        assertEquals(3.0, metrics.grossPerKm, delta)
        assertEquals(75.0, metrics.grossPerHour, delta)
    }

    // =========================================================================
    // 2. VIAJE POCO RENTABLE
    // =========================================================================
    @Test
    fun unprofitableTrip_shouldReturnRejectWithLowKmAndHourlyRate() {
        val trip = createSampleTrip(
            rawFare = 20.0,
            pickupDistanceKm = 1.0,
            pickupDurationMinutes = 3.0,
            tripDistanceKm = 18.0,
            tripDurationMinutes = 55.0
        )

        val config = defaultConfig.copy(
            costPerKm = 0.25,
            minGrossPerKmRate = 1.10,
            minGrossHourlyRate = 22.0
        )

        val evaluation = engine.evaluate(trip, config)

        assertEquals(Decision.REJECT, evaluation.decision)
        assertTrue(evaluation.reasons.contains(DecisionReason.REJECT_LOW_KM_RATE))
        assertTrue(evaluation.reasons.contains(DecisionReason.REJECT_LOW_HOURLY_RATE))

        val metrics = evaluation.metrics
        assertNotNull(metrics)
        val expectedGrossPerKm = 20.0 / 19.0 // ~1.0526
        val expectedGrossPerHour = 20.0 / (58.0 / 60.0) // ~20.6896
        assertEquals(expectedGrossPerKm, metrics!!.grossPerKm, delta)
        assertEquals(expectedGrossPerHour, metrics.grossPerHour, delta)
    }

    // =========================================================================
    // 3. RECOGIDA EXCESIVA
    // =========================================================================
    @Test
    fun excessivePickupDistance_shouldReturnRejectWithExcessivePickupReason() {
        val trip = createSampleTrip(
            rawFare = 12.0,
            pickupDistanceKm = 8.0,
            pickupDurationMinutes = 16.0,
            tripDistanceKm = 4.0,
            tripDurationMinutes = 10.0
        )

        val config = defaultConfig.copy(
            maxPickupDistanceKm = 4.0,
            maxPickupTimeMinutes = 20.0
        )

        val evaluation = engine.evaluate(trip, config)

        assertEquals(Decision.REJECT, evaluation.decision)
        assertTrue(evaluation.reasons.contains(DecisionReason.REJECT_EXCESSIVE_PICKUP_DISTANCE))
    }

    @Test
    fun excessivePickupTime_shouldReturnRejectWithExcessivePickupTimeReason() {
        val trip = createSampleTrip(
            rawFare = 12.0,
            pickupDistanceKm = 3.0,
            pickupDurationMinutes = 15.0,
            tripDistanceKm = 4.0,
            tripDurationMinutes = 10.0
        )

        val config = defaultConfig.copy(
            maxPickupDistanceKm = 5.0,
            maxPickupTimeMinutes = 10.0
        )

        val evaluation = engine.evaluate(trip, config)

        assertEquals(Decision.REJECT, evaluation.decision)
        assertTrue(evaluation.reasons.contains(DecisionReason.REJECT_EXCESSIVE_PICKUP_TIME))
    }

    // =========================================================================
    // 4. FALTA INFORMACIÓN CRÍTICA (NULLs)
    // =========================================================================
    @Test
    fun missingTripDuration_shouldReturnUnknownWithMissingTripTimeReason() {
        val trip = createSampleTrip(
            rawFare = 15.0,
            pickupDistanceKm = 2.0,
            pickupDurationMinutes = 4.0,
            tripDistanceKm = 3.0,
            tripDurationMinutes = null
        )

        val evaluation = engine.evaluate(trip, defaultConfig)

        assertEquals(Decision.UNKNOWN, evaluation.decision)
        assertNull(evaluation.metrics)
        assertTrue(evaluation.reasons.contains(DecisionReason.UNKNOWN_MISSING_TRIP_TIME))
    }

    @Test
    fun missingFare_shouldReturnUnknownWithMissingFareReason() {
        val trip = createSampleTrip(rawFare = null)

        val evaluation = engine.evaluate(trip, defaultConfig)

        assertEquals(Decision.UNKNOWN, evaluation.decision)
        assertNull(evaluation.metrics)
        assertTrue(evaluation.reasons.contains(DecisionReason.UNKNOWN_MISSING_FARE))
    }

    @Test
    fun missingPickupDistance_shouldReturnUnknownWithMissingPickupDistanceReason() {
        val trip = createSampleTrip(pickupDistanceKm = null)

        val evaluation = engine.evaluate(trip, defaultConfig)

        assertEquals(Decision.UNKNOWN, evaluation.decision)
        assertNull(evaluation.metrics)
        assertTrue(evaluation.reasons.contains(DecisionReason.UNKNOWN_MISSING_PICKUP_DISTANCE))
    }

    @Test
    fun missingPickupDuration_shouldReturnUnknownWithMissingPickupTimeReason() {
        val trip = createSampleTrip(pickupDurationMinutes = null)

        val evaluation = engine.evaluate(trip, defaultConfig)

        assertEquals(Decision.UNKNOWN, evaluation.decision)
        assertNull(evaluation.metrics)
        assertTrue(evaluation.reasons.contains(DecisionReason.UNKNOWN_MISSING_PICKUP_TIME))
    }

    @Test
    fun missingTripDistance_shouldReturnUnknownWithMissingTripDistanceReason() {
        val trip = createSampleTrip(tripDistanceKm = null)

        val evaluation = engine.evaluate(trip, defaultConfig)

        assertEquals(Decision.UNKNOWN, evaluation.decision)
        assertNull(evaluation.metrics)
        assertTrue(evaluation.reasons.contains(DecisionReason.UNKNOWN_MISSING_TRIP_DISTANCE))
    }

    // =========================================================================
    // 5. BENEFICIO NETO NEGATIVO
    // =========================================================================
    @Test
    fun negativeNetProfit_shouldReturnRejectWithLowNetProfitReason() {
        val trip = createSampleTrip(
            rawFare = 4.0,
            pickupDistanceKm = 5.0,
            pickupDurationMinutes = 10.0,
            tripDistanceKm = 8.0,
            tripDurationMinutes = 15.0
        )

        val config = defaultConfig.copy(
            costPerKm = 0.35,
            costPerHour = 5.0,
            minNetTripProfit = 1.0,
            minNetHourlyRate = 10.0,
            minGrossHourlyRate = 5.0,
            minGrossPerKmRate = 0.20,
            maxPickupDistanceKm = 10.0,
            maxPickupTimeMinutes = 20.0
        )

        val evaluation = engine.evaluate(trip, config)

        assertEquals(Decision.REJECT, evaluation.decision)
        assertTrue(evaluation.reasons.contains(DecisionReason.REJECT_LOW_NET_PROFIT))

        val metrics = evaluation.metrics
        assertNotNull(metrics)
        // totalDistance = 13.0 km -> costKm = 13.0 * 0.35 = 4.55 €
        // totalDuration = 25.0 min = (25/60) h -> costHour = (25/60) * 5.0 = 2.08333 €
        // estimatedCost = 4.55 + 2.08333 = 6.63333 € > 4.0 €
        // netProfit = 4.0 - 6.63333 = -2.63333 €
        assertTrue(metrics!!.estimatedOperatingCost > 4.0)
        assertTrue(metrics.netProfit < 0.0)
    }

    // =========================================================================
    // 6. DISTANCIA INVÁLIDA
    // =========================================================================
    @Test
    fun zeroTotalDistance_shouldReturnUnknownWithoutArtificialSubstitution() {
        val trip = createSampleTrip(
            pickupDistanceKm = 0.0,
            tripDistanceKm = 0.0
        )

        val evaluation = engine.evaluate(trip, defaultConfig)

        assertEquals(Decision.UNKNOWN, evaluation.decision)
        assertNull(evaluation.metrics)
        assertTrue(evaluation.reasons.contains(DecisionReason.UNKNOWN_INVALID_DATA))
    }

    @Test
    fun negativeDistance_shouldReturnUnknown() {
        val trip = createSampleTrip(
            pickupDistanceKm = -1.0,
            tripDistanceKm = 5.0
        )

        val evaluation = engine.evaluate(trip, defaultConfig)

        assertEquals(Decision.UNKNOWN, evaluation.decision)
        assertNull(evaluation.metrics)
        assertTrue(evaluation.reasons.contains(DecisionReason.UNKNOWN_INVALID_DATA))
    }

    // =========================================================================
    // 7. TIEMPO INVÁLIDO
    // =========================================================================
    @Test
    fun zeroTotalDuration_shouldReturnUnknownWithoutArtificialSubstitution() {
        val trip = createSampleTrip(
            pickupDurationMinutes = 0.0,
            tripDurationMinutes = 0.0
        )

        val evaluation = engine.evaluate(trip, defaultConfig)

        assertEquals(Decision.UNKNOWN, evaluation.decision)
        assertNull(evaluation.metrics)
        assertTrue(evaluation.reasons.contains(DecisionReason.UNKNOWN_INVALID_DATA))
    }

    @Test
    fun negativeDuration_shouldReturnUnknown() {
        val trip = createSampleTrip(
            pickupDurationMinutes = 5.0,
            tripDurationMinutes = -10.0
        )

        val evaluation = engine.evaluate(trip, defaultConfig)

        assertEquals(Decision.UNKNOWN, evaluation.decision)
        assertNull(evaluation.metrics)
        assertTrue(evaluation.reasons.contains(DecisionReason.UNKNOWN_INVALID_DATA))
    }

    // =========================================================================
    // 8. PRECIO INVÁLIDO
    // =========================================================================
    @Test
    fun zeroFare_shouldReturnUnknown() {
        val trip = createSampleTrip(rawFare = 0.0)

        val evaluation = engine.evaluate(trip, defaultConfig)

        assertEquals(Decision.UNKNOWN, evaluation.decision)
        assertNull(evaluation.metrics)
        assertTrue(evaluation.reasons.contains(DecisionReason.UNKNOWN_INVALID_DATA))
    }

    @Test
    fun negativeFare_shouldReturnUnknown() {
        val trip = createSampleTrip(rawFare = -5.0)

        val evaluation = engine.evaluate(trip, defaultConfig)

        assertEquals(Decision.UNKNOWN, evaluation.decision)
        assertNull(evaluation.metrics)
        assertTrue(evaluation.reasons.contains(DecisionReason.UNKNOWN_INVALID_DATA))
    }

    // =========================================================================
    // 9. VERIFICACIÓN MATEMÁTICA EXACTA CON TOLERANCIA
    // =========================================================================
    @Test
    fun mathematicalCalculations_shouldBeAccurateWithTolerance() {
        val trip = createSampleTrip(
            rawFare = 30.0,
            pickupDistanceKm = 3.5,
            pickupDurationMinutes = 7.5,
            tripDistanceKm = 16.5,
            tripDurationMinutes = 22.5
        )

        val config = ProfitabilityConfig(
            costPerKm = 0.22,
            costPerHour = 3.60,
            minGrossHourlyRate = 25.0,
            minGrossPerKmRate = 1.30,
            minNetTripProfit = 5.0,
            minNetHourlyRate = 18.0,
            maxPickupDistanceKm = 5.0,
            maxPickupTimeMinutes = 10.0
        )

        val evaluation = engine.evaluate(trip, config, evaluationTimestamp = 1700000500000L)

        assertEquals(1700000500000L, evaluation.evaluationTimestamp)
        assertEquals(Decision.ACCEPT, evaluation.decision)

        val metrics = evaluation.metrics
        assertNotNull(metrics)

        // totalDistance = 3.5 + 16.5 = 20.0 km
        assertEquals(20.0, metrics!!.totalDistanceKm, delta)

        // totalDuration = 7.5 + 22.5 = 30.0 min
        assertEquals(30.0, metrics.totalDurationMinutes, delta)

        // duration in hours = 30.0 / 60.0 = 0.5 hours
        // estimatedOperatingCost = (20.0 * 0.22) + (0.5 * 3.60) = 4.40 + 1.80 = 6.20 €
        assertEquals(6.20, metrics.estimatedOperatingCost, delta)

        // grossProfit = 30.0 €
        assertEquals(30.0, metrics.grossProfit, delta)

        // netProfit = 30.0 - 6.20 = 23.80 €
        assertEquals(23.80, metrics.netProfit, delta)

        // grossPerKm = 30.0 / 20.0 = 1.50 €/km
        assertEquals(1.50, metrics.grossPerKm, delta)

        // grossPerHour = 30.0 / 0.5 = 60.00 €/h
        assertEquals(60.00, metrics.grossPerHour, delta)

        // netPerHour = 23.80 / 0.5 = 47.60 €/h
        assertEquals(47.60, metrics.netPerHour, delta)
    }

    // =========================================================================
    // 10. INMUTABILIDAD CONCEPTUAL
    // =========================================================================
    @Test
    fun immutabilityVerification_shouldMaintainDataIntegrity() {
        val originalTrip = createSampleTrip(rawFare = 25.0)
        val originalConfig = defaultConfig

        val evaluation = engine.evaluate(originalTrip, originalConfig)

        // Verificar que copiar un objeto no altera el original
        val modifiedTrip = originalTrip.copy(rawFare = 50.0)
        assertEquals(25.0, originalTrip.rawFare!!, delta)
        assertEquals(50.0, modifiedTrip.rawFare!!, delta)

        // Verificar que la evaluación contiene referencias inmutables coherentes
        assertEquals(originalTrip, evaluation.trip)
        assertEquals(originalConfig, evaluation.configUsed)
    }
}
