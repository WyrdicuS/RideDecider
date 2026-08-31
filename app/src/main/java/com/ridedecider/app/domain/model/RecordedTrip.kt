package com.ridedecider.app.domain.model

/**
 * Registro inmutable de un viaje en el sistema de seguimiento económico.
 */
data class RecordedTrip(
    val id: String,
    val trip: Trip,
    val evaluation: TripEvaluation,
    val status: TripTrackingStatus,
    val recordedTimestamp: Long,
    val completedTimestamp: Long? = null,
    val finalEarningsEur: Double? = null,
    val durationMinutes: Double? = null,
    val cancellationFeeEur: Double? = null,
    val cancellationReason: CancellationReason? = null,
    val cancelledTimestamp: Long? = null
)
