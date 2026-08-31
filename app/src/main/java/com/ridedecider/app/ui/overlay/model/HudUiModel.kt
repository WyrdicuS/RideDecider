package com.ridedecider.app.ui.overlay.model

import com.ridedecider.app.domain.model.Decision
import com.ridedecider.app.domain.model.TripOfferType

/**
 * Modelo de datos inmutable preparado exclusivamente para la presentación en el HUD flotante definitivo.
 * Diseñado para reconocimiento visual inmediato durante la conducción:
 * 1. Nivel visual (Color + Símbolo: ◆, ★, ○, ✕)
 * 2. Importe grande en formato español (ej. "18,50 €")
 * 3. Rentabilidad por distancia (ej. "1,72 €/km")
 * 4. Rentabilidad por hora (ej. "28,40 €/h")
 */
data class HudUiModel(
    val tier: HudVisualTier,
    val decision: Decision,
    val fareText: String,
    val grossPerKmText: String,
    val grossPerHourText: String,
    val isCashPayment: Boolean = false,
    val offerType: TripOfferType = TripOfferType.TRIP_OFFER,
    val offerTypeText: String = "VIAJE DIRECTO",
    // Campos opcionales
    val decisionText: String = tier.label,
    val totalDistanceText: String = "",
    val totalDurationText: String = "",
    val pickupSummaryText: String = "",
    val tripSummaryText: String = "",
    val mainReasonText: String? = null,
    val reasons: List<String> = emptyList(),
    val passengerRating: String? = null
)
