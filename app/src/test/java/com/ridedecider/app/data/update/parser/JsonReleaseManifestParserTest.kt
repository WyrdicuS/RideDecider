package com.ridedecider.app.data.update.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JsonReleaseManifestParserTest {

    private lateinit var parser: JsonReleaseManifestParser

    @Before
    fun setUp() {
        parser = JsonReleaseManifestParser()
    }

    @Test
    fun parse_validCompleteManifest_returnsSuccessWithAllFields() {
        val json = """
            {
                "versionCode": 2,
                "versionName": "1.0.1",
                "minSupportedVersionCode": 1,
                "mandatory": true,
                "apkUrl": "https://updates.ridedecider.com/releases/ridedecider-1.0.1.apk",
                "apkSha256": "2e10214ef49e776501761e65f6abc43b9dd42a239e8256d28aad9a52794dfc62",
                "apkSizeBytes": 15485760,
                "releaseDate": "2026-09-01T12:00:00Z",
                "releaseNotes": [
                    "Mejora en cálculo de ritmos",
                    "Corrección de lectura en pantalla"
                ]
            }
        """.trimIndent()

        val result = parser.parse(json)
        assertTrue(result.isSuccess)

        val manifest = result.getOrThrow()
        assertEquals(2, manifest.versionCode)
        assertEquals("1.0.1", manifest.versionName)
        assertEquals(1, manifest.minSupportedVersionCode)
        assertTrue(manifest.isMandatory)
        assertEquals("https://updates.ridedecider.com/releases/ridedecider-1.0.1.apk", manifest.apkUrl)
        assertEquals("2e10214ef49e776501761e65f6abc43b9dd42a239e8256d28aad9a52794dfc62", manifest.apkSha256)
        assertEquals(15485760L, manifest.apkSizeBytes)
        assertEquals("2026-09-01T12:00:00Z", manifest.releaseDate)
        assertEquals(2, manifest.releaseNotes.size)
        assertEquals("Mejora en cálculo de ritmos", manifest.releaseNotes[0])
        assertEquals("Corrección de lectura en pantalla", manifest.releaseNotes[1])
    }

    @Test
    fun parse_withAliasKeys_downloadUrlAndSha256_returnsSuccess() {
        val json = """
            {
                "versionCode": 3,
                "versionName": "1.1.0",
                "downloadUrl": "https://server.com/app.apk",
                "sha256": "abc123def456",
                "notes": ["Nota 1"]
            }
        """.trimIndent()

        val result = parser.parse(json)
        assertTrue(result.isSuccess)

        val manifest = result.getOrThrow()
        assertEquals(3, manifest.versionCode)
        assertEquals("1.1.0", manifest.versionName)
        assertEquals("https://server.com/app.apk", manifest.apkUrl)
        assertEquals("abc123def456", manifest.apkSha256)
        assertFalse(manifest.isMandatory)
        assertEquals(1, manifest.releaseNotes.size)
        assertEquals("Nota 1", manifest.releaseNotes[0])
    }

    @Test
    fun parse_missingVersionCode_returnsFailure() {
        val json = """
            {
                "versionName": "1.0.1",
                "apkUrl": "https://server.com/app.apk",
                "apkSha256": "abc"
            }
        """.trimIndent()

        val result = parser.parse(json)
        assertTrue(result.isFailure)
    }

    @Test
    fun parse_zeroOrNegativeVersionCode_returnsFailure() {
        val json = """
            {
                "versionCode": 0,
                "versionName": "1.0.1",
                "apkUrl": "https://server.com/app.apk",
                "apkSha256": "abc"
            }
        """.trimIndent()

        val result = parser.parse(json)
        assertTrue(result.isFailure)
    }

    @Test
    fun parse_blankVersionName_returnsFailure() {
        val json = """
            {
                "versionCode": 2,
                "versionName": "   ",
                "apkUrl": "https://server.com/app.apk",
                "apkSha256": "abc"
            }
        """.trimIndent()

        val result = parser.parse(json)
        assertTrue(result.isFailure)
    }

    @Test
    fun parse_missingApkUrl_returnsFailure() {
        val json = """
            {
                "versionCode": 2,
                "versionName": "1.0.1",
                "apkSha256": "abc"
            }
        """.trimIndent()

        val result = parser.parse(json)
        assertTrue(result.isFailure)
    }

    @Test
    fun parse_missingSha256_returnsFailure() {
        val json = """
            {
                "versionCode": 2,
                "versionName": "1.0.1",
                "apkUrl": "https://server.com/app.apk"
            }
        """.trimIndent()

        val result = parser.parse(json)
        assertTrue(result.isFailure)
    }

    @Test
    fun parse_invalidJsonSyntax_returnsFailure() {
        val result = parser.parse("{ invalid json content ...")
        assertTrue(result.isFailure)
    }
}
