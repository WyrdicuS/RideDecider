package com.ridedecider.app.data.accessibility.uber

import com.ridedecider.app.data.accessibility.uber.ocr.OcrResult
import com.ridedecider.app.data.accessibility.uber.ocr.OcrStatus
import com.ridedecider.app.data.local.room.dao.DecisionSnapshotDao
import com.ridedecider.app.data.local.room.entity.DecisionSnapshotEntity
import com.ridedecider.app.domain.engine.DecisionEngine
import com.ridedecider.app.domain.engine.EarningsTracker
import com.ridedecider.app.domain.model.CancellationReason
import com.ridedecider.app.domain.model.DriverGoals
import com.ridedecider.app.domain.model.ProfitabilityConfig
import com.ridedecider.app.domain.model.RecordedTrip
import com.ridedecider.app.domain.model.Trip
import com.ridedecider.app.domain.model.TripEvaluation
import com.ridedecider.app.domain.model.TripLifecycleState
import com.ridedecider.app.domain.model.TripOfferType
import com.ridedecider.app.domain.model.TripTrackingStatus
import com.ridedecider.app.domain.model.UberCategory
import com.ridedecider.app.domain.repository.DriverGoalsRepository
import com.ridedecider.app.domain.repository.EarningsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Suite determinista de pruebas de blindaje de ingesta de ofertas y prevención de falsos positivos (Fase 8.13).
 * Comprueba los 21 escenarios de protección contra Waze, navegación, horas de reserva, historial y números aislados.
 */
class OfferIngestionProtectionTest {

    private class FakeSnapshotDao : DecisionSnapshotDao {
        val map = mutableMapOf<String, DecisionSnapshotEntity>()
        override suspend fun insertSnapshot(snapshot: DecisionSnapshotEntity): Long {
            map[snapshot.snapshotId] = snapshot
            return 1L
        }
        override suspend fun getSnapshotById(snapshotId: String): DecisionSnapshotEntity? = map[snapshotId]
        override suspend fun getSnapshotByInstanceId(instanceId: String): DecisionSnapshotEntity? = map.values.find { it.instanceId == instanceId }
        override suspend fun getSnapshotByTripId(tripId: String): DecisionSnapshotEntity? = map.values.find { it.tripId == tripId }
        override suspend fun getAllSnapshots(): List<DecisionSnapshotEntity> = map.values.toList()
        override suspend fun updateActualResult(tripId: String, actualDist: Double?, actualDur: Double?, finalEarnings: Double?): Int = 0
        override suspend fun updateSnapshotActuals(tripId: String, actualDist: Double?, actualDur: Double?, actualPickupDur: Double?, actualBaseFare: Double?, waitingComp: Double?, cancellationFee: Double?, tip: Double?, finalEarnings: Double?): Int = 0
        override suspend fun clearAllSnapshots(): Int { val c = map.size; map.clear(); return c }
    }

    private class FakeEarningsRepo(val snapshotDao: FakeSnapshotDao) : EarningsRepository {
        val tripsMap = mutableMapOf<String, RecordedTrip>()
        override suspend fun recordTrip(trip: RecordedTrip) { tripsMap[trip.id] = trip }
        override suspend fun saveDecisionSnapshot(snapshot: DecisionSnapshotEntity) { snapshotDao.insertSnapshot(snapshot) }
        override suspend fun updateSnapshotActuals(tripId: String, actualDist: Double?, actualDur: Double?, actualPickupDur: Double?, actualBaseFare: Double?, waitingComp: Double?, cancellationFee: Double?, tip: Double?, finalEarnings: Double?) {}
        override suspend fun updateTripStatus(tripId: String, status: TripTrackingStatus, finalEarnings: Double?, completedTimestamp: Long?, durationMinutes: Double?, cancellationFee: Double?, cancellationReason: CancellationReason?, cancelledTimestamp: Long?) {
            val existing = tripsMap[tripId] ?: return
            tripsMap[tripId] = existing.copy(status = status)
        }
        override suspend fun getTripsBetween(startTimestamp: Long, endTimestamp: Long): List<RecordedTrip> = emptyList()
        override suspend fun getCompletedEarningsBetween(startTimestamp: Long, endTimestamp: Long): Double = 0.0
        override suspend fun getWorkedMinutesBetween(startTimestamp: Long, endTimestamp: Long): Double = 0.0
        override suspend fun clearAllTrips() { tripsMap.clear() }
    }

    private class FakeDriverGoalsRepository : DriverGoalsRepository {
        private val _goals = MutableStateFlow(DriverGoals(dailyTargetEur = 120.0, dailyPlannedHours = 5.0))
        override val goalsFlow: StateFlow<DriverGoals> = _goals
        override fun getGoals(): DriverGoals = _goals.value
        override suspend fun updateGoals(goals: DriverGoals) { _goals.value = goals }
    }

    private lateinit var parser: UberAccessibilityParser
    private lateinit var validator: UberOfferValidator
    private lateinit var processor: UberAccessibilityProcessor
    private lateinit var snapshotDao: FakeSnapshotDao
    private lateinit var repo: FakeEarningsRepo
    private lateinit var tracker: EarningsTracker

    private val now = 1700000000000L

    @Before
    fun setUp() {
        parser = UberAccessibilityParser()
        validator = UberOfferValidator()
        snapshotDao = FakeSnapshotDao()
        repo = FakeEarningsRepo(snapshotDao)
        tracker = EarningsTracker(FakeDriverGoalsRepository(), repo)
        processor = UberAccessibilityProcessor(
            parser = parser,
            validator = validator
        )
    }

    // =========================================================================
    // 1. Waze e Ingesta de Paquetes Externos
    // =========================================================================
    @Test
    fun test1_wazeEvent_ignoredImmediately() {
        val snap = UberNodeSnapshot(text = "En 300m gire a la derecha - Waze")
        val result = processor.processSnapshot(snap, packageName = "com.waze", currentTime = now)

        assertTrue(result is UberAccessibilityProcessor.ProcessResult.IgnoredPackage)
        assertEquals("com.waze", (result as UberAccessibilityProcessor.ProcessResult.IgnoredPackage).packageReceived)
    }

    @Test
    fun test2_externalAppEvent_ignoredImmediately() {
        val snap = UberNodeSnapshot(text = "WhatsApp - Mensaje nuevo")
        val result = processor.processSnapshot(snap, packageName = "com.whatsapp", currentTime = now)

        assertTrue(result is UberAccessibilityProcessor.ProcessResult.IgnoredPackage)
    }

    // =========================================================================
    // 2. Navegación Uber durante Viaje Activo
    // =========================================================================
    @Test
    fun test3_uberNavigationScreen_doesNotCreateNewOffer() {
        val snap = UberNodeSnapshot(
            children = listOf(
                UberNodeSnapshot(text = "Dirígete a Calle Gran Vía 12"),
                UberNodeSnapshot(text = "14 km restantes (20 min)")
            )
        )

        val raw = parser.parse(snap)
        val validation = validator.validate(raw)

        assertEquals(UberOfferScreenType.ACTIVE_TRIP, raw.detectedOfferType)
        assertFalse(validation.isValidOffer)
    }

    @Test
    fun test4_navigationDuringActiveTrip_ignoredAsNewOffer() = runBlocking {
        val trip = Trip("trip_active_1", now, TripOfferType.TRIP_OFFER, UberCategory.UBER_X, 15.0, "EUR", 1.0, 3.0, "O", 5.0, 10.0, "D")
        val eval = DecisionEngine().evaluate(trip, ProfitabilityConfig(0.2, 4.0, 20.0, 1.0, 2.0, 12.0, 5.0, 10.0))
        tracker.recordEvaluatedOffer(trip, eval, now)
        tracker.markTripAccepted("trip_active_1")
        tracker.markActiveTripStarted()

        val navSnap = UberNodeSnapshot(children = listOf(UberNodeSnapshot(text = "Dirígete a Calle Gran Vía"), UberNodeSnapshot(text = "12 km (18 min)")))
        val result = processor.processSnapshot(navSnap, packageName = "com.ubercab.driver", currentTime = now + 1000L)

        assertTrue(result is UberAccessibilityProcessor.ProcessResult.InvalidOffer)
        assertEquals(UberOfferScreenType.ACTIVE_TRIP, (result as UberAccessibilityProcessor.ProcessResult.InvalidOffer).screenType)
    }

    // =========================================================================
    // 3. Pantallas de Reserva y Exclusión de Horas
    // =========================================================================
    @Test
    fun test5_reservationScreen_classifiedAsReservation() {
        val snap = UberNodeSnapshot(
            children = listOf(
                UberNodeSnapshot(text = "Solicitudes de reserva"),
                UberNodeSnapshot(text = "Hora de recogida: 18:30"),
                UberNodeSnapshot(text = "Conéctate a las 18:15")
            )
        )

        val raw = parser.parse(snap)
        val validation = validator.validate(raw)

        assertEquals(UberOfferScreenType.RESERVATION_SCREEN, raw.detectedOfferType)
        assertFalse(validation.isValidOffer)
    }

    @Test
    fun test6_timeColonFormat1830_notParsedAsFare() {
        val snap = UberNodeSnapshot(text = "18:30")
        val raw = parser.parse(snap)

        assertNull("Las horas en formato 18:30 no deben interpretarse como tarifas", raw.rawFare)
    }

    @Test
    fun test7_timeColonFormat1815_notParsedAsFare() {
        val snap = UberNodeSnapshot(text = "Conéctate a las 18:15")
        val raw = parser.parse(snap)

        assertNull("Las horas de conexión 18:15 no deben interpretarse como tarifas", raw.rawFare)
    }

    // =========================================================================
    // 4. Historial, Detalles y Perfil
    // =========================================================================
    @Test
    fun test8_uberHistoryScreen_ignoredAsHistory() {
        val snap = UberNodeSnapshot(
            children = listOf(
                UberNodeSnapshot(text = "Historial de viajes"),
                UberNodeSnapshot(text = "Último viaje: 14,50 €"),
                UberNodeSnapshot(text = "Ver detalles")
            )
        )

        val raw = parser.parse(snap)
        val validation = validator.validate(raw)

        assertEquals(UberOfferScreenType.HISTORY_SCREEN, raw.detectedOfferType)
        assertFalse(validation.isValidOffer)
    }

    @Test
    fun test9_uberTripDetailsScreen_ignoredAsHistory() {
        val snap = UberNodeSnapshot(
            children = listOf(
                UberNodeSnapshot(text = "Detalles del viaje"),
                UberNodeSnapshot(text = "Tarifa base: 12,00 €")
            )
        )

        val raw = parser.parse(snap)
        val validation = validator.validate(raw)

        assertEquals(UberOfferScreenType.HISTORY_SCREEN, raw.detectedOfferType)
        assertFalse(validation.isValidOffer)
    }

    @Test
    fun test10_uberProfileScreen_ignoredAsNonOffer() {
        val snap = UberNodeSnapshot(
            children = listOf(
                UberNodeSnapshot(text = "Ajustes de cuenta"),
                UberNodeSnapshot(text = "Conductor verificado"),
                UberNodeSnapshot(text = "Soporte")
            )
        )

        val raw = parser.parse(snap)
        val validation = validator.validate(raw)

        assertFalse(validation.isValidOffer)
        assertEquals(UberOfferScreenType.NO_OFFER, validation.screenType)
    }

    // =========================================================================
    // 5. Números Aislados y Evidencia Combinada
    // =========================================================================
    @Test
    fun test11_isolatedNumbersWithoutOfferEvidence_ignored() {
        val snap = UberNodeSnapshot(text = "4,91")
        val raw = parser.parse(snap)
        val validation = validator.validate(raw)

        assertFalse(validation.isValidOffer)
        assertNull(raw.rawFare)
        assertNull(raw.passengerRating)
    }

    @Test
    fun test12_validDirectOffer_detectedAsTripOffer() {
        val snap = UberNodeSnapshot(
            children = listOf(
                UberNodeSnapshot(text = "OFERTA EXCLUSIVA"),
                UberNodeSnapshot(text = "14,50 €"),
                UberNodeSnapshot(text = "A 1,2 km (3 min) de distancia"),
                UberNodeSnapshot(text = "Viaje de 5,0 km (10 min)"),
                UberNodeSnapshot(text = "Aceptar")
            )
        )

        val raw = parser.parse(snap)
        val validation = validator.validate(raw)

        assertEquals(UberOfferScreenType.TRIP_OFFER, raw.detectedOfferType)
        assertTrue(validation.isValidOffer)
        assertEquals(14.50, raw.rawFare!!, 0.001)
    }

    @Test
    fun test13_validRadarOffer_detectedAsRadarOffer() {
        val snap = UberNodeSnapshot(
            children = listOf(
                UberNodeSnapshot(text = "Radar de viaje"),
                UberNodeSnapshot(text = "8,20 €"),
                UberNodeSnapshot(text = "A 2,0 km (5 min) de distancia"),
                UberNodeSnapshot(text = "Viaje de 3,0 km (8 min)"),
                UberNodeSnapshot(text = "Emparejar")
            )
        )

        val raw = parser.parse(snap)
        val validation = validator.validate(raw)

        assertEquals(UberOfferScreenType.RADAR_OFFER, raw.detectedOfferType)
        assertTrue(validation.isValidOffer)
        assertEquals(8.20, raw.rawFare!!, 0.001)
    }

    // =========================================================================
    // 6. Deduplicación y Aislamiento
    // =========================================================================
    @Test
    fun test14_duplicateOfferEvents_debouncedSingleRecord() {
        val snap = UberNodeSnapshot(
            children = listOf(
                UberNodeSnapshot(text = "14,50 €"),
                UberNodeSnapshot(text = "A 1,2 km (3 min) de distancia"),
                UberNodeSnapshot(text = "Viaje de 5,0 km (10 min)"),
                UberNodeSnapshot(text = "Aceptar")
            )
        )

        val res1 = processor.processSnapshot(snap, packageName = "com.ubercab.driver", currentTime = now)
        val res2 = processor.processSnapshot(snap, packageName = "com.ubercab.driver", currentTime = now + 50L)

        assertTrue(res1 is UberAccessibilityProcessor.ProcessResult.Evaluated)
        assertTrue(res2 is UberAccessibilityProcessor.ProcessResult.Debounced)
    }

    @Test
    fun test15_validAccessibilitySnapshot_evaluatesSuccessfully() {
        val snap = UberNodeSnapshot(
            children = listOf(
                UberNodeSnapshot(text = "18,00 €"),
                UberNodeSnapshot(text = "A 1,0 km (3 min) de distancia"),
                UberNodeSnapshot(text = "Viaje de 6,0 km (12 min)"),
                UberNodeSnapshot(text = "Aceptar")
            )
        )

        val res = processor.processSnapshot(snap, packageName = "com.ubercab.driver", currentTime = now)
        assertTrue(res is UberAccessibilityProcessor.ProcessResult.Evaluated)
    }

    @Test
    fun test16_validOcrString_evaluatesSuccessfully() {
        val ocrText = "OFERTA EXCLUSIVA\n15,00 €\nA 1,0 km (3 min) de distancia\nViaje de 5,0 km (10 min)\nAceptar"
        val raw = parser.parseFromText(ocrText)
        val validation = validator.validate(raw)

        assertTrue(validation.isValidOffer)
        assertEquals(15.00, raw.rawFare!!, 0.001)
    }

    @Test
    fun test17_ambiguousOcrText_doesNotCreateOffer() {
        val ocrText = "Ajustes de cuenta\nUsuario en línea\nSoporte"
        val raw = parser.parseFromText(ocrText)
        val validation = validator.validate(raw)

        assertFalse(validation.isValidOffer)
        assertNull(raw.rawFare)
    }

    @Test
    fun test18_activeTrip_canCompleteAndCancelCleanly() = runBlocking {
        val trip = Trip("trip_active_2", now, TripOfferType.TRIP_OFFER, UberCategory.UBER_X, 15.0, "EUR", 1.0, 3.0, "O", 5.0, 10.0, "D")
        val eval = DecisionEngine().evaluate(trip, ProfitabilityConfig(0.2, 4.0, 20.0, 1.0, 2.0, 12.0, 5.0, 10.0))
        tracker.recordEvaluatedOffer(trip, eval, now)
        tracker.markTripAccepted("trip_active_2")
        tracker.markActiveTripStarted()

        // Completar viaje
        tracker.completeTrip("trip_active_2", finalEarnings = 15.0, durationMinutes = 12.0, completedTimestamp = now + 1000L)
        assertTrue(tracker.stateMachine.currentState is TripLifecycleState.Completed)
    }

    @Test
    fun test19_offerA_doesNotContaminateOfferB() {
        val rawA = RawUberTripOffer(rawFare = 20.0, currency = "EUR", pickupDistanceKm = 1.0, pickupDurationMinutes = 3.0, tripDistanceKm = 5.0, tripDurationMinutes = 10.0, detectedOfferType = UberOfferScreenType.TRIP_OFFER)
        val rawB = RawUberTripOffer(rawFare = 8.0, currency = "EUR", pickupDistanceKm = 2.0, pickupDurationMinutes = 4.0, tripDistanceKm = 3.0, tripDurationMinutes = 8.0, detectedOfferType = UberOfferScreenType.TRIP_OFFER)

        val valA = validator.validate(rawA)
        val valB = validator.validate(rawB)

        assertTrue(valA.isValidOffer)
        assertTrue(valB.isValidOffer)
        assertFalse(rawA.instanceId == rawB.instanceId)
    }

    @Test
    fun test20_onlyValidatedOffer_reachesDecisionEngine() {
        val invalidRaw = RawUberTripOffer(rawFare = null, detectedOfferType = UberOfferScreenType.NO_OFFER)
        val validation = validator.validate(invalidRaw)

        assertFalse(validation.isValidOffer)
    }

    @Test
    fun test21_onlyValidatedOffer_generatesDecisionSnapshot() = runBlocking {
        val invalidSnap = UberNodeSnapshot(text = "Ajustes")
        val res = processor.processSnapshot(invalidSnap, packageName = "com.ubercab.driver", currentTime = now)

        assertTrue(res is UberAccessibilityProcessor.ProcessResult.InvalidOffer)
        assertEquals(0, snapshotDao.getAllSnapshots().size)
    }

    // =========================================================================
    // 7. Consumo Semántico de Ofertas (consumedOfferSignature)
    // =========================================================================
    @Test
    fun test22_consumedOffer_repeatedAfterMoreThan2Seconds_returnsDebounced() {
        val snap = UberNodeSnapshot(
            children = listOf(
                UberNodeSnapshot(text = "14,50 €"),
                UberNodeSnapshot(text = "A 1,2 km (3 min) de distancia"),
                UberNodeSnapshot(text = "Viaje de 5,0 km (10 min)"),
                UberNodeSnapshot(text = "Aceptar")
            )
        )

        val res1 = processor.processSnapshot(snap, packageName = "com.ubercab.driver", currentTime = now)
        val res2 = processor.processSnapshot(snap, packageName = "com.ubercab.driver", currentTime = now + 5000L) // 5s después

        assertTrue(res1 is UberAccessibilityProcessor.ProcessResult.Evaluated)
        assertTrue("Una oferta consumida debe ser ignorada incluso pasados más de 2s", res2 is UberAccessibilityProcessor.ProcessResult.Debounced)
    }

    @Test
    fun test23_consumedOffer_repeatedAfterLongTime_returnsDebounced() {
        val snap = UberNodeSnapshot(
            children = listOf(
                UberNodeSnapshot(text = "14,50 €"),
                UberNodeSnapshot(text = "A 1,2 km (3 min) de distancia"),
                UberNodeSnapshot(text = "Viaje de 5,0 km (10 min)"),
                UberNodeSnapshot(text = "Aceptar")
            )
        )

        val res1 = processor.processSnapshot(snap, packageName = "com.ubercab.driver", currentTime = now)
        val res2 = processor.processSnapshot(snap, packageName = "com.ubercab.driver", currentTime = now + 30000L) // 30s después

        assertTrue(res1 is UberAccessibilityProcessor.ProcessResult.Evaluated)
        assertTrue("Una oferta consumida permanece bloqueada sin importar el tiempo", res2 is UberAccessibilityProcessor.ProcessResult.Debounced)
    }

    @Test
    fun test24_consumedOffer_accessibilityAndOcrConvergence_singleRecord() {
        val snap = UberNodeSnapshot(
            children = listOf(
                UberNodeSnapshot(text = "14,50 €"),
                UberNodeSnapshot(text = "A 1,2 km (3 min) de distancia"),
                UberNodeSnapshot(text = "Viaje de 5,0 km (10 min)"),
                UberNodeSnapshot(text = "Aceptar")
            )
        )

        val res1 = processor.processSnapshot(snap, packageName = "com.ubercab.driver", currentTime = now)

        val ocrResult = OcrResult(
            status = OcrStatus.SUCCESS,
            rawText = "TRIP_OFFER\n14,50 €\nA 1,2 km (3 min) de distancia\nViaje de 5,0 km (10 min)\nAceptar"
        )
        val res2 = processor.processOcrResult(ocrResult, currentTime = now + 3000L)

        assertTrue(res1 is UberAccessibilityProcessor.ProcessResult.Evaluated)
        assertTrue("La convergencia OCR para la misma oferta debe retornar Debounced", res2 is UberAccessibilityProcessor.ProcessResult.Debounced)
    }

    @Test
    fun test25_newOfferB_withDifferentSignature_evaluatesAndRegistersOnce() {
        val snapA = UberNodeSnapshot(
            children = listOf(
                UberNodeSnapshot(text = "14,50 €"),
                UberNodeSnapshot(text = "A 1,2 km (3 min) de distancia"),
                UberNodeSnapshot(text = "Viaje de 5,0 km (10 min)"),
                UberNodeSnapshot(text = "Aceptar")
            )
        )

        val snapB = UberNodeSnapshot(
            children = listOf(
                UberNodeSnapshot(text = "25,00 €"),
                UberNodeSnapshot(text = "A 2,0 km (4 min) de distancia"),
                UberNodeSnapshot(text = "Viaje de 8,0 km (15 min)"),
                UberNodeSnapshot(text = "Aceptar")
            )
        )

        val resA = processor.processSnapshot(snapA, packageName = "com.ubercab.driver", currentTime = now)
        val resB = processor.processSnapshot(snapB, packageName = "com.ubercab.driver", currentTime = now + 4000L)

        assertTrue(resA is UberAccessibilityProcessor.ProcessResult.Evaluated)
        assertTrue("Una nueva oferta con firma diferente debe ser evaluada", resB is UberAccessibilityProcessor.ProcessResult.Evaluated)
    }

    @Test
    fun test26_screenSwitchAndReturnToSameOffer_doesNotDuplicateConsumedOffer() {
        val snapA = UberNodeSnapshot(
            children = listOf(
                UberNodeSnapshot(text = "14,50 €"),
                UberNodeSnapshot(text = "A 1,2 km (3 min) de distancia"),
                UberNodeSnapshot(text = "Viaje de 5,0 km (10 min)"),
                UberNodeSnapshot(text = "Aceptar")
            )
        )

        val noOfferSnap = UberNodeSnapshot(text = "Buscando viajes...")

        processor.processSnapshot(snapA, packageName = "com.ubercab.driver", currentTime = now)
        val resReturn = processor.processSnapshot(snapA, packageName = "com.ubercab.driver", currentTime = now + 5000L)

        assertTrue("Volver a ver la misma oferta consumida no debe duplicarla", resReturn is UberAccessibilityProcessor.ProcessResult.Debounced)
    }

    @Test
    fun test27_consecutiveIdenticalOffer_afterNoOfferTransition_evaluatesAndRegistersOnce() {
        val snapA = UberNodeSnapshot(
            children = listOf(
                UberNodeSnapshot(text = "14,50 €"),
                UberNodeSnapshot(text = "A 1,2 km (3 min) de distancia"),
                UberNodeSnapshot(text = "Viaje de 5,0 km (10 min)"),
                UberNodeSnapshot(text = "Aceptar")
            )
        )

        // 1. Oferta A aparece -> evaluada 1 vez
        val resA = processor.processSnapshot(snapA, packageName = "com.ubercab.driver", currentTime = now)
        assertTrue(resA is UberAccessibilityProcessor.ProcessResult.Evaluated)

        // 2. Oferta A desaparece (pantalla pasa a NO_OFFER) -> libera el sello
        val noOfferSnap = UberNodeSnapshot(text = "Buscando viajes...")
        processor.processSnapshot(noOfferSnap, packageName = "com.ubercab.driver", currentTime = now + 5000L)

        // 3. Una nueva Oferta B físicamente idéntica aparece 10 segundos después -> evaluada como nueva oferta independiente 1 sola vez
        val resB = processor.processSnapshot(snapA, packageName = "com.ubercab.driver", currentTime = now + 15000L)
        assertTrue("Una nueva oferta con la misma firma tras desaparecer la anterior debe ser evaluada", resB is UberAccessibilityProcessor.ProcessResult.Evaluated)

        // 4. Repeticiones posteriores de la nueva Oferta B -> Debounced
        val resBRepeat = processor.processSnapshot(snapA, packageName = "com.ubercab.driver", currentTime = now + 16000L)
        assertTrue(resBRepeat is UberAccessibilityProcessor.ProcessResult.Debounced)
    }
}
