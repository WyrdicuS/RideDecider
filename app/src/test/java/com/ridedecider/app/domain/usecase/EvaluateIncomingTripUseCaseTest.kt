package com.ridedecider.app.domain.usecase

import com.ridedecider.app.domain.engine.DecisionEngine
import com.ridedecider.app.domain.model.Decision
import com.ridedecider.app.domain.model.ProfitabilityConfig
import com.ridedecider.app.domain.model.Trip
import com.ridedecider.app.domain.model.TripOfferType
import com.ridedecider.app.domain.model.UberCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios para [EvaluateIncomingTripUseCase].
 *
 * Verifica el contrato de ejecución del caso de uso, su delegación en [DecisionEngine],
 * la inmutabilidad de los parámetros de entrada y la correcta propagación del resultado.
 */
class EvaluateIncomingTripUseCaseTest {

    private lateinit var decisionEngine: DecisionEngine
    private lateinit var useCase: EvaluateIncomingTripUseCase
    private lateinit var defaultConfig: ProfitabilityConfig

    @Before
    fun setUp() {
        decisionEngine = DecisionEngine()
        useCase = EvaluateIncomingTripUseCase(decisionEngine)
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
        id: String = "trip-test-1",
        fare: Double? = 15.0,
        pickupDistance: Double? = 2.0,
        pickupDuration: Double? = 4.0,
        tripDistance: Double? = 3.0,
        tripDuration: Double? = 8.0
    ): Trip {
        return Trip(
            id = id,
            timestamp = 1700000000000L,
            offerType = TripOfferType.TRIP_OFFER,
            category = UberCategory.UBER_X,
            rawFare = fare,
            currency = "EUR",
            pickupDistanceKm = pickupDistance,
            pickupDurationMinutes = pickupDuration,
            pickupAddress = "Calle Mayor 1",
            tripDistanceKm = tripDistance,
            tripDurationMinutes = tripDuration,
            dropoffAddress = "Calle Sol 10"
        )
    }

    // =========================================================================
    // TC-UC-01: Trip rentable + configuración válida
    // =========================================================================
    @Test
    fun tcUc01_profitableTrip_shouldReturnAcceptDecision() {
        val trip = createSampleTrip(
            fare = 15.0,
            pickupDistance = 2.0,
            pickupDuration = 4.0,
            tripDistance = 3.0,
            tripDuration = 8.0
        )

        val evaluation = useCase(trip, defaultConfig)

        assertEquals(Decision.ACCEPT, evaluation.decision)
    }

    // =========================================================================
    // TC-UC-02: Trip poco rentable
    // =========================================================================
    @Test
    fun tcUc02_unprofitableTrip_shouldReturnRejectDecision() {
        val trip = createSampleTrip(
            fare = 20.0,
            pickupDistance = 1.0,
            pickupDuration = 3.0,
            tripDistance = 18.0,
            tripDuration = 55.0
        )

        val config = defaultConfig.copy(
            costPerKm = 0.25,
            minGrossPerKmRate = 1.10,
            minGrossHourlyRate = 22.0
        )

        val evaluation = useCase(trip, config)

        assertEquals(Decision.REJECT, evaluation.decision)
    }

    // =========================================================================
    // TC-UC-03: Trip con datos inválidos o incompletos
    // =========================================================================
    @Test
    fun tcUc03_incompleteOrInvalidTrip_shouldReturnUnknownDecision() {
        val incompleteTrip = createSampleTrip(tripDuration = null)

        val evaluation = useCase(incompleteTrip, defaultConfig)

        assertEquals(Decision.UNKNOWN, evaluation.decision)
        assertNull(evaluation.metrics)
    }

    // =========================================================================
    // TC-UC-04: El UseCase no modifica los objetos de entrada
    // =========================================================================
    @Test
    fun tcUc04_invocation_shouldNotMutateInputObjects() {
        val originalTrip = createSampleTrip(fare = 25.0)
        val originalConfig = defaultConfig

        val evaluation = useCase(originalTrip, originalConfig)

        assertEquals(25.0, originalTrip.rawFare!!, 0.0001)
        assertEquals(defaultConfig.costPerKm, originalConfig.costPerKm, 0.0001)
        assertSame(originalTrip, evaluation.trip)
        assertSame(originalConfig, evaluation.configUsed)
    }

    // =========================================================================
    // TC-UC-05: El UseCase propaga fielmente las métricas de DecisionEngine
    // =========================================================================
    @Test
    fun tcUc05_metricsPropagation_shouldMatchEngineCalculations() {
        val trip = createSampleTrip(
            fare = 15.0,
            pickupDistance = 2.0,
            pickupDuration = 4.0,
            tripDistance = 3.0,
            tripDuration = 8.0
        )

        val evaluation = useCase(trip, defaultConfig)

        assertNotNull(evaluation.metrics)
        val metrics = evaluation.metrics!!
        assertEquals(5.0, metrics.totalDistanceKm, 0.0001)
        assertEquals(12.0, metrics.totalDurationMinutes, 0.0001)
        assertEquals(3.0, metrics.grossPerKm, 0.0001)
        assertEquals(75.0, metrics.grossPerHour, 0.0001)
    }
}
