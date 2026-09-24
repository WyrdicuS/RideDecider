package com.ridedecider.app.domain.engine

import com.ridedecider.app.data.accessibility.uber.InMemoryProfitabilityConfigProvider
import com.ridedecider.app.domain.model.Decision
import com.ridedecider.app.domain.model.ProfitabilityConfig
import com.ridedecider.app.domain.model.Trip
import com.ridedecider.app.domain.model.TripOfferType
import com.ridedecider.app.domain.model.UberCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ThresholdIndependenceTest {

    private val delta = 0.0001

    @Test
    fun `config provider defaults are independent of driver goals`() {
        val provider = InMemoryProfitabilityConfigProvider()
        val config = provider.getConfig()

        assertEquals(24.0, config.minGrossHourlyRate, delta)
        assertEquals(18.0, config.minNetHourlyRate, delta)
    }

    @Test
    fun `same offer produces same decision regardless of goal amount`() {
        val engine = DecisionEngine()
        val config = InMemoryProfitabilityConfigProvider().getConfig()

        val trip = Trip(
            id = "r1-test",
            timestamp = 1700000000000L,
            offerType = TripOfferType.TRIP_OFFER,
            category = UberCategory.UBER_X,
            rawFare = 18.0,
            currency = "EUR",
            pickupDistanceKm = 1.5,
            pickupDurationMinutes = 4.0,
            pickupAddress = "Test",
            tripDistanceKm = 5.0,
            tripDurationMinutes = 15.0,
            dropoffAddress = "Test"
        )

        val evalNoGoal = engine.evaluate(trip, config, economicContext = null)
        val evalLowGoal = engine.evaluate(trip, config, economicContext = null)
        val evalHighGoal = engine.evaluate(trip, config, economicContext = null)

        assertSame(evalNoGoal.decision, evalLowGoal.decision)
        assertSame(evalNoGoal.decision, evalHighGoal.decision)
        assertEquals(evalNoGoal.reasons, evalLowGoal.reasons)
        assertEquals(evalNoGoal.reasons, evalHighGoal.reasons)
    }

    @Test
    fun `config thresholds do not change when goals would have changed them before R1`() {
        val provider = InMemoryProfitabilityConfigProvider()

        val configBefore = provider.getConfig()

        assertEquals(24.0, configBefore.minGrossHourlyRate, delta)
        assertEquals(18.0, configBefore.minNetHourlyRate, delta)

        val configAfter = provider.getConfig()
        assertEquals(configBefore.minGrossHourlyRate, configAfter.minGrossHourlyRate, delta)
        assertEquals(configBefore.minNetHourlyRate, configAfter.minNetHourlyRate, delta)
    }

    @Test
    fun `borderline offer evaluated consistently with stable thresholds`() {
        val engine = DecisionEngine()
        val config = InMemoryProfitabilityConfigProvider().getConfig()

        val trip = Trip(
            id = "r1-borderline",
            timestamp = 1700000000000L,
            offerType = TripOfferType.TRIP_OFFER,
            category = UberCategory.UBER_X,
            rawFare = 10.0,
            currency = "EUR",
            pickupDistanceKm = 2.0,
            pickupDurationMinutes = 5.0,
            pickupAddress = "Test",
            tripDistanceKm = 3.0,
            tripDurationMinutes = 10.0,
            dropoffAddress = "Test"
        )

        val eval1 = engine.evaluate(trip, config)
        val eval2 = engine.evaluate(trip, config)

        assertEquals(eval1.decision, eval2.decision)
        assertEquals(eval1.reasons, eval2.reasons)
        assertEquals(
            eval1.metrics?.profitabilityScore,
            eval2.metrics?.profitabilityScore
        )
    }
}
