package com.ridedecider.app.domain.model.opportunity

data class HistoricalContext(
    val medianGrossPerHour: Double?,
    val medianGrossPerKm: Double?,
    val medianNetPerHour: Double?,
    val sampleSize: Int,
    val timeSlotMedianGrossPerHour: Double?,
    val timeSlotSampleSize: Int,
    val lastUpdated: Long
)
