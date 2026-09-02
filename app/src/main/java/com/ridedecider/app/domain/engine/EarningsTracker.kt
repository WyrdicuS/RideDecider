package com.ridedecider.app.domain.engine

import com.ridedecider.app.data.local.room.entity.DecisionSnapshotEntity
import com.ridedecider.app.domain.model.DriverEconomicContext
import com.ridedecider.app.domain.model.DriverGoals
import com.ridedecider.app.domain.model.EarningsProgress
import com.ridedecider.app.domain.model.GoalPeriod
import com.ridedecider.app.domain.model.RecordedTrip
import com.ridedecider.app.domain.model.Trip
import com.ridedecider.app.domain.model.TripEvaluation
import com.ridedecider.app.domain.model.TripTrackingStatus
import com.ridedecider.app.domain.repository.DriverGoalsRepository
import com.ridedecider.app.domain.repository.EarningsRepository
import java.util.Calendar
import java.util.UUID

/**
 * Gestor de seguimiento económico y cálculo de ciclos temporales (diario, semanal, mensual).
 * Coordina la acumulación de ganancias reales y genera el contexto económico [DriverEconomicContext].
 */
class EarningsTracker(
    private val goalsRepository: DriverGoalsRepository,
    private val earningsRepository: EarningsRepository
) {

    val stateMachine = TripLifecycleStateMachine()

    val currentTripId: String?
        get() = when (val s = stateMachine.currentState) {
            is com.ridedecider.app.domain.model.TripLifecycleState.PendingAcceptance -> s.trip.id
            is com.ridedecider.app.domain.model.TripLifecycleState.Assigned -> s.trip.id
            is com.ridedecider.app.domain.model.TripLifecycleState.ActiveTrip -> s.trip.id
            is com.ridedecider.app.domain.model.TripLifecycleState.OfferDetected -> s.trip.id
            else -> null
        }

    /**
     * Registra una oferta evaluada por el DecisionEngine y congela su snapshot inmutable de decisión (Learning Data Foundation).
     * Nota: Este registro NO suma a las ganancias reales hasta que se confirme como COMPLETED.
     */
    suspend fun recordEvaluatedOffer(trip: Trip, evaluation: TripEvaluation, timestamp: Long = System.currentTimeMillis()): RecordedTrip {
        stateMachine.onOfferDetected(trip, evaluation, timestamp)
        val recordedTrip = RecordedTrip(
            id = trip.id,
            trip = trip,
            evaluation = evaluation,
            status = TripTrackingStatus.EVALUATED,
            recordedTimestamp = timestamp
        )
        earningsRepository.recordTrip(recordedTrip)

        // Congelar fotografía inmutable de la decisión en t0 (Learning Data Foundation)
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
        val minuteOfHour = calendar.get(Calendar.MINUTE)
        val timeBucket = when (hourOfDay) {
            in 7..9 -> "MORNING_RUSH"
            in 10..13 -> "DAY"
            in 14..16 -> "MIDDAY_EXIT"
            in 17..20 -> "EVENING_RUSH"
            else -> "NIGHT"
        }

        val snapshotEntity = DecisionSnapshotEntity(
            snapshotId = UUID.randomUUID().toString(),
            instanceId = trip.id,
            tripId = trip.id,
            recordedTimestamp = timestamp,
            appVersion = "1.0.1",
            engineVersion = "2.0",
            offerType = trip.offerType.name,
            category = trip.category.name,
            estimatedFareEur = trip.rawFare,
            currency = trip.currency,
            estimatedPickupDistanceKm = trip.pickupDistanceKm,
            estimatedPickupDurationMinutes = trip.pickupDurationMinutes,
            estimatedTripDistanceKm = trip.tripDistanceKm,
            estimatedTripDurationMinutes = trip.tripDurationMinutes,
            estimatedTotalDistanceKm = evaluation.metrics?.totalDistanceKm,
            estimatedTotalDurationMinutes = evaluation.metrics?.totalDurationMinutes,
            grossPerKm = evaluation.metrics?.grossPerKm,
            grossPerHour = evaluation.metrics?.grossPerHour,
            effectiveGrossPerKm = evaluation.metrics?.effectiveGrossPerKm,
            estimatedOperatingCost = evaluation.metrics?.estimatedOperatingCost,
            estimatedNetProfit = evaluation.metrics?.netProfit,
            netPerHour = evaluation.metrics?.netPerHour,
            pickupSpeedKmh = evaluation.metrics?.pickupSpeedKmh,
            pickupDistanceRatio = evaluation.metrics?.pickupDistanceRatio ?: 0.0,
            pickupTimeRatio = evaluation.metrics?.pickupTimeRatio ?: 0.0,
            profitabilityScore = evaluation.metrics?.profitabilityScore ?: 100,
            decision = evaluation.decision.name,
            decisionReasonsCommaSeparated = evaluation.reasons.joinToString(",") { it.name },
            profitabilityLevel = evaluation.profitabilityLevel.name,
            kinematicsSource = "LEGACY_UNSPECIFIED",
            dayOfWeek = dayOfWeek,
            hourOfDay = hourOfDay,
            minuteOfHour = minuteOfHour,
            timeBucket = timeBucket,
            wazeEstimatedDurationMinutes = null,
            wazeAvailable = false,
            uberWazeDurationDeltaMinutes = null,
            wazeEstimateTimestamp = null,
            actualDistanceKm = null,
            actualDurationMinutes = null,
            actualPickupDurationMinutes = null,
            actualBaseFareEur = null,
            waitingCompensationEur = null,
            cancellationFeeEur = null,
            tipEur = null,
            finalEarningsEur = null
        )

        try {
            earningsRepository.saveDecisionSnapshot(snapshotEntity)
        } catch (_: Exception) {}

        return recordedTrip
    }

    /**
     * Notifica la desaparición o expiración de la oferta sin asignación.
     */
    fun onOfferDismissed(timestamp: Long = System.currentTimeMillis()) {
        stateMachine.onOfferDismissed(timestamp)
    }

    /**
     * Marca un viaje como aceptado por el conductor en Uber Driver.
     */
    suspend fun markTripAccepted(tripId: String) {
        stateMachine.onOfferAcceptedManually(tripId)
        earningsRepository.updateTripStatus(tripId, TripTrackingStatus.ACCEPTED_BY_DRIVER)
    }

    /**
     * Notifica la asignación automática de un Trip Radar por parte de Uber.
     */
    suspend fun markRadarAutoAssigned(tripId: String) {
        stateMachine.onRadarAutoAssigned(tripId)
        earningsRepository.updateTripStatus(tripId, TripTrackingStatus.ACCEPTED_BY_DRIVER)
    }

    /**
     * Notifica el inicio efectivo de navegación o recogida de un viaje activo.
     */
    suspend fun markActiveTripStarted() {
        val state = stateMachine.onActiveTripStarted()
        if (state is com.ridedecider.app.domain.model.TripLifecycleState.ActiveTrip) {
            earningsRepository.updateTripStatus(state.trip.id, TripTrackingStatus.ACCEPTED_BY_DRIVER)
        }
    }

    /**
     * Registra la finalización real de un viaje con su importe y duración definitivos.
     * Únicamente este estado computa para el progreso económico del conductor.
     */
    suspend fun completeTrip(
        tripId: String,
        finalEarnings: Double,
        durationMinutes: Double,
        completedTimestamp: Long = System.currentTimeMillis()
    ) {
        stateMachine.onTripCompleted(finalEarnings, durationMinutes, completedTimestamp)
        earningsRepository.updateTripStatus(
            tripId = tripId,
            status = TripTrackingStatus.COMPLETED,
            finalEarnings = finalEarnings,
            completedTimestamp = completedTimestamp,
            durationMinutes = durationMinutes
        )
        // Actualizar los resultados reales en el snapshot de decisión correspondiente
        earningsRepository.updateSnapshotActuals(
            tripId = tripId,
            actualDur = durationMinutes,
            finalEarnings = finalEarnings
        )
    }

    /**
     * Registra la cancelación de un viaje asignado o activo (pasajero, conductor, Uber, No-Show).
     * Si Uber acredita una compensación por cancelación (cancellationFee), se registra separadamente.
     */
    suspend fun cancelTrip(
        tripId: String,
        reason: com.ridedecider.app.domain.model.CancellationReason,
        cancellationFee: Double? = null,
        timestamp: Long = System.currentTimeMillis()
    ) {
        stateMachine.onTripCancelled(reason, cancellationFee, timestamp)
        val trackingStatus = when (reason) {
            com.ridedecider.app.domain.model.CancellationReason.RIDER -> TripTrackingStatus.CANCELLED_BY_RIDER
            com.ridedecider.app.domain.model.CancellationReason.DRIVER -> TripTrackingStatus.CANCELLED_BY_DRIVER
            com.ridedecider.app.domain.model.CancellationReason.UBER -> TripTrackingStatus.CANCELLED_BY_UBER
            com.ridedecider.app.domain.model.CancellationReason.NO_SHOW -> TripTrackingStatus.CANCELLED_NO_SHOW
            com.ridedecider.app.domain.model.CancellationReason.UNKNOWN -> TripTrackingStatus.CANCELLED_UNKNOWN
        }
        earningsRepository.updateTripStatus(
            tripId = tripId,
            status = trackingStatus,
            finalEarnings = null,
            completedTimestamp = null,
            durationMinutes = null,
            cancellationFee = cancellationFee,
            cancellationReason = reason,
            cancelledTimestamp = timestamp
        )
        // Actualizar la compensación de cancelación en el snapshot si aplica
        if (cancellationFee != null && cancellationFee > 0.0) {
            earningsRepository.updateSnapshotActuals(
                tripId = tripId,
                cancellationFee = cancellationFee
            )
        }
    }

    /**
     * Genera el progreso del día actual (00:00:00.000 a 23:59:59.999 en zona horaria local).
     */
    suspend fun getDailyProgress(timestamp: Long = System.currentTimeMillis()): EarningsProgress {
        val goals = goalsRepository.getGoals()
        val (start, end) = getDayBounds(timestamp)
        val earnedEur = earningsRepository.getCompletedEarningsBetween(start, end)
        val workedMinutes = earningsRepository.getWorkedMinutesBetween(start, end)
        val workedHours = workedMinutes / 60.0

        return TargetProgressCalculator.calculateProgress(
            period = GoalPeriod.DAILY,
            targetEur = goals.dailyTargetEur,
            earnedEur = earnedEur,
            plannedHours = goals.dailyPlannedHours,
            workedHours = workedHours
        )
    }

    /**
     * Genera el progreso de la semana actual (Lunes 00:00 a Domingo 23:59 en zona horaria local).
     */
    suspend fun getWeeklyProgress(timestamp: Long = System.currentTimeMillis()): EarningsProgress {
        val goals = goalsRepository.getGoals()
        val (start, end) = getWeekBounds(timestamp)
        val earnedEur = earningsRepository.getCompletedEarningsBetween(start, end)
        val workedMinutes = earningsRepository.getWorkedMinutesBetween(start, end)
        val workedHours = workedMinutes / 60.0

        return TargetProgressCalculator.calculateProgress(
            period = GoalPeriod.WEEKLY,
            targetEur = goals.weeklyTargetEur,
            earnedEur = earnedEur,
            plannedHours = goals.weeklyPlannedHours,
            workedHours = workedHours
        )
    }

    /**
     * Genera el progreso del mes actual (Día 1 00:00 a fin de mes 23:59 en zona horaria local).
     */
    suspend fun getMonthlyProgress(timestamp: Long = System.currentTimeMillis()): EarningsProgress {
        val goals = goalsRepository.getGoals()
        val (start, end) = getMonthBounds(timestamp)
        val earnedEur = earningsRepository.getCompletedEarningsBetween(start, end)
        val workedMinutes = earningsRepository.getWorkedMinutesBetween(start, end)
        val workedHours = workedMinutes / 60.0

        return TargetProgressCalculator.calculateProgress(
            period = GoalPeriod.MONTHLY,
            targetEur = goals.monthlyTargetEur,
            earnedEur = earnedEur,
            plannedHours = goals.monthlyPlannedHours,
            workedHours = workedHours
        )
    }

    /**
     * Genera el contexto económico unificado para el DecisionEngine.
     */
    suspend fun getEconomicContext(timestamp: Long = System.currentTimeMillis()): DriverEconomicContext {
        return DriverEconomicContext(
            dailyProgress = getDailyProgress(timestamp),
            weeklyProgress = getWeeklyProgress(timestamp),
            monthlyProgress = getMonthlyProgress(timestamp),
            timestamp = timestamp
        )
    }

    // =========================================================================
    // Lógica de límites de calendario sin dependencias externas
    // =========================================================================

    fun getDayBounds(timestamp: Long): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val start = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val end = calendar.timeInMillis

        return Pair(start, end)
    }

    fun getWeekBounds(timestamp: Long): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
            firstDayOfWeek = Calendar.MONDAY
        }
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val start = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_WEEK, 6)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val end = calendar.timeInMillis

        return Pair(start, end)
    }

    fun getMonthBounds(timestamp: Long): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val start = calendar.timeInMillis

        val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        calendar.set(Calendar.DAY_OF_MONTH, maxDay)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val end = calendar.timeInMillis

        return Pair(start, end)
    }

    suspend fun clearAllTrips() {
        stateMachine.reset()
        earningsRepository.clearAllTrips()
    }
}
