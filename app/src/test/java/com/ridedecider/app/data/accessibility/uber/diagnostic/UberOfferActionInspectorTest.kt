package com.ridedecider.app.data.accessibility.uber.diagnostic

import com.ridedecider.app.data.accessibility.uber.UberNodeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitarios para [UberOfferActionInspector].
 *
 * Verifica la detección de nodos de acción ("Aceptar", "Emparejar", etc.),
 * la inclusión de la ruta de padres [PARENT_PATH] y descendientes [CHILDREN],
 * y comprueba que textos no relacionados (ej. indicadores de demanda "1-18 min") no disparen este diagnóstico.
 */
class UberOfferActionInspectorTest {

    @Test
    fun nullOrEmptySnapshot_shouldReturnEmptyList() {
        assertTrue(UberOfferActionInspector.inspect(null).isEmpty())
        assertTrue(UberOfferActionInspector.inspect(UberNodeSnapshot()).isEmpty())
    }

    @Test
    fun demandIndicators_shouldNotTriggerActionNodeDiagnostic() {
        val demandNode1 = UberNodeSnapshot(text = "1-18 min")
        val demandNode2 = UberNodeSnapshot(text = "1-15 min")
        val mapLabel = UberNodeSnapshot(text = "Alta demanda en tu zona")
        val root = UberNodeSnapshot(
            className = "android.widget.FrameLayout",
            children = listOf(demandNode1, demandNode2, mapLabel)
        )

        val reports = UberOfferActionInspector.inspect(root)

        assertTrue(reports.isEmpty())
    }

    @Test
    fun acceptButton_shouldGenerateCompleteActionReportWithHierarchy() {
        val acceptBtn = UberNodeSnapshot(
            className = "android.widget.Button",
            text = "Aceptar",
            viewIdResourceName = "com.ubercab.driver:id/accept_btn",
            isClickable = true,
            isEnabled = true,
            isVisibleToUser = true
        )
        val card = UberNodeSnapshot(
            className = "android.widget.LinearLayout",
            viewIdResourceName = "com.ubercab.driver:id/bottom_sheet",
            children = listOf(acceptBtn)
        )
        val root = UberNodeSnapshot(
            className = "android.widget.FrameLayout",
            viewIdResourceName = "android:id/content",
            children = listOf(card)
        )

        val reports = UberOfferActionInspector.inspect(root)

        assertEquals(1, reports.size)
        val report = reports[0]

        // 1. Encabezado del nodo de acción
        assertTrue(report.contains("[OFFER_ACTION_NODE]"))
        assertTrue(report.contains("text=\"Aceptar\""))
        assertTrue(report.contains("className=\"Button\""))
        assertTrue(report.contains("id=\"com.ubercab.driver:id/accept_btn\""))
        assertTrue(report.contains("clickable=true"))
        assertTrue(report.contains("enabled=true"))
        assertTrue(report.contains("visible=true"))

        // 2. Ruta de padres
        assertTrue(report.contains("[PARENT_PATH]"))
        assertTrue(report.contains("[Level 0] [FrameLayout] id=\"android:id/content\""))
        assertTrue(report.contains("[Level 1] [LinearLayout] id=\"com.ubercab.driver:id/bottom_sheet\""))

        // 3. Descendientes
        assertTrue(report.contains("[CHILDREN]"))
    }

    @Test
    fun matchButton_shouldGenerateActionReportForRadar() {
        val matchBtn = UberNodeSnapshot(
            className = "android.widget.Button",
            text = "Emparejar",
            isClickable = true
        )
        val root = UberNodeSnapshot(children = listOf(matchBtn))

        val reports = UberOfferActionInspector.inspect(root)

        assertEquals(1, reports.size)
        assertTrue(reports[0].contains("[OFFER_ACTION_NODE]"))
        assertTrue(reports[0].contains("text=\"Emparejar\""))
    }

    @Test
    fun contentDescriptionMatch_shouldBeDetected() {
        val node = UberNodeSnapshot(
            className = "android.view.View",
            contentDescription = "Match offer with passenger",
            isClickable = true
        )
        val root = UberNodeSnapshot(children = listOf(node))

        val reports = UberOfferActionInspector.inspect(root)

        assertEquals(1, reports.size)
        assertTrue(reports[0].contains("[OFFER_ACTION_NODE]"))
        assertTrue(reports[0].contains("desc=\"Match offer with passenger\""))
    }
}
