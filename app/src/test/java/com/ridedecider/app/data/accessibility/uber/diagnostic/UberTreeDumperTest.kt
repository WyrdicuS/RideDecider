package com.ridedecider.app.data.accessibility.uber.diagnostic

import com.ridedecider.app.data.accessibility.uber.UberNodeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitarios para [UberTreeDumper].
 *
 * Verifica el formateo estructurado del árbol, las sangrías jerárquicas, los límites de seguridad
 * (profundidad máxima, número máximo de nodos) y el truncado de textos largos.
 */
class UberTreeDumperTest {

    @Test
    fun nullSnapshot_shouldReturnNullPlaceholder() {
        val result = UberTreeDumper.dump(null)
        assertEquals("<SNAPSHOT_NULL>", result)
    }

    @Test
    fun emptySnapshot_shouldDumpRootNode() {
        val root = UberNodeSnapshot(className = "android.widget.FrameLayout")

        val result = UberTreeDumper.dump(root)

        assertTrue(result.contains("=== UBER ACCESSIBILITY TREE DUMP ==="))
        assertTrue(result.contains("- [FrameLayout]"))
        assertTrue(result.contains("Total nodos impresos: 1"))
    }

    @Test
    fun nestedTree_shouldFormatWithIndentationAndAttributes() {
        val button = UberNodeSnapshot(
            className = "android.widget.Button",
            text = "Aceptar",
            viewIdResourceName = "com.ubercab.driver:id/accept_btn",
            isClickable = true
        )
        val textNode = UberNodeSnapshot(
            className = "android.widget.TextView",
            text = "14.20 €"
        )
        val root = UberNodeSnapshot(
            className = "android.widget.LinearLayout",
            children = listOf(textNode, button)
        )

        val result = UberTreeDumper.dump(root)

        assertTrue(result.contains("- [LinearLayout]"))
        assertTrue(result.contains("  - [TextView] text=\"14.20 €\""))
        assertTrue(result.contains("  - [Button] id=accept_btn text=\"Aceptar\" [clickable]"))
    }

    @Test
    fun maxDepth_shouldTruncateDeeperNodes() {
        val level3 = UberNodeSnapshot(text = "Nivel 3")
        val level2 = UberNodeSnapshot(text = "Nivel 2", children = listOf(level3))
        val level1 = UberNodeSnapshot(text = "Nivel 1", children = listOf(level2))
        val root = UberNodeSnapshot(text = "Nivel 0", children = listOf(level1))

        val result = UberTreeDumper.dump(root, maxDepth = 2)

        assertTrue(result.contains("Nivel 0"))
        assertTrue(result.contains("Nivel 1"))
        assertTrue(result.contains("Nivel 2"))
        assertTrue(result.contains("[TRUNCATED: Profundidad máxima 2]"))
        assertFalse(result.contains("text=\"Nivel 3\""))
    }

    @Test
    fun maxNodes_shouldLimitTotalNodesPrinted() {
        val children = (1..10).map { UberNodeSnapshot(text = "Item $it") }
        val root = UberNodeSnapshot(text = "Root", children = children)

        val result = UberTreeDumper.dump(root, maxNodes = 4)

        assertTrue(result.contains("[TRUNCATED: Límite de 4 nodos alcanzado]"))
        assertTrue(result.contains("Total nodos impresos: 4"))
    }

    @Test
    fun textTruncation_shouldTruncateLongStrings() {
        val longText = "Calle de la Princesa 142 Portal B Piso 3 Puerta Derecha Madrid"
        val node = UberNodeSnapshot(text = longText)

        val result = UberTreeDumper.dump(node, maxTextLength = 15)

        assertTrue(result.contains("Calle de la Pri…"))
        assertFalse(result.contains("Puerta Derecha"))
    }
}
