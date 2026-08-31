package com.ridedecider.app.ui.overlay.model

/**
 * Estado observable y reactivo del HUD flotante.
 */
sealed class HudState {
    /**
     * El HUD está oculto (sin oferta en pantalla, viaje activo o timeout de seguridad).
     */
    data object Hidden : HudState()

    /**
     * El HUD está visible mostrando la evaluación económica y recomendación de una oferta.
     */
    data class Visible(val data: HudUiModel) : HudState()
}
