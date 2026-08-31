package com.ridedecider.app.data.accessibility.uber

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios para [UberAccessibilityParser].
 *
 * Valida la extracción textual y semántica de árboles de nodos de accesibilidad en [RawUberTripOffer],
 * asegurando la ausencia de datos inventados y la correcta detección de ofertas, radar y pantallas no relevantes.
 */
class UberAccessibilityParserTest {

    private lateinit var parser: UberAccessibilityParser

    @Before
    fun setUp() {
        parser = UberAccessibilityParser()
    }

    private fun createTree(vararg texts: String): UberNodeSnapshot {
        val children = texts.map { UberNodeSnapshot(text = it) }
        return UberNodeSnapshot(
            className = "android.widget.FrameLayout",
            children = children
        )
    }

    // =========================================================================
    // TC-PARSER-01: Pantalla vacía
    // =========================================================================
    @Test
    fun tcParser01_emptySnapshot_shouldReturnNoOfferWithoutInventedData() {
        val root = UberNodeSnapshot()

        val offer = parser.parse(root)

        assertEquals(UberOfferScreenType.NO_OFFER, offer.detectedOfferType)
        assertNull(offer.rawFare)
        assertNull(offer.currency)
        assertNull(offer.tripDistanceKm)
        assertNull(offer.tripDurationMinutes)
        assertNull(offer.category)
    }

    // =========================================================================
    // TC-PARSER-02: Oferta individual válida
    // =========================================================================
    @Test
    fun tcParser02_exclusiveOffer_shouldExtractTripOfferAndKinematics() {
        val root = createTree("14,20 €", "3,2 km", "11 min", "Aceptar")

        val offer = parser.parse(root)

        assertEquals(UberOfferScreenType.TRIP_OFFER, offer.detectedOfferType)
        assertEquals(14.20, offer.rawFare!!, 0.0001)
        assertEquals("EUR", offer.currency)
        assertEquals(3.2, offer.tripDistanceKm!!, 0.0001)
        assertEquals(11.0, offer.tripDurationMinutes!!, 0.0001)
    }

    // =========================================================================
    // TC-PARSER-03: Oferta Radar de viajes expandida
    // =========================================================================
    @Test
    fun tcParser03_radarOffer_shouldExtractRadarOfferAndValues() {
        val root = createTree("8,50 €", "1,1 km", "6 min", "Emparejar")

        val offer = parser.parse(root)

        assertEquals(UberOfferScreenType.RADAR_OFFER, offer.detectedOfferType)
        assertEquals(8.50, offer.rawFare!!, 0.0001)
        assertEquals("EUR", offer.currency)
        assertEquals(1.1, offer.tripDistanceKm!!, 0.0001)
        assertEquals(6.0, offer.tripDurationMinutes!!, 0.0001)
    }

    // =========================================================================
    // TC-PARSER-04: Ganancias diarias acumuladas
    // =========================================================================
    @Test
    fun tcParser04_dailyEarnings_shouldNotExtractAsTripOffer() {
        val root = createTree("Hoy: 84,50 €", "4,95 ★", "Desconectar")

        val offer = parser.parse(root)

        assertEquals(UberOfferScreenType.NO_OFFER, offer.detectedOfferType)
        assertNull(offer.rawFare)
    }

    // =========================================================================
    // TC-PARSER-05: Números aislados (velocidad / odómetro)
    // =========================================================================
    @Test
    fun tcParser05_isolatedNumbers_shouldNotConvertIntoOffer() {
        val root = createTree("65 km/h", "12 min restantes", "200 m")

        val offer = parser.parse(root)

        assertEquals(UberOfferScreenType.ACTIVE_TRIP, offer.detectedOfferType)
        assertNull(offer.rawFare)
    }

    // =========================================================================
    // TC-PARSER-06: Reconocimiento de divisas (EUR / USD)
    // =========================================================================
    @Test
    fun tcParser06_currencies_shouldRecognizeEurAndUsd() {
        val eurRoot = createTree("14.20 €", "Aceptar", "5 km", "10 min")
        val eurOffer = parser.parse(eurRoot)
        assertEquals("EUR", eurOffer.currency)
        assertEquals(14.20, eurOffer.rawFare!!, 0.0001)

        val usdRoot = createTree("$18.50", "Accept", "5 km", "10 min")
        val usdOffer = parser.parse(usdRoot)
        assertEquals("USD", usdOffer.currency)
        assertEquals(18.50, usdOffer.rawFare!!, 0.0001)
    }

    // =========================================================================
    // TC-PARSER-07: Formatos decimales (coma y punto)
    // =========================================================================
    @Test
    fun tcParser07_decimalFormats_shouldParseCommaAndDot() {
        val commaRoot = createTree("14,20 €", "Aceptar", "5 km")
        val commaOffer = parser.parse(commaRoot)
        assertEquals(14.20, commaOffer.rawFare!!, 0.0001)

        val dotRoot = createTree("14.20 €", "Aceptar", "5 km")
        val dotOffer = parser.parse(dotRoot)
        assertEquals(14.20, dotOffer.rawFare!!, 0.0001)
    }

    // =========================================================================
    // TC-PARSER-08: Normalización de metros a kilómetros
    // =========================================================================
    @Test
    fun tcParser08_metersToKm_shouldNormalizeDistancesBelow1Km() {
        val root = createTree("800 m", "3,5 km", "Aceptar", "12.00 €", "10 min")

        val offer = parser.parse(root)

        assertEquals(0.8, offer.pickupDistanceKm!!, 0.0001)
        assertEquals(3.5, offer.tripDistanceKm!!, 0.0001)
    }

    // =========================================================================
    // TC-PARSER-09: Tiempos combinados (horas y minutos)
    // =========================================================================
    @Test
    fun tcParser09_combinedHoursAndMinutes_shouldNormalizeToMinutes() {
        val root = createTree("1 h 15 min", "Aceptar", "25.00 €", "40 km")

        val offer = parser.parse(root)

        assertEquals(75.0, offer.tripDurationMinutes!!, 0.0001)
    }

    // =========================================================================
    // TC-PARSER-10: Categorías de servicio
    // =========================================================================
    @Test
    fun tcParser10_serviceCategories_shouldExtractCategoryName() {
        val xRoot = createTree("UberX", "Aceptar", "15.00 €", "5 km", "10 min")
        assertEquals("UberX", parser.parse(xRoot).category)

        val comfortRoot = createTree("Comfort", "Aceptar", "15.00 €", "5 km", "10 min")
        assertEquals("Comfort", parser.parse(comfortRoot).category)

        val unrecRoot = createTree("ServicioEspecial", "Aceptar", "15.00 €", "5 km", "10 min")
        assertNull(parser.parse(unrecRoot).category)
    }

    // =========================================================================
    // TC-PARSER-11: Datos parciales
    // =========================================================================
    @Test
    fun tcParser11_partialData_shouldKeepMissingFieldsAsNull() {
        val root = createTree("14.20 €", "Aceptar", "UberX")

        val offer = parser.parse(root)

        assertEquals(14.20, offer.rawFare!!, 0.0001)
        assertNull(offer.pickupDistanceKm)
        assertNull(offer.pickupDurationMinutes)
    }

    // =========================================================================
    // TC-PARSER-12: Navegación activa (ACTIVE_TRIP)
    // =========================================================================
    @Test
    fun tcParser12_activeTripNavigation_shouldClassifyAsActiveTrip() {
        val root = createTree("Gire a la derecha en 200 m", "65 km/h", "12 min restantes")

        val offer = parser.parse(root)

        assertEquals(UberOfferScreenType.ACTIVE_TRIP, offer.detectedOfferType)
    }

    // =========================================================================
    // TC-PARSER-13: Texto irrelevante / promociones
    // =========================================================================
    @Test
    fun tcParser13_irrelevantPromotions_shouldNotGenerateFalseOffer() {
        val root = createTree("Invita a tus amigos y gana más conduciendo", "Descubre nuevas funciones")

        val offer = parser.parse(root)

        assertEquals(UberOfferScreenType.NO_OFFER, offer.detectedOfferType)
        assertNull(offer.rawFare)
    }

    // =========================================================================
    // TC-PARSER-14: Detección exhaustiva de todas las categorías de Uber
    // =========================================================================
    @Test
    fun tcParser14_allSupportedUberCategories_shouldBeDetectedCorrectly() {
        val categories = listOf(
            "UberX",
            "Comfort",
            "Uber Black",
            "Uber Metropolitan",
            "Uber Pet",
            "UberVAN",
            "UberX Priority",
            "UberX and Share"
        )

        for (cat in categories) {
            val root = createTree(cat, "Aceptar", "12.50 €", "4 km", "8 min")
            val offer = parser.parse(root)
            assertEquals("La categoría $cat debe detectarse correctamente", cat, offer.category)
        }
    }

    // =========================================================================
    // TC-PARSER-15: Oferta con etiqueta "Exclusiva"
    // =========================================================================
    @Test
    fun tcParser15_exclusiveLabel_shouldClassifyAsTripOffer() {
        val root = createTree("Exclusiva", "5,02 €", "3,5 km", "6 min")

        val offer = parser.parse(root)

        assertEquals(UberOfferScreenType.TRIP_OFFER, offer.detectedOfferType)
        assertEquals(5.02, offer.rawFare!!, 0.0001)
        assertEquals(3.5, offer.tripDistanceKm!!, 0.0001)
        assertEquals(6.0, offer.tripDurationMinutes!!, 0.0001)
    }

    // =========================================================================
    // TC-PARSER-16: Los indicadores de demanda ("1-18 min") no se confunden con duración de viaje
    // =========================================================================
    @Test
    fun tcParser16_mapDemandIndicators_shouldNotBeParsedAsTripDuration() {
        val mapRoot = createTree("1-18 min", "1-15 min", "Alta demanda")
        val mapOffer = parser.parse(mapRoot)

        assertEquals(UberOfferScreenType.NO_OFFER, mapOffer.detectedOfferType)
        assertNull(mapOffer.tripDurationMinutes)
        assertNull(mapOffer.pickupDurationMinutes)

        val offerWithDemand = createTree(
            "UberX",
            "Exclusiva",
            "5,02 €",
            "1-18 min",
            "A 6 min (3,5 km) de distancia",
            "Viaje de 8 min (3,1 km)",
            "Aceptar"
        )

        val offer = parser.parse(offerWithDemand)

        assertEquals(UberOfferScreenType.TRIP_OFFER, offer.detectedOfferType)
        assertEquals("UberX", offer.category)
        assertEquals(5.02, offer.rawFare!!, 0.0001)
        assertEquals(3.5, offer.pickupDistanceKm!!, 0.0001)
        assertEquals(6.0, offer.pickupDurationMinutes!!, 0.0001)
        assertEquals(3.1, offer.tripDistanceKm!!, 0.0001)
        assertEquals(8.0, offer.tripDurationMinutes!!, 0.0001)
    }

    // =========================================================================
    // TC-PARSER-17: Falsos positivos por botones genéricos aislados
    // =========================================================================
    @Test
    fun tcParser17_genericButtonIsolated_shouldReturnNoOffer() {
        val isolatedAccept = createTree("Aceptar", "Términos y condiciones")
        val resultAccept = parser.parse(isolatedAccept)
        assertEquals(UberOfferScreenType.NO_OFFER, resultAccept.detectedOfferType)

        val isolatedMatch = createTree("Emparejar", "Dispositivos Bluetooth")
        val resultMatch = parser.parse(isolatedMatch)
        assertEquals(UberOfferScreenType.NO_OFFER, resultMatch.detectedOfferType)
    }

    // =========================================================================
    // TC-PARSER-18: Ausencia de precio / distancia / duración
    // =========================================================================
    @Test
    fun tcParser18_missingPriceOrDistanceOrDuration_shouldReflectInRawOffer() {
        val noFare = createTree("UberX", "Aceptar", "4,5 km", "12 min")
        val offerNoFare = parser.parse(noFare)
        assertEquals(UberOfferScreenType.TRIP_OFFER, offerNoFare.detectedOfferType)
        assertNull(offerNoFare.rawFare)
        assertEquals(4.5, offerNoFare.tripDistanceKm!!, 0.0001)
        assertEquals(12.0, offerNoFare.tripDurationMinutes!!, 0.0001)

        val noDist = createTree("UberX", "Aceptar", "10.50 €")
        val offerNoDist = parser.parse(noDist)
        assertEquals(UberOfferScreenType.TRIP_OFFER, offerNoDist.detectedOfferType)
        assertEquals(10.50, offerNoDist.rawFare!!, 0.0001)
        assertNull(offerNoDist.tripDistanceKm)
        assertNull(offerNoDist.tripDurationMinutes)
    }

    // =========================================================================
    // TC-PARSER-19: Píldora estructural de Radar con acción "Emparejar"
    // =========================================================================
    @Test
    fun tcParser19_radarPillWithAction_shouldClassifyAsRadarOffer() {
        val radarPillNode = UberNodeSnapshot(
            className = "android.widget.TextView",
            text = "Radar de viaje",
            viewIdResourceName = "com.ubercab.driver:id/ub__driver_job_offers_pill_title"
        )
        val matchBtn = UberNodeSnapshot(
            className = "android.widget.Button",
            text = "Emparejar",
            isClickable = true
        )
        val root = UberNodeSnapshot(
            className = "android.widget.FrameLayout",
            children = listOf(radarPillNode, matchBtn)
        )

        val offer = parser.parse(root)

        assertEquals(UberOfferScreenType.RADAR_OFFER, offer.detectedOfferType)
        assertNull(offer.rawFare)
        assertNull(offer.pickupDistanceKm)
        assertNull(offer.tripDistanceKm)
    }

    // =========================================================================
    // TC-PARSER-20: Píldora de Radar aislada en mapa sin datos ni acción -> NO_OFFER
    // =========================================================================
    @Test
    fun tcParser20_radarPillByTextWithoutDataOrAction_shouldClassifyAsNoOffer() {
        val root = createTree("Radar de viaje")

        val offer = parser.parse(root)

        assertEquals(UberOfferScreenType.NO_OFFER, offer.detectedOfferType)
        assertNull(offer.rawFare)
        assertNull(offer.tripDistanceKm)
    }

    // =========================================================================
    // TC-PARSER-21: Exclusión estructural del contador de ganancias acumuladas (84,50 €)
    // =========================================================================
    @Test
    fun tcParser21_earningsTrackerExclusionWithAmount_shouldReturnNullFare() {
        val earningsNode = UberNodeSnapshot(
            className = "android.widget.TextView",
            text = "84,50 €",
            viewIdResourceName = "com.ubercab.driver:id/ub__earnings_tracker_counter_content_status"
        )
        val root = UberNodeSnapshot(
            className = "android.widget.FrameLayout",
            children = listOf(earningsNode)
        )

        val offer = parser.parse(root)

        assertNull("El contador de ganancias acumuladas no debe extraerse como tarifa", offer.rawFare)
        assertEquals(UberOfferScreenType.NO_OFFER, offer.detectedOfferType)
    }

    // =========================================================================
    // TC-PARSER-22: Exclusión estructural del contador de ganancias acumuladas (0,00€)
    // =========================================================================
    @Test
    fun tcParser22_earningsTrackerExclusionZeroEuro_shouldReturnNullFare() {
        val earningsZero = UberNodeSnapshot(
            className = "android.widget.TextView",
            text = "0,00€",
            viewIdResourceName = "com.ubercab.driver:id/ub__earnings_tracker_counter_content_status"
        )
        val root = UberNodeSnapshot(
            className = "android.widget.FrameLayout",
            children = listOf(earningsZero)
        )

        val offer = parser.parse(root)

        assertNull("El contador de 0,00€ acumulado no debe extraerse como tarifa", offer.rawFare)
        assertEquals(UberOfferScreenType.NO_OFFER, offer.detectedOfferType)
    }

    // =========================================================================
    // TC-PARSER-23: Oferta real con precio + distancia + duración + "Aceptar"
    // =========================================================================
    @Test
    fun tcParser23_realOfferWithAllKinematicsAndAccept_shouldClassifyAsTripOffer() {
        val fare = UberNodeSnapshot(text = "5,02 €", className = "android.widget.TextView")
        val pickup = UberNodeSnapshot(text = "A 6 min (3.5 km) de distancia", className = "android.widget.TextView")
        val trip = UberNodeSnapshot(text = "Viaje de 6 min (3.1 km)", className = "android.widget.TextView")
        val acceptBtn = UberNodeSnapshot(text = "Aceptar", className = "android.widget.Button", isClickable = true)
        val category = UberNodeSnapshot(text = "UberX", className = "android.widget.TextView")

        val root = UberNodeSnapshot(
            className = "android.widget.FrameLayout",
            children = listOf(category, fare, pickup, trip, acceptBtn)
        )

        val offer = parser.parse(root)

        assertEquals(UberOfferScreenType.TRIP_OFFER, offer.detectedOfferType)
        assertEquals(5.02, offer.rawFare!!, 0.0001)
        assertEquals("EUR", offer.currency)
        assertEquals("UberX", offer.category)
        assertEquals(3.5, offer.pickupDistanceKm!!, 0.0001)
        assertEquals(6.0, offer.pickupDurationMinutes!!, 0.0001)
        assertEquals(3.1, offer.tripDistanceKm!!, 0.0001)
        assertEquals(6.0, offer.tripDurationMinutes!!, 0.0001)
    }

    // =========================================================================
    // TC-PARSER-24: Nodo genérico con texto "Radar" aislado sin Resource ID
    // =========================================================================
    @Test
    fun tcParser24_genericRadarTextIsolated_shouldNotClassifyAsRadarOffer() {
        val genericRadar = createTree("Radar")

        val offer = parser.parse(genericRadar)

        assertEquals("La palabra 'Radar' aislada no debe considerarse una oferta radar", UberOfferScreenType.NO_OFFER, offer.detectedOfferType)
    }

    // =========================================================================
    // TC-PARSER-25: Trip Radar expandido con ub_driver_offers_browse_pill_container y primary_touch_area
    // =========================================================================
    @Test
    fun tcParser25_radarExpandedWithBrowseContainerAndPrimaryTouchArea_shouldClassifyAsRadarOffer() {
        val fare = UberNodeSnapshot(text = "7,50 €", className = "android.widget.TextView")
        val pickup = UberNodeSnapshot(text = "A 4 min (1.8 km) de distancia", className = "android.widget.TextView")
        val trip = UberNodeSnapshot(text = "Viaje de 12 min (5.0 km)", className = "android.widget.TextView")
        val matchBtn = UberNodeSnapshot(text = "Emparejar", className = "android.widget.Button", isClickable = true)

        val touchArea = UberNodeSnapshot(
            viewIdResourceName = UberAccessibilityConstants.PRIMARY_TOUCH_AREA_RES_ID,
            className = "android.widget.FrameLayout",
            children = listOf(fare, pickup, trip, matchBtn)
        )

        val browseContainer = UberNodeSnapshot(
            viewIdResourceName = UberAccessibilityConstants.RADAR_BROWSE_CONTAINER_RES_ID,
            className = "android.widget.FrameLayout",
            children = listOf(touchArea)
        )

        val root = UberNodeSnapshot(
            viewIdResourceName = UberAccessibilityConstants.ONLINE_VIEW_RES_ID,
            className = "android.widget.FrameLayout",
            children = listOf(browseContainer)
        )

        val offer = parser.parse(root)

        assertEquals(UberOfferScreenType.RADAR_OFFER, offer.detectedOfferType)
        assertEquals(7.50, offer.rawFare!!, 0.0001)
        assertEquals("EUR", offer.currency)
        assertEquals(1.8, offer.pickupDistanceKm!!, 0.0001)
        assertEquals(4.0, offer.pickupDurationMinutes!!, 0.0001)
        assertEquals(5.0, offer.tripDistanceKm!!, 0.0001)
        assertEquals(12.0, offer.tripDurationMinutes!!, 0.0001)
    }

    // =========================================================================
    // TC-PARSER-26: Oferta de viaje con barra de progreso de cuenta atrás de 5s
    // =========================================================================
    @Test
    fun tcParser26_tripOfferWithCountdownProgress_shouldClassifyAsTripOffer() {
        val fare = UberNodeSnapshot(text = "11,80 €", className = "android.widget.TextView")
        val kinematics = UberNodeSnapshot(text = "Viaje de 8 min (4.2 km)", className = "android.widget.TextView")
        val progressBar = UberNodeSnapshot(
            viewIdResourceName = UberAccessibilityConstants.UPFRONT_OFFER_PROGRESS_RES_ID,
            className = "android.widget.ProgressBar"
        )
        val progressText = UberNodeSnapshot(
            viewIdResourceName = UberAccessibilityConstants.UPFRONT_OFFER_PROGRESS_TEXT_RES_ID,
            text = "5s",
            className = "android.widget.TextView"
        )

        val root = UberNodeSnapshot(
            className = "android.widget.FrameLayout",
            children = listOf(fare, kinematics, progressBar, progressText)
        )

        val offer = parser.parse(root)

        assertEquals(UberOfferScreenType.TRIP_OFFER, offer.detectedOfferType)
        assertEquals(11.80, offer.rawFare!!, 0.0001)
        assertEquals(4.2, offer.tripDistanceKm!!, 0.0001)
        assertEquals(8.0, offer.tripDurationMinutes!!, 0.0001)
    }

    // =========================================================================
    // TC-PARSER-27: Píldora Radar con badge, contenedor y acción confirmados
    // =========================================================================
    @Test
    fun tcParser27_radarPillWithBadgeAndContainer_shouldClassifyAsRadarOffer() {
        val pillTitle = UberNodeSnapshot(
            viewIdResourceName = UberAccessibilityConstants.RADAR_PILL_TITLE_RES_ID,
            text = "Radar de viaje",
            className = "android.widget.TextView"
        )
        val pillBadge = UberNodeSnapshot(
            viewIdResourceName = UberAccessibilityConstants.RADAR_PILL_BADGE_RES_ID,
            text = "1",
            className = "android.widget.TextView"
        )
        val matchBtn = UberNodeSnapshot(
            text = "Emparejar",
            className = "android.widget.Button",
            isClickable = true
        )
        val pillContainer = UberNodeSnapshot(
            viewIdResourceName = UberAccessibilityConstants.RADAR_PILL_CONTAINER_RES_ID,
            className = "android.widget.FrameLayout",
            isClickable = true,
            children = listOf(pillTitle, pillBadge, matchBtn)
        )

        val root = UberNodeSnapshot(
            className = "android.widget.FrameLayout",
            children = listOf(pillContainer)
        )

        val offer = parser.parse(root)

        assertEquals(UberOfferScreenType.RADAR_OFFER, offer.detectedOfferType)
    }

    // =========================================================================
    // TC-PARSER-28: Captura real Viaje Directo (Exclusiva, 5,02 €, recogida + destino)
    // =========================================================================
    @Test
    fun tcParser28_realDirectTripScreenshot_shouldExtractAllFieldsWithPrecision() {
        val root = UberNodeSnapshot(
            className = "android.widget.FrameLayout",
            children = listOf(
                UberNodeSnapshot(text = "UberX"),
                UberNodeSnapshot(text = "Exclusiva"),
                UberNodeSnapshot(text = "5,02 €"),
                UberNodeSnapshot(text = "Pago en efectivo"),
                UberNodeSnapshot(text = "★ 4,91"),
                UberNodeSnapshot(text = "Tarifa (excl. tasa de servicio de Uber)"),
                UberNodeSnapshot(text = "A 6 min (3.5 km) de distancia"),
                UberNodeSnapshot(text = "Calle del Pósito 1, Fuenlabrada"),
                UberNodeSnapshot(text = "Viaje de 6 min (3.1 km)"),
                UberNodeSnapshot(text = "Urbanización Parque Miraflores 56, Fuenlabrada"),
                UberNodeSnapshot(text = "Aceptar")
            )
        )

        val offer = parser.parse(root)

        assertEquals(UberOfferScreenType.TRIP_OFFER, offer.detectedOfferType)
        assertEquals(5.02, offer.rawFare!!, 0.001)
        assertEquals("EUR", offer.currency)
        assertEquals(3.5, offer.pickupDistanceKm!!, 0.001)
        assertEquals(6.0, offer.pickupDurationMinutes!!, 0.001)
        assertEquals("Calle del Pósito 1, Fuenlabrada", offer.pickupAddress)
        assertEquals(3.1, offer.tripDistanceKm!!, 0.001)
        assertEquals(6.0, offer.tripDurationMinutes!!, 0.001)
        assertEquals("Urbanización Parque Miraflores 56, Fuenlabrada", offer.dropoffAddress)
        assertEquals("UberX", offer.category)
        assertEquals(true, offer.isCashPayment)
        assertEquals(4.91, offer.passengerRating!!, 0.001)
    }

    // =========================================================================
    // TC-PARSER-29: Captura real Viaje Radar (Emparejar, 5 € entero, recogida + destino)
    // =========================================================================
    @Test
    fun tcParser29_realRadarTripScreenshot_integerFare_shouldExtractAllFieldsWithPrecision() {
        val root = UberNodeSnapshot(
            className = "android.widget.FrameLayout",
            children = listOf(
                UberNodeSnapshot(text = "UberX"),
                UberNodeSnapshot(text = "5 €"),
                UberNodeSnapshot(text = "Pago en efectivo"),
                UberNodeSnapshot(text = "★ 4,91"),
                UberNodeSnapshot(text = "Tarifa (excl. tasa de servicio de Uber)"),
                UberNodeSnapshot(text = "A 5 min (3.4 km) de distancia"),
                UberNodeSnapshot(text = "Calle del Pósito 1, Fuenlabrada"),
                UberNodeSnapshot(text = "Viaje de 6 min (3.1 km)"),
                UberNodeSnapshot(text = "Urbanización Parque Miraflores 56, Fuenlabrada"),
                UberNodeSnapshot(text = "Emparejar")
            )
        )

        val offer = parser.parse(root)

        assertEquals(UberOfferScreenType.RADAR_OFFER, offer.detectedOfferType)
        assertEquals(5.00, offer.rawFare!!, 0.001)
        assertEquals("EUR", offer.currency)
        assertEquals(3.4, offer.pickupDistanceKm!!, 0.001)
        assertEquals(5.0, offer.pickupDurationMinutes!!, 0.001)
        assertEquals("Calle del Pósito 1, Fuenlabrada", offer.pickupAddress)
        assertEquals(3.1, offer.tripDistanceKm!!, 0.001)
        assertEquals(6.0, offer.tripDurationMinutes!!, 0.001)
        assertEquals("Urbanización Parque Miraflores 56, Fuenlabrada", offer.dropoffAddress)
        assertEquals("UberX", offer.category)
        assertEquals(true, offer.isCashPayment)
        assertEquals(4.91, offer.passengerRating!!, 0.001)
    }

    // =========================================================================
    // TC-PARSER-30: Varias categorías (Comfort, Black, Van, etc.)
    // =========================================================================
    @Test
    fun tcParser30_variousCategories_shouldExtractCategoryProperly() {
        val comfortRoot = UberNodeSnapshot(
            className = "android.widget.FrameLayout",
            children = listOf(
                UberNodeSnapshot(text = "Comfort"),
                UberNodeSnapshot(text = "12,50 €"),
                UberNodeSnapshot(text = "A 4 min (2.0 km) de distancia"),
                UberNodeSnapshot(text = "Viaje de 10 min (5.0 km)"),
                UberNodeSnapshot(text = "Aceptar")
            )
        )
        val comfortOffer = parser.parse(comfortRoot)
        assertEquals("Comfort", comfortOffer.category)
        assertEquals(UberOfferScreenType.TRIP_OFFER, comfortOffer.detectedOfferType)

        val blackRoot = UberNodeSnapshot(
            className = "android.widget.FrameLayout",
            children = listOf(
                UberNodeSnapshot(text = "Uber Black"),
                UberNodeSnapshot(text = "25 €"),
                UberNodeSnapshot(text = "A 3 min (1.5 km) de distancia"),
                UberNodeSnapshot(text = "Viaje de 15 min (8.0 km)"),
                UberNodeSnapshot(text = "Emparejar")
            )
        )
        val blackOffer = parser.parse(blackRoot)
        assertEquals("Uber Black", blackOffer.category)
        assertEquals(UberOfferScreenType.RADAR_OFFER, blackOffer.detectedOfferType)
    }

    // =========================================================================
    // TC-PARSER-31: Detección explícita de cancelación por el pasajero (Español)
    // =========================================================================
    @Test
    fun tcParser31_riderCancellationExplicitMessage_shouldClassifyAsTripCancelled() {
        val cancelRoot = UberNodeSnapshot(
            className = "android.widget.FrameLayout",
            children = listOf(
                UberNodeSnapshot(text = "El viaje fue cancelado por el pasajero")
            )
        )

        val offer = parser.parse(cancelRoot)

        assertEquals(UberOfferScreenType.TRIP_CANCELLED, offer.detectedOfferType)
        assertEquals("El viaje fue cancelado por el pasajero", offer.cancellationReasonText)
    }

    // =========================================================================
    // TC-PARSER-32: Detección de cancelación por el pasajero (Inglés)
    // =========================================================================
    @Test
    fun tcParser32_riderCancellationEnglishVariant_shouldClassifyAsTripCancelled() {
        val cancelRoot = UberNodeSnapshot(
            className = "android.widget.FrameLayout",
            children = listOf(
                UberNodeSnapshot(text = "The trip was cancelled by the rider")
            )
        )

        val offer = parser.parse(cancelRoot)

        assertEquals(UberOfferScreenType.TRIP_CANCELLED, offer.detectedOfferType)
        assertEquals("The trip was cancelled by the rider", offer.cancellationReasonText)
    }

    // =========================================================================
    // TC-PARSER-33: Cancelación del pasajero con compensación
    // =========================================================================
    // =========================================================================
    // TC-PARSER-34: Caso real de Trip Radar en vivo (Móstoles 8,22 €)
    // =========================================================================
    @Test
    fun tcParser34_liveUberRadarMostoles_shouldExtractAllValuesAccurately() {
        val root = UberNodeSnapshot(
            className = "android.widget.FrameLayout",
            children = listOf(
                UberNodeSnapshot(text = "0,00€", viewIdResourceName = "com.ubercab.driver:id/ub__earnings_tracker_counter_content_status"),
                UberNodeSnapshot(text = "Radar de viaje 3"),
                UberNodeSnapshot(text = "UberX"),
                UberNodeSnapshot(text = "8,22 €"),
                UberNodeSnapshot(text = "★ 4,71"),
                UberNodeSnapshot(text = "Tarifa (excl. tasa de servicio de Uber)"),
                UberNodeSnapshot(text = "A 8 min (2.5 km) de distancia"),
                UberNodeSnapshot(text = "Calle Buenos Aires 1, Móstoles"),
                UberNodeSnapshot(text = "Viaje de 7 min (2.3 km)"),
                UberNodeSnapshot(text = "Calle Doctor Luis Montes 2, Móstoles"),
                UberNodeSnapshot(text = "Emparejar")
            )
        )

        val offer = parser.parse(root)

        assertEquals(UberOfferScreenType.RADAR_OFFER, offer.detectedOfferType)
        assertEquals(8.22, offer.rawFare ?: 0.0, 0.001)
        assertEquals("EUR", offer.currency)
        assertEquals(2.5, offer.pickupDistanceKm ?: 0.0, 0.001)
        assertEquals(8.0, offer.pickupDurationMinutes ?: 0.0, 0.001)
        assertEquals(2.3, offer.tripDistanceKm ?: 0.0, 0.001)
        assertEquals(7.0, offer.tripDurationMinutes ?: 0.0, 0.001)
        assertEquals("Calle Buenos Aires 1, Móstoles", offer.pickupAddress)
        assertEquals("Calle Doctor Luis Montes 2, Móstoles", offer.dropoffAddress)
        assertEquals("UberX", offer.category)
        assertEquals(4.71, offer.passengerRating ?: 0.0, 0.001)
    }
}



