package com.ridedecider.app.ui.overlay

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pruebas unitarias de compatibilidad de tipos de ventana WindowManager en [HudOverlayManager].
 */
class HudOverlayManagerCompatibilityTest {

    @Test
    fun resolveWindowType_api24_returnsTypeSystemAlert() {
        @Suppress("DEPRECATION")
        val expected = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        val actual = HudOverlayManager.resolveWindowType(sdkVersion = 24)
        assertEquals(expected, actual)
    }

    @Test
    fun resolveWindowType_api25_returnsTypeSystemAlert() {
        @Suppress("DEPRECATION")
        val expected = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        val actual = HudOverlayManager.resolveWindowType(sdkVersion = 25)
        assertEquals(expected, actual)
    }

    @Test
    fun resolveWindowType_api26_returnsTypeApplicationOverlay() {
        val expected = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val actual = HudOverlayManager.resolveWindowType(sdkVersion = 26)
        assertEquals(expected, actual)
    }

    @Test
    fun resolveWindowType_api30_returnsTypeApplicationOverlay() {
        val expected = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val actual = HudOverlayManager.resolveWindowType(sdkVersion = 30)
        assertEquals(expected, actual)
    }

    @Test
    fun resolveWindowType_api36_returnsTypeApplicationOverlay() {
        val expected = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val actual = HudOverlayManager.resolveWindowType(sdkVersion = 36)
        assertEquals(expected, actual)
    }
}
