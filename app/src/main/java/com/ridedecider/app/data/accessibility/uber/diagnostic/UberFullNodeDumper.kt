package com.ridedecider.app.data.accessibility.uber.diagnostic

import com.ridedecider.app.data.accessibility.uber.UberNodeSnapshot

/**
 * Utilidad diagnóstica pura para generar el volcado exhaustivo [FULL_NODE_DUMP]
 * de todos los nodos de un snapshot con formato compacto y resumen estadístico final.
 */
object UberFullNodeDumper {

    data class DumpResult(
        val dumpText: String,
        val totalNodes: Int,
        val textNodesCount: Int,
        val contentDescCount: Int,
        val resourceIdCount: Int,
        val visibleNodesCount: Int,
        val clickableNodesCount: Int
    )

    /**
     * Recorre recursivamente todo el árbol de nodos y construye el reporte [FULL_NODE_DUMP].
     *
     * @param rootNode Raíz del snapshot.
     * @return [DumpResult] con el texto formateado y conteos estadísticos.
     */
    fun dump(rootNode: UberNodeSnapshot?): DumpResult {
        if (rootNode == null) {
            return DumpResult(
                dumpText = "[FULL_NODE_DUMP]\nnodes=0\n<SNAPSHOT_NULL>\n[FULL_NODE_DUMP_END]\nnodes=0\ntextNodes=0\ncontentDescriptionNodes=0\nresourceIdNodes=0\nvisibleNodes=0\nclickableNodes=0",
                totalNodes = 0,
                textNodesCount = 0,
                contentDescCount = 0,
                resourceIdCount = 0,
                visibleNodesCount = 0,
                clickableNodesCount = 0
            )
        }

        val sb = StringBuilder()
        var nodeIndex = 0
        var textCount = 0
        var descCount = 0
        var resIdCount = 0
        var visibleCount = 0
        var clickableCount = 0

        val flatNodes = rootNode.flatten()
        val totalNodes = flatNodes.size

        sb.appendLine("[FULL_NODE_DUMP]")
        sb.appendLine("nodes=$totalNodes")

        fun traverse(node: UberNodeSnapshot, depth: Int) {
            nodeIndex++
            val idx = nodeIndex
            val className = node.className ?: "android.view.View"
            val text = node.text
            val desc = node.contentDescription
            val resId = node.viewIdResourceName
            val visible = node.isVisibleToUser
            val clickable = node.isClickable
            val enabled = node.isEnabled
            val bounds = node.boundsInScreen ?: "null"

            if (text != null && text.isNotBlank()) textCount++
            if (desc != null && desc.isNotBlank()) descCount++
            if (resId != null && resId.isNotBlank()) resIdCount++
            if (visible) visibleCount++
            if (clickable) clickableCount++

            val textFormatted = text?.let { "\"$it\"" } ?: "null"
            val descFormatted = desc?.let { "\"$it\"" } ?: "null"
            val idFormatted = resId?.let { "\"$it\"" } ?: "null"

            sb.appendLine()
            sb.appendLine("NODE #$idx depth=$depth")
            sb.appendLine("class=$className")
            sb.appendLine("text=$textFormatted")
            sb.appendLine("desc=$descFormatted")
            sb.appendLine("id=$idFormatted")
            sb.appendLine("visible=$visible")
            sb.appendLine("clickable=$clickable")
            sb.appendLine("enabled=$enabled")
            sb.appendLine("bounds=$bounds")

            for (child in node.children) {
                traverse(child, depth + 1)
            }
        }

        traverse(rootNode, 0)

        sb.appendLine()
        sb.appendLine("[FULL_NODE_DUMP_END]")
        sb.appendLine("nodes=$totalNodes")
        sb.appendLine("textNodes=$textCount")
        sb.appendLine("contentDescriptionNodes=$descCount")
        sb.appendLine("resourceIdNodes=$resIdCount")
        sb.appendLine("visibleNodes=$visibleCount")
        sb.appendLine("clickableNodes=$clickableCount")

        return DumpResult(
            dumpText = sb.toString().trimEnd(),
            totalNodes = totalNodes,
            textNodesCount = textCount,
            contentDescCount = descCount,
            resourceIdCount = resIdCount,
            visibleNodesCount = visibleCount,
            clickableNodesCount = clickableCount
        )
    }
}
