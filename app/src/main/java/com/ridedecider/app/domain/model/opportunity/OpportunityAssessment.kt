package com.ridedecider.app.domain.model.opportunity

import com.ridedecider.app.domain.model.Decision

data class OpportunityAssessment(
    val quality: OpportunityQuality,
    val recommendation: Recommendation,
    val confidence: Confidence,
    val speDecision: Decision,
    val speOverridden: Boolean,
    val overrideJustification: String?,
    val reasoning: List<String>
)
