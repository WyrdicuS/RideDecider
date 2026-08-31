package com.ridedecider.app.ui.overlay.state

import com.ridedecider.app.domain.model.TripEvaluation
import com.ridedecider.app.ui.overlay.mapper.HudUiModelMapper
import com.ridedecider.app.ui.overlay.model.HudState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Fuente única de verdad para el estado reactivo del HUD flotante.
 * Totalmente en memoria, sin operaciones de E/S ni bloqueo de hilos.
 */
object HudStateHolder {

    private val _state = MutableStateFlow<HudState>(HudState.Hidden)
    val state: StateFlow<HudState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)
    private var delayedHideJob: Job? = null

    /**
     * Publica una nueva evaluación de viaje en el HUD.
     */
    fun emitEvaluation(evaluation: TripEvaluation) {
        delayedHideJob?.cancel()
        delayedHideJob = null
        val uiModel = HudUiModelMapper.map(evaluation)
        _state.value = HudState.Visible(uiModel)
    }

    /**
     * Oculta el HUD de forma inmediata (por ejemplo, al pulsar Aceptar, Emparejar o Rechazar).
     */
    fun hideImmediately() {
        delayedHideJob?.cancel()
        delayedHideJob = null
        if (_state.value !is HudState.Hidden) {
            _state.value = HudState.Hidden
        }
    }

    /**
     * Oculta el HUD tras un retardo (por defecto 2 segundos tras desaparecer la tarjeta de oferta).
     */
    fun hideWithDelay(delayMs: Long = 2000L) {
        if (_state.value is HudState.Hidden) return
        if (delayedHideJob?.isActive == true) return

        delayedHideJob = scope.launch {
            delay(delayMs)
            _state.value = HudState.Hidden
            delayedHideJob = null
        }
    }

    /**
     * Oculta el HUD de forma inmediata.
     */
    fun hide() {
        hideImmediately()
    }
}
