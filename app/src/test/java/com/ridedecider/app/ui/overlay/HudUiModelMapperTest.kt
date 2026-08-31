package com.ridedecider.app.ui.overlay

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.ui.graphics.Color
import com.ridedecider.app.domain.model.Decision
import com.ridedecider.app.domain.model.DecisionReason
import com.ridedecider.app.domain.model.EvaluationMetrics
import com.ridedecider.app.domain.model.ProfitabilityConfig
import com.ridedecider.app.domain.model.Trip
import com.ridedecider.app.domain.model.TripEvaluation
import com.ridedecider.app.domain.model.TripOfferType
import com.ridedecider.app.domain.model.UberCategory
import com.ridedecider.app.ui.overlay.mapper.HudUiModelMapper
import com.ridedecider.app.ui.overlay.model.HudState
import com.ridedecider.app.ui.overlay.model.HudVisualTier
import com.ridedecider.app.ui.overlay.state.HudStateHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HudUiModelMapperTest {

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

    // 1. Nivel EXCELENTE -> Púrpura intenso (0xFFA855F7) y Símbolo ◆
    @Test
    fun tier_excellent_shouldMapToPurpleAndDiamond() {
        val trip = Trip(
            id = "test_excellent",
            timestamp = System.currentTimeMillis(),
            offerType = TripOfferType.TRIP_OFFER,
            category = UberCategory.COMFORT,
            rawFare = 18.50,
            currency = "EUR",
            pickupDistanceKm = 1.0,
            pickupDurationMinutes = 3.0,
            pickupAddress = null,
            tripDistanceKm = 9.0,
            tripDurationMinutes = 20.0,
            dropoffAddress = null
        )

        // Ratios muy altos (> 31.25 €/h y > 1.50 €/km)
        val metrics = EvaluationMetrics(
            totalDistanceKm = 10.0,
            totalDurationMinutes = 23.0,
            estimatedOperatingCost = 2.00,
            grossProfit = 18.50,
            netProfit = 16.50,
            grossPerKm = 1.85,
            grossPerHour = 48.26,
            netPerHour = 43.04
        )

        val evaluation = TripEvaluation(
            trip = trip,
            configUsed = defaultConfig,
            metrics = metrics,
            decision = Decision.ACCEPT,
            reasons = listOf(DecisionReason.ACCEPT_HIGH_PROFITABILITY),
            evaluationTimestamp = System.currentTimeMillis(),
            profitabilityLevel = com.ridedecider.app.domain.model.TripProfitabilityLevel.EXCELLENT
        )

        val uiModel = HudUiModelMapper.map(evaluation)

        assertEquals(HudVisualTier.EXCELLENT, uiModel.tier)
        assertEquals(androidx.compose.material.icons.Icons.Rounded.AutoAwesome, uiModel.tier.icon)
        assertEquals("EXCELENTE", uiModel.tier.label)
        assertEquals(HudVisualTier.EXCELLENT.color, uiModel.tier.color)
        assertEquals("18,50 €", uiModel.fareText)
        assertEquals("1,85 €/km", uiModel.grossPerKmText)
        assertEquals("48,26 €/h", uiModel.grossPerHourText)
    }

    // 2. Nivel BUENO
    @Test
    fun tier_good_shouldMapToGreenAndStar() {
        val trip = Trip(
            id = "test_good",
            timestamp = System.currentTimeMillis(),
            offerType = TripOfferType.TRIP_OFFER,
            category = UberCategory.UBER_X,
            rawFare = 13.20,
            currency = "EUR",
            pickupDistanceKm = 1.5,
            pickupDurationMinutes = 4.0,
            pickupAddress = null,
            tripDistanceKm = 8.0,
            tripDurationMinutes = 22.0,
            dropoffAddress = null
        )

        // Cumple umbrales (> 25 €/h y > 1.20 €/km)
        val metrics = EvaluationMetrics(
            totalDistanceKm = 9.5,
            totalDurationMinutes = 26.0,
            estimatedOperatingCost = 1.90,
            grossProfit = 13.20,
            netProfit = 11.30,
            grossPerKm = 1.389,
            grossPerHour = 30.46,
            netPerHour = 26.07
        )

        val evaluation = TripEvaluation(
            trip = trip,
            configUsed = defaultConfig,
            metrics = metrics,
            decision = Decision.ACCEPT,
            reasons = listOf(DecisionReason.ACCEPT_HIGH_PROFITABILITY),
            evaluationTimestamp = System.currentTimeMillis(),
            profitabilityLevel = com.ridedecider.app.domain.model.TripProfitabilityLevel.GOOD
        )

        val uiModel = HudUiModelMapper.map(evaluation)

        assertEquals(HudVisualTier.GOOD, uiModel.tier)
        assertEquals(androidx.compose.material.icons.Icons.Rounded.CheckCircle, uiModel.tier.icon)
        assertEquals("BUENO", uiModel.tier.label)
        assertEquals(HudVisualTier.GOOD.color, uiModel.tier.color)
        assertEquals("13,20 €", uiModel.fareText)
        assertEquals("1,39 €/km", uiModel.grossPerKmText)
        assertEquals("30,46 €/h", uiModel.grossPerHourText)
    }

    // 3. Nivel ACEPTABLE
    @Test
    fun tier_acceptable_shouldMapToAmberAndCircle() {
        val trip = Trip(
            id = "test_acceptable",
            timestamp = System.currentTimeMillis(),
            offerType = TripOfferType.TRIP_OFFER,
            category = UberCategory.UBER_X,
            rawFare = 9.80,
            currency = "EUR",
            pickupDistanceKm = 2.0,
            pickupDurationMinutes = 5.0,
            pickupAddress = null,
            tripDistanceKm = 7.0,
            tripDurationMinutes = 18.0,
            dropoffAddress = null
        )

        // Margen aceptable
        val metrics = EvaluationMetrics(
            totalDistanceKm = 9.0,
            totalDurationMinutes = 23.0,
            estimatedOperatingCost = 1.80,
            grossProfit = 9.80,
            netProfit = 8.00,
            grossPerKm = 1.088,
            grossPerHour = 25.56,
            netPerHour = 20.87
        )

        val evaluation = TripEvaluation(
            trip = trip,
            configUsed = defaultConfig,
            metrics = metrics,
            decision = Decision.ACCEPT,
            reasons = emptyList(),
            evaluationTimestamp = System.currentTimeMillis(),
            profitabilityLevel = com.ridedecider.app.domain.model.TripProfitabilityLevel.ACCEPTABLE
        )

        val uiModel = HudUiModelMapper.map(evaluation)

        assertEquals(HudVisualTier.ACCEPTABLE, uiModel.tier)
        assertEquals(androidx.compose.material.icons.Icons.Rounded.Info, uiModel.tier.icon)
        assertEquals("ACEPTABLE", uiModel.tier.label)
        assertEquals(HudVisualTier.ACCEPTABLE.color, uiModel.tier.color)
        assertEquals("9,80 €", uiModel.fareText)
    }

    // 4. Nivel MALO
    @Test
    fun tier_bad_shouldMapToRedAndCross() {
        val trip = Trip(
            id = "test_bad",
            timestamp = System.currentTimeMillis(),
            offerType = TripOfferType.RADAR_OFFER,
            category = UberCategory.UBER_X,
            rawFare = 6.40,
            currency = "EUR",
            pickupDistanceKm = 3.0,
            pickupDurationMinutes = 8.0,
            pickupAddress = "Calle Mayor 1",
            tripDistanceKm = 7.0,
            tripDurationMinutes = 25.0,
            dropoffAddress = "Avenida Central 5",
            isCashPayment = false
        )

        val metrics = EvaluationMetrics(
            totalDistanceKm = 10.0,
            totalDurationMinutes = 33.0,
            estimatedOperatingCost = 2.00,
            grossProfit = 6.40,
            netProfit = 4.40,
            grossPerKm = 0.64,
            grossPerHour = 11.63,
            netPerHour = 8.00
        )

        val evaluation = TripEvaluation(
            trip = trip,
            configUsed = defaultConfig,
            metrics = metrics,
            decision = Decision.REJECT,
            reasons = listOf(DecisionReason.REJECT_LOW_KM_RATE, DecisionReason.REJECT_LOW_HOURLY_RATE),
            evaluationTimestamp = System.currentTimeMillis(),
            profitabilityLevel = com.ridedecider.app.domain.model.TripProfitabilityLevel.BAD
        )

        val uiModel = HudUiModelMapper.map(evaluation)

        assertEquals(HudVisualTier.BAD, uiModel.tier)
        assertEquals(androidx.compose.material.icons.Icons.Rounded.Cancel, uiModel.tier.icon)
        assertEquals("MALO", uiModel.tier.label)
        assertEquals(HudVisualTier.BAD.color, uiModel.tier.color)
        assertEquals("6,40 €", uiModel.fareText)
        assertEquals("0,64 €/km", uiModel.grossPerKmText)
        assertEquals("11,63 €/h", uiModel.grossPerHourText)
    }

    // 5. Verificación de Formato de Importes y Ratios en Español
    @Test
    fun map_spanishFormatting_correctDecimalSeparator() {
        val trip = Trip(
            id = "test_format",
            timestamp = System.currentTimeMillis(),
            offerType = TripOfferType.TRIP_OFFER,
            category = UberCategory.UBER_X,
            rawFare = 9.01,
            currency = "EUR",
            pickupDistanceKm = 1.0,
            pickupDurationMinutes = 2.0,
            pickupAddress = null,
            tripDistanceKm = 4.0,
            tripDurationMinutes = 8.0,
            dropoffAddress = null
        )

        val metrics = EvaluationMetrics(
            totalDistanceKm = 5.0,
            totalDurationMinutes = 10.0,
            estimatedOperatingCost = 1.00,
            grossProfit = 9.01,
            netProfit = 8.01,
            grossPerKm = 1.802,
            grossPerHour = 54.06,
            netPerHour = 48.06
        )

        val evaluation = TripEvaluation(
            trip = trip,
            configUsed = defaultConfig,
            metrics = metrics,
            decision = Decision.ACCEPT,
            reasons = listOf(DecisionReason.ACCEPT_HIGH_PROFITABILITY),
            evaluationTimestamp = System.currentTimeMillis()
        )

        val uiModel = HudUiModelMapper.map(evaluation)

        assertEquals("9,01 €", uiModel.fareText)
        assertEquals("1,80 €/km", uiModel.grossPerKmText)
        assertEquals("54,06 €/h", uiModel.grossPerHourText)
    }

    // 6. HudStateHolder emit & hide
    @Test
    fun hudStateHolder_emitAndHide_shouldUpdateFlowSeamlessly() {
        HudStateHolder.hide()
        assertTrue(HudStateHolder.state.value is HudState.Hidden)

        val trip = Trip(
            id = "test_state_trip",
            timestamp = System.currentTimeMillis(),
            offerType = TripOfferType.TRIP_OFFER,
            category = UberCategory.UBER_X,
            rawFare = 5.02,
            currency = "EUR",
            pickupDistanceKm = 1.0,
            pickupDurationMinutes = 3.0,
            pickupAddress = null,
            tripDistanceKm = 3.0,
            tripDurationMinutes = 8.0,
            dropoffAddress = null
        )

        val evaluation = TripEvaluation(
            trip = trip,
            configUsed = defaultConfig,
            metrics = null,
            decision = Decision.UNKNOWN,
            reasons = emptyList(),
            evaluationTimestamp = System.currentTimeMillis()
        )

        HudStateHolder.emitEvaluation(evaluation)
        val currentState = HudStateHolder.state.value
        assertTrue(currentState is HudState.Visible)
        assertEquals("5,02 €", (currentState as HudState.Visible).data.fareText)
        assertEquals(HudVisualTier.BAD, (currentState as HudState.Visible).data.tier)

        HudStateHolder.hide()
        assertTrue(HudStateHolder.state.value is HudState.Hidden)
    }
}
