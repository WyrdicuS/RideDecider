package com.ridedecider.app.data.preferences

import com.ridedecider.app.domain.model.DecisionMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests de R5: DecisionMode debe tener una unica fuente de verdad, default MANUAL,
 * y persistir de forma segura ante valores ausentes/invalidos.
 *
 * Usa un [DecisionModeStore] en memoria (sin Context/SharedPreferences reales) para
 * simular persistencia y reinicios sin requerir Robolectric.
 */
class DecisionModeRepositoryTest {

    private class InMemoryDecisionModeStore(initial: String? = null) : DecisionModeStore {
        var backing: String? = initial

        override fun read(): String? = backing

        override fun write(value: String) {
            backing = value
        }
    }

    // 16. Default = MANUAL
    @Test
    fun default_whenNoPersistedValue_isManual() {
        val store = InMemoryDecisionModeStore(initial = null)
        val repo = DecisionModeRepository(store)

        assertEquals(DecisionMode.MANUAL, repo.currentMode)
        assertEquals(DecisionMode.MANUAL, repo.modeFlow.value)
    }

    // 17. Cambiar a AUTOMATIC persiste (en el store subyacente)
    @Test
    fun setMode_toAutomatic_persistsInStore() {
        val store = InMemoryDecisionModeStore(initial = null)
        val repo = DecisionModeRepository(store)

        repo.setMode(DecisionMode.AUTOMATIC)

        assertEquals(DecisionMode.AUTOMATIC, repo.currentMode)
        assertEquals("AUTOMATIC", store.backing)
    }

    // 18. Reinicio (nueva instancia de repositorio sobre el mismo store) mantiene AUTOMATIC
    @Test
    fun restart_withPersistedAutomatic_restoresAutomatic() {
        val store = InMemoryDecisionModeStore(initial = null)
        val repoBeforeRestart = DecisionModeRepository(store)
        repoBeforeRestart.setMode(DecisionMode.AUTOMATIC)

        // Simula el reinicio de servicio/app: nueva instancia leyendo el mismo store persistido.
        val repoAfterRestart = DecisionModeRepository(store)

        assertEquals(DecisionMode.AUTOMATIC, repoAfterRestart.currentMode)
    }

    // 19. Volver a MANUAL persiste
    @Test
    fun setMode_backToManual_persists() {
        val store = InMemoryDecisionModeStore(initial = "AUTOMATIC")
        val repo = DecisionModeRepository(store)
        assertEquals(DecisionMode.AUTOMATIC, repo.currentMode)

        repo.setMode(DecisionMode.MANUAL)

        assertEquals(DecisionMode.MANUAL, repo.currentMode)
        assertEquals("MANUAL", store.backing)

        val repoAfterRestart = DecisionModeRepository(store)
        assertEquals(DecisionMode.MANUAL, repoAfterRestart.currentMode)
    }

    // 20. Valor persistido invalido/corrupto -> MANUAL seguro
    @Test
    fun invalidPersistedValue_fallsBackToManual() {
        val store = InMemoryDecisionModeStore(initial = "CORRUPTED_VALUE")
        val repo = DecisionModeRepository(store)

        assertEquals(DecisionMode.MANUAL, repo.currentMode)
    }

    @Test
    fun invalidPersistedValue_emptyString_fallsBackToManual() {
        val store = InMemoryDecisionModeStore(initial = "")
        val repo = DecisionModeRepository(store)

        assertEquals(DecisionMode.MANUAL, repo.currentMode)
    }
}
