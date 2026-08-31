package com.ridedecider.app.data.accessibility.uber

import com.ridedecider.app.domain.model.ProfitabilityConfig
import com.ridedecider.app.domain.repository.ProfitabilityConfigProvider

/**
 * Implementación en memoria de [ProfitabilityConfigProvider] para pruebas y estado inicial.
 */
class InMemoryProfitabilityConfigProvider(
    private var config: ProfitabilityConfig = ProfitabilityConfig(
        costPerKm = 0.20,
        costPerHour = 4.0,
        minGrossHourlyRate = 24.0, // Objetivo: 120 € en 5 horas = 24.00 €/h
        minGrossPerKmRate = 1.10,
        minNetTripProfit = 2.0,
        minNetHourlyRate = 18.0,
        maxPickupDistanceKm = 4.5,
        maxPickupTimeMinutes = 9.0
    )
) : ProfitabilityConfigProvider {

    override fun getConfig(): ProfitabilityConfig = config

    /**
     * Permite actualizar la configuración en memoria durante pruebas o cambios de usuario.
     */
    fun updateConfig(newConfig: ProfitabilityConfig) {
        config = newConfig
    }
}
