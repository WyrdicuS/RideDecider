package com.ridedecider.app.data.accessibility.uber

/**
 * Resultado estructurado e inmutable de la validación y clasificación de un [RawUberTripOffer].
 *
 * @property screenType Clasificación contextual de la pantalla detectada.
 * @property isValidOffer Indica si la oferta contiene todos los datos obligatorios válidos y puede transformarse en un Trip de dominio.
 * @property reasons Lista de motivos que explican la validación o rechazo de la oferta.
 */
data class UberOfferValidationResult(
    val screenType: UberOfferScreenType,
    val isValidOffer: Boolean,
    val reasons: List<UberValidationReason>
)
