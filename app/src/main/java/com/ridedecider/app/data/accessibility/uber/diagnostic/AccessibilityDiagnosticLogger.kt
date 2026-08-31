package com.ridedecider.app.data.accessibility.uber.diagnostic

/**
 * Contrato abstracto para el registro controlado de diagnósticos y árboles de accesibilidad.
 */
interface AccessibilityDiagnosticLogger {
    /** Indica si el volcado textual del árbol de nodos de accesibilidad está activado. */
    val isTreeDebugEnabled: Boolean

    /** Indica si el sistema de registro de diagnósticos está habilitado. */
    val isLoggingEnabled: Boolean

    /** Registra un evento o punto de control operativo. */
    fun logEvent(tag: String, message: String)

    /** Registra el resultado estructurado de la ejecución del pipeline. */
    fun logPipelineResult(summary: String)

    /** Registra la representación jerárquica formateada del árbol de accesibilidad. */
    fun logTreeDump(treeText: String)

    /** Registra el informe especializado de un nodo de acción de oferta ([OFFER_ACTION_NODE]). */
    fun logOfferActionNode(diagnosticReport: String) {
        logPipelineResult(diagnosticReport)
    }

    /** Registra el volcado plano completo de todos los nodos del snapshot ([DIAG_NODE_SCAN], [NODE]). */
    fun logFlatScan(flatScanReport: String) {
        logTreeDump(flatScanReport)
    }

    /** Registra el hallazgo de palabras clave operativas ([DIAG_OFFER_TEXT_FOUND], [DIAG_RADAR_TEXT_FOUND], etc.). */
    fun logKeywordDiagnostic(keywordReport: String) {
        logPipelineResult(keywordReport)
    }

    /** Registra el volcado exhaustivo [FULL_NODE_DUMP] de todos los nodos con cooldown. */
    fun logFullNodeDump(dumpText: String) {
        logTreeDump(dumpText)
    }
}
