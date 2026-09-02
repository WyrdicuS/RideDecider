package com.ridedecider.app.data.accessibility.uber

import com.ridedecider.app.domain.model.TripOfferType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Suite determinista de auditoría forense para comprobar el comportamiento de sourceTimestamp,
 * la estabilidad de identificadores, deduplicación y el caso extremo de Math.abs(Int.MIN_VALUE).
 */
class IdentityAndTimestampAuditTest {

    private val mapper = RawUberTripOfferMapper()

    // =========================================================================
    // TEST 1: Demostración del caso extremo Math.abs(Int.MIN_VALUE)
    // =========================================================================
    @Test
    fun test1_mathAbsIntMinValue_returnsNegativeValue() {
        val minInt = Int.MIN_VALUE
        val absResult = Math.abs(minInt)

        // Demuestra que en Java/Kotlin, Math.abs(Int.MIN_VALUE) es negativo (-2147483648)
        assertEquals(Int.MIN_VALUE, absResult)
        assertTrue(absResult < 0)
    }

    // =========================================================================
    // TEST 2: Firma física pura produce mismo ID para la misma oferta
    // =========================================================================
    @Test
    fun test2_samePhysicalOffer_producesIdenticalDeterministicId() {
        val raw1 = RawUberTripOffer(
            rawFare = 15.0,
            currency = "EUR",
            pickupDistanceKm = 1.0,
            pickupDurationMinutes = 3.0,
            tripDistanceKm = 5.0,
            tripDurationMinutes = 10.0,
            pickupAddress = "Calle Mayor 1",
            dropoffAddress = "Gran Vía 10",
            detectedOfferType = UberOfferScreenType.TRIP_OFFER,
            sourceTimestamp = 1000000L
        )

        val raw2 = raw1.copy(sourceTimestamp = 1000050L) // 50ms después

        val res1 = mapper.mapToDomain(raw1)
        val res2 = mapper.mapToDomain(raw2)

        assertTrue(res1 is TripMappingResult.Success)
        assertTrue(res2 is TripMappingResult.Success)

        val trip1 = (res1 as TripMappingResult.Success).trip
        val trip2 = (res2 as TripMappingResult.Success).trip

        // La firma física pura actual produce ID idéntico a pesar del timestamp diferente
        assertEquals(trip1.id, trip2.id)
    }

    // =========================================================================
    // TEST 3: Demostración de que incorporar timestamp al ID directamente rompería la deduplicación
    // =========================================================================
    @Test
    fun test3_incorporatingTimestampDirectlyIntoId_wouldBreakSameOfferDeduplication() {
        val fare = 15.0
        val pickup = "calle mayor 1"
        val dropoff = "gran vía 10"

        // Si el timestamp estuviera concatenado en el ID:
        val time1 = 1000000L
        val time2 = 1000050L // Evento 50ms después para la MISMA tarjeta en Compose

        val idWithTime1 = "uber_${Math.abs("TRIP_OFFER_${fare}_1.0_5.0_10.0_${pickup}_${dropoff}_$time1".hashCode())}"
        val idWithTime2 = "uber_${Math.abs("TRIP_OFFER_${fare}_1.0_5.0_10.0_${pickup}_${dropoff}_$time2".hashCode())}"

        // Demuestra que concatenar directamente el timestamp generaría dos IDs completamente diferentes
        assertNotEquals(idWithTime1, idWithTime2)
    }

    // =========================================================================
    // TEST 4: Estabilidad de firma entre Ruta A y Ruta B (OCR)
    // =========================================================================
    @Test
    fun test4_routeA_and_routeB_sameOffer_producesIdenticalPhysicalId() {
        val offerRouteA = RawUberTripOffer(
            rawFare = 12.0,
            currency = "EUR",
            pickupDistanceKm = 2.0,
            pickupDurationMinutes = 5.0,
            tripDistanceKm = 8.0,
            tripDurationMinutes = 15.0,
            pickupAddress = "Origen",
            dropoffAddress = "Destino",
            detectedOfferType = UberOfferScreenType.TRIP_OFFER,
            sourceTimestamp = 2000000L
        )

        // Ruta B OCR procesa la misma oferta 800ms después
        val offerRouteB = offerRouteA.copy(sourceTimestamp = 2000800L)

        val tripA = (mapper.mapToDomain(offerRouteA) as TripMappingResult.Success).trip
        val tripB = (mapper.mapToDomain(offerRouteB) as TripMappingResult.Success).trip

        assertEquals(tripA.id, tripB.id)
    }
}
