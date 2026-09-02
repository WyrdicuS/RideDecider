package com.ridedecider.app.data.accessibility.uber

import com.ridedecider.app.domain.model.TripOfferType
import com.ridedecider.app.domain.model.UberCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios para [RawUberTripOfferMapper].
 *
 * Verifica la correcta transformación de [RawUberTripOffer] a la entidad [com.ridedecider.app.domain.model.Trip],
 * la gestión segura de campos opcionales vs obligatorios y el determinismo en la generación de IDs.
 */
class RawUberTripOfferMapperTest {

    private lateinit var mapper: RawUberTripOfferMapper

    @Before
    fun setUp() {
        mapper = RawUberTripOfferMapper()
    }

    private fun createValidRawOffer(
        offerType: UberOfferScreenType = UberOfferScreenType.TRIP_OFFER,
        fare: Double? = 14.20,
        currency: String? = "EUR",
        pickupDistance: Double? = 3.2,
        pickupDuration: Double? = 6.0,
        tripDistance: Double? = 8.5,
        tripDuration: Double? = 18.0,
        pickupAddress: String? = "Calle Mayor 10",
        dropoffAddress: String? = "Aeropuerto T4",
        category: String? = "UberX",
        timestamp: Long = 1700000000000L
    ): RawUberTripOffer {
        return RawUberTripOffer(
            rawFare = fare,
            currency = currency,
            pickupDistanceKm = pickupDistance,
            pickupDurationMinutes = pickupDuration,
            tripDistanceKm = tripDistance,
            tripDurationMinutes = tripDuration,
            pickupAddress = pickupAddress,
            dropoffAddress = dropoffAddress,
            category = category,
            detectedOfferType = offerType,
            sourceTimestamp = timestamp
        )
    }

    // =========================================================================
    // TC-MAP-01: Oferta completamente válida
    // =========================================================================
    @Test
    fun tcMap01_validRawOffer_shouldReturnSuccessWithCorrectTrip() {
        val raw = createValidRawOffer(
            offerType = UberOfferScreenType.TRIP_OFFER,
            fare = 14.20,
            currency = "EUR",
            pickupDistance = 3.2,
            pickupDuration = 6.0,
            tripDistance = 8.5,
            tripDuration = 18.0,
            pickupAddress = "Calle Mayor 10",
            dropoffAddress = "Aeropuerto T4",
            category = "UberX",
            timestamp = 1700000000000L
        )

        val result = mapper.mapToDomain(raw)

        assertTrue(result is TripMappingResult.Success)
        val trip = (result as TripMappingResult.Success).trip

        assertEquals(1700000000000L, trip.timestamp)
        assertEquals(TripOfferType.TRIP_OFFER, trip.offerType)
        assertEquals(UberCategory.UBER_X, trip.category)
        assertEquals(14.20, trip.rawFare!!, 0.0001)
        assertEquals("EUR", trip.currency)
        assertEquals(3.2, trip.pickupDistanceKm!!, 0.0001)
        assertEquals(6.0, trip.pickupDurationMinutes!!, 0.0001)
        assertEquals(8.5, trip.tripDistanceKm!!, 0.0001)
        assertEquals(18.0, trip.tripDurationMinutes!!, 0.0001)
        assertEquals("Calle Mayor 10", trip.pickupAddress)
        assertEquals("Aeropuerto T4", trip.dropoffAddress)
    }

    // =========================================================================
    // TC-MAP-02: Falta tarifa
    // =========================================================================
    @Test
    fun tcMap02_missingFare_shouldReturnFailureMissingFare() {
        val raw = createValidRawOffer(fare = null)

        val result = mapper.mapToDomain(raw)

        assertTrue(result is TripMappingResult.Failure)
        assertEquals(TripMappingFailureReason.MISSING_FARE, (result as TripMappingResult.Failure).reason)
    }

    // =========================================================================
    // TC-MAP-03: Falta distancia de recogida
    // =========================================================================
    @Test
    fun tcMap03_missingPickupDistance_shouldReturnFailureMissingPickupDistance() {
        val raw = createValidRawOffer(pickupDistance = null)

        val result = mapper.mapToDomain(raw)

        assertTrue(result is TripMappingResult.Failure)
        assertEquals(TripMappingFailureReason.MISSING_PICKUP_DISTANCE, (result as TripMappingResult.Failure).reason)
    }

    // =========================================================================
    // TC-MAP-04: Falta duración de recogida
    // =========================================================================
    @Test
    fun tcMap04_missingPickupDuration_shouldReturnFailureMissingPickupDuration() {
        val raw = createValidRawOffer(pickupDuration = null)

        val result = mapper.mapToDomain(raw)

        assertTrue(result is TripMappingResult.Failure)
        assertEquals(TripMappingFailureReason.MISSING_PICKUP_DURATION, (result as TripMappingResult.Failure).reason)
    }

    // =========================================================================
    // TC-MAP-05: Falta distancia del viaje
    // =========================================================================
    @Test
    fun tcMap05_missingTripDistance_shouldReturnFailureMissingTripDistance() {
        val raw = createValidRawOffer(tripDistance = null)

        val result = mapper.mapToDomain(raw)

        assertTrue(result is TripMappingResult.Failure)
        assertEquals(TripMappingFailureReason.MISSING_TRIP_DISTANCE, (result as TripMappingResult.Failure).reason)
    }

    // =========================================================================
    // TC-MAP-06: Falta duración del viaje
    // =========================================================================
    @Test
    fun tcMap06_missingTripDuration_shouldReturnFailureMissingTripDuration() {
        val raw = createValidRawOffer(tripDuration = null)

        val result = mapper.mapToDomain(raw)

        assertTrue(result is TripMappingResult.Failure)
        assertEquals(TripMappingFailureReason.MISSING_TRIP_DURATION, (result as TripMappingResult.Failure).reason)
    }

    // =========================================================================
    // TC-MAP-07: Falta tipo de oferta
    // =========================================================================
    @Test
    fun tcMap07_missingOfferType_shouldReturnFailureMissingOfferType() {
        val raw = createValidRawOffer(offerType = UberOfferScreenType.UNKNOWN)

        val result = mapper.mapToDomain(raw)

        assertTrue(result is TripMappingResult.Failure)
        assertEquals(TripMappingFailureReason.MISSING_OFFER_TYPE, (result as TripMappingResult.Failure).reason)
    }

    // =========================================================================
    // TC-MAP-08: Direcciones null
    // =========================================================================
    @Test
    fun tcMap08_nullAddresses_shouldReturnSuccessWithNullAddresses() {
        val raw = createValidRawOffer(
            pickupAddress = null,
            dropoffAddress = null
        )

        val result = mapper.mapToDomain(raw)

        assertTrue(result is TripMappingResult.Success)
        val trip = (result as TripMappingResult.Success).trip
        assertNull(trip.pickupAddress)
        assertNull(trip.dropoffAddress)
    }

    // =========================================================================
    // TC-MAP-09: Categoría null o no reconocida
    // =========================================================================
    @Test
    fun tcMap09_nullOrUnknownCategory_shouldMapToUnknownCategory() {
        val rawNullCategory = createValidRawOffer(category = null)
        val resultNull = mapper.mapToDomain(rawNullCategory)
        assertTrue(resultNull is TripMappingResult.Success)
        assertEquals(UberCategory.UNKNOWN, (resultNull as TripMappingResult.Success).trip.category)

        val rawUnrecognized = createValidRawOffer(category = "UnknownCustomFleet")
        val resultUnrecognized = mapper.mapToDomain(rawUnrecognized)
        assertTrue(resultUnrecognized is TripMappingResult.Success)
        assertEquals(UberCategory.UNKNOWN, (resultUnrecognized as TripMappingResult.Success).trip.category)
    }

    // =========================================================================
    // TC-MAP-10: Mismo objeto de oferta mantiene su UUID instanceId
    // =========================================================================
    @Test
    fun tcMap10_sameData_shouldProduceSameInstanceId() {
        val raw1 = createValidRawOffer(timestamp = 1700000000000L, fare = 20.0)
        val raw2 = raw1.copy() // Misma instancia copiada conserva instanceId

        val result1 = mapper.mapToDomain(raw1) as TripMappingResult.Success
        val result2 = mapper.mapToDomain(raw2) as TripMappingResult.Success

        assertEquals(result1.trip.id, result2.trip.id)
    }

    // =========================================================================
    // TC-MAP-11: Instancias independientes obtienen UUIDs unívocos aislados
    // =========================================================================
    @Test
    fun tcMap11_differentInstances_shouldProduceDifferentInstanceIds() {
        val raw1 = createValidRawOffer(fare = 15.0, pickupAddress = "Calle Mayor 10")
        val raw2 = createValidRawOffer(fare = 15.0, pickupAddress = "Calle Mayor 10")

        val id1 = (mapper.mapToDomain(raw1) as TripMappingResult.Success).trip.id
        val id2 = (mapper.mapToDomain(raw2) as TripMappingResult.Success).trip.id

        assertNotEquals(id1, id2)
        assertTrue("El ID debe ser un UUID de 36 caracteres", id1.length == 36 && id2.length == 36)
    }

    // =========================================================================
    // TC-MAP-12: Falta divisa (Currency)
    // =========================================================================
    @Test
    fun tcMap12_missingCurrency_shouldReturnFailureMissingCurrency() {
        val raw = createValidRawOffer(currency = null)

        val result = mapper.mapToDomain(raw)

        assertTrue(result is TripMappingResult.Failure)
        assertEquals(TripMappingFailureReason.MISSING_CURRENCY, (result as TripMappingResult.Failure).reason)
    }

    // =========================================================================
    // TC-MAP-13: Mapeo de Pago en Efectivo y Valoración
    // =========================================================================
    @Test
    fun tcMap13_cashPaymentAndRating_shouldMapProperly() {
        val raw = createValidRawOffer().copy(
            isCashPayment = true,
            passengerRating = 4.91
        )

        val result = mapper.mapToDomain(raw)

        assertTrue(result is TripMappingResult.Success)
        val trip = (result as TripMappingResult.Success).trip
        assertTrue(trip.isCashPayment)
        assertEquals(4.91, trip.passengerRating!!, 0.001)
    }
}

