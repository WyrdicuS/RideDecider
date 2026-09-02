package com.ridedecider.app.data.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pruebas unitarias deterministas para [AccessibilityServiceStateTracker].
 */
class AccessibilityServiceStateTrackerTest {

    @Test
    fun test1_initialState_defaultsToEnabledDisconnected() {
        val tracker = AccessibilityServiceStateTracker()
        assertEquals(AccessibilityServiceStatus.ENABLED_DISCONNECTED, tracker.status.value)
    }

    @Test
    fun test2_reconcile_whenDisabledInSettings_transitionsToDisabled() {
        val isEnabledInSettings = false
        val tracker = AccessibilityServiceStateTracker(
            settingsChecker = { isEnabledInSettings }
        )

        tracker.reconcile(null)
        assertEquals(AccessibilityServiceStatus.DISABLED, tracker.status.value)
    }

    @Test
    fun test3_reconcile_whenEnabledInSettingsButNotConnected_transitionsToEnabledDisconnected() {
        val isEnabledInSettings = true
        val tracker = AccessibilityServiceStateTracker(
            settingsChecker = { isEnabledInSettings }
        )

        tracker.reconcile(null)
        assertEquals(AccessibilityServiceStatus.ENABLED_DISCONNECTED, tracker.status.value)
    }

    @Test
    fun test4_onConnected_transitionsToConnected() {
        val tracker = AccessibilityServiceStateTracker()
        tracker.onConnected()
        assertEquals(AccessibilityServiceStatus.CONNECTED, tracker.status.value)
    }

    @Test
    fun test5_onInterrupted_transitionsToInterrupted_whenConnected() {
        val tracker = AccessibilityServiceStateTracker()
        tracker.onConnected()
        assertEquals(AccessibilityServiceStatus.CONNECTED, tracker.status.value)

        tracker.onInterrupted()
        assertEquals(AccessibilityServiceStatus.INTERRUPTED, tracker.status.value)
    }

    @Test
    fun test6_onDestroyed_whenEnabledInSettings_transitionsToEnabledDisconnected() {
        val isEnabledInSettings = true
        val tracker = AccessibilityServiceStateTracker(
            settingsChecker = { isEnabledInSettings }
        )
        tracker.onConnected()
        assertEquals(AccessibilityServiceStatus.CONNECTED, tracker.status.value)

        tracker.onDestroyed(null)
        assertEquals(AccessibilityServiceStatus.ENABLED_DISCONNECTED, tracker.status.value)
    }

    @Test
    fun test7_onDestroyed_whenDisabledInSettings_transitionsToDisabled() {
        var isEnabledInSettings = true
        val tracker = AccessibilityServiceStateTracker(
            settingsChecker = { isEnabledInSettings }
        )
        tracker.onConnected()
        assertEquals(AccessibilityServiceStatus.CONNECTED, tracker.status.value)

        // El usuario desactiva el servicio en Ajustes
        isEnabledInSettings = false
        tracker.onDestroyed(null)
        assertEquals(AccessibilityServiceStatus.DISABLED, tracker.status.value)
    }

    @Test
    fun test8_reconcile_whileConnectedAndEnabled_maintainsConnected() {
        val isEnabledInSettings = true
        val tracker = AccessibilityServiceStateTracker(
            settingsChecker = { isEnabledInSettings }
        )
        tracker.onConnected()
        assertEquals(AccessibilityServiceStatus.CONNECTED, tracker.status.value)

        tracker.reconcile(null)
        assertEquals(AccessibilityServiceStatus.CONNECTED, tracker.status.value)
    }

    @Test
    fun test9_reconcile_whileInterruptedAndEnabled_maintainsInterrupted() {
        val isEnabledInSettings = true
        val tracker = AccessibilityServiceStateTracker(
            settingsChecker = { isEnabledInSettings }
        )
        tracker.onConnected()
        tracker.onInterrupted()
        assertEquals(AccessibilityServiceStatus.INTERRUPTED, tracker.status.value)

        tracker.reconcile(null)
        assertEquals(AccessibilityServiceStatus.INTERRUPTED, tracker.status.value)
    }

    @Test
    fun test10_recreatingTracker_withSettingsEnabled_startsAtEnabledDisconnected() {
        // Simula la recreación del tracker tras process death cuando Settings.Secure sigue en true
        val isEnabledInSettings = true
        val newTrackerAfterProcessDeath = AccessibilityServiceStateTracker(
            settingsChecker = { isEnabledInSettings }
        )

        newTrackerAfterProcessDeath.reconcile(null)
        // Demuestra que StateFlow no es persistente y requiere que el OS vuelva a llamar a onConnected()
        assertEquals(AccessibilityServiceStatus.ENABLED_DISCONNECTED, newTrackerAfterProcessDeath.status.value)
    }
}
