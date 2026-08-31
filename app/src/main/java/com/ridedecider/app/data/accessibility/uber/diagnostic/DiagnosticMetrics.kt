package com.ridedecider.app.data.accessibility.uber.diagnostic

import java.util.concurrent.atomic.AtomicLong

/**
 * Acumulador en memoria de métricas operativas y de diagnóstico del servicio de accesibilidad.
 *
 * Clase pura de Kotlin thread-safe sin dependencias de Android.
 */
class DiagnosticMetrics {
    val eventsReceived = AtomicLong(0)
    val uberEventsProcessed = AtomicLong(0)
    val nullRootsCount = AtomicLong(0)
    val snapshotsCreated = AtomicLong(0)
    val validOffersCount = AtomicLong(0)
    val invalidOffersCount = AtomicLong(0)
    val mappingFailuresCount = AtomicLong(0)
    val evaluationsCount = AtomicLong(0)
    val debouncedCount = AtomicLong(0)

    fun reset() {
        eventsReceived.set(0)
        uberEventsProcessed.set(0)
        nullRootsCount.set(0)
        snapshotsCreated.set(0)
        validOffersCount.set(0)
        invalidOffersCount.set(0)
        mappingFailuresCount.set(0)
        evaluationsCount.set(0)
        debouncedCount.set(0)
    }

    fun toSummaryString(): String {
        return """
            [DIAGNOSTIC_METRICS]
            - Total eventos recibidos: ${eventsReceived.get()}
            - Eventos Uber procesados: ${uberEventsProcessed.get()}
            - Roots nulos: ${nullRootsCount.get()}
            - Snapshots creados: ${snapshotsCreated.get()}
            - Ofertas válidas: ${validOffersCount.get()}
            - Ofertas rechazadas (invalid): ${invalidOffersCount.get()}
            - Mappings fallidos: ${mappingFailuresCount.get()}
            - Evaluaciones completadas: ${evaluationsCount.get()}
            - Eventos descartados por debounce: ${debouncedCount.get()}
        """.trimIndent()
    }
}
