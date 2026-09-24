package com.ridedecider.app.domain.model.opportunity

import com.ridedecider.app.domain.model.DecisionReason

enum class DecisionReasonCategory {
    ECONOMIC,
    OPERATIONAL,
    INFORMATIONAL,
    DATA_QUALITY
}

fun DecisionReason.category(): DecisionReasonCategory = when (this) {
    DecisionReason.REJECT_LOW_HOURLY_RATE -> DecisionReasonCategory.ECONOMIC
    DecisionReason.REJECT_LOW_KM_RATE -> DecisionReasonCategory.ECONOMIC
    DecisionReason.REJECT_LOW_EFFECTIVE_KM_RATE -> DecisionReasonCategory.ECONOMIC
    DecisionReason.REJECT_LOW_NET_PROFIT -> DecisionReasonCategory.ECONOMIC
    DecisionReason.REJECT_LOW_NET_HOURLY_RATE -> DecisionReasonCategory.ECONOMIC

    DecisionReason.REJECT_EXCESSIVE_PICKUP_DISTANCE -> DecisionReasonCategory.OPERATIONAL
    DecisionReason.REJECT_EXCESSIVE_PICKUP_TIME -> DecisionReasonCategory.OPERATIONAL

    DecisionReason.UNKNOWN_MISSING_FARE -> DecisionReasonCategory.DATA_QUALITY
    DecisionReason.UNKNOWN_MISSING_PICKUP_DISTANCE -> DecisionReasonCategory.DATA_QUALITY
    DecisionReason.UNKNOWN_MISSING_PICKUP_TIME -> DecisionReasonCategory.DATA_QUALITY
    DecisionReason.UNKNOWN_MISSING_TRIP_DISTANCE -> DecisionReasonCategory.DATA_QUALITY
    DecisionReason.UNKNOWN_MISSING_TRIP_TIME -> DecisionReasonCategory.DATA_QUALITY
    DecisionReason.UNKNOWN_INVALID_DATA -> DecisionReasonCategory.DATA_QUALITY

    DecisionReason.ACCEPT_HIGH_PROFITABILITY -> DecisionReasonCategory.INFORMATIONAL
    DecisionReason.CONTEXT_BELOW_REQUIRED_HOURLY_RATE -> DecisionReasonCategory.INFORMATIONAL
    DecisionReason.CONTEXT_ABOVE_REQUIRED_HOURLY_RATE -> DecisionReasonCategory.INFORMATIONAL
    DecisionReason.CONTEXT_DAILY_TARGET_REACHED -> DecisionReasonCategory.INFORMATIONAL
}
