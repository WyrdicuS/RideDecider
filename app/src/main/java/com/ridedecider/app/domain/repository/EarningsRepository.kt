package com.ridedecider.app.domain.repository

import com.ridedecider.app.domain.model.RecordedTrip
import com.ridedecider.app.domain.model.TripTrackingStatus

/**
 * Contrato de repositorio para el registro y consulta de viajes y ganancias del conductor.
 */
interface EarningsRepository {
    /**
     * Registra un viaje en el historial.
     */
    suspend fun recordTrip(trip: RecordedTrip)

    /**
     * Actualiza el estado de un viaje existente (ej. EVALUATED -> ACCEPTED_BY_DRIVER -> COMPLETED o CANCELLED).
     */
    suspend fun updateTripStatus(
        tripId: String,
        status: TripTrackingStatus,
        finalEarnings: Double? = null,
        completedTimestamp: Long? = null,
        durationMinutes: Double? = null,
        cancellationFee: Double? = null,
        cancellationReason: com.ridedecider.app.domain.model.CancellationReason? = null,
        cancelledTimestamp: Long? = null
    )

    /**
     * Obtiene los viajes registrados dentro de un rango de tiempo.
     */
    suspend fun getTripsBetween(startTimestamp: Long, endTimestamp: Long): List<RecordedTrip>

    /**
     * Obtiene el total acumulado de ganancias completadas ([TripTrackingStatus.COMPLETED]) en un rango.
     */
    suspend fun getCompletedEarningsBetween(startTimestamp: Long, endTimestamp: Long): Double

    /**
     * Obtiene el total de minutos trabajados en viajes completados en un rango.
     */
    suspend fun getWorkedMinutesBetween(startTimestamp: Long, endTimestamp: Long): Double

    /**
     * Borra todos los viajes registrados del historial.
     */
    suspend fun clearAllTrips()
}
