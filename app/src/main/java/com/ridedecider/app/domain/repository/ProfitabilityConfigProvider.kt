package com.ridedecider.app.domain.repository

import com.ridedecider.app.domain.model.ProfitabilityConfig

/**
 * Proveedor abstracto para obtener la configuración de costes y rentabilidad del conductor.
 */
interface ProfitabilityConfigProvider {

    /**
     * Devuelve la configuración de rentabilidad actual del conductor.
     */
    fun getConfig(): ProfitabilityConfig
}
