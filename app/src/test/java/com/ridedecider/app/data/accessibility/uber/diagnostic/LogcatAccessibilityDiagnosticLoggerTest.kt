package com.ridedecider.app.data.accessibility.uber.diagnostic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Pruebas unitarias para verificar la construcción dinámica de rutas en [LogcatAccessibilityDiagnosticLogger].
 */
class LogcatAccessibilityDiagnosticLoggerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun logFile_usesCustomFilesDir_correctly() {
        val fakeFilesDir = tempFolder.newFolder("mock_files_dir")
        val logger = LogcatAccessibilityDiagnosticLogger(
            customFilesDir = fakeFilesDir
        )

        assertEquals("live_diag.txt", logger.logFile.name)
        assertEquals(fakeFilesDir.absolutePath, logger.logFile.parentFile?.absolutePath)
    }

    @Test
    fun logEvent_appendsToFile_inDynamicDirectory() {
        val fakeFilesDir = tempFolder.newFolder("mock_files_dir_2")
        val logger = LogcatAccessibilityDiagnosticLogger(
            isLoggingEnabled = true,
            customFilesDir = fakeFilesDir
        )

        logger.logEvent("TEST_TAG", "Mensaje de prueba de compatibilidad")

        assertTrue(logger.logFile.exists())
        val fileContent = logger.logFile.readText()
        assertTrue(fileContent.contains("[TEST_TAG] Mensaje de prueba de compatibilidad"))
    }
}
