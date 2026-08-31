package com.ridedecider.app.data.accessibility.uber

import com.ridedecider.app.domain.model.CancellationReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UberAccessibilityParserLifecycleTest {

    private lateinit var parser: UberAccessibilityParser

    @Before
    fun setUp() {
        parser = UberAccessibilityParser()
    }

    private fun createSnapshot(vararg texts: String, resIds: List<String?> = emptyList()): UberNodeSnapshot {
        val children = texts.mapIndexed { index, text ->
            UberNodeSnapshot(
                text = text,
                viewIdResourceName = resIds.getOrNull(index)
            )
        }
        return UberNodeSnapshot(
            className = "android.widget.FrameLayout",
            children = children
        )
    }

    // 1. Detección de ACTIVE_TRIP mediante señales combinadas de navegación
    @Test
    fun activeTrip_detectedByTurnAndManeuverSignals() {
        val snapshot = createSnapshot(
            "Gire a la derecha en 200 m",
            "Calle Alcalá",
            "Límite de velocidad: 50 km/h",
            "8 min restantes",
            "2.4 km restantes"
        )

        val rawOffer = parser.parse(snapshot)

        assertEquals(UberOfferScreenType.ACTIVE_TRIP, rawOffer.detectedOfferType)
        assertNull(rawOffer.rawFare)
    }

    // 2. Detección de ACTIVE_TRIP en inglés
    @Test
    fun activeTrip_detectedByEnglishSignals() {
        val snapshot = createSnapshot(
            "Turn left onto Main St",
            "Speed limit: 30 mph",
            "5 min remaining"
        )

        val rawOffer = parser.parse(snapshot)

        assertEquals(UberOfferScreenType.ACTIVE_TRIP, rawOffer.detectedOfferType)
    }

    // 3. Detección de Cancelación por Pasajero con tarifa de compensación
    @Test
    fun cancellation_byRider_withFee() {
        val snapshot = createSnapshot(
            "El viaje fue cancelado por el pasajero",
            "Tarifa de cancelación: 4,50 €"
        )

        val rawOffer = parser.parse(snapshot)

        assertEquals(UberOfferScreenType.TRIP_CANCELLED, rawOffer.detectedOfferType)
        assertEquals(CancellationReason.RIDER, rawOffer.cancellationReason)
        assertEquals(4.50, rawOffer.cancellationFee ?: 0.0, 0.001)
    }

    // 4. Detección de Cancelación por No-Show con tarifa de compensación
    @Test
    fun cancellation_noShow_withFee() {
        val snapshot = createSnapshot(
            "El pasajero no se presentó tras el tiempo de espera",
            "Tarifa de cancelación: 5,00 €"
        )

        val rawOffer = parser.parse(snapshot)

        assertEquals(UberOfferScreenType.TRIP_CANCELLED, rawOffer.detectedOfferType)
        assertEquals(CancellationReason.NO_SHOW, rawOffer.cancellationReason)
        assertEquals(5.00, rawOffer.cancellationFee ?: 0.0, 0.001)
    }

    // 5. Detección de Cancelación por Conductor
    @Test
    fun cancellation_byDriver_withoutFee() {
        val snapshot = createSnapshot(
            "Has cancelado el viaje",
            "El viaje ha terminado"
        )

        val rawOffer = parser.parse(snapshot)

        assertEquals(UberOfferScreenType.TRIP_CANCELLED, rawOffer.detectedOfferType)
        assertEquals(CancellationReason.DRIVER, rawOffer.cancellationReason)
        assertNull(rawOffer.cancellationFee)
    }

    // 6. Detección de Cancelación por Uber
    @Test
    fun cancellation_byUber_withFee() {
        val snapshot = createSnapshot(
            "Viaje cancelado por Uber",
            "Tarifa de cancelación: 3,00 €"
        )

        val rawOffer = parser.parse(snapshot)

        assertEquals(UberOfferScreenType.TRIP_CANCELLED, rawOffer.detectedOfferType)
        assertEquals(CancellationReason.UBER, rawOffer.cancellationReason)
        assertEquals(3.00, rawOffer.cancellationFee ?: 0.0, 0.001)
    }

    // 7. Detección de Cancelación con motivo desconocido
    @Test
    fun cancellation_generic_unknownReason() {
        val snapshot = createSnapshot(
            "Viaje cancelado",
            "Vuelve al mapa para recibir más viajes"
        )

        val rawOffer = parser.parse(snapshot)

        assertEquals(UberOfferScreenType.TRIP_CANCELLED, rawOffer.detectedOfferType)
        assertEquals(CancellationReason.UNKNOWN, rawOffer.cancellationReason)
    }

    // 8. Detección de Finalización de Viaje con Importe Ganado
    @Test
    fun completion_withFinalFare() {
        val snapshot = createSnapshot(
            "Viaje completado",
            "Has ganado",
            "11,50 €",
            "Resumen del viaje",
            "14 min",
            "4.2 km"
        )

        val rawOffer = parser.parse(snapshot)

        assertEquals(UberOfferScreenType.TRIP_COMPLETED, rawOffer.detectedOfferType)
        assertEquals(11.50, rawOffer.finalEarningsEur ?: 0.0, 0.001)
    }
}
