package com.ridedecider.app.domain.engine

import com.ridedecider.app.data.accessibility.uber.KinematicsSource
import com.ridedecider.app.domain.model.Decision
import com.ridedecider.app.domain.model.DecisionReason
import com.ridedecider.app.domain.model.EvaluationMetrics
import com.ridedecider.app.domain.model.ProfitabilityConfig
import com.ridedecider.app.domain.model.Trip
import com.ridedecider.app.domain.model.TripEvaluation
import com.ridedecider.app.domain.model.TripOfferType
import com.ridedecider.app.domain.model.TripProfitabilityLevel
import com.ridedecider.app.domain.model.UberCategory
import com.ridedecider.app.domain.model.opportunity.Confidence
import com.ridedecider.app.domain.model.opportunity.HistoricalContext
import com.ridedecider.app.domain.model.opportunity.OpportunityQuality
import com.ridedecider.app.domain.model.opportunity.Recommendation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultOpportunityEvaluatorTest {

    private val evaluator = DefaultOpportunityEvaluator()

    private val defaultConfig = ProfitabilityConfig(
        costPerKm = 0.20,
        costPerHour = 4.0,
        minGrossHourlyRate = 24.0,
        minGrossPerKmRate = 1.10,
        minNetTripProfit = 2.0,
        minNetHourlyRate = 18.0,
        maxPickupDistanceKm = 4.5,
        maxPickupTimeMinutes = 9.0
    )

    private fun trip(
        fare: Double = 15.0,
        pickupKm: Double = 1.0,
        pickupMin: Double = 3.0,
        tripKm: Double = 5.0,
        tripMin: Double = 12.0,
        cash: Boolean = false
    ) = Trip(
        id = "test-${System.nanoTime()}",
        timestamp = System.currentTimeMillis(),
        offerType = TripOfferType.TRIP_OFFER,
        category = UberCategory.UBER_X,
        rawFare = fare,
        currency = "EUR",
        pickupDistanceKm = pickupKm,
        pickupDurationMinutes = pickupMin,
        pickupAddress = "Origin",
        tripDistanceKm = tripKm,
        tripDurationMinutes = tripMin,
        dropoffAddress = "Destination",
        isCashPayment = cash
    )

    private fun metrics(
        fare: Double,
        pickupKm: Double,
        pickupMin: Double,
        tripKm: Double,
        tripMin: Double,
        config: ProfitabilityConfig = defaultConfig
    ): EvaluationMetrics {
        val totalKm = pickupKm + tripKm
        val totalMin = pickupMin + tripMin
        val totalHours = totalMin / 60.0
        val operatingCost = totalKm * config.costPerKm + totalHours * config.costPerHour
        val netProfit = fare - operatingCost
        val grossPerKm = fare / totalKm
        val grossPerHour = fare / totalHours
        val netPerHour = netProfit / totalHours
        val pickupDistanceRatio = pickupKm / totalKm
        val pickupTimeRatio = pickupMin / totalMin
        val effectiveDistance = pickupKm * config.pickupDistanceWeight + tripKm
        val effectiveGrossPerKm = fare / effectiveDistance
        return EvaluationMetrics(
            totalDistanceKm = totalKm,
            totalDurationMinutes = totalMin,
            estimatedOperatingCost = operatingCost,
            grossProfit = fare,
            netProfit = netProfit,
            grossPerKm = grossPerKm,
            grossPerHour = grossPerHour,
            netPerHour = netPerHour,
            pickupSpeedKmh = if (pickupKm > 0 && pickupMin > 0) pickupKm / (pickupMin / 60.0) else null,
            pickupDistanceRatio = pickupDistanceRatio,
            pickupTimeRatio = pickupTimeRatio,
            effectiveGrossPerKm = effectiveGrossPerKm,
            profitabilityScore = 75
        )
    }

    private fun acceptEvaluation(
        fare: Double,
        pickupKm: Double,
        pickupMin: Double,
        tripKm: Double,
        tripMin: Double,
        level: TripProfitabilityLevel = TripProfitabilityLevel.GOOD
    ): TripEvaluation {
        val t = trip(fare, pickupKm, pickupMin, tripKm, tripMin)
        val m = metrics(fare, pickupKm, pickupMin, tripKm, tripMin)
        return TripEvaluation(
            trip = t,
            configUsed = defaultConfig,
            metrics = m,
            decision = Decision.ACCEPT,
            reasons = listOf(DecisionReason.ACCEPT_HIGH_PROFITABILITY),
            evaluationTimestamp = System.currentTimeMillis(),
            profitabilityLevel = level
        )
    }

    private fun rejectEvaluation(
        fare: Double,
        pickupKm: Double,
        pickupMin: Double,
        tripKm: Double,
        tripMin: Double,
        reasons: List<DecisionReason>,
        level: TripProfitabilityLevel = TripProfitabilityLevel.BAD
    ): TripEvaluation {
        val t = trip(fare, pickupKm, pickupMin, tripKm, tripMin)
        val m = metrics(fare, pickupKm, pickupMin, tripKm, tripMin)
        return TripEvaluation(
            trip = t,
            configUsed = defaultConfig,
            metrics = m,
            decision = Decision.REJECT,
            reasons = reasons,
            evaluationTimestamp = System.currentTimeMillis(),
            profitabilityLevel = level
        )
    }

    private fun unknownEvaluation(): TripEvaluation {
        val t = trip(fare = 0.0)
        return TripEvaluation(
            trip = t,
            configUsed = defaultConfig,
            metrics = null,
            decision = Decision.UNKNOWN,
            reasons = listOf(DecisionReason.UNKNOWN_MISSING_FARE),
            evaluationTimestamp = System.currentTimeMillis(),
            profitabilityLevel = TripProfitabilityLevel.BAD
        )
    }

    private val reliableHistory = HistoricalContext(
        medianGrossPerHour = 23.0,
        medianGrossPerKm = 1.15,
        medianNetPerHour = 17.0,
        sampleSize = 50,
        timeSlotMedianGrossPerHour = 24.0,
        timeSlotSampleSize = 15,
        lastUpdated = System.currentTimeMillis()
    )

    private val unreliableHistory = HistoricalContext(
        medianGrossPerHour = 22.0,
        medianGrossPerKm = 1.10,
        medianNetPerHour = 16.0,
        sampleSize = 5,
        timeSlotMedianGrossPerHour = null,
        timeSlotSampleSize = 0,
        lastUpdated = System.currentTimeMillis()
    )

    // ── A. SPE ACCEPT + strong opportunity ──

    @Test
    fun `A - SPE ACCEPT with strong economics produces TAKE`() {
        // 25€ fare, short trip → ~60€/h (2.5× minimum), clearly EXCEPTIONAL
        val eval = acceptEvaluation(
            fare = 25.0, pickupKm = 1.0, pickupMin = 2.0,
            tripKm = 6.0, tripMin = 23.0
        )
        val result = evaluator.evaluate(eval, eval.trip, reliableHistory, KinematicsSource.EXPLICIT_DUAL)

        assertEquals(Recommendation.TAKE, result.recommendation)
        assertTrue(
            result.quality == OpportunityQuality.EXCEPTIONAL || result.quality == OpportunityQuality.GOOD
        )
        assertEquals(Confidence.HIGH, result.confidence)
        assertEquals(Decision.ACCEPT, result.speDecision)
        assertFalse(result.speOverridden)
        assertNull(result.overrideJustification)
        assertTrue(result.reasoning.isNotEmpty())
    }

    // ── B. SPE ACCEPT + weak opportunity ──

    @Test
    fun `B - SPE ACCEPT with marginal economics produces EVALUATE`() {
        // fare=13€, long trip → ~27€/h (1.13× minimum), below GOOD threshold
        val eval = acceptEvaluation(
            fare = 13.0, pickupKm = 1.0, pickupMin = 2.0,
            tripKm = 9.0, tripMin = 27.0,
            level = TripProfitabilityLevel.ACCEPTABLE
        )
        val result = evaluator.evaluate(eval, eval.trip, null, KinematicsSource.EXPLICIT_DUAL)

        assertEquals(Recommendation.EVALUATE, result.recommendation)
        assertEquals(OpportunityQuality.MARGINAL, result.quality)
        assertFalse(result.speOverridden)
    }

    // ── C. SPE REJECT + economic reason ──

    @Test
    fun `C - SPE REJECT with economic reason produces SKIP and no override`() {
        val eval = rejectEvaluation(
            fare = 5.0, pickupKm = 1.0, pickupMin = 3.0,
            tripKm = 4.0, tripMin = 15.0,
            reasons = listOf(DecisionReason.REJECT_LOW_HOURLY_RATE, DecisionReason.REJECT_LOW_NET_PROFIT)
        )
        val result = evaluator.evaluate(eval, eval.trip, reliableHistory, KinematicsSource.EXPLICIT_DUAL)

        assertEquals(Recommendation.SKIP, result.recommendation)
        assertEquals(OpportunityQuality.POOR, result.quality)
        assertFalse(result.speOverridden)
        assertNull(result.overrideJustification)
        assertTrue(result.reasoning.any { "Bloqueo económico" in it })
    }

    // ── D. SPE REJECT + only pickup distance ──

    @Test
    fun `D - SPE REJECT only pickup distance with strong economics produces EVALUATE`() {
        // fare=40€, pickup 6km/8min, trip 10km/20min → ~85.7€/h
        val eval = rejectEvaluation(
            fare = 40.0, pickupKm = 6.0, pickupMin = 8.0,
            tripKm = 10.0, tripMin = 20.0,
            reasons = listOf(DecisionReason.REJECT_EXCESSIVE_PICKUP_DISTANCE)
        )
        val result = evaluator.evaluate(eval, eval.trip, reliableHistory, KinematicsSource.EXPLICIT_DUAL)

        assertEquals(Recommendation.EVALUATE, result.recommendation)
        assertTrue(result.speOverridden)
        assertEquals(Decision.REJECT, result.speDecision)
        assertNotNull(result.overrideJustification)
        assertTrue(
            result.quality == OpportunityQuality.EXCEPTIONAL || result.quality == OpportunityQuality.GOOD
        )
    }

    // ── E. SPE REJECT + only pickup duration ──

    @Test
    fun `E - SPE REJECT only pickup duration with strong economics produces EVALUATE`() {
        // fare=35€, pickup 3km/10min, trip 8km/18min → 75€/h
        val eval = rejectEvaluation(
            fare = 35.0, pickupKm = 3.0, pickupMin = 10.0,
            tripKm = 8.0, tripMin = 18.0,
            reasons = listOf(DecisionReason.REJECT_EXCESSIVE_PICKUP_TIME)
        )
        val result = evaluator.evaluate(eval, eval.trip, null, KinematicsSource.EXPLICIT_DUAL)

        assertEquals(Recommendation.EVALUATE, result.recommendation)
        assertTrue(result.speOverridden)
        assertNotNull(result.overrideJustification)
    }

    // ── F. SPE REJECT + pickup distance + pickup duration ──

    @Test
    fun `F - SPE REJECT both pickup reasons with strong economics produces EVALUATE`() {
        // fare=50€, pickup 7km/11min, trip 12km/22min → 90.9€/h
        val eval = rejectEvaluation(
            fare = 50.0, pickupKm = 7.0, pickupMin = 11.0,
            tripKm = 12.0, tripMin = 22.0,
            reasons = listOf(
                DecisionReason.REJECT_EXCESSIVE_PICKUP_DISTANCE,
                DecisionReason.REJECT_EXCESSIVE_PICKUP_TIME
            )
        )
        val result = evaluator.evaluate(eval, eval.trip, reliableHistory, KinematicsSource.EXPLICIT_DUAL)

        assertEquals(Recommendation.EVALUATE, result.recommendation)
        assertTrue(result.speOverridden)
        assertEquals(Decision.REJECT, result.speDecision)
        assertTrue(result.reasoning.any { "operativo" in it.lowercase() })
    }

    // ── G. SPE UNKNOWN ──

    @Test
    fun `G - SPE UNKNOWN produces SKIP with LOW confidence`() {
        val eval = unknownEvaluation()
        val result = evaluator.evaluate(eval, eval.trip, reliableHistory, KinematicsSource.MISSING)

        assertEquals(Recommendation.SKIP, result.recommendation)
        assertEquals(OpportunityQuality.POOR, result.quality)
        assertEquals(Confidence.LOW, result.confidence)
        assertEquals(Decision.UNKNOWN, result.speDecision)
        assertFalse(result.speOverridden)
    }

    // ── H. Sufficient historical context ──

    @Test
    fun `H - historical context upgrades quality when offer significantly exceeds median`() {
        // fare=18€, pickup 1km/2min, trip 6km/16min → 60€/h
        // hourlyRatio = 60/24 = 2.5 → already EXCEPTIONAL by ratio
        // But with a GOOD-level offer: fare=16€, trip giving ~32€/h (1.33×, GOOD threshold)
        // median=23€/h → 32/23 = 1.39 → below upgrade threshold (1.5)
        // Now with fare=18, giving ~36€/h: 36/23 = 1.57 → triggers upgrade
        val eval = acceptEvaluation(
            fare = 18.0, pickupKm = 1.0, pickupMin = 2.0,
            tripKm = 7.0, tripMin = 28.0,
            level = TripProfitabilityLevel.GOOD
        )
        // grossPerHour = 18 / (30/60) = 36€/h, hourlyRatio = 1.5 → GOOD
        // histRatio = 36/23 = 1.57 ≥ 1.5 → upgrade to EXCEPTIONAL
        val result = evaluator.evaluate(eval, eval.trip, reliableHistory, KinematicsSource.EXPLICIT_DUAL)

        assertEquals(OpportunityQuality.EXCEPTIONAL, result.quality)
        assertTrue(result.reasoning.any { "mediana histórica" in it })
    }

    // ── H2. Unreliable history does NOT upgrade quality ──

    @Test
    fun `H2 - unreliable history does not upgrade quality even with high ratio`() {
        // Same offer as H: grossPerHour=36€/h, medianGrossPerHour=22€/h → histRatio=1.64 ≥ 1.5
        // But unreliableHistory.sampleSize=5 < 20 → upgrade blocked
        // hourlyRatio = 36/24 = 1.5 → GOOD (not upgraded to EXCEPTIONAL)
        val eval = acceptEvaluation(
            fare = 18.0, pickupKm = 1.0, pickupMin = 2.0,
            tripKm = 7.0, tripMin = 28.0,
            level = TripProfitabilityLevel.GOOD
        )
        val result = evaluator.evaluate(eval, eval.trip, unreliableHistory, KinematicsSource.EXPLICIT_DUAL)

        assertEquals(OpportunityQuality.GOOD, result.quality)
        assertTrue(result.reasoning.any { "muestra limitada" in it })
    }

    // ── I. Insufficient historical context ──

    @Test
    fun `I - unreliable history degrades confidence but does not block assessment`() {
        val eval = acceptEvaluation(
            fare = 25.0, pickupKm = 1.0, pickupMin = 2.0,
            tripKm = 6.0, tripMin = 23.0
        )
        val withReliable = evaluator.evaluate(eval, eval.trip, reliableHistory, KinematicsSource.EXPLICIT_DUAL)
        val withUnreliable = evaluator.evaluate(eval, eval.trip, unreliableHistory, KinematicsSource.EXPLICIT_DUAL)

        assertEquals(Confidence.HIGH, withReliable.confidence)
        assertEquals(Confidence.MEDIUM, withUnreliable.confidence)
        assertTrue(withUnreliable.recommendation != Recommendation.SKIP)
        assertTrue(withUnreliable.reasoning.any { "muestra limitada" in it })
    }

    // ── J. KinematicsSource OCR_SUSPECT ──

    @Test
    fun `J - OCR_SUSPECT produces LOW confidence`() {
        val eval = acceptEvaluation(
            fare = 20.0, pickupKm = 1.0, pickupMin = 3.0,
            tripKm = 5.0, tripMin = 12.0
        )
        val result = evaluator.evaluate(eval, eval.trip, null, KinematicsSource.OCR_SUSPECT)

        assertEquals(Confidence.LOW, result.confidence)
        assertEquals(Recommendation.EVALUATE, result.recommendation)
    }

    // ── K. Exceptional offer with long pickup (conceptual 137€ case) ──

    @Test
    fun `K - exceptional offer with long pickup gets EVALUATE with EXCEPTIONAL quality`() {
        // Adjusted fare to 140€ to ensure grossPerKm > 1.10 (only operational rejection)
        // pickup 9.4km/12min, trip 116.3km/104min, total 125.7km/116min
        // grossPerHour = 140 / (116/60) = 72.4€/h → hourlyRatio = 3.02
        // grossPerKm = 140 / 125.7 = 1.114 → kmRatio = 1.01
        // netProfit = 140 - (125.7*0.20 + (116/60)*4.0) = 140 - 25.14 - 7.73 = 107.13
        val eval = rejectEvaluation(
            fare = 140.0, pickupKm = 9.4, pickupMin = 12.0,
            tripKm = 116.3, tripMin = 104.0,
            reasons = listOf(
                DecisionReason.REJECT_EXCESSIVE_PICKUP_DISTANCE,
                DecisionReason.REJECT_EXCESSIVE_PICKUP_TIME
            )
        )
        val result = evaluator.evaluate(eval, eval.trip, reliableHistory, KinematicsSource.EXPLICIT_DUAL)

        assertEquals(OpportunityQuality.EXCEPTIONAL, result.quality)
        assertEquals(Recommendation.EVALUATE, result.recommendation)
        assertEquals(Confidence.HIGH, result.confidence)
        assertEquals(Decision.REJECT, result.speDecision)
        assertTrue(result.speOverridden)
        assertNotNull(result.overrideJustification)
        assertTrue(result.reasoning.any { "operativo" in it.lowercase() })
        assertTrue(result.reasoning.any { "€/h" in it })
    }

    // ── L. Absence of HistoricalContext ──

    @Test
    fun `L - null historical context still produces valid assessment`() {
        val eval = acceptEvaluation(
            fare = 20.0, pickupKm = 1.0, pickupMin = 3.0,
            tripKm = 5.0, tripMin = 12.0
        )
        val result = evaluator.evaluate(eval, eval.trip, null, KinematicsSource.EXPLICIT_DUAL)

        assertEquals(Confidence.HIGH, result.confidence)
        assertTrue(result.recommendation != Recommendation.SKIP)
        assertFalse(result.reasoning.any { "mediana histórica" in it })
    }

    // ── Additional edge cases ──

    @Test
    fun `operational reject with weak economics does not override`() {
        // fare=19€, pickup 5km/7min, trip 12km/25min → total 17km/32min
        // grossPerHour = 19/(32/60) = 35.6€/h → hourlyRatio = 1.48 < 1.5 override threshold
        // grossPerKm = 19/17 = 1.12 → passes economic check
        // All economic minimums pass but hourly below override threshold
        val eval = rejectEvaluation(
            fare = 19.0, pickupKm = 5.0, pickupMin = 7.0,
            tripKm = 12.0, tripMin = 25.0,
            reasons = listOf(DecisionReason.REJECT_EXCESSIVE_PICKUP_DISTANCE)
        )
        val result = evaluator.evaluate(eval, eval.trip, null, KinematicsSource.EXPLICIT_DUAL)

        assertEquals(Recommendation.SKIP, result.recommendation)
        assertFalse(result.speOverridden)
        assertEquals(OpportunityQuality.MARGINAL, result.quality)
    }

    @Test
    fun `mixed economic and operational reject is irrecoverable`() {
        val eval = rejectEvaluation(
            fare = 6.0, pickupKm = 5.5, pickupMin = 10.0,
            tripKm = 4.0, tripMin = 12.0,
            reasons = listOf(
                DecisionReason.REJECT_EXCESSIVE_PICKUP_DISTANCE,
                DecisionReason.REJECT_LOW_HOURLY_RATE
            )
        )
        val result = evaluator.evaluate(eval, eval.trip, reliableHistory, KinematicsSource.EXPLICIT_DUAL)

        assertEquals(Recommendation.SKIP, result.recommendation)
        assertEquals(OpportunityQuality.POOR, result.quality)
        assertFalse(result.speOverridden)
    }

    @Test
    fun `cash payment noted in reasoning`() {
        val t = trip(fare = 20.0, pickupKm = 1.0, pickupMin = 3.0, tripKm = 5.0, tripMin = 12.0, cash = true)
        val m = metrics(20.0, 1.0, 3.0, 5.0, 12.0)
        val eval = TripEvaluation(
            trip = t, configUsed = defaultConfig, metrics = m,
            decision = Decision.ACCEPT,
            reasons = listOf(DecisionReason.ACCEPT_HIGH_PROFITABILITY),
            evaluationTimestamp = System.currentTimeMillis(),
            profitabilityLevel = TripProfitabilityLevel.GOOD
        )
        val result = evaluator.evaluate(eval, t, null, KinematicsSource.EXPLICIT_DUAL)

        assertTrue(result.reasoning.any { "efectivo" in it.lowercase() })
    }

    @Test
    fun `UNIFIED_INFERRED produces MEDIUM confidence`() {
        val eval = acceptEvaluation(
            fare = 20.0, pickupKm = 1.0, pickupMin = 3.0,
            tripKm = 5.0, tripMin = 12.0
        )
        val result = evaluator.evaluate(eval, eval.trip, null, KinematicsSource.UNIFIED_INFERRED)

        assertEquals(Confidence.MEDIUM, result.confidence)
    }
}
