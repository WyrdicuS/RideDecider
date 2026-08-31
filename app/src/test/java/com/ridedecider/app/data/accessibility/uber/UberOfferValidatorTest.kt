package com.ridedecider.app.data.accessibility.uber

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios para [UberOfferValidator].
 *
 * Verifica la clasificación contextual de pantallas, la eliminación de falsos positivos
 * (números aislados de ganancias/velocidad), la integridad estructural y los límites numéricos.
 */
class UberOfferValidatorTest {

    private lateinit var validator: UberOfferValidator

    @Before
    fun setUp() {
        validator = UberOfferValidator()
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
        category: String? = "UberX"
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
            detectedOfferType = offerType
        )
    }

    // =========================================================================
    // TC-VAL-01: Raw vacío
    // =========================================================================
    @Test
    fun tcVal01_emptyRawOffer_shouldReturnNoOfferAndInvalid() {
        val emptyRaw = RawUberTripOffer()

        val result = validator.validate(emptyRaw)

        assertEquals(UberOfferScreenType.NO_OFFER, result.screenType)
        assertFalse(result.isValidOffer)
        assertTrue(result.reasons.contains(UberValidationReason.MISSING_OFFER_TYPE))
    }

    // =========================================================================
    // TC-VAL-02: Oferta TRIP válida
    // =========================================================================
    @Test
    fun tcVal02_validTripOffer_shouldReturnTripOfferAndValid() {
        val raw = createValidRawOffer(
            offerType = UberOfferScreenType.TRIP_OFFER,
            fare = 14.20,
            pickupDistance = 3.2,
            pickupDuration = 6.0,
            tripDistance = 8.5,
            tripDuration = 18.0
        )

        val result = validator.validate(raw)

        assertEquals(UberOfferScreenType.TRIP_OFFER, result.screenType)
        assertTrue(result.isValidOffer)
        assertTrue(result.reasons.contains(UberValidationReason.VALID_TRIP_OFFER))
    }

    // =========================================================================
    // TC-VAL-03: Oferta RADAR válida
    // =========================================================================
    @Test
    fun tcVal03_validRadarOffer_shouldReturnRadarOfferAndValid() {
        val raw = createValidRawOffer(
            offerType = UberOfferScreenType.RADAR_OFFER,
            fare = 8.50,
            pickupDistance = 1.1,
            pickupDuration = 4.0,
            tripDistance = 6.0,
            tripDuration = 14.0
        )

        val result = validator.validate(raw)

        assertEquals(UberOfferScreenType.RADAR_OFFER, result.screenType)
        assertTrue(result.isValidOffer)
        assertTrue(result.reasons.contains(UberValidationReason.VALID_RADAR_OFFER))
    }

    // =========================================================================
    // TC-VAL-04: Pantalla con únicamente número de tarifa (ganancias del día)
    // =========================================================================
    @Test
    fun tcVal04_isolatedFareWithoutTripStructure_shouldReturnNoOfferAndInvalid() {
        val raw = RawUberTripOffer(
            rawFare = 84.50,
            detectedOfferType = null,
            pickupDistanceKm = null,
            pickupDurationMinutes = null,
            tripDistanceKm = null,
            tripDurationMinutes = null
        )

        val result = validator.validate(raw)

        assertEquals(UberOfferScreenType.NO_OFFER, result.screenType)
        assertFalse(result.isValidOffer)
    }

    // =========================================================================
    // TC-VAL-05: Fare = 0.0
    // =========================================================================
    @Test
    fun tcVal05_zeroFare_shouldReturnUnknownAndInvalidFare() {
        val raw = createValidRawOffer(fare = 0.0)

        val result = validator.validate(raw)

        assertEquals(UberOfferScreenType.UNKNOWN, result.screenType)
        assertFalse(result.isValidOffer)
        assertTrue(result.reasons.contains(UberValidationReason.INVALID_FARE))
    }

    // =========================================================================
    // TC-VAL-06: Fare negativa
    // =========================================================================
    @Test
    fun tcVal06_negativeFare_shouldReturnUnknownAndInvalidFare() {
        val raw = createValidRawOffer(fare = -15.0)

        val result = validator.validate(raw)

        assertEquals(UberOfferScreenType.UNKNOWN, result.screenType)
        assertFalse(result.isValidOffer)
        assertTrue(result.reasons.contains(UberValidationReason.INVALID_FARE))
    }

    // =========================================================================
    // TC-VAL-07: Distancia negativa
    // =========================================================================
    @Test
    fun tcVal07_negativeDistance_shouldReturnUnknownAndInvalidDistance() {
        val raw = createValidRawOffer(pickupDistance = -2.0)

        val result = validator.validate(raw)

        assertEquals(UberOfferScreenType.UNKNOWN, result.screenType)
        assertFalse(result.isValidOffer)
        assertTrue(result.reasons.contains(UberValidationReason.INVALID_DISTANCE))
    }

    // =========================================================================
    // TC-VAL-08: Tiempo negativo
    // =========================================================================
    @Test
    fun tcVal08_negativeDuration_shouldReturnUnknownAndInvalidDuration() {
        val raw = createValidRawOffer(tripDuration = -10.0)

        val result = validator.validate(raw)

        assertEquals(UberOfferScreenType.UNKNOWN, result.screenType)
        assertFalse(result.isValidOffer)
        assertTrue(result.reasons.contains(UberValidationReason.INVALID_DURATION))
    }

    // =========================================================================
    // TC-VAL-09: Oferta incompleta (falta tripDuration)
    // =========================================================================
    @Test
    fun tcVal09_missingTripDuration_shouldReturnUnknownAndMissingTripDuration() {
        val raw = createValidRawOffer(tripDuration = null)

        val result = validator.validate(raw)

        assertEquals(UberOfferScreenType.UNKNOWN, result.screenType)
        assertFalse(result.isValidOffer)
        assertTrue(result.reasons.contains(UberValidationReason.INCOMPLETE_OFFER))
        assertTrue(result.reasons.contains(UberValidationReason.MISSING_TRIP_DURATION))
    }

    // =========================================================================
    // TC-VAL-10: Categoría UNKNOWN/null con datos válidos
    // =========================================================================
    @Test
    fun tcVal10_unknownCategory_shouldRemainValidOffer() {
        val raw = createValidRawOffer(category = null)

        val result = validator.validate(raw)

        assertEquals(UberOfferScreenType.TRIP_OFFER, result.screenType)
        assertTrue(result.isValidOffer)
    }

    // =========================================================================
    // TC-VAL-11: Dirección de destino null con datos válidos
    // =========================================================================
    @Test
    fun tcVal11_nullDropoffAddress_shouldRemainValidOffer() {
        val raw = createValidRawOffer(dropoffAddress = null)

        val result = validator.validate(raw)

        assertEquals(UberOfferScreenType.TRIP_OFFER, result.screenType)
        assertTrue(result.isValidOffer)
    }

    // =========================================================================
    // TC-VAL-12: Valores fuera de límites (físicamente imposibles)
    // =========================================================================
    @Test
    fun tcVal12_valuesOutOfBounds_shouldReturnUnknownAndInvalidReasons() {
        val raw = createValidRawOffer(
            fare = 9999.0,
            tripDistance = 500.0,
            tripDuration = 1000.0
        )

        val result = validator.validate(raw)

        assertEquals(UberOfferScreenType.UNKNOWN, result.screenType)
        assertFalse(result.isValidOffer)
        assertTrue(result.reasons.contains(UberValidationReason.INVALID_FARE))
        assertTrue(result.reasons.contains(UberValidationReason.INVALID_DISTANCE))
        assertTrue(result.reasons.contains(UberValidationReason.INVALID_DURATION))
    }

    // =========================================================================
    // TC-VAL-13: Pantalla explícita de navegación activa (ACTIVE_TRIP)
    // =========================================================================
    @Test
    fun tcVal13_activeTripScreen_shouldReturnActiveTripAndInvalidOffer() {
        val raw = RawUberTripOffer(detectedOfferType = UberOfferScreenType.ACTIVE_TRIP)

        val result = validator.validate(raw)

        assertEquals(UberOfferScreenType.ACTIVE_TRIP, result.screenType)
        assertFalse(result.isValidOffer)
    }
}
