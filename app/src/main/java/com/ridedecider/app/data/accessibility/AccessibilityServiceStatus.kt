package com.ridedecider.app.data.accessibility

/**
 * Estado explícito del servicio de accesibilidad en RideDecider.
 *
 * Sincroniza la fuente de verdad del sistema operativo Android ([android.provider.Settings.Secure])
 * con el estado de la instancia en memoria del [android.accessibilityservice.AccessibilityService].
 */
enum class AccessibilityServiceStatus {
    /**
     * El ajuste de accesibilidad para RideDecider está deshabilitado en Android ([Settings.Secure]).
     */
    DISABLED,

    /**
     * El servicio está habilitado en los ajustes de Android, pero la instancia aún no está vinculada en memoria.
     */
    ENABLED_DISCONNECTED,

    /**
     * La instancia de [UberAccessibilityService] ha completado `onServiceConnected()` y está activa.
     */
    CONNECTED,

    /**
     * El sistema operativo ha pausado o interrumpido temporalmente la entrega de eventos (`onInterrupt()`).
     */
    INTERRUPTED;

    /**
     * Indica si el servicio está en un estado funcional operativo.
     */
    val isOperative: Boolean
        get() = this == CONNECTED || this == INTERRUPTED
}
