package com.ridedecider.app.data.preferences

import android.content.Context
import com.ridedecider.app.domain.model.DecisionMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Almacenamiento clave-valor minimo requerido por [DecisionModeRepository].
 * Abstraido para permitir pruebas unitarias JVM sin depender de un Context Android real.
 */
interface DecisionModeStore {
    fun read(): String?
    fun write(value: String)
}

private class SharedPreferencesDecisionModeStore(context: Context) : DecisionModeStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun read(): String? = prefs.getString(KEY_MODE, null)

    override fun write(value: String) {
        prefs.edit().putString(KEY_MODE, value).apply()
    }

    companion object {
        const val PREFS_NAME = "ridedecider_decision_mode"
        const val KEY_MODE = "current_mode"
    }
}

/**
 * Fuente unica de verdad para el [DecisionMode] activo (MANUAL / AUTOMATIC).
 * Persistencia ligera: es una preferencia de presentacion, no un dato de dominio economico.
 *
 * Default seguro: [DecisionMode.MANUAL] cuando no hay valor persistido o el valor es invalido.
 */
class DecisionModeRepository(private val store: DecisionModeStore) {

    constructor(context: Context) : this(SharedPreferencesDecisionModeStore(context))

    private val _modeFlow = MutableStateFlow(parseMode(store.read()))
    val modeFlow: StateFlow<DecisionMode> = _modeFlow.asStateFlow()

    val currentMode: DecisionMode
        get() = _modeFlow.value

    fun setMode(mode: DecisionMode) {
        store.write(mode.name)
        _modeFlow.value = mode
    }

    companion object {
        internal fun parseMode(stored: String?): DecisionMode {
            if (stored == null) return DecisionMode.MANUAL
            return try {
                DecisionMode.valueOf(stored)
            } catch (e: IllegalArgumentException) {
                DecisionMode.MANUAL
            }
        }
    }
}
