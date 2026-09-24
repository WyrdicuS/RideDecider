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
import com.ridedecider.app.domain.model.opportunity.OpportunityAssessment
import com.ridedecider.app.domain.model.opportunity.OpportunityQuality
import com.ridedecider.app.domain.model.opportunity.Recommendation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpportunityEvaluatorCalibrationTest {

    private val evaluator = DefaultOpportunityEvaluator()

    private val config = ProfitabilityConfig(
        costPerKm = 0.20,
        costPerHour = 4.0,
        minGrossHourlyRate = 24.0,
        minGrossPerKmRate = 1.10,
        minNetTripProfit = 2.0,
        minNetHourlyRate = 18.0,
        maxPickupDistanceKm = 4.5,
        maxPickupTimeMinutes = 9.0
    )

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

    private fun trip(
        fare: Double, pickupKm: Double, pickupMin: Double,
        tripKm: Double, tripMin: Double, cash: Boolean = false
    ) = Trip(
        id = "cal-${System.nanoTime()}",
        timestamp = System.currentTimeMillis(),
        offerType = TripOfferType.TRIP_OFFER,
        category = UberCategory.UBER_X,
        rawFare = fare, currency = "EUR",
        pickupDistanceKm = pickupKm, pickupDurationMinutes = pickupMin,
        pickupAddress = "Origin",
        tripDistanceKm = tripKm, tripDurationMinutes = tripMin,
        dropoffAddress = "Destination",
        isCashPayment = cash
    )

    private fun metrics(
        fare: Double, pickupKm: Double, pickupMin: Double,
        tripKm: Double, tripMin: Double
    ): EvaluationMetrics {
        val totalKm = pickupKm + tripKm
        val totalMin = pickupMin + tripMin
        val totalHours = totalMin / 60.0
        val opCost = totalKm * config.costPerKm + totalHours * config.costPerHour
        val netProfit = fare - opCost
        return EvaluationMetrics(
            totalDistanceKm = totalKm,
            totalDurationMinutes = totalMin,
            estimatedOperatingCost = opCost,
            grossProfit = fare,
            netProfit = netProfit,
            grossPerKm = fare / totalKm,
            grossPerHour = fare / totalHours,
            netPerHour = netProfit / totalHours,
            pickupSpeedKmh = if (pickupMin > 0) pickupKm / (pickupMin / 60.0) else null,
            pickupDistanceRatio = pickupKm / totalKm,
            pickupTimeRatio = pickupMin / totalMin,
            effectiveGrossPerKm = fare / (pickupKm * config.pickupDistanceWeight + tripKm),
            profitabilityScore = 75
        )
    }

    private fun accept(
        fare: Double, pickupKm: Double, pickupMin: Double,
        tripKm: Double, tripMin: Double
    ): TripEvaluation {
        val t = trip(fare, pickupKm, pickupMin, tripKm, tripMin)
        val m = metrics(fare, pickupKm, pickupMin, tripKm, tripMin)
        return TripEvaluation(
            trip = t, configUsed = config, metrics = m,
            decision = Decision.ACCEPT,
            reasons = listOf(DecisionReason.ACCEPT_HIGH_PROFITABILITY),
            evaluationTimestamp = System.currentTimeMillis(),
            profitabilityLevel = TripProfitabilityLevel.GOOD
        )
    }

    private fun reject(
        fare: Double, pickupKm: Double, pickupMin: Double,
        tripKm: Double, tripMin: Double,
        reasons: List<DecisionReason>
    ): TripEvaluation {
        val t = trip(fare, pickupKm, pickupMin, tripKm, tripMin)
        val m = metrics(fare, pickupKm, pickupMin, tripKm, tripMin)
        return TripEvaluation(
            trip = t, configUsed = config, metrics = m,
            decision = Decision.REJECT, reasons = reasons,
            evaluationTimestamp = System.currentTimeMillis(),
            profitabilityLevel = TripProfitabilityLevel.BAD
        )
    }

    private fun eval(
        evaluation: TripEvaluation,
        history: HistoricalContext? = null,
        ks: KinematicsSource = KinematicsSource.EXPLICIT_DUAL
    ): OpportunityAssessment {
        return evaluator.evaluate(evaluation, evaluation.trip, history, ks)
    }

    private fun logResult(label: String, r: OpportunityAssessment, m: EvaluationMetrics?) {
        val hRatio = if (m != null) "%.2f".format(m.grossPerHour / config.minGrossHourlyRate) else "?"
        val kRatio = if (m != null) "%.2f".format(m.grossPerKm / config.minGrossPerKmRate) else "?"
        println("[$label] Q=${r.quality} R=${r.recommendation} C=${r.confidence} " +
                "SPE=${r.speDecision} override=${r.speOverridden} " +
                "hRatio=$hRatio kRatio=$kRatio " +
                "€/h=${"%.1f".format(m?.grossPerHour ?: 0.0)} " +
                "€/km=${"%.2f".format(m?.grossPerKm ?: 0.0)} " +
                "net=${"%.1f".format(m?.netProfit ?: 0.0)}")
    }

    // ════════════════════════════════════════════════════════
    // A. BASIC CASES
    // ════════════════════════════════════════════════════════

    @Test
    fun `A1 - clearly bad offer produces SKIP`() {
        // fare=5€, pickup 1/3, trip 4/15 → 18min total
        // grossPerHour=16.67 → hourlyRatio=0.69 → POOR
        val e = accept(fare = 5.0, pickupKm = 1.0, pickupMin = 3.0, tripKm = 4.0, tripMin = 15.0)
        val r = eval(e)
        logResult("A1", r, e.metrics)
        assertEquals(OpportunityQuality.POOR, r.quality)
        assertEquals(Recommendation.SKIP, r.recommendation)
    }

    @Test
    fun `A2 - marginal offer produces EVALUATE`() {
        // fare=13€, pickup 1/2, trip 9/27 → 29min total
        // grossPerHour=26.9 → hourlyRatio=1.12 → MARGINAL
        val e = accept(fare = 13.0, pickupKm = 1.0, pickupMin = 2.0, tripKm = 9.0, tripMin = 27.0)
        val r = eval(e)
        logResult("A2", r, e.metrics)
        assertEquals(OpportunityQuality.MARGINAL, r.quality)
        assertEquals(Recommendation.EVALUATE, r.recommendation)
    }

    @Test
    fun `A3 - good offer produces TAKE`() {
        // fare=15€, pickup 1/2, trip 7/22 → 24min, 8km total
        // grossPerHour=37.5 → hourlyRatio=1.56 → GOOD
        // grossPerKm=1.875 → kmRatio=1.70
        val e = accept(fare = 15.0, pickupKm = 1.0, pickupMin = 2.0, tripKm = 7.0, tripMin = 22.0)
        val r = eval(e)
        logResult("A3", r, e.metrics)
        assertEquals(OpportunityQuality.GOOD, r.quality)
        assertEquals(Recommendation.TAKE, r.recommendation)
    }

    @Test
    fun `A4 - exceptional offer produces TAKE`() {
        // fare=25€, pickup 1/2, trip 4/12 → 14min, 5km total
        // grossPerHour=107.1 → hourlyRatio=4.46 → EXCEPTIONAL
        val e = accept(fare = 25.0, pickupKm = 1.0, pickupMin = 2.0, tripKm = 4.0, tripMin = 12.0)
        val r = eval(e)
        logResult("A4", r, e.metrics)
        assertEquals(OpportunityQuality.EXCEPTIONAL, r.quality)
        assertEquals(Recommendation.TAKE, r.recommendation)
    }

    // ════════════════════════════════════════════════════════
    // B. ECONOMIC DIMENSIONS — multidimensionality probes
    // ════════════════════════════════════════════════════════

    @Test
    fun `B5 - high eurPerHour but bad eurPerKm still EXCEPTIONAL`() {
        // Highway scenario: fast but long distance.
        // fare=20€, pickup 2/4, trip 20/15 → 22km/19min
        // grossPerHour=63.2 → hourlyRatio=2.63 → EXCEPTIONAL
        // grossPerKm=0.91 → kmRatio=0.83 → bad
        // RESULT: EXCEPTIONAL — kmRatio irrelevant when hourlyRatio >= 2.0
        val e = accept(fare = 20.0, pickupKm = 2.0, pickupMin = 4.0, tripKm = 20.0, tripMin = 15.0)
        val r = eval(e)
        logResult("B5", r, e.metrics)
        assertEquals(OpportunityQuality.EXCEPTIONAL, r.quality)
        assertEquals(Recommendation.TAKE, r.recommendation)
    }

    @Test
    fun `B6 - high eurPerKm but mediocre eurPerHour only MARGINAL`() {
        // Short distance, long wait. fare=10€, pickup 1/2, trip 3/22 → 4km/24min
        // grossPerHour=25.0 → hourlyRatio=1.04 → MARGINAL
        // grossPerKm=2.50 → kmRatio=2.27 → excellent
        // RESULT: MARGINAL — €/km cannot lift quality above what €/h determines
        val e = accept(fare = 10.0, pickupKm = 1.0, pickupMin = 2.0, tripKm = 3.0, tripMin = 22.0)
        val r = eval(e)
        logResult("B6", r, e.metrics)
        assertEquals(OpportunityQuality.MARGINAL, r.quality)
        assertEquals(Recommendation.EVALUATE, r.recommendation)
    }

    @Test
    fun `B7 - both eurPerHour and eurPerKm high produces GOOD or EXCEPTIONAL`() {
        // fare=18€, pickup 1/2, trip 5/14 → 6km/16min
        // grossPerHour=67.5 → hourlyRatio=2.81 → EXCEPTIONAL
        // grossPerKm=3.0 → kmRatio=2.73 → excellent
        val e = accept(fare = 18.0, pickupKm = 1.0, pickupMin = 2.0, tripKm = 5.0, tripMin = 14.0)
        val r = eval(e)
        logResult("B7", r, e.metrics)
        assertEquals(OpportunityQuality.EXCEPTIONAL, r.quality)
        assertEquals(Recommendation.TAKE, r.recommendation)
    }

    @Test
    fun `B8 - netProfit very high but economy mediocre still MARGINAL`() {
        // Long trip, moderate fare. fare=30€, pickup 2/3, trip 25/55 → 27km/58min
        // grossPerHour=31.0 → hourlyRatio=1.29 → ≥1.25
        // grossPerKm=1.11 → kmRatio=1.01 → ≥1.0 → GOOD
        // netProfit=30-(27*0.20+(58/60)*4.0)=30-5.4-3.87=20.73 → excellent
        // BUT: hourlyRatio determines level. Let me find a case where netProfit is high
        // but hourlyRatio is marginal.
        // fare=25€, pickup 2/4, trip 22/50 → 24km/54min
        // grossPerHour=27.78 → hourlyRatio=1.16 → MARGINAL
        // grossPerKm=1.04 → kmRatio=0.95 → doesn't matter at MARGINAL level
        // netProfit=25-(24*0.20+(54/60)*4.0)=25-4.8-3.6=16.6 → great net
        val e = accept(fare = 25.0, pickupKm = 2.0, pickupMin = 4.0, tripKm = 22.0, tripMin = 50.0)
        val r = eval(e)
        logResult("B8", r, e.metrics)
        assertEquals(OpportunityQuality.MARGINAL, r.quality)
        assertEquals(Recommendation.EVALUATE, r.recommendation)
    }

    @Test
    fun `B5b - kmRatio below 1 downgrades GOOD to MARGINAL at boundary`() {
        // fare=15€, pickup 1/2, trip 14/22 → 15km/24min
        // grossPerHour=37.5 → hourlyRatio=1.5625 → ≥1.25 candidate for GOOD
        // grossPerKm=1.0 → kmRatio=0.909 → <1.0 → downgrades to MARGINAL
        val e = accept(fare = 15.0, pickupKm = 1.0, pickupMin = 2.0, tripKm = 14.0, tripMin = 22.0)
        val r = eval(e)
        logResult("B5b", r, e.metrics)
        assertEquals(OpportunityQuality.MARGINAL, r.quality)
        assertEquals(Recommendation.EVALUATE, r.recommendation)
    }

    @Test
    fun `B5c - kmRatio above 1 keeps GOOD at boundary`() {
        // fare=15€, pickup 1/2, trip 12/22 → 13km/24min
        // grossPerHour=37.5 → hourlyRatio=1.5625 → ≥1.25
        // grossPerKm=1.154 → kmRatio=1.049 → ≥1.0 → GOOD
        val e = accept(fare = 15.0, pickupKm = 1.0, pickupMin = 2.0, tripKm = 12.0, tripMin = 22.0)
        val r = eval(e)
        logResult("B5c", r, e.metrics)
        assertEquals(OpportunityQuality.GOOD, r.quality)
        assertEquals(Recommendation.TAKE, r.recommendation)
    }

    // ════════════════════════════════════════════════════════
    // C. PICKUP / OPERATIONAL
    // ════════════════════════════════════════════════════════

    @Test
    fun `C9 - normal pickup with good economy`() {
        // fare=15€, pickup 2/4, trip 5/12 → 7km/16min
        // grossPerHour=56.25 → hourlyRatio=2.34 → EXCEPTIONAL
        val e = accept(fare = 15.0, pickupKm = 2.0, pickupMin = 4.0, tripKm = 5.0, tripMin = 12.0)
        val r = eval(e)
        logResult("C9", r, e.metrics)
        assertEquals(Recommendation.TAKE, r.recommendation)
        assertFalse(r.speOverridden)
    }

    @Test
    fun `C10 - long pickup with good economy gets EVALUATE`() {
        // fare=30€, pickup 6/9, trip 8/18 → 14km/27min
        // grossPerHour=66.7 → hourlyRatio=2.78 → EXCEPTIONAL
        // grossPerKm=2.14 → passes all economics
        // SPE REJECT only pickup distance
        val e = reject(
            fare = 30.0, pickupKm = 6.0, pickupMin = 9.0, tripKm = 8.0, tripMin = 18.0,
            reasons = listOf(DecisionReason.REJECT_EXCESSIVE_PICKUP_DISTANCE)
        )
        val r = eval(e, reliableHistory)
        logResult("C10", r, e.metrics)
        assertEquals(Recommendation.EVALUATE, r.recommendation)
        assertTrue(r.speOverridden)
    }

    @Test
    fun `C11 - very long pickup with exceptional economy gets EVALUATE`() {
        // fare=60€, pickup 12/15, trip 15/25 → 27km/40min
        // grossPerHour=90 → hourlyRatio=3.75 → EXCEPTIONAL
        val e = reject(
            fare = 60.0, pickupKm = 12.0, pickupMin = 15.0, tripKm = 15.0, tripMin = 25.0,
            reasons = listOf(
                DecisionReason.REJECT_EXCESSIVE_PICKUP_DISTANCE,
                DecisionReason.REJECT_EXCESSIVE_PICKUP_TIME
            )
        )
        val r = eval(e, reliableHistory)
        logResult("C11", r, e.metrics)
        assertEquals(Recommendation.EVALUATE, r.recommendation)
        assertTrue(r.speOverridden)
        assertEquals(OpportunityQuality.EXCEPTIONAL, r.quality)
    }

    @Test
    fun `C12 - operational reject with insufficient economy produces SKIP`() {
        // fare=12€, pickup 6/8, trip 5/12 → 11km/20min
        // grossPerHour=36 → hourlyRatio=1.5 → passes override threshold
        // grossPerKm=1.09 → kmRatio=0.99 → FAILS allEconomicsPass
        val e = reject(
            fare = 12.0, pickupKm = 6.0, pickupMin = 8.0, tripKm = 5.0, tripMin = 12.0,
            reasons = listOf(DecisionReason.REJECT_EXCESSIVE_PICKUP_DISTANCE)
        )
        val r = eval(e)
        logResult("C12", r, e.metrics)
        assertEquals(Recommendation.SKIP, r.recommendation)
        assertFalse(r.speOverridden)
        assertEquals(OpportunityQuality.POOR, r.quality)
    }

    @Test
    fun `C13 - operational reject with exceptional economy never produces TAKE`() {
        // fare=50€, pickup 7/10, trip 8/15 → 15km/25min
        // grossPerHour=120 → hourlyRatio=5.0 → EXCEPTIONAL
        // grossPerKm=3.33 → kmRatio=3.03
        val e = reject(
            fare = 50.0, pickupKm = 7.0, pickupMin = 10.0, tripKm = 8.0, tripMin = 15.0,
            reasons = listOf(DecisionReason.REJECT_EXCESSIVE_PICKUP_DISTANCE)
        )
        val r = eval(e, reliableHistory)
        logResult("C13", r, e.metrics)
        assertEquals(Recommendation.EVALUATE, r.recommendation)
        assertTrue(r.speOverridden)
        assertTrue(r.recommendation != Recommendation.TAKE)
    }

    @Test
    fun `C14 - economic reject never overrides`() {
        val e = reject(
            fare = 50.0, pickupKm = 1.0, pickupMin = 2.0, tripKm = 5.0, tripMin = 12.0,
            reasons = listOf(DecisionReason.REJECT_LOW_HOURLY_RATE)
        )
        val r = eval(e, reliableHistory)
        logResult("C14", r, e.metrics)
        assertEquals(Recommendation.SKIP, r.recommendation)
        assertFalse(r.speOverridden)
        assertEquals(OpportunityQuality.POOR, r.quality)
    }

    // ════════════════════════════════════════════════════════
    // D. DATA QUALITY
    // ════════════════════════════════════════════════════════

    @Test
    fun `D15 - UNKNOWN produces SKIP POOR LOW`() {
        val t = trip(fare = 0.0, pickupKm = 0.0, pickupMin = 0.0, tripKm = 0.0, tripMin = 0.0)
        val e = TripEvaluation(
            trip = t, configUsed = config, metrics = null,
            decision = Decision.UNKNOWN,
            reasons = listOf(DecisionReason.UNKNOWN_MISSING_FARE),
            evaluationTimestamp = System.currentTimeMillis(),
            profitabilityLevel = TripProfitabilityLevel.BAD
        )
        val r = evaluator.evaluate(e, t, reliableHistory, KinematicsSource.EXPLICIT_DUAL)
        logResult("D15", r, null)
        assertEquals(Recommendation.SKIP, r.recommendation)
        assertEquals(OpportunityQuality.POOR, r.quality)
        assertEquals(Confidence.LOW, r.confidence)
    }

    @Test
    fun `D16 - OCR_SUSPECT never produces TAKE even with exceptional metrics`() {
        val e = accept(fare = 25.0, pickupKm = 1.0, pickupMin = 2.0, tripKm = 4.0, tripMin = 12.0)
        val r = eval(e, reliableHistory, KinematicsSource.OCR_SUSPECT)
        logResult("D16", r, e.metrics)
        assertEquals(Confidence.LOW, r.confidence)
        assertTrue(r.recommendation != Recommendation.TAKE)
    }

    @Test
    fun `D17 - MISSING never produces TAKE`() {
        val e = accept(fare = 25.0, pickupKm = 1.0, pickupMin = 2.0, tripKm = 4.0, tripMin = 12.0)
        val r = eval(e, reliableHistory, KinematicsSource.MISSING)
        logResult("D17", r, e.metrics)
        assertEquals(Confidence.LOW, r.confidence)
        assertTrue(r.recommendation != Recommendation.TAKE)
    }

    @Test
    fun `D18 - EXPLICIT_DUAL with good data behaves normally`() {
        val e = accept(fare = 20.0, pickupKm = 1.0, pickupMin = 3.0, tripKm = 5.0, tripMin = 12.0)
        val r = eval(e, reliableHistory, KinematicsSource.EXPLICIT_DUAL)
        logResult("D18", r, e.metrics)
        assertEquals(Confidence.HIGH, r.confidence)
        assertEquals(Recommendation.TAKE, r.recommendation)
    }

    // ════════════════════════════════════════════════════════
    // E. HISTORICAL CONTEXT
    // ════════════════════════════════════════════════════════

    @Test
    fun `E19 - unreliable history with high ratio does NOT upgrade`() {
        // fare=18€, pickup 1/2, trip 7/28 → 30min → 36€/h
        // hourlyRatio=1.5 → GOOD
        // unreliableHistory median=22 → histRatio=36/22=1.64 ≥ 1.5
        // BUT sampleSize=5 < 20 → NO upgrade
        val e = accept(fare = 18.0, pickupKm = 1.0, pickupMin = 2.0, tripKm = 7.0, tripMin = 28.0)
        val r = eval(e, unreliableHistory)
        logResult("E19", r, e.metrics)
        assertEquals(OpportunityQuality.GOOD, r.quality)
    }

    @Test
    fun `E20 - reliable history with high ratio DOES upgrade`() {
        // Same offer, reliableHistory median=23 → histRatio=36/23=1.57 ≥ 1.5
        // sampleSize=50 ≥ 20 → upgrade GOOD→EXCEPTIONAL
        val e = accept(fare = 18.0, pickupKm = 1.0, pickupMin = 2.0, tripKm = 7.0, tripMin = 28.0)
        val r = eval(e, reliableHistory)
        logResult("E20", r, e.metrics)
        assertEquals(OpportunityQuality.EXCEPTIONAL, r.quality)
    }

    @Test
    fun `E21 - null history produces base quality`() {
        val e = accept(fare = 18.0, pickupKm = 1.0, pickupMin = 2.0, tripKm = 7.0, tripMin = 28.0)
        val r = eval(e, null)
        logResult("E21", r, e.metrics)
        assertEquals(OpportunityQuality.GOOD, r.quality)
        assertFalse(r.reasoning.any { "mediana" in it })
    }

    @Test
    fun `E22 - history does not convert bad opportunity to good`() {
        // fare=8€, pickup 1/3, trip 6/20 → 7km/23min
        // grossPerHour=20.87 → hourlyRatio=0.87 → POOR
        // histRatio=20.87/23=0.91 → below 1.5, no upgrade anyway
        // Even if histRatio were high, POOR can only upgrade to MARGINAL
        val e = accept(fare = 8.0, pickupKm = 1.0, pickupMin = 3.0, tripKm = 6.0, tripMin = 20.0)
        val r = eval(e, reliableHistory)
        logResult("E22", r, e.metrics)
        assertEquals(OpportunityQuality.POOR, r.quality)
        assertEquals(Recommendation.SKIP, r.recommendation)
    }

    @Test
    fun `E22b - history upgrade of POOR with extreme ratio produces MARGINAL not GOOD`() {
        // Need: POOR base quality + histRatio ≥ 1.5
        // POOR requires hourlyRatio < 1.0, meaning grossPerHour < 24
        // histRatio ≥ 1.5 with median=23 means grossPerHour ≥ 34.5
        // But grossPerHour < 24 AND grossPerHour ≥ 34.5 is impossible!
        // So with standard config, historical upgrade can NEVER rescue a POOR offer.
        // Let's verify with a custom history where median is very low:
        val lowMedianHistory = HistoricalContext(
            medianGrossPerHour = 10.0,
            medianGrossPerKm = 0.80,
            medianNetPerHour = 6.0,
            sampleSize = 50,
            timeSlotMedianGrossPerHour = null,
            timeSlotSampleSize = 0,
            lastUpdated = System.currentTimeMillis()
        )
        // fare=7€, pickup 1/2, trip 5/17 → 6km/19min
        // grossPerHour=22.1 → hourlyRatio=0.92 → POOR
        // histRatio=22.1/10=2.21 ≥ 1.5 → upgrade POOR→MARGINAL
        val e = accept(fare = 7.0, pickupKm = 1.0, pickupMin = 2.0, tripKm = 5.0, tripMin = 17.0)
        val r = eval(e, lowMedianHistory)
        logResult("E22b", r, e.metrics)
        assertEquals(OpportunityQuality.MARGINAL, r.quality)
        assertEquals(Recommendation.EVALUATE, r.recommendation)
    }

    // ════════════════════════════════════════════════════════
    // F. LONG-TRIP CASE (137€ representative)
    // ════════════════════════════════════════════════════════

    @Test
    fun `F23 - long trip 137eur case with exact numbers`() {
        // fare=137.05€, pickup 9.4/12, trip 116.3/104 → 125.7km/116min
        // grossPerHour=137.05/(116/60)=70.9 → hourlyRatio=2.95 → EXCEPTIONAL
        // grossPerKm=137.05/125.7=1.090 → kmRatio=0.991 → below 1.0
        // BUT: hourlyRatio >= 2.0 → EXCEPTIONAL regardless of kmRatio
        // SPE would add REJECT_LOW_KM_RATE (economic) because 1.090 < 1.10
        // → economic block → SKIP, no override possible
        val e = reject(
            fare = 137.05, pickupKm = 9.4, pickupMin = 12.0, tripKm = 116.3, tripMin = 104.0,
            reasons = listOf(
                DecisionReason.REJECT_EXCESSIVE_PICKUP_DISTANCE,
                DecisionReason.REJECT_EXCESSIVE_PICKUP_TIME,
                DecisionReason.REJECT_LOW_KM_RATE
            )
        )
        val r = eval(e, reliableHistory)
        logResult("F23-exact137", r, e.metrics)
        assertEquals(Recommendation.SKIP, r.recommendation)
        assertEquals(OpportunityQuality.POOR, r.quality)
        assertFalse(r.speOverridden)
    }

    @Test
    fun `F23b - long trip 140eur variant with only operational rejection`() {
        // fare=140€, pickup 9.4/12, trip 116.3/104 → 125.7km/116min
        // grossPerHour=140/(116/60)=72.4 → hourlyRatio=3.02 → EXCEPTIONAL
        // grossPerKm=140/125.7=1.114 → kmRatio=1.01 → passes
        // SPE REJECT only operational → override possible
        val e = reject(
            fare = 140.0, pickupKm = 9.4, pickupMin = 12.0, tripKm = 116.3, tripMin = 104.0,
            reasons = listOf(
                DecisionReason.REJECT_EXCESSIVE_PICKUP_DISTANCE,
                DecisionReason.REJECT_EXCESSIVE_PICKUP_TIME
            )
        )
        val r = eval(e, reliableHistory)
        logResult("F23b-140eur", r, e.metrics)
        assertEquals(Recommendation.EVALUATE, r.recommendation)
        assertEquals(OpportunityQuality.EXCEPTIONAL, r.quality)
        assertTrue(r.speOverridden)
        assertTrue(r.recommendation != Recommendation.TAKE)
    }

    // ════════════════════════════════════════════════════════
    // G. EXTREMES
    // ════════════════════════════════════════════════════════

    @Test
    fun `G24 - very high distance low fare proportionally`() {
        // fare=30€, pickup 2/4, trip 40/60 → 42km/64min
        // grossPerHour=28.1 → hourlyRatio=1.17 → MARGINAL
        // grossPerKm=0.71 → kmRatio=0.65
        val e = accept(fare = 30.0, pickupKm = 2.0, pickupMin = 4.0, tripKm = 40.0, tripMin = 60.0)
        val r = eval(e)
        logResult("G24", r, e.metrics)
        assertEquals(OpportunityQuality.MARGINAL, r.quality)
    }

    @Test
    fun `G25 - very high distance high fare`() {
        // fare=120€, pickup 2/4, trip 80/90 → 82km/94min
        // grossPerHour=76.6 → hourlyRatio=3.19 → EXCEPTIONAL
        // grossPerKm=1.46 → kmRatio=1.33
        val e = accept(fare = 120.0, pickupKm = 2.0, pickupMin = 4.0, tripKm = 80.0, tripMin = 90.0)
        val r = eval(e)
        logResult("G25", r, e.metrics)
        assertEquals(OpportunityQuality.EXCEPTIONAL, r.quality)
        assertEquals(Recommendation.TAKE, r.recommendation)
    }

    @Test
    fun `G26 - very long duration high fare`() {
        // fare=80€, pickup 2/5, trip 30/110 → 32km/115min
        // grossPerHour=41.7 → hourlyRatio=1.74 → GOOD
        // grossPerKm=2.5 → kmRatio=2.27
        val e = accept(fare = 80.0, pickupKm = 2.0, pickupMin = 5.0, tripKm = 30.0, tripMin = 110.0)
        val r = eval(e)
        logResult("G26", r, e.metrics)
        assertEquals(OpportunityQuality.GOOD, r.quality)
        assertEquals(Recommendation.TAKE, r.recommendation)
    }

    @Test
    fun `G27 - high pickup very profitable trip operational reject`() {
        // fare=45€, pickup 10/14, trip 10/16 → 20km/30min
        // grossPerHour=90 → hourlyRatio=3.75 → EXCEPTIONAL
        // grossPerKm=2.25 → kmRatio=2.05
        val e = reject(
            fare = 45.0, pickupKm = 10.0, pickupMin = 14.0, tripKm = 10.0, tripMin = 16.0,
            reasons = listOf(
                DecisionReason.REJECT_EXCESSIVE_PICKUP_DISTANCE,
                DecisionReason.REJECT_EXCESSIVE_PICKUP_TIME
            )
        )
        val r = eval(e, reliableHistory)
        logResult("G27", r, e.metrics)
        assertEquals(Recommendation.EVALUATE, r.recommendation)
        assertTrue(r.speOverridden)
    }

    @Test
    fun `G28 - extreme hourly ratio very short trip`() {
        // fare=15€, pickup 0.5/1, trip 1/3 → 1.5km/4min
        // grossPerHour=225 → hourlyRatio=9.38 → EXCEPTIONAL
        // grossPerKm=10.0 → kmRatio=9.09
        // Very short trip, massive ratios
        val e = accept(fare = 15.0, pickupKm = 0.5, pickupMin = 1.0, tripKm = 1.0, tripMin = 3.0)
        val r = eval(e)
        logResult("G28", r, e.metrics)
        assertEquals(OpportunityQuality.EXCEPTIONAL, r.quality)
        assertEquals(Recommendation.TAKE, r.recommendation)
    }

    @Test
    fun `G29 - absurd scenario 1eur fare`() {
        // fare=1€, pickup 1/3, trip 2/8 → 3km/11min
        // grossPerHour=5.45 → hourlyRatio=0.23 → POOR
        val e = accept(fare = 1.0, pickupKm = 1.0, pickupMin = 3.0, tripKm = 2.0, tripMin = 8.0)
        val r = eval(e)
        logResult("G29", r, e.metrics)
        assertEquals(OpportunityQuality.POOR, r.quality)
        assertEquals(Recommendation.SKIP, r.recommendation)
    }
}
