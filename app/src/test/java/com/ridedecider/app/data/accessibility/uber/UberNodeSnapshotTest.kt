package com.ridedecider.app.data.accessibility.uber

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitarios para [UberNodeSnapshot].
 *
 * Verifica el aplanado de árboles de nodos, la búsqueda por texto y por ID de vista
 * y la integridad de la estructura inmutable.
 */
class UberNodeSnapshotTest {

    @Test
    fun flatten_singleNode_shouldReturnSingleElementList() {
        val root = UberNodeSnapshot(text = "Root", viewIdResourceName = "root_id")

        val flattened = root.flatten()

        assertEquals(1, flattened.size)
        assertEquals("Root", flattened[0].text)
    }

    @Test
    fun flatten_nestedTree_shouldReturnAllNodesInDepthFirstOrder() {
        val child1 = UberNodeSnapshot(text = "14.20 €")
        val child2 = UberNodeSnapshot(text = "3.2 km")
        val container = UberNodeSnapshot(
            className = "android.widget.LinearLayout",
            children = listOf(child1, child2)
        )
        val root = UberNodeSnapshot(
            className = "android.widget.FrameLayout",
            children = listOf(container)
        )

        val flattened = root.flatten()

        assertEquals(4, flattened.size)
        assertEquals("android.widget.FrameLayout", flattened[0].className)
        assertEquals("android.widget.LinearLayout", flattened[1].className)
        assertEquals("14.20 €", flattened[2].text)
        assertEquals("3.2 km", flattened[3].text)
    }

    @Test
    fun findNodesByText_matchingTextAndContentDescription_shouldReturnCorrectNodes() {
        val button = UberNodeSnapshot(
            text = "Aceptar",
            isClickable = true,
            viewIdResourceName = "com.ubercab.driver:id/accept_btn"
        )
        val icon = UberNodeSnapshot(
            contentDescription = "Icono de destino: Aeropuerto T4"
        )
        val other = UberNodeSnapshot(text = "10 min")

        val root = UberNodeSnapshot(children = listOf(button, icon, other))

        val acceptResults = root.findNodesByText("aceptar", ignoreCase = true)
        assertEquals(1, acceptResults.size)
        assertEquals("Aceptar", acceptResults[0].text)
        assertTrue(acceptResults[0].isClickable)

        val airportResults = root.findNodesByText("Aeropuerto")
        assertEquals(1, airportResults.size)
        assertEquals("Icono de destino: Aeropuerto T4", airportResults[0].contentDescription)

        val emptyResults = root.findNodesByText("TextoInexistente")
        assertTrue(emptyResults.isEmpty())

        val blankSearch = root.findNodesByText("")
        assertTrue(blankSearch.isEmpty())
    }

    @Test
    fun findNodesByViewId_shouldFilterMatchingResourceNames() {
        val fareNode = UberNodeSnapshot(
            text = "25.00 €",
            viewIdResourceName = "com.ubercab.driver:id/fare_text"
        )
        val distanceNode = UberNodeSnapshot(
            text = "5.0 km",
            viewIdResourceName = "com.ubercab.driver:id/distance_text"
        )
        val root = UberNodeSnapshot(children = listOf(fareNode, distanceNode))

        val found = root.findNodesByViewId("com.ubercab.driver:id/fare_text")
        assertEquals(1, found.size)
        assertEquals("25.00 €", found[0].text)

        val notFound = root.findNodesByViewId("com.ubercab.driver:id/missing_id")
        assertTrue(notFound.isEmpty())
    }
}
