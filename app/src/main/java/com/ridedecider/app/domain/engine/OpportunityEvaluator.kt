package com.ridedecider.app.domain.engine

import com.ridedecider.app.data.accessibility.uber.KinematicsSource
import com.ridedecider.app.domain.model.Trip
import com.ridedecider.app.domain.model.TripEvaluation
import com.ridedecider.app.domain.model.opportunity.HistoricalContext
import com.ridedecider.app.domain.model.opportunity.OpportunityAssessment

interface OpportunityEvaluator {

    fun evaluate(
        evaluation: TripEvaluation,
        trip: Trip,
        historicalContext: HistoricalContext?,
        kinematicsSource: KinematicsSource
    ): OpportunityAssessment
}
