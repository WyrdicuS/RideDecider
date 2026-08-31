package com.ridedecider.app.data.update

import com.ridedecider.app.data.update.parser.JsonReleaseManifestParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateConfigTest {

    @Test
    fun latestManifestUrl_matchesExpectedGitHubReleasesPattern() {
        val expected = "https://github.com/WyrdicuS/RideDecider/releases/latest/download/latest.json"
        assertEquals(expected, UpdateConfig.LATEST_MANIFEST_URL)
    }

    @Test
    fun getReleaseApkUrl_formatsVersionCorrectly() {
        val apkUrl = UpdateConfig.getReleaseApkUrl("1.0.1")
        val expected = "https://github.com/WyrdicuS/RideDecider/releases/download/v1.0.1/RideDecider-1.0.1.apk"
        assertEquals(expected, apkUrl)
    }

    @Test
    fun parser_parsesGitHubReleaseManifestTemplateSuccessfully() {
        val parser = JsonReleaseManifestParser()
        val gitHubManifestJson = """
            {
                "versionCode": 2,
                "versionName": "1.0.1",
                "minSupportedVersionCode": 1,
                "mandatory": false,
                "apkUrl": "${UpdateConfig.getReleaseApkUrl("1.0.1")}",
                "apkSha256": "2e10214ef49e776501761e65f6abc43b9dd42a239e8256d28aad9a52794dfc62",
                "apkSizeBytes": 16024349,
                "releaseDate": "2026-09-01T12:00:00Z",
                "releaseNotes": [
                    "Mejoras de rendimiento",
                    "Corrección de errores"
                ]
            }
        """.trimIndent()

        val result = parser.parse(gitHubManifestJson)
        assertTrue(result.isSuccess)

        val manifest = result.getOrThrow()
        assertEquals(2, manifest.versionCode)
        assertEquals("1.0.1", manifest.versionName)
        assertEquals("https://github.com/WyrdicuS/RideDecider/releases/download/v1.0.1/RideDecider-1.0.1.apk", manifest.apkUrl)
        assertEquals(2, manifest.releaseNotes.size)
    }
}
