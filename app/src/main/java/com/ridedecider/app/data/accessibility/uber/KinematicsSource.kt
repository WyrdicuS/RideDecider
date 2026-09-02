package com.ridedecider.app.data.accessibility.uber

/**
 * Clasificación semántica del origen y la procedencia de los datos cinemáticos
 * (distancias y duraciones) extraídos de la interfaz de Uber o por OCR.
 */
enum class KinematicsSource {
    /**
     * Dos distancias leídas explícitamente del texto/nodo/OCR (recogida > 0 y viaje > 0).
     */
    EXPLICIT_DUAL,

    /**
     * Cinemática unificada legítima: Una sola distancia leída en tarjeta (ej. "9.5 km"),
     * infiriendo la recogida como 0.0 km y el viaje como la distancia leída.
     */
    UNIFIED_INFERRED,

    /**
     * Recogida leída explícitamente como "0 m" o "0 km" con un viaje > 0.0.
     */
    EXPLICIT_ZERO_PICKUP,

    /**
     * Distancia total de viaje igual a 0.0 km procedente de un reconocimiento OCR corrupto o ambiguo.
     */
    OCR_SUSPECT,

    /**
     * Datos cinemáticos ausentes o no detectados en pantalla.
     */
    MISSING,

    /**
     * Valor predeterminado seguro para mantener compatibilidad retroactiva 100% con constructores legados y tests.
     */
    LEGACY_UNSPECIFIED
}
