package com.ridedecider.app.domain.model.opportunity

import com.ridedecider.app.data.accessibility.uber.KinematicsSource
import com.ridedecider.app.domain.model.Decision
import com.ridedecider.app.domain.model.DecisionMode
import com.ridedecider.app.domain.model.DecisionReason
import com.ridedecider.app.domain.model.Trip
import com.ridedecider.app.domain.model.TripEvaluation
import com.ridedecider.app.domain.model.TripOfferType
import com.ridedecider.app.domain.model.UberCategory
import com.ridedecider.app.domain.engine.OpportunityEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpportunityContractsTest {

    @Test
    fun `all 17 DecisionReason values are categorized`() {
        val allReasons = DecisionReason.entries
        assertEquals(17, allReasons.size)
        allReasons.forEach { reason ->
            val category = reason.category()
            assertTrue(
                "DecisionReason.$reason must have a category",
                DecisionReasonCategory.entries.contains(category)
            )
        }
    }

    @Test
    fun `economic reasons are correctly categorized`() {
        val economicReasons = listOf(
            DecisionReason.REJECT_LOW_HOURLY_RATE,
            DecisionReason.REJECT_LOW_KM_RATE,
            DecisionReason.REJECT_LOW_EFFECTIVE_KM_RATE,
            DecisionReason.REJECT_LOW_NET_PROFIT,
            DecisionReason.REJECT_LOW_NET_HOURLY_RATE
        )
        economicReasons.forEach { reason ->
            assertEquals(
                "DecisionReason.$reason should be ECONOMIC",
                DecisionReasonCategory.ECONOMIC,
                reason.category()
            )
        }
    }

    @Test
    fun `operational reasons are correctly categorized`() {
        val operationalReasons = listOf(
            DecisionReason.REJECT_EXCESSIVE_PICKUP_DISTANCE,
            DecisionReason.REJECT_EXCESSIVE_PICKUP_TIME
        )
        operationalReasons.forEach { reason ->
            assertEquals(
                "DecisionReason.$reason should be OPERATIONAL",
                DecisionReasonCategory.OPERATIONAL,
                reason.category()
            )
        }
    }

    @Test
    fun `data quality reasons are correctly categorized`() {
        val dataQualityReasons = listOf(
            DecisionReason.UNKNOWN_MISSING_FARE,
            DecisionReason.UNKNOWN_MISSING_PICKUP_DISTANCE,
            DecisionReason.UNKNOWN_MISSING_PICKUP_TIME,
            DecisionReason.UNKNOWN_MISSING_TRIP_DISTANCE,
            DecisionReason.UNKNOWN_MISSING_TRIP_TIME,
            DecisionReason.UNKNOWN_INVALID_DATA
        )
        dataQualityReasons.forEach { reason ->
            assertEquals(
                "DecisionReason.$reason should be DATA_QUALITY",
                DecisionReasonCategory.DATA_QUALITY,
                reason.category()
            )
        }
    }

    @Test
    fun `informational reasons are correctly categorized`() {
        val informationalReasons = listOf(
            DecisionReason.ACCEPT_HIGH_PROFITABILITY,
            DecisionReason.CONTEXT_BELOW_REQUIRED_HOURLY_RATE,
            DecisionReason.CONTEXT_ABOVE_REQUIRED_HOURLY_RATE,
            DecisionReason.CONTEXT_DAILY_TARGET_REACHED
        )
        informationalReasons.forEach { reason ->
            assertEquals(
                "DecisionReason.$reason should be INFORMATIONAL",
                DecisionReasonCategory.INFORMATIONAL,
                reason.category()
            )
        }
    }

    @Test
    fun `OpportunityAssessment is constructible with all fields`() {
        val assessment = OpportunityAssessment(
            quality = OpportunityQuality.EXCEPTIONAL,
            recommendation = Recommendation.EVALUATE,
            confidence = Confidence.HIGH,
            speDecision = Decision.REJECT,
            speOverridden = true,
            overrideJustification = "Only operational reasons, exceptional profitability",
            reasoning = listOf("grossPerHour 70.9 exceeds median by 3x")
        )

        assertEquals(OpportunityQuality.EXCEPTIONAL, assessment.quality)
        assertEquals(Recommendation.EVALUATE, assessment.recommendation)
        assertEquals(Confidence.HIGH, assessment.confidence)
        assertEquals(Decision.REJECT, assessment.speDecision)
        assertTrue(assessment.speOverridden)
        assertEquals("Only operational reasons, exceptional profitability", assessment.overrideJustification)
        assertEquals(1, assessment.reasoning.size)
    }

    @Test
    fun `OpportunityAssessment is constructible without override`() {
        val assessment = OpportunityAssessment(
            quality = OpportunityQuality.GOOD,
            recommendation = Recommendation.TAKE,
            confidence = Confidence.HIGH,
            speDecision = Decision.ACCEPT,
            speOverridden = false,
            overrideJustification = null,
            reasoning = listOf("All economic signals positive")
        )

        assertFalse(assessment.speOverridden)
        assertNull(assessment.overrideJustification)
    }

    @Test
    fun `HistoricalContext is constructible with nulls for insufficient data`() {
        val context = HistoricalContext(
            medianGrossPerHour = null,
            medianGrossPerKm = null,
            medianNetPerHour = null,
            sampleSize = 0,
            timeSlotMedianGrossPerHour = null,
            timeSlotSampleSize = 0,
            lastUpdated = System.currentTimeMillis()
        )

        assertEquals(0, context.sampleSize)
        assertNull(context.medianGrossPerHour)
    }

    @Test
    fun `HistoricalContext is constructible with full data`() {
        val context = HistoricalContext(
            medianGrossPerHour = 23.5,
            medianGrossPerKm = 1.15,
            medianNetPerHour = 18.0,
            sampleSize = 150,
            timeSlotMedianGrossPerHour = 25.0,
            timeSlotSampleSize = 30,
            lastUpdated = 1700000000000L
        )

        assertEquals(150, context.sampleSize)
        assertEquals(23.5, context.medianGrossPerHour!!, 0.001)
    }

    @Test
    fun `OpportunityEvaluator interface is implementable`() {
        val stub = object : OpportunityEvaluator {
            override fun evaluate(
                evaluation: TripEvaluation,
                trip: Trip,
                historicalContext: HistoricalContext?,
                kinematicsSource: KinematicsSource
            ): OpportunityAssessment {
                return OpportunityAssessment(
                    quality = OpportunityQuality.POOR,
                    recommendation = Recommendation.SKIP,
                    confidence = Confidence.LOW,
                    speDecision = evaluation.decision,
                    speOverridden = false,
                    overrideJustification = null,
                    reasoning = emptyList()
                )
            }
        }

        assertTrue(stub is OpportunityEvaluator)
    }

    @Test
    fun `all enum values exist`() {
        assertEquals(4, OpportunityQuality.entries.size)
        assertEquals(3, Recommendation.entries.size)
        assertEquals(3, Confidence.entries.size)
        assertEquals(2, DecisionMode.entries.size)
        assertEquals(4, DecisionReasonCategory.entries.size)
    }
}
