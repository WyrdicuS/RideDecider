package com.ridedecider.app.data.accessibility

import android.content.Context
import android.provider.Settings
import com.ridedecider.app.data.accessibility.uber.UberAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gestor reactivo del estado del [UberAccessibilityService].
 *
 * Mantiene el estado en memoria mediante un [StateFlow] y permite reconciliar
 * la fuente de verdad del sistema operativo ([Settings.Secure]) con el estado de la instancia.
 */
class AccessibilityServiceStateTracker(
    initialStatus: AccessibilityServiceStatus = AccessibilityServiceStatus.ENABLED_DISCONNECTED,
    private val settingsChecker: ((Context?) -> Boolean)? = null
) {

    private val _status = MutableStateFlow(initialStatus)

    /**
     * Estado reactivo observable por la UI y otros componentes.
     */
    val status: StateFlow<AccessibilityServiceStatus> = _status.asStateFlow()

    @Volatile
    private var isInstanceConnected: Boolean = false

    /**
     * Notifica que la instancia del servicio ha completado `onServiceConnected()`.
     */
    fun onConnected() {
        synchronized(this) {
            isInstanceConnected = true
            _status.value = AccessibilityServiceStatus.CONNECTED
        }
    }

    /**
     * Notifica que el sistema operativo ha suspendido temporalmente los eventos (`onInterrupt()`).
     * Mantiene la instancia marcada como activa sin forzar una desconexión destructiva.
     */
    fun onInterrupted() {
        synchronized(this) {
            if (isInstanceConnected || _status.value == AccessibilityServiceStatus.CONNECTED) {
                _status.value = AccessibilityServiceStatus.INTERRUPTED
            }
        }
    }

    /**
     * Notifica que la instancia del servicio ha sido destruida (`onDestroy()`).
     * Reconcilia con [Settings.Secure] si se pasa [context], o pasa a [AccessibilityServiceStatus.ENABLED_DISCONNECTED]
     * a menos que el servicio ya estuviera marcado como [AccessibilityServiceStatus.DISABLED].
     */
    fun onDestroyed(context: Context? = null) {
        synchronized(this) {
            isInstanceConnected = false
            if (context != null || settingsChecker != null) {
                reconcileInternal(context)
            } else {
                if (_status.value != AccessibilityServiceStatus.DISABLED) {
                    _status.value = AccessibilityServiceStatus.ENABLED_DISCONNECTED
                }
            }
        }
    }

    /**
     * Reconcilia el estado en memoria con la fuente de verdad del sistema operativo Android ([Settings.Secure]).
     *
     * @param context Contexto necesario para acceder a [android.content.ContentResolver].
     */
    fun reconcile(context: Context? = null) {
        synchronized(this) {
            reconcileInternal(context)
        }
    }

    private fun reconcileInternal(context: Context?) {
        val isEnabledInSettings = isServiceEnabledInSettings(context)

        if (!isEnabledInSettings) {
            isInstanceConnected = false
            _status.value = AccessibilityServiceStatus.DISABLED
            return
        }

        // Si está habilitado en Settings.Secure:
        if (isInstanceConnected) {
            // Si la instancia sigue conectada y el último estado era INTERRUPTED, mantener INTERRUPTED
            if (_status.value == AccessibilityServiceStatus.INTERRUPTED) {
                _status.value = AccessibilityServiceStatus.INTERRUPTED
            } else {
                _status.value = AccessibilityServiceStatus.CONNECTED
            }
        } else {
            _status.value = AccessibilityServiceStatus.ENABLED_DISCONNECTED
        }
    }

    /**
     * Comprueba si el servicio está habilitado en los ajustes del sistema.
     */
    fun isServiceEnabledInSettings(context: Context? = null): Boolean {
        if (settingsChecker != null) {
            return settingsChecker.invoke(context)
        }

        if (context == null) return false

        val expectedServiceName = "${context.packageName}/${UberAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(":").any { it.equals(expectedServiceName, ignoreCase = true) }
    }
}
