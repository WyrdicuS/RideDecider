package com.ridedecider.app.domain.engine

import com.ridedecider.app.domain.model.update.AppReleaseManifest
import com.ridedecider.app.domain.model.update.UpdateEvaluationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppUpdateEvaluatorTest {

    private lateinit var evaluator: AppUpdateEvaluator

    @Before
    fun setUp() {
        evaluator = AppUpdateEvaluator()
    }

    @Test
    fun evaluate_newerVersionAvailable_returnsUpdateAvailable() {
        val manifest = AppReleaseManifest(
            versionCode = 2,
            versionName = "1.0.1",
            apkUrl = "https://example.com/app.apk",
            apkSha256 = "abcdef123456"
        )

        val result = evaluator.evaluate(
            installedVersionCode = 1,
            installedVersionName = "1.0.0",
            manifest = manifest
        )

        assertTrue(result is UpdateEvaluationResult.UpdateAvailable)
        val update = result as UpdateEvaluationResult.UpdateAvailable
        assertEquals(2, update.manifest.versionCode)
        assertEquals("1.0.1", update.manifest.versionName)
        assertEquals(1, update.currentVersionCode)
        assertEquals("1.0.0", update.currentVersionName)
    }

    @Test
    fun evaluate_sameVersion_returnsUpToDate() {
        val manifest = AppReleaseManifest(
            versionCode = 1,
            versionName = "1.0.0",
            apkUrl = "https://example.com/app.apk",
            apkSha256 = "abcdef123456"
        )

        val result = evaluator.evaluate(
            installedVersionCode = 1,
            installedVersionName = "1.0.0",
            manifest = manifest
        )

        assertTrue(result is UpdateEvaluationResult.UpToDate)
    }

    @Test
    fun evaluate_olderVersionInManifest_returnsUpToDate() {
        val manifest = AppReleaseManifest(
            versionCode = 1,
            versionName = "1.0.0",
            apkUrl = "https://example.com/app.apk",
            apkSha256 = "abcdef123456"
        )

        val result = evaluator.evaluate(
            installedVersionCode = 2,
            installedVersionName = "1.0.1",
            manifest = manifest
        )

        assertTrue(result is UpdateEvaluationResult.UpToDate)
    }

    @Test
    fun evaluate_invalidManifest_returnsIgnoredOrInvalid() {
        val manifestZero = AppReleaseManifest(
            versionCode = 0,
            versionName = "1.0.0",
            apkUrl = "https://example.com/app.apk",
            apkSha256 = "abcdef123456"
        )

        val result = evaluator.evaluate(
            installedVersionCode = 1,
            installedVersionName = "1.0.0",
            manifest = manifestZero
        )

        assertTrue(result is UpdateEvaluationResult.IgnoredOrInvalid)
    }
}
