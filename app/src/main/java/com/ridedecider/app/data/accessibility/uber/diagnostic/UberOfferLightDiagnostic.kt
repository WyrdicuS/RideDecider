package com.ridedecider.app.data.accessibility.uber.diagnostic

import com.ridedecider.app.data.accessibility.uber.UberNodeSnapshot

/**
 * Diagnóstico ultraligero y de alto rendimiento para detectar candidatos a botón de oferta
 * ("Aceptar" / "Accept" -> [OFFER_CANDIDATE]) o Radar ("Emparejar" / "Match" -> [RADAR_CANDIDATE])
 * sin sobrecargar el hilo de UI ni el buffer de Logcat.
 *
 * Características de rendimiento:
 * - Un único pase DFS O(N) lineal sin llamadas recursivas secundarias.
 * - Sin asignaciones de cadenas pesadas ni llamadas a Logcat masivas.
 * - Sin resolución de rutas de ancestros O(N²) ni volcados de árbol completos.
 */
object UberOfferLightDiagnostic {

    private val OFFER_KEYWORDS = listOf("aceptar", "accept")
    private val RADAR_KEYWORDS = listOf("emparejar", "match")

    /**
     * Inspecciona el árbol [rootNode] de forma ligera y devuelve exclusivamente los candidatos encontrados.
     *
     * @param rootNode Raíz del snapshot.
     * @return Lista de cadenas compactas formateadas para Logcat.
     */
    fun scanCandidates(rootNode: UberNodeSnapshot?): List<String> {
        if (rootNode == null) return emptyList()

        val candidates = mutableListOf<String>()

        fun traverse(node: UberNodeSnapshot) {
            val text = node.text?.trim()
            val desc = node.contentDescription?.trim()

            val isOffer = isMatch(text, OFFER_KEYWORDS) || isMatch(desc, OFFER_KEYWORDS)
            val isRadar = isMatch(text, RADAR_KEYWORDS) || isMatch(desc, RADAR_KEYWORDS)

            if (isOffer || isRadar) {
                val tag = if (isOffer) "[OFFER_CANDIDATE]" else "[RADAR_CANDIDATE]"
                val className = node.className?.substringAfterLast('.') ?: "View"
                val textStr = text?.let { "\"$it\"" } ?: "null"
                val descStr = desc?.let { "\"$it\"" } ?: "null"
                val resIdStr = node.viewIdResourceName ?: "null"

                candidates.add(
                    "$tag text=$textStr className=\"$className\" resourceId=\"$resIdStr\" contentDescription=$descStr clickable=${node.isClickable} enabled=${node.isEnabled} visible=${node.isVisibleToUser}"
                )
            }

            for (child in node.children) {
                traverse(child)
            }
        }

        traverse(rootNode)
        return candidates
    }

    private fun isMatch(input: String?, keywords: List<String>): Boolean {
        if (input == null || input.isEmpty()) return false
        val lower = input.lowercase()
        return keywords.any { kw ->
            lower == kw || lower.contains(Regex("""(?i)\b$kw\b"""))
        }
    }
}
