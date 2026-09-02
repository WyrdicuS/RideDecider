package com.ridedecider.app.data.accessibility.uber.diagnostic

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Implementación de [AccessibilityDiagnosticLogger] que emite logs a Logcat de Android en nivel INFO.
 */
class LogcatAccessibilityDiagnosticLogger(
    override var isLoggingEnabled: Boolean = true,
    override var isTreeDebugEnabled: Boolean = true,
    private val tag: String = "RideDeciderDiag",
    context: Context? = null,
    customFilesDir: File? = null
) : AccessibilityDiagnosticLogger {

    val logFile: File = run {
        val baseDir = customFilesDir ?: context?.filesDir ?: File("/data/data/com.ridedecider.app/files")
        File(baseDir, "live_diag.txt")
    }

    private fun appendToFile(line: String) {
        try {
            logFile.parentFile?.mkdirs()
            java.io.FileOutputStream(logFile, true).use { out ->
                val time = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
                out.write("$time: $line\n".toByteArray(Charsets.UTF_8))
            }
        } catch (_: Exception) {}
    }

    private fun safeLogcat(tag: String, message: String) {
        try {
            Log.e(tag, message)
        } catch (_: Throwable) {}
    }

    override fun logEvent(tag: String, message: String) {
        if (!isLoggingEnabled) return
        safeLogcat(this.tag, "[$tag] $message")
        appendToFile("[$tag] $message")
    }

    override fun logPipelineResult(summary: String) {
        if (!isLoggingEnabled) return
        safeLogcat(tag, "[PIPELINE_RESULT] $summary")
        appendToFile("[PIPELINE_RESULT] $summary")
    }

    override fun logTreeDump(treeText: String) {
        if (!isLoggingEnabled || !isTreeDebugEnabled) return
        treeText.lines().chunked(25).forEach { chunk ->
            safeLogcat(tag, chunk.joinToString("\n"))
        }
        appendToFile("[TREE_DUMP]\n$treeText")
    }

    override fun logOfferActionNode(diagnosticReport: String) {
        if (!isLoggingEnabled) return
        diagnosticReport.lines().chunked(25).forEach { chunk ->
            safeLogcat(tag, chunk.joinToString("\n"))
        }
        appendToFile("[ACTION_NODE]\n$diagnosticReport")
    }

    override fun logFlatScan(flatScanReport: String) {
        if (!isLoggingEnabled || !isTreeDebugEnabled) return
        flatScanReport.lines().chunked(25).forEach { chunk ->
            safeLogcat(tag, chunk.joinToString("\n"))
        }
        appendToFile("[FLAT_SCAN]\n$flatScanReport")
    }

    override fun logKeywordDiagnostic(keywordReport: String) {
        if (!isLoggingEnabled) return
        keywordReport.lines().chunked(25).forEach { chunk ->
            safeLogcat(tag, chunk.joinToString("\n"))
        }
        appendToFile("[KEYWORD_DIAG]\n$keywordReport")
    }

    override fun logFullNodeDump(dumpText: String) {
        if (!isLoggingEnabled) return
        dumpText.lines().chunked(25).forEach { chunk ->
            safeLogcat(tag, chunk.joinToString("\n"))
        }
        appendToFile("[FULL_NODE_DUMP]\n$dumpText")
    }
}
