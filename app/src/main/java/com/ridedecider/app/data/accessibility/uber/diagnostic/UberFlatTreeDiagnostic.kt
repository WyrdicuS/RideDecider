package com.ridedecider.app.data.accessibility.uber.diagnostic

import com.ridedecider.app.data.accessibility.uber.UberNodeSnapshot

/**
 * Utilidad de diagnóstico puro en Kotlin para escanear de forma plana el 100% de los nodos
 * de un [UberNodeSnapshot], sin restricciones de profundidad de árbol, y detectar palabras clave
 * operativas de Uber Driver ("Aceptar", "Emparejar", "5,02", "€", "min", "km", "UberX", "Exclusiva").
 */
object UberFlatTreeDiagnostic {

    val TARGET_KEYWORDS = listOf(
        "Aceptar",
        "Emparejar",
        "Match",
        "5,02",
        "5.02",
        "€",
        "min",
        "km",
        "UberX",
        "Exclusiva"
    )

    data class DiagnosticScanResult(
        val flatScanSummary: String,
        val keywordReports: List<String>
    )

    /**
     * Escanea exhaustivamente todos los nodos del snapshot produciendo:
     * 1. Un resumen plano de cada nodo con todos sus atributos.
     * 2. Búsquedas dirigidas para palabras clave y reconstrucción de la ruta de ancestros [PARENT_PATH].
     *
     * @param rootNode Raíz del snapshot.
     * @return [DiagnosticScanResult] con el volcado plano y los reportes de palabras clave.
     */
    fun scanAndInspect(rootNode: UberNodeSnapshot?): DiagnosticScanResult {
        if (rootNode == null) {
            return DiagnosticScanResult(
                flatScanSummary = "[DIAG_NODE_SCAN] snapshotNodes=0 scannedNodes=0\n<SNAPSHOT_NULL>",
                keywordReports = emptyList()
            )
        }

        val flatNodes = rootNode.flatten()
        val totalNodes = flatNodes.size

        // 1. Construir volcado plano de todos los nodos
        val flatSummarySb = StringBuilder()
        flatSummarySb.appendLine("[DIAG_NODE_SCAN] snapshotNodes=$totalNodes scannedNodes=$totalNodes")

        flatNodes.forEachIndexed { index, node ->
            val className = node.className ?: "android.view.View"
            val textStr = node.text?.let { "\"$it\"" } ?: "null"
            val descStr = node.contentDescription?.let { "\"$it\"" } ?: "null"
            val resIdStr = node.viewIdResourceName?.let { "\"$it\"" } ?: "null"
            val childCount = node.children.size

            flatSummarySb.appendLine(
                "[NODE] index=$index class=$className text=$textStr contentDescription=$descStr resourceId=$resIdStr clickable=${node.isClickable} enabled=${node.isEnabled} visible=${node.isVisibleToUser} childCount=$childCount"
            )
        }

        // 2. Búsqueda exhaustiva de palabras clave y rutas de ancestros
        val keywordReports = mutableListOf<String>()

        for ((index, node) in flatNodes.withIndex()) {
            val nodeText = node.text ?: ""
            val nodeDesc = node.contentDescription ?: ""

            for (keyword in TARGET_KEYWORDS) {
                val matchesText = nodeText.contains(keyword, ignoreCase = true)
                val matchesDesc = nodeDesc.contains(keyword, ignoreCase = true)

                if (matchesText || matchesDesc) {
                    val reportSb = StringBuilder()
                    val className = node.className ?: "android.view.View"
                    val textStr = node.text?.let { "\"$it\"" } ?: "null"
                    val descStr = node.contentDescription?.let { "\"$it\"" } ?: "null"
                    val resIdStr = node.viewIdResourceName?.let { "\"$it\"" } ?: "null"

                    val headerTag = when {
                        keyword.equals("Aceptar", ignoreCase = true) || keyword.equals("Accept", ignoreCase = true) ->
                            "[DIAG_OFFER_TEXT_FOUND] text=\"Aceptar\""
                        keyword.equals("Emparejar", ignoreCase = true) || keyword.equals("Match", ignoreCase = true) ->
                            "[DIAG_RADAR_TEXT_FOUND] text=\"Emparejar\""
                        else ->
                            "[DIAG_KEYWORD_FOUND] keyword=\"$keyword\""
                    }

                    reportSb.appendLine("$headerTag index=$index class=$className text=$textStr contentDescription=$descStr resourceId=$resIdStr clickable=${node.isClickable} enabled=${node.isEnabled} visible=${node.isVisibleToUser}")

                    // Reconstruir la ruta completa de padres desde la raíz hasta este nodo
                    val parentPath = findPathToNode(rootNode, node)
                    reportSb.appendLine("[PARENT_PATH]")
                    if (parentPath.isEmpty() || (parentPath.size == 1 && parentPath[0] === node)) {
                        reportSb.appendLine("  -> Level 0 (Nodo raíz): [$className] id=$resIdStr")
                    } else {
                        parentPath.forEachIndexed { level, ancestor ->
                            val aClass = ancestor.className?.substringAfterLast('.') ?: "Node"
                            val aId = ancestor.viewIdResourceName ?: "null"
                            val aText = ancestor.text?.let { " text=\"${it.take(30)}\"" } ?: ""
                            val aDesc = ancestor.contentDescription?.let { " desc=\"${it.take(30)}\"" } ?: ""
                            reportSb.appendLine("  -> Level $level: [$aClass] id=$aId$aText$aDesc")
                        }
                    }

                    keywordReports.add(reportSb.toString().trimEnd())
                }
            }
        }

        return DiagnosticScanResult(
            flatScanSummary = flatSummarySb.toString().trimEnd(),
            keywordReports = keywordReports
        )
    }

    /**
     * Encuentra la cadena de ancestros desde [root] hasta [target] mediante búsqueda en profundidad.
     * Funciona para cualquier profundidad de árbol (ej. > 20 niveles).
     */
    fun findPathToNode(root: UberNodeSnapshot, target: UberNodeSnapshot): List<UberNodeSnapshot> {
        val path = mutableListOf<UberNodeSnapshot>()

        fun search(current: UberNodeSnapshot): Boolean {
            path.add(current)
            if (current === target || (current == target && current.text == target.text && current.viewIdResourceName == target.viewIdResourceName)) {
                return true
            }
            for (child in current.children) {
                if (search(child)) {
                    return true
                }
            }
            path.removeAt(path.size - 1)
            return false
        }

        search(root)
        return path
    }
}
