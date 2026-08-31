package com.ridedecider.app.data.accessibility.uber.diagnostic

import com.ridedecider.app.data.accessibility.uber.UberNodeSnapshot

/**
 * Utilidad pura en Kotlin para convertir un [UberNodeSnapshot] en una representación
 * jerárquica formateada, truncada y acotada para depuración y diagnóstico seguro.
 */
object UberTreeDumper {

    const val DEFAULT_MAX_DEPTH = 40
    const val DEFAULT_MAX_NODES = 600
    const val DEFAULT_MAX_TEXT_LENGTH = 100

    /**
     * Vuelca el subárbol de nodos en texto estructurado con indentación y límites de seguridad.
     *
     * @param rootNode Nodo raíz del snapshot.
     * @param maxDepth Profundidad máxima de recursión permitida.
     * @param maxNodes Número máximo total de nodos a imprimir.
     * @param maxTextLength Longitud máxima por cadena de texto antes de truncar con elipsis.
     * @return Cadena formateada multilínea.
     */
    fun dump(
        rootNode: UberNodeSnapshot?,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
        maxNodes: Int = DEFAULT_MAX_NODES,
        maxTextLength: Int = DEFAULT_MAX_TEXT_LENGTH
    ): String {
        if (rootNode == null) return "<SNAPSHOT_NULL>"

        val sb = StringBuilder()
        sb.appendLine("=== UBER ACCESSIBILITY TREE DUMP ===")
        sb.appendLine("[TREE_DUMP_START]")
        var nodeCounter = 0

        fun formatNode(node: UberNodeSnapshot, currentDepth: Int) {
            if (nodeCounter >= maxNodes) {
                if (nodeCounter == maxNodes) {
                    val indent = "  ".repeat(currentDepth)
                    sb.appendLine("$indent... [TRUNCATED: Límite de $maxNodes nodos alcanzado]")
                    nodeCounter++
                }
                return
            }

            nodeCounter++
            val indent = "  ".repeat(currentDepth)

            val simpleClassName = node.className?.substringAfterLast('.') ?: "Node"
            val textStr = sanitizeAndTruncate(node.text, maxTextLength)
            val descStr = sanitizeAndTruncate(node.contentDescription, maxTextLength)
            val viewIdStr = node.viewIdResourceName?.substringAfterLast('/') ?: ""

            sb.append("$indent- [$simpleClassName]")
            if (viewIdStr.isNotBlank()) sb.append(" id=$viewIdStr")
            if (textStr != null) sb.append(" text=\"$textStr\"")
            if (descStr != null) sb.append(" desc=\"$descStr\"")
            if (node.isClickable) sb.append(" [clickable]")
            if (!node.isEnabled) sb.append(" [disabled]")
            if (!node.isVisibleToUser) sb.append(" [hidden]")
            sb.appendLine()

            if (currentDepth < maxDepth) {
                for (child in node.children) {
                    formatNode(child, currentDepth + 1)
                }
            } else if (node.children.isNotEmpty()) {
                val childIndent = "  ".repeat(currentDepth + 1)
                sb.appendLine("$childIndent... [TRUNCATED: Profundidad máxima $maxDepth]")
            }
        }

        formatNode(rootNode, 0)
        sb.appendLine("[TREE_DUMP_END] (Total nodos impresos: ${minOf(nodeCounter, maxNodes)})")
        sb.append("=== FIN DEL DUMP (Total nodos impresos: ${minOf(nodeCounter, maxNodes)}) ===")
        return sb.toString()
    }

    private fun sanitizeAndTruncate(input: String?, maxLength: Int): String? {
        if (input == null) return null
        val singleLine = input.replace('\n', ' ').replace('\r', ' ').trim()
        if (singleLine.isBlank()) return null
        return if (singleLine.length > maxLength) {
            singleLine.take(maxLength) + "…"
        } else {
            singleLine
        }
    }
}
