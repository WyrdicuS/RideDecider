package com.ridedecider.app.domain.model

/**
 * Nivel cualitativo de rentabilidad y calidad económica de una oferta de viaje de Uber.
 *
 * Se utiliza para comunicar de forma rápida e intuitiva el valor de la oportunidad al conductor:
 * - [EXCELLENT]: Extremadamente rentable. Supera holgadamente los umbrales mínimos y/o el ritmo económico deseado.
 * - [GOOD]: Rentable y equilibrado. Cumple plenamente con todos los criterios y costes operativos.
 * - [ACCEPTABLE]: Viable pero con márgenes ajustados o valor contextual específico (p. ej. retorno o margen mínimo positivo).
 * - [BAD]: Inviable o poco rentable. No alcanza los estándares mínimos de rentabilidad por km/h o costes.
 */
enum class TripProfitabilityLevel {
    EXCELLENT,
    GOOD,
    ACCEPTABLE,
    BAD
}
