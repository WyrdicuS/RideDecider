package com.ridedecider.app.data.update

import com.ridedecider.app.data.update.parser.JsonReleaseManifestParser
import com.ridedecider.app.domain.engine.AppUpdateEvaluator
import com.ridedecider.app.domain.model.update.UpdateEvaluationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseManifest101ValidationTest {

    private val manifestJson = """
        {
          "versionCode": 2,
          "versionName": "1.0.1",
          "minSupportedVersionCode": 1,
          "mandatory": false,
          "apkUrl": "https://github.com/WyrdicuS/RideDecider/releases/download/v1.0.1/RideDecider-1.0.1.apk",
          "apkSha256": "36def20e45d9e3856ee38f47e34d00ffb72f16639111a49b163e48169f610dd3",
          "apkSizeBytes": 16024331,
          "releaseDate": "2026-09-01T00:00:00Z",
          "releaseNotes": [
            "Mejoras de rendimiento y estabilidad",
            "Correcciones menores de visualización",
            "Validación del sistema de actualización privada OTA"
          ]
        }
    """.trimIndent()

    @Test
    fun manifest101_parsesCleanlyWithAllFields() {
        val parser = JsonReleaseManifestParser()
        val result = parser.parse(manifestJson)
        assertTrue(result.isSuccess)

        val manifest = result.getOrThrow()
        assertEquals(2, manifest.versionCode)
        assertEquals("1.0.1", manifest.versionName)
        assertEquals(1, manifest.minSupportedVersionCode)
        assertFalse(manifest.isMandatory)
        assertEquals("https://github.com/WyrdicuS/RideDecider/releases/download/v1.0.1/RideDecider-1.0.1.apk", manifest.apkUrl)
        assertEquals("36def20e45d9e3856ee38f47e34d00ffb72f16639111a49b163e48169f610dd3", manifest.apkSha256)
        assertEquals(16024331L, manifest.apkSizeBytes)
        assertEquals("2026-09-01T00:00:00Z", manifest.releaseDate)
        assertEquals(3, manifest.releaseNotes.size)
    }

    @Test
    fun manifest101_evaluatedAgainst100_triggersUpdateAvailable() {
        val parser = JsonReleaseManifestParser()
        val evaluator = AppUpdateEvaluator()

        val manifest = parser.parse(manifestJson).getOrThrow()
        val evaluation = evaluator.evaluate(
            installedVersionCode = 1,
            installedVersionName = "1.0.0",
            manifest = manifest
        )

        assertTrue(evaluation is UpdateEvaluationResult.UpdateAvailable)
        val available = evaluation as UpdateEvaluationResult.UpdateAvailable
        assertEquals(2, available.manifest.versionCode)
        assertEquals("1.0.1", available.manifest.versionName)
    }

    @Test
    fun manifest101_evaluatedAgainst101_triggersUpToDate() {
        val parser = JsonReleaseManifestParser()
        val evaluator = AppUpdateEvaluator()

        val manifest = parser.parse(manifestJson).getOrThrow()
        val evaluation = evaluator.evaluate(
            installedVersionCode = 2,
            installedVersionName = "1.0.1",
            manifest = manifest
        )

        assertTrue(evaluation is UpdateEvaluationResult.UpToDate)
    }

    @Test
    fun manifest101_evaluatedAgainstFutureVersion_triggersUpToDate() {
        val parser = JsonReleaseManifestParser()
        val evaluator = AppUpdateEvaluator()

        val manifest = parser.parse(manifestJson).getOrThrow()
        val evaluation = evaluator.evaluate(
            installedVersionCode = 3,
            installedVersionName = "1.0.2",
            manifest = manifest
        )

        assertTrue(evaluation is UpdateEvaluationResult.UpToDate)
    }
}
