package com.ridedecider.app.data.accessibility.uber.diagnostic

/**
 * Implementación en memoria de [AccessibilityDiagnosticLogger] para pruebas unitarias JVM y diagnósticos.
 */
class InMemoryAccessibilityDiagnosticLogger(
    override var isLoggingEnabled: Boolean = true,
    override var isTreeDebugEnabled: Boolean = true
) : AccessibilityDiagnosticLogger {

    val eventLogs = mutableListOf<Pair<String, String>>()
    val pipelineResults = mutableListOf<String>()
    val treeDumps = mutableListOf<String>()
    val offerActionLogs = mutableListOf<String>()
    val flatScanLogs = mutableListOf<String>()
    val keywordLogs = mutableListOf<String>()
    val fullDumpLogs = mutableListOf<String>()

    override fun logEvent(tag: String, message: String) {
        if (!isLoggingEnabled) return
        eventLogs.add(tag to message)
    }

    override fun logPipelineResult(summary: String) {
        if (!isLoggingEnabled) return
        pipelineResults.add(summary)
    }

    override fun logTreeDump(treeText: String) {
        if (!isLoggingEnabled || !isTreeDebugEnabled) return
        treeDumps.add(treeText)
    }

    override fun logOfferActionNode(diagnosticReport: String) {
        if (!isLoggingEnabled) return
        offerActionLogs.add(diagnosticReport)
        pipelineResults.add(diagnosticReport)
    }

    override fun logFlatScan(flatScanReport: String) {
        if (!isLoggingEnabled || !isTreeDebugEnabled) return
        flatScanLogs.add(flatScanReport)
    }

    override fun logKeywordDiagnostic(keywordReport: String) {
        if (!isLoggingEnabled) return
        keywordLogs.add(keywordReport)
        pipelineResults.add(keywordReport)
    }

    override fun logFullNodeDump(dumpText: String) {
        if (!isLoggingEnabled) return
        fullDumpLogs.add(dumpText)
    }

    fun clear() {
        eventLogs.clear()
        pipelineResults.clear()
        treeDumps.clear()
        offerActionLogs.clear()
        flatScanLogs.clear()
        keywordLogs.clear()
        fullDumpLogs.clear()
    }
}
