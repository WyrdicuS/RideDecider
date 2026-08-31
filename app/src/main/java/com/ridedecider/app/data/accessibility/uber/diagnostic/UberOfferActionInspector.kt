package com.ridedecider.app.data.accessibility.uber.diagnostic

import com.ridedecider.app.data.accessibility.uber.UberNodeSnapshot

/**
 * Inspector de diagnóstico para localizar, analizar y documentar nodos de acción
 * de oferta ("Aceptar", "Accept", "Emparejar", "Match") dentro del árbol de accesibilidad.
 *
 * Clase pura en Kotlin sin dependencias de Android ni efectos secundarios sobre la UI.
 */
object UberOfferActionInspector {

    private val ACTION_KEYWORDS = listOf("aceptar", "accept", "emparejar", "match")

    /**
     * Inspecciona el árbol [rootNode] en busca de nodos de acción de oferta y genera un informe
     * detallado con atributos del nodo, ruta de padres [PARENT_PATH] y descendientes [CHILDREN].
     *
     * @param rootNode Raíz del árbol de accesibilidad.
     * @return Lista de informes formateados (uno por cada nodo de acción encontrado).
     */
    fun inspect(rootNode: UberNodeSnapshot?): List<String> {
        if (rootNode == null) return emptyList()

        val results = mutableListOf<String>()
        val matches = findActionNodesWithPaths(rootNode, emptyList())

        for ((targetNode, parentPath) in matches) {
            val sb = StringBuilder()
            val className = targetNode.className?.substringAfterLast('.') ?: "Node"
            val textStr = targetNode.text ?: "null"
            val descStr = targetNode.contentDescription ?: "null"
            val viewId = targetNode.viewIdResourceName ?: "null"

            sb.appendLine("[OFFER_ACTION_NODE] text=\"$textStr\" desc=\"$descStr\" className=\"$className\" id=\"$viewId\" clickable=${targetNode.isClickable} enabled=${targetNode.isEnabled} visible=${targetNode.isVisibleToUser}")

            sb.appendLine("[PARENT_PATH]")
            if (parentPath.isEmpty()) {
                sb.appendLine("  (Nodo raíz)")
            } else {
                parentPath.forEachIndexed { index, parent ->
                    val pClass = parent.className?.substringAfterLast('.') ?: "Node"
                    val pId = parent.viewIdResourceName ?: ""
                    val pText = parent.text?.let { " text=\"${it.take(30)}\"" } ?: ""
                    val pDesc = parent.contentDescription?.let { " desc=\"${it.take(30)}\"" } ?: ""
                    val idPart = if (pId.isNotBlank()) " id=\"$pId\"" else ""
                    sb.appendLine("  -> [Level $index] [$pClass]$idPart$pText$pDesc")
                }
            }

            sb.appendLine("[CHILDREN]")
            if (targetNode.children.isEmpty()) {
                sb.appendLine("  (Sin hijos)")
            } else {
                formatChildren(targetNode.children, 1, sb)
            }

            results.add(sb.toString().trimEnd())
        }

        return results
    }

    private fun isActionNode(node: UberNodeSnapshot): Boolean {
        val text = node.text?.trim()?.lowercase()
        val desc = node.contentDescription?.trim()?.lowercase()

        val matchesText = text != null && ACTION_KEYWORDS.any { kw ->
            text == kw || text.contains(Regex("""(?i)\b$kw\b"""))
        }
        val matchesDesc = desc != null && ACTION_KEYWORDS.any { kw ->
            desc == kw || desc.contains(Regex("""(?i)\b$kw\b"""))
        }

        return matchesText || matchesDesc
    }

    private fun findActionNodesWithPaths(
        current: UberNodeSnapshot,
        currentPath: List<UberNodeSnapshot>
    ): List<Pair<UberNodeSnapshot, List<UberNodeSnapshot>>> {
        val found = mutableListOf<Pair<UberNodeSnapshot, List<UberNodeSnapshot>>>()

        if (isActionNode(current)) {
            found.add(current to currentPath)
        }

        val nextPath = currentPath + current
        for (child in current.children) {
            found.addAll(findActionNodesWithPaths(child, nextPath))
        }

        return found
    }

    private fun formatChildren(children: List<UberNodeSnapshot>, indentLevel: Int, sb: StringBuilder) {
        val indent = "  ".repeat(indentLevel)
        for (child in children) {
            val cClass = child.className?.substringAfterLast('.') ?: "Node"
            val textStr = child.text?.let { " text=\"${it.take(40)}\"" } ?: ""
            val descStr = child.contentDescription?.let { " desc=\"${it.take(40)}\"" } ?: ""
            val idStr = child.viewIdResourceName?.let { " id=\"$it\"" } ?: ""
            val clickStr = if (child.isClickable) " [clickable]" else ""
            sb.appendLine("$indent- [$cClass]$idStr$textStr$descStr$clickStr")

            if (child.children.isNotEmpty()) {
                formatChildren(child.children, indentLevel + 1, sb)
            }
        }
    }
}
