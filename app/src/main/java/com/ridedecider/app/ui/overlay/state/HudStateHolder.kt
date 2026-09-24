package com.ridedecider.app.ui.overlay.state

import com.ridedecider.app.domain.model.DecisionMode
import com.ridedecider.app.domain.model.TripEvaluation
import com.ridedecider.app.domain.model.opportunity.OpportunityAssessment
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
 *
 * [currentMode] es una copia derivada del [DecisionMode] activo, mantenida sincronizada
 * mediante [setDecisionMode] (llamado por quien observa la unica fuente de verdad persistida,
 * [com.ridedecider.app.data.preferences.DecisionModeRepository]). Este holder nunca persiste
 * ni decide el modo por si mismo.
 */
object HudStateHolder {

    private val _state = MutableStateFlow<HudState>(HudState.Hidden)
    val state: StateFlow<HudState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)
    private var delayedHideJob: Job? = null

    @Volatile
    private var currentMode: DecisionMode = DecisionMode.MANUAL

    private var lastEvaluation: TripEvaluation? = null
    private var lastAssessment: OpportunityAssessment? = null

    /**
     * Actualiza el modo de decision activo. Si el HUD esta visible, re-renderiza
     * inmediatamente la ultima evaluacion con el nuevo modo (sin re-evaluar economia).
     */
    fun setDecisionMode(mode: DecisionMode) {
        currentMode = mode
        val evaluation = lastEvaluation
        if (evaluation != null && _state.value is HudState.Visible) {
            val uiModel = HudUiModelMapper.map(evaluation, lastAssessment, currentMode)
            _state.value = HudState.Visible(uiModel)
        }
    }

    /**
     * Publica una nueva evaluación de viaje en el HUD, opcionalmente enriquecida con
     * [OpportunityAssessment] para el modo AUTOMATIC.
     */
    fun emitEvaluation(evaluation: TripEvaluation, assessment: OpportunityAssessment? = null) {
        delayedHideJob?.cancel()
        delayedHideJob = null
        lastEvaluation = evaluation
        lastAssessment = assessment
        val uiModel = HudUiModelMapper.map(evaluation, assessment, currentMode)
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
