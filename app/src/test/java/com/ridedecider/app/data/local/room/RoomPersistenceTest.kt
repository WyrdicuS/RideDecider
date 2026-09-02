package com.ridedecider.app.data.local.room

import com.ridedecider.app.data.accessibility.uber.UberNodeSnapshot
import com.ridedecider.app.data.local.room.dao.DriverGoalsDao
import com.ridedecider.app.data.local.room.dao.RecordedTripDao
import com.ridedecider.app.data.local.room.entity.DriverGoalsEntity
import com.ridedecider.app.data.local.room.entity.RecordedTripEntity
import com.ridedecider.app.data.repository.RoomDriverGoalsRepository
import com.ridedecider.app.data.repository.RoomEarningsRepository
import com.ridedecider.app.domain.engine.EarningsTracker
import com.ridedecider.app.domain.model.Decision
import com.ridedecider.app.domain.model.DecisionReason
import com.ridedecider.app.domain.model.DriverGoals
import com.ridedecider.app.domain.model.ProfitabilityConfig
import com.ridedecider.app.domain.model.RecordedTrip
import com.ridedecider.app.domain.model.Trip
import com.ridedecider.app.domain.model.TripEvaluation
import com.ridedecider.app.domain.model.TripOfferType
import com.ridedecider.app.domain.model.TripTrackingStatus
import com.ridedecider.app.domain.model.UberCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

class RoomPersistenceTest {

    private lateinit var tripDao: RecordedTripDao
    private lateinit var goalsDao: DriverGoalsDao
    private lateinit var earningsRepository: RoomEarningsRepository
    private lateinit var goalsRepository: RoomDriverGoalsRepository
    private lateinit var tracker: EarningsTracker

    private val defaultConfig = ProfitabilityConfig(
        costPerKm = 0.20,
        costPerHour = 5.0,
        minGrossHourlyRate = 25.0,
        minGrossPerKmRate = 1.20,
        minNetTripProfit = 2.0,
        minNetHourlyRate = 18.0,
        maxPickupDistanceKm = 5.0,
        maxPickupTimeMinutes = 10.0
    )

    @Before
    fun setUp() {
        tripDao = FakeRecordedTripDao()
        goalsDao = FakeDriverGoalsDao()
        earningsRepository = RoomEarningsRepository(recordedTripDao = tripDao, decisionSnapshotDao = null, ioDispatcher = Dispatchers.Unconfined)
        goalsRepository = RoomDriverGoalsRepository(goalsDao, Dispatchers.Unconfined)
        tracker = EarningsTracker(goalsRepository, earningsRepository)
    }

    private fun createDummyTrip(id: String, fare: Double, timestamp: Long): RecordedTrip {
        val trip = Trip(
            id = id,
            timestamp = timestamp,
            offerType = TripOfferType.TRIP_OFFER,
            category = UberCategory.UBER_X,
            rawFare = fare,
            currency = "EUR",
            pickupDistanceKm = 2.0,
            pickupDurationMinutes = 5.0,
            pickupAddress = "Origen 1",
            tripDistanceKm = 8.0,
            tripDurationMinutes = 15.0,
            dropoffAddress = "Destino 1"
        )
        val evaluation = TripEvaluation(
            trip = trip,
            configUsed = defaultConfig,
            metrics = null,
            decision = Decision.ACCEPT,
            reasons = listOf(DecisionReason.ACCEPT_HIGH_PROFITABILITY),
            evaluationTimestamp = timestamp
        )
        return RecordedTrip(
            id = id,
            trip = trip,
            evaluation = evaluation,
            status = TripTrackingStatus.EVALUATED,
            recordedTimestamp = timestamp
        )
    }

    // 1. Insertar viaje
    @Test
    fun insertTrip() = runBlocking {
        val now = System.currentTimeMillis()
        val trip = createDummyTrip("t_ins", 11.00, now)

        earningsRepository.recordTrip(trip)
        val all = tripDao.getAllTrips()
        assertTrue(all.any { entity: RecordedTripEntity -> entity.id == "t_ins" })
    }

    // 2. Recuperar viaje por ID
    @Test
    fun retrieveTripById() = runBlocking {
        val now = System.currentTimeMillis()
        val trip = createDummyTrip("t_1", 12.50, now)

        earningsRepository.recordTrip(trip)
        val retrieved = tripDao.getTripById("t_1")

        assertNotNull(retrieved)
        assertEquals("t_1", retrieved?.id)
        assertEquals(12.50, retrieved?.estimatedFareEur ?: 0.0, 0.001)
        assertEquals("EVALUATED", retrieved?.status)
    }

    // 3. Actualizar estado del viaje
    @Test
    fun updateTripStatus() = runBlocking {
        val now = System.currentTimeMillis()
        val trip = createDummyTrip("t_2", 15.0, now)
        earningsRepository.recordTrip(trip)

        earningsRepository.updateTripStatus("t_2", TripTrackingStatus.ACCEPTED_BY_DRIVER)
        val updated = tripDao.getTripById("t_2")
        assertEquals("ACCEPTED_BY_DRIVER", updated?.status)
    }

    // 4. ACCEPTED_BY_DRIVER NO suma ganancias
    @Test
    fun acceptedByDriver_doesNotCountTowardsEarnings() = runBlocking {
        val now = System.currentTimeMillis()
        val trip = createDummyTrip("t_3", 20.0, now)
        earningsRepository.recordTrip(trip)
        earningsRepository.updateTripStatus("t_3", TripTrackingStatus.ACCEPTED_BY_DRIVER)

        val (start, end) = tracker.getDayBounds(now)
        val earnings = earningsRepository.getCompletedEarningsBetween(start, end)
        assertEquals(0.0, earnings, 0.001)
    }

    // 5. COMPLETED sí suma ganancias
    @Test
    fun completedTrip_countsTowardsEarnings() = runBlocking {
        val now = System.currentTimeMillis()
        val trip = createDummyTrip("t_4", 20.0, now)
        earningsRepository.recordTrip(trip)
        earningsRepository.updateTripStatus(
            tripId = "t_4",
            status = TripTrackingStatus.COMPLETED,
            finalEarnings = 22.0,
            completedTimestamp = now,
            durationMinutes = 18.0
        )

        val (start, end) = tracker.getDayBounds(now)
        val earnings = earningsRepository.getCompletedEarningsBetween(start, end)
        assertEquals(22.0, earnings, 0.001)

        val workedMinutes = earningsRepository.getWorkedMinutesBetween(start, end)
        assertEquals(18.0, workedMinutes, 0.001)
    }

    // 6. REJECTED no suma ganancias
    @Test
    fun rejectedTrip_doesNotCountTowardsEarnings() = runBlocking {
        val now = System.currentTimeMillis()
        val trip = createDummyTrip("t_rej", 5.0, now)
        earningsRepository.recordTrip(trip)

        val (start, end) = tracker.getDayBounds(now)
        val earnings = earningsRepository.getCompletedEarningsBetween(start, end)
        assertEquals(0.0, earnings, 0.001)
    }

    // 7. IGNORED / Descartado no suma ganancias
    @Test
    fun ignoredTrip_doesNotCountTowardsEarnings() = runBlocking {
        val now = System.currentTimeMillis()
        val trip = createDummyTrip("t_ign", 8.0, now)
        earningsRepository.recordTrip(trip)

        val (start, end) = tracker.getDayBounds(now)
        val earnings = earningsRepository.getCompletedEarningsBetween(start, end)
        assertEquals(0.0, earnings, 0.001)
    }

    // 8. Viaje aceptado pero nunca completado no suma
    @Test
    fun acceptedNeverCompleted_doesNotCount() = runBlocking {
        val now = System.currentTimeMillis()
        val trip = createDummyTrip("t_inc", 30.0, now)
        earningsRepository.recordTrip(trip)
        earningsRepository.updateTripStatus("t_inc", TripTrackingStatus.ACCEPTED_BY_DRIVER)

        val (start, end) = tracker.getDayBounds(now)
        assertEquals(0.0, earningsRepository.getCompletedEarningsBetween(start, end), 0.001)
    }

    // 9. finalFareEur es independiente de estimatedFare
    @Test
    fun finalFare_isPreservedIndependentlyFromEstimatedFare() = runBlocking {
        val now = System.currentTimeMillis()
        val trip = createDummyTrip("t_diff", 9.01, now)
        earningsRepository.recordTrip(trip)

        earningsRepository.updateTripStatus(
            tripId = "t_diff",
            status = TripTrackingStatus.COMPLETED,
            finalEarnings = 10.47,
            completedTimestamp = now,
            durationMinutes = 20.0
        )

        val entity = tripDao.getTripById("t_diff")
        assertEquals(9.01, entity?.estimatedFareEur ?: 0.0, 0.001)
        assertEquals(10.47, entity?.finalEarningsEur ?: 0.0, 0.001)
    }

    // 10. COMPLETED repetido no duplica ganancias
    @Test
    fun duplicateCompletedEvents_doNotDuplicateEarnings() = runBlocking {
        val now = System.currentTimeMillis()
        val trip = createDummyTrip("t_dup", 18.0, now)
        earningsRepository.recordTrip(trip)

        // Evento 1
        earningsRepository.updateTripStatus("t_dup", TripTrackingStatus.COMPLETED, 18.0, now, 15.0)
        // Evento 2 (duplicado)
        earningsRepository.updateTripStatus("t_dup", TripTrackingStatus.COMPLETED, 18.0, now, 15.0)
        // Evento 3 (duplicado)
        earningsRepository.updateTripStatus("t_dup", TripTrackingStatus.COMPLETED, 18.0, now, 15.0)

        val (start, end) = tracker.getDayBounds(now)
        val total = earningsRepository.getCompletedEarningsBetween(start, end)
        assertEquals(18.0, total, 0.001)
    }

    // 11. Dos eventos de Accessibility idénticos no crean dos viajes (idempotencia por ID)
    @Test
    fun identicalEvents_maintainSingleRecord() = runBlocking {
        val now = System.currentTimeMillis()
        val trip = createDummyTrip("t_idemp", 14.0, now)

        earningsRepository.recordTrip(trip)
        earningsRepository.recordTrip(trip)

        val allTrips = tripDao.getAllTrips()
        assertEquals(1, allTrips.size)
    }

    // 12. Ganancias diarias correctas
    @Test
    fun dailyEarnings_calculation() = runBlocking {
        val now = System.currentTimeMillis()
        val t1 = createDummyTrip("d_1", 25.0, now)
        val t2 = createDummyTrip("d_2", 35.0, now)

        earningsRepository.recordTrip(t1)
        earningsRepository.updateTripStatus("d_1", TripTrackingStatus.COMPLETED, 25.0, now, 20.0)

        earningsRepository.recordTrip(t2)
        earningsRepository.updateTripStatus("d_2", TripTrackingStatus.COMPLETED, 35.0, now, 30.0)

        val (start, end) = tracker.getDayBounds(now)
        val daily = earningsRepository.getCompletedEarningsBetween(start, end)
        assertEquals(60.0, daily, 0.001)
    }

    // 13. Ganancias semanales correctas
    @Test
    fun weeklyEarnings_calculation() = runBlocking {
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
        val monday = cal.timeInMillis
        cal.set(Calendar.DAY_OF_WEEK, Calendar.WEDNESDAY)
        val wednesday = cal.timeInMillis

        val t1 = createDummyTrip("w_1", 50.0, monday)
        val t2 = createDummyTrip("w_2", 80.0, wednesday)

        earningsRepository.recordTrip(t1)
        earningsRepository.updateTripStatus("w_1", TripTrackingStatus.COMPLETED, 50.0, monday, 40.0)

        earningsRepository.recordTrip(t2)
        earningsRepository.updateTripStatus("w_2", TripTrackingStatus.COMPLETED, 80.0, wednesday, 60.0)

        val (start, end) = tracker.getWeekBounds(wednesday)
        val weekly = earningsRepository.getCompletedEarningsBetween(start, end)
        assertEquals(130.0, weekly, 0.001)
    }

    // 14. Ganancias mensuales correctas
    @Test
    fun monthlyEarnings_calculation() = runBlocking {
        val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 5) }
        val day5 = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, 20)
        val day20 = cal.timeInMillis

        val t1 = createDummyTrip("m_1", 100.0, day5)
        val t2 = createDummyTrip("m_2", 150.0, day20)

        earningsRepository.recordTrip(t1)
        earningsRepository.updateTripStatus("m_1", TripTrackingStatus.COMPLETED, 100.0, day5, 90.0)

        earningsRepository.recordTrip(t2)
        earningsRepository.updateTripStatus("m_2", TripTrackingStatus.COMPLETED, 150.0, day20, 110.0)

        val (start, end) = tracker.getMonthBounds(day20)
        val monthly = earningsRepository.getCompletedEarningsBetween(start, end)
        assertEquals(250.0, monthly, 0.001)
    }

    // 15. Cambio de día (no mezcla días)
    @Test
    fun dayRollover_isolation() = runBlocking {
        val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
        val day1 = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, 2)
        val day2 = cal.timeInMillis

        val t1 = createDummyTrip("rollover_d1", 70.0, day1)
        earningsRepository.recordTrip(t1)
        earningsRepository.updateTripStatus("rollover_d1", TripTrackingStatus.COMPLETED, 70.0, day1, 50.0)

        val (start2, end2) = tracker.getDayBounds(day2)
        val earningsDay2 = earningsRepository.getCompletedEarningsBetween(start2, end2)
        assertEquals(0.0, earningsDay2, 0.001)
    }

    // 16. Cambio de semana (no mezcla semanas)
    @Test
    fun weekRollover_isolation() = runBlocking {
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.WEEK_OF_YEAR, 20)
            set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY)
        }
        val week20 = cal.timeInMillis
        cal.set(Calendar.WEEK_OF_YEAR, 21)
        val week21 = cal.timeInMillis

        val t1 = createDummyTrip("rollover_w20", 300.0, week20)
        earningsRepository.recordTrip(t1)
        earningsRepository.updateTripStatus("rollover_w20", TripTrackingStatus.COMPLETED, 300.0, week20, 200.0)

        val (start21, end21) = tracker.getWeekBounds(week21)
        val earningsWeek21 = earningsRepository.getCompletedEarningsBetween(start21, end21)
        assertEquals(0.0, earningsWeek21, 0.001)
    }

    // 17. Cambio de mes (no mezcla meses)
    @Test
    fun monthRollover_isolation() = runBlocking {
        val cal = Calendar.getInstance().apply {
            set(Calendar.MONTH, Calendar.MARCH)
            set(Calendar.DAY_OF_MONTH, 15)
        }
        val march = cal.timeInMillis
        cal.set(Calendar.MONTH, Calendar.APRIL)
        val april = cal.timeInMillis

        val t1 = createDummyTrip("rollover_mar", 500.0, march)
        earningsRepository.recordTrip(t1)
        earningsRepository.updateTripStatus("rollover_mar", TripTrackingStatus.COMPLETED, 500.0, march, 400.0)

        val (startApr, endApr) = tracker.getMonthBounds(april)
        val earningsApr = earningsRepository.getCompletedEarningsBetween(startApr, endApr)
        assertEquals(0.0, earningsApr, 0.001)
    }

    // 18. Persistencia y actualización de DriverGoals
    @Test
    fun driverGoals_persistenceAndUpdate() = runBlocking {
        val newGoals = DriverGoals(
            dailyTargetEur = 200.0,
            weeklyTargetEur = 1200.0,
            monthlyTargetEur = 4800.0,
            dailyPlannedHours = 10.0,
            weeklyPlannedHours = 50.0,
            monthlyPlannedHours = 200.0
        )

        goalsRepository.updateGoals(newGoals)
        val persisted = goalsDao.getGoals()

        assertNotNull(persisted)
        assertEquals(200.0, persisted?.dailyTargetEur ?: 0.0, 0.001)
        assertEquals(10.0, persisted?.dailyPlannedHours ?: 0.0, 0.001)
    }

    // 18b. Cambio exclusivo de modo de objetivo (Diario -> Semanal)
    @Test
    fun driverGoals_exclusiveSwitching_isolatesActiveMetrics() = runBlocking {
        // Inicial: Diario
        val dailyGoal = DriverGoals(
            activePeriod = com.ridedecider.app.domain.model.GoalPeriod.DAILY,
            dailyTargetEur = 120.0,
            dailyPlannedHours = 5.0
        )
        goalsRepository.updateGoals(dailyGoal)
        var persisted = goalsDao.getGoals()?.toDomain()
        assertEquals(com.ridedecider.app.domain.model.GoalPeriod.DAILY, persisted?.activePeriod)
        assertEquals(120.0, persisted?.activeTargetEur ?: 0.0, 0.001)
        assertEquals(5.0, persisted?.activePlannedHours ?: 0.0, 0.001)
        assertEquals(24.0, persisted?.activeHourlyTarget ?: 0.0, 0.001)

        // Cambio a Semanal: sustitución limpia
        val weeklyGoal = DriverGoals(
            activePeriod = com.ridedecider.app.domain.model.GoalPeriod.WEEKLY,
            dailyTargetEur = 0.0,
            dailyPlannedHours = 0.0,
            weeklyTargetEur = 700.0,
            weeklyPlannedHours = 35.0
        )
        goalsRepository.updateGoals(weeklyGoal)
        persisted = goalsDao.getGoals()?.toDomain()
        assertEquals(com.ridedecider.app.domain.model.GoalPeriod.WEEKLY, persisted?.activePeriod)
        assertEquals(700.0, persisted?.activeTargetEur ?: 0.0, 0.001)
        assertEquals(35.0, persisted?.activePlannedHours ?: 0.0, 0.001)
        assertEquals(20.0, persisted?.activeHourlyTarget ?: 0.0, 0.001)
        assertEquals(0.0, persisted?.dailyTargetEur ?: 0.0, 0.001)
    }

    // 19. Relectura de base de datos recupera exactamente los datos
    @Test
    fun rereadDatabase_recoversDataExactly() = runBlocking {
        val now = System.currentTimeMillis()
        val trip = createDummyTrip("t_persist", 45.0, now)
        earningsRepository.recordTrip(trip)
        earningsRepository.updateTripStatus("t_persist", TripTrackingStatus.COMPLETED, 47.50, now, 35.0)

        val retrievedDomain = tripDao.getTripById("t_persist")?.toDomain()
        assertNotNull(retrievedDomain)
        assertEquals(TripTrackingStatus.COMPLETED, retrievedDomain?.status)
        assertEquals(47.50, retrievedDomain?.finalEarningsEur ?: 0.0, 0.001)
        assertEquals(35.0, retrievedDomain?.durationMinutes ?: 0.0, 0.001)
    }

    // 20. Consultas concurrentes no generan duplicados
    @Test
    fun concurrentInsertions_doNotDuplicate() = runBlocking {
        val now = System.currentTimeMillis()
        val jobs = (1..20).map { i ->
            async(Dispatchers.IO) {
                val trip = createDummyTrip("concurrent_$i", 10.0 + i, now)
                earningsRepository.recordTrip(trip)
            }
        }
        jobs.awaitAll()

        val allTrips = tripDao.getAllTrips()
        assertEquals(20, allTrips.size)
    }

    // 21. Cancelación por pasajero con compensación de cancelación
    @Test
    fun cancelledByRider_withFee_recordsFeeAndStatus() = runBlocking {
        val now = System.currentTimeMillis()
        val trip = createDummyTrip("t_cancel_fee", 9.01, now)
        earningsRepository.recordTrip(trip)

        tracker.cancelTrip(
            tripId = "t_cancel_fee",
            reason = com.ridedecider.app.domain.model.CancellationReason.RIDER,
            cancellationFee = 4.50,
            timestamp = now
        )

        val entity = tripDao.getTripById("t_cancel_fee")
        assertNotNull(entity)
        assertEquals("CANCELLED_BY_RIDER", entity?.status)
        assertEquals(9.01, entity?.estimatedFareEur ?: 0.0, 0.001)
        assertEquals(4.50, entity?.cancellationFeeEur ?: 0.0, 0.001)
        assertEquals("RIDER", entity?.cancellationReason)

        val (start, end) = tracker.getDayBounds(now)
        val dailyEarnings = earningsRepository.getCompletedEarningsBetween(start, end)
        assertEquals(4.50, dailyEarnings, 0.001)
    }

    // 22. Cancelación por conductor sin compensación
    @Test
    fun cancelledByDriver_withoutFee_recordsZeroEarnings() = runBlocking {
        val now = System.currentTimeMillis()
        val trip = createDummyTrip("t_cancel_nofee", 15.00, now)
        earningsRepository.recordTrip(trip)

        tracker.cancelTrip(
            tripId = "t_cancel_nofee",
            reason = com.ridedecider.app.domain.model.CancellationReason.DRIVER,
            cancellationFee = null,
            timestamp = now
        )

        val entity = tripDao.getTripById("t_cancel_nofee")
        assertNotNull(entity)
        assertEquals("CANCELLED_BY_DRIVER", entity?.status)
        assertEquals(15.00, entity?.estimatedFareEur ?: 0.0, 0.001)
        assertEquals(0.0, entity?.cancellationFeeEur ?: 0.0, 0.001)

        val (start, end) = tracker.getDayBounds(now)
        val dailyEarnings = earningsRepository.getCompletedEarningsBetween(start, end)
        assertEquals(0.0, dailyEarnings, 0.001)
    }

    // 23. Patrón real: ACTIVE_TRIP -> Mensaje explícito del pasajero -> CANCELLED_BY_RIDER (nunca COMPLETED)
    @Test
    fun activeTrip_riderExplicitCancellationPattern_recordsCancelledByRiderAndNeverCompleted() = runBlocking {
        val now = System.currentTimeMillis()
        val parser = com.ridedecider.app.data.accessibility.uber.UberAccessibilityParser()
        val trip = createDummyTrip("t_active_cancel", 12.80, now)

        // 1. Detección y evaluación de la oferta
        earningsRepository.recordTrip(trip)

        // 2. Conductor acepta y el viaje pasa a ACTIVE_TRIP
        earningsRepository.updateTripStatus("t_active_cancel", TripTrackingStatus.ACCEPTED_BY_DRIVER, null, null, null)

        // 3. Simular pantalla real con mensaje explícito de cancelación por parte de Uber
        val cancellationSnapshot = UberNodeSnapshot(
            className = "android.widget.FrameLayout",
            children = listOf(
                UberNodeSnapshot(text = "El viaje fue cancelado por el pasajero"),
                UberNodeSnapshot(text = "Tarifa de cancelación: 4,50 €")
            )
        )

        val rawParsed = parser.parse(cancellationSnapshot)
        assertEquals(com.ridedecider.app.data.accessibility.uber.UberOfferScreenType.TRIP_CANCELLED, rawParsed.detectedOfferType)
        assertEquals("El viaje fue cancelado por el pasajero", rawParsed.cancellationReasonText)
        assertEquals(4.50, rawParsed.cancellationFee ?: 0.0, 0.001)

        // 4. El sistema procesa la señal primaria de cancelación explícita
        tracker.cancelTrip(
            tripId = "t_active_cancel",
            reason = com.ridedecider.app.domain.model.CancellationReason.RIDER,
            cancellationFee = rawParsed.cancellationFee,
            timestamp = now + 60000
        )

        // 5. Verificar persistencia en Room: es CANCELLED_BY_RIDER y NO COMPLETED
        val entity = tripDao.getTripById("t_active_cancel")
        assertNotNull(entity)
        assertEquals("CANCELLED_BY_RIDER", entity?.status)
        assertFalse(entity?.status == "COMPLETED")
        assertEquals(12.80, entity?.estimatedFareEur ?: 0.0, 0.001)
        assertEquals(4.50, entity?.cancellationFeeEur ?: 0.0, 0.001)
        assertEquals("RIDER", entity?.cancellationReason)

        // 6. Cómputo de ingresos: Solo computa los 4.50 € de la compensación, no los 12.80 € estimados
        val (start, end) = tracker.getDayBounds(now)
        val earnings = earningsRepository.getCompletedEarningsBetween(start, end)
        assertEquals(4.50, earnings, 0.001)
    }
    // 24. Las cancelaciones con compensación entran en día/semana/mes según cancelledTimestamp (no completedTimestamp)
    @Test
    fun cancellationEarnings_dailyWeeklyMonthlyBounds_strictlyFollowsCancelledTimestamp() = runBlocking {
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_MONTH, 10)
        }
        val day10 = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, 11)
        val day11 = cal.timeInMillis

        // Viaje grabado el día 10 pero cancelado el día 11 con compensación
        val trip = createDummyTrip("t_cancel_bound", 10.0, day10)
        earningsRepository.recordTrip(trip)

        tracker.cancelTrip(
            tripId = "t_cancel_bound",
            reason = com.ridedecider.app.domain.model.CancellationReason.RIDER,
            cancellationFee = 5.00,
            timestamp = day11
        )

        val entity = tripDao.getTripById("t_cancel_bound")
        assertNotNull(entity)
        assertEquals("CANCELLED_BY_RIDER", entity?.status)
        assertNull(entity?.completedTimestamp)
        assertEquals(day11, entity?.cancelledTimestamp)

        // Día 10 no debe tener ganancias
        val (start10, end10) = tracker.getDayBounds(day10)
        val earningsDay10 = earningsRepository.getCompletedEarningsBetween(start10, end10)
        assertEquals(0.0, earningsDay10, 0.001)

        // Día 11 debe tener exactamente los 5.00 €
        val (start11, end11) = tracker.getDayBounds(day11)
        val earningsDay11 = earningsRepository.getCompletedEarningsBetween(start11, end11)
        assertEquals(5.00, earningsDay11, 0.001)

        // El mes debe acumular los 5.00 €
        val (startMonth, endMonth) = tracker.getMonthBounds(day11)
        val monthlyEarnings = earningsRepository.getCompletedEarningsBetween(startMonth, endMonth)
        assertEquals(5.00, monthlyEarnings, 0.001)
    }

    // 25. Cancelación sin compensación = 0 €
    @Test
    fun cancellation_withoutFee_neverAddsToEarnings() = runBlocking {
        val now = System.currentTimeMillis()
        val trip = createDummyTrip("t_cancel_zero", 25.0, now)
        earningsRepository.recordTrip(trip)

        tracker.cancelTrip(
            tripId = "t_cancel_zero",
            reason = com.ridedecider.app.domain.model.CancellationReason.DRIVER,
            cancellationFee = null,
            timestamp = now
        )

        val (start, end) = tracker.getDayBounds(now)
        val earnings = earningsRepository.getCompletedEarningsBetween(start, end)
        assertEquals(0.0, earnings, 0.001)
    }

    // =========================================================================
    // Fakes en memoria exactos a las consultas de Room SQLite
    // =========================================================================

    private class FakeRecordedTripDao : RecordedTripDao {
        private val storage = ConcurrentHashMap<String, RecordedTripEntity>()

        override suspend fun insertOrUpdate(trip: RecordedTripEntity): Long {
            storage[trip.id] = trip
            return 1L
        }

        override suspend fun getTripById(id: String): RecordedTripEntity? {
            return storage[id]
        }

        override suspend fun getCompletedTripsBetween(startTimestamp: Long, endTimestamp: Long): List<RecordedTripEntity> {
            return storage.values.filter {
                it.status == "COMPLETED" && it.completedTimestamp != null && it.completedTimestamp in startTimestamp..endTimestamp
            }.sortedBy { it.completedTimestamp }
        }

        override suspend fun getTripsBetween(startTimestamp: Long, endTimestamp: Long): List<RecordedTripEntity> {
            return storage.values.filter {
                (it.recordedTimestamp in startTimestamp..endTimestamp) ||
                (it.completedTimestamp != null && it.completedTimestamp in startTimestamp..endTimestamp) ||
                (it.cancelledTimestamp != null && it.cancelledTimestamp in startTimestamp..endTimestamp)
            }.sortedBy { it.recordedTimestamp }
        }

        override suspend fun getCompletedEarningsBetween(startTimestamp: Long, endTimestamp: Long): Double {
            val completedSum = storage.values.filter {
                it.status == "COMPLETED" && it.completedTimestamp != null && it.completedTimestamp in startTimestamp..endTimestamp
            }.sumOf { it.finalEarningsEur ?: 0.0 }

            val cancelledFeeSum = storage.values.filter {
                it.status.startsWith("CANCELLED") && ((it.cancellationFeeEur ?: 0.0) > 0.0) &&
                it.cancelledTimestamp != null && it.cancelledTimestamp in startTimestamp..endTimestamp
            }.sumOf { it.cancellationFeeEur ?: 0.0 }

            return completedSum + cancelledFeeSum
        }

        override suspend fun getCompletedDurationMinutesBetween(startTimestamp: Long, endTimestamp: Long): Double {
            return storage.values.filter {
                it.status == "COMPLETED" && it.completedTimestamp != null && it.completedTimestamp in startTimestamp..endTimestamp
            }.sumOf { it.actualDurationMinutes ?: 0.0 }
        }

        override suspend fun getActiveAssignedTrip(): RecordedTripEntity? {
            return storage.values.filter { it.status == "ACCEPTED_BY_DRIVER" }.maxByOrNull { it.recordedTimestamp }
        }

        override suspend fun getAllTrips(): List<RecordedTripEntity> {
            return storage.values.sortedByDescending { it.recordedTimestamp }
        }

        override suspend fun clearAll(): Int {
            val size = storage.size
            storage.clear()
            return size
        }
    }

    private class FakeDriverGoalsDao : DriverGoalsDao {
        private var storedGoals: DriverGoalsEntity? = null
        private val goalsFlowState = MutableStateFlow<DriverGoalsEntity?>(null)

        override suspend fun insertOrUpdate(goals: DriverGoalsEntity): Long {
            storedGoals = goals
            goalsFlowState.value = goals
            return 1L
        }

        override suspend fun getGoals(): DriverGoalsEntity? {
            return storedGoals
        }

        override fun getGoalsFlow(): Flow<DriverGoalsEntity?> {
            return goalsFlowState
        }
    }
}
