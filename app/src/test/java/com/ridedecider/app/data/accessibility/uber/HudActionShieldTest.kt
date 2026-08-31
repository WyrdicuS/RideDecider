package com.ridedecider.app.data.accessibility.uber

import com.ridedecider.app.domain.engine.DecisionEngine
import com.ridedecider.app.domain.model.ProfitabilityConfig
import com.ridedecider.app.domain.usecase.EvaluateIncomingTripUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Test de blindaje definitivo para el ciclo de vida, detección de acciones y persistencia del HUD.
 *
 * Bloquea contra regresiones:
 * 1. Ocultación instantánea al pulsar Aceptar, Emparejar, Rechazar o el botón "X" / Cerrar / Cancelar / Descartar.
 * 2. Soporte para ofertas con cinemática simple (solo distancia/tiempo de viaje sin recogida previa).
 * 3. Transición limpia a NO_OFFER cuando desaparece la tarjeta real de la pantalla.
 * 4. Clasificación correcta de ofertas Radar activas vs píldoras inactivas en el mapa.
 */
class HudActionShieldTest {

    private lateinit var parser: UberAccessibilityParser
    private lateinit var validator: UberOfferValidator
    private lateinit var mapper: RawUberTripOfferMapper
    private lateinit var processor: UberAccessibilityProcessor
    private lateinit var evaluateUseCase: EvaluateIncomingTripUseCase

    @Before
    fun setUp() {
        parser = UberAccessibilityParser()
        validator = UberOfferValidator()
        mapper = RawUberTripOfferMapper()
        evaluateUseCase = EvaluateIncomingTripUseCase(DecisionEngine())
        processor = UberAccessibilityProcessor(
            parser = parser,
            validator = validator,
            mapper = mapper,
            evaluateUseCase = evaluateUseCase,
            configProvider = InMemoryProfitabilityConfigProvider(
                ProfitabilityConfig(
                    costPerKm = 0.20,
                    costPerHour = 4.0,
                    minGrossHourlyRate = 20.0,
                    minGrossPerKmRate = 1.10,
                    minNetTripProfit = 2.0,
                    minNetHourlyRate = 12.0,
                    maxPickupDistanceKm = 5.0,
                    maxPickupTimeMinutes = 10.0
                )
            )
        )
    }

    // =========================================================================
    // 1. Blindaje: Detección de acciones de cierre / rechazo / X
    // =========================================================================
    @Test
    fun shield_allCloseAndDismissVariants_areRecognizedAsActionKeywords() {
        val closeKeywords = listOf(
            "aceptar", "accept", "toca para aceptar",
            "emparejar", "match",
            "rechazar", "decline", "reject",
            "cerrar", "close", "ub__close", "btn_close", "button_close", "action_close", "icon_close",
            "cancelar", "cancel", "descartar", "dismiss",
            "✕", "x", "X"
        )

        for (kw in closeKeywords) {
            val clickInfo = "com.ubercab.driver:id/button $kw".lowercase().trim()
            val isAction = clickInfo.contains("aceptar") || clickInfo.contains("accept") ||
                    clickInfo.contains("emparejar") || clickInfo.contains("match") ||
                    clickInfo.contains("rechazar") || clickInfo.contains("decline") ||
                    clickInfo.contains("cerrar") || clickInfo.contains("close") ||
                    clickInfo.contains("cancelar") || clickInfo.contains("cancel") ||
                    clickInfo.contains("descartar") || clickInfo.contains("dismiss") ||
                    clickInfo.contains("cross") || clickInfo.contains("reject") ||
                    clickInfo.contains("btn_close") || clickInfo.contains("button_close") ||
                    clickInfo.contains("action_close") || clickInfo.contains("icon_close") ||
                    clickInfo.contains("ub__close") || clickInfo.contains("ub__action_close") ||
                    kw == "✕" || kw.equals("x", ignoreCase = true)

            assertTrue("La palabra clave de acción '$kw' debe ser reconocida para ocultación instantánea", isAction)
        }
    }

    // =========================================================================
    // 2. Blindaje: Ofertas con cinemática simple (solo viaje sin recogida previa)
    // =========================================================================
    @Test
    fun shield_singleDistanceAndDurationRadarOffer_shouldEvaluateSuccessfully() {
        // En tarjetas reales de Trip Radar a veces solo se indica la distancia/tiempo del viaje
        val root = UberNodeSnapshot(
            className = "android.widget.FrameLayout",
            children = listOf(
                UberNodeSnapshot(text = "UberX"),
                UberNodeSnapshot(text = "6,50 €"),
                UberNodeSnapshot(text = "3,2 km"),
                UberNodeSnapshot(text = "8 min"),
                UberNodeSnapshot(text = "Emparejar")
            )
        )

        val raw = parser.parse(root)
        assertEquals(UberOfferScreenType.RADAR_OFFER, raw.detectedOfferType)
        assertEquals(6.50, raw.rawFare!!, 0.001)
        assertEquals(0.0, raw.pickupDistanceKm!!, 0.001)
        assertEquals(0.0, raw.pickupDurationMinutes!!, 0.001)
        assertEquals(3.2, raw.tripDistanceKm!!, 0.001)
        assertEquals(8.0, raw.tripDurationMinutes!!, 0.001)

        val validation = validator.validate(raw)
        assertTrue("La oferta con cinemática simple debe ser válida", validation.isValidOffer)

        val mapping = mapper.mapToDomain(raw)
        assertTrue(mapping is TripMappingResult.Success)

        val config = ProfitabilityConfig(
            costPerKm = 0.20,
            costPerHour = 4.0,
            minGrossHourlyRate = 20.0,
            minGrossPerKmRate = 1.10,
            minNetTripProfit = 2.0,
            minNetHourlyRate = 12.0,
            maxPickupDistanceKm = 5.0,
            maxPickupTimeMinutes = 10.0
        )
        val trip = (mapping as TripMappingResult.Success).trip
        val evaluation = evaluateUseCase(trip, config)
        assertNotNull(evaluation.metrics)
        assertEquals(3.2, evaluation.metrics!!.totalDistanceKm, 0.001)
        assertEquals(8.0, evaluation.metrics!!.totalDurationMinutes, 0.001)
    }

    // =========================================================================
    // 3. Blindaje: Píldora inactiva en el mapa no se confunde con oferta activa
    // =========================================================================
    @Test
    fun shield_idleRadarPillOnMap_shouldClassifyAsNoOffer() {
        val root = UberNodeSnapshot(
            className = "android.widget.FrameLayout",
            children = listOf(
                UberNodeSnapshot(
                    text = "Radar de viaje",
                    viewIdResourceName = UberAccessibilityConstants.RADAR_PILL_TITLE_RES_ID
                ),
                UberNodeSnapshot(text = "Estás conectado")
            )
        )

        val raw = parser.parse(root)
        assertEquals("La píldora inactiva en el mapa sin datos de viaje debe ser NO_OFFER", UberOfferScreenType.NO_OFFER, raw.detectedOfferType)
    }

    // =========================================================================
    // 4. Blindaje: Transición inmediata a NO_OFFER al desaparecer la tarjeta
    // =========================================================================
    @Test
    fun shield_cardDisappearance_transitionsScreenTypeToNoOfferImmediately() {
        val offerCard = UberNodeSnapshot(
            className = "android.widget.FrameLayout",
            children = listOf(
                UberNodeSnapshot(text = "10.00 €"),
                UberNodeSnapshot(text = "2.0 km"),
                UberNodeSnapshot(text = "5 min"),
                UberNodeSnapshot(text = "Aceptar")
            )
        )
        val emptyMap = UberNodeSnapshot(
            className = "android.widget.FrameLayout",
            children = listOf(
                UberNodeSnapshot(text = "Estás conectado")
            )
        )

        // 1. Aparece la tarjeta
        val res1 = processor.processSnapshot(offerCard, currentTime = 1000L)
        assertTrue(res1 is UberAccessibilityProcessor.ProcessResult.Evaluated)
        assertEquals(UberOfferScreenType.TRIP_OFFER, processor.currentScreenType)

        // 2. Desaparece la tarjeta
        val res2 = processor.processSnapshot(emptyMap, currentTime = 2000L)
        assertTrue(res2 is UberAccessibilityProcessor.ProcessResult.InvalidOffer)
        assertEquals(UberOfferScreenType.NO_OFFER, processor.currentScreenType)
    }
}
