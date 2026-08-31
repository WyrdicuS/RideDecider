package com.ridedecider.app.data.update

import com.ridedecider.app.data.update.source.MockUpdateSource
import com.ridedecider.app.domain.model.update.UpdateEvaluationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class AppUpdateCheckerTest {

    private lateinit var mockSource: MockUpdateSource
    private lateinit var checker: AppUpdateChecker

    @Before
    fun setUp() {
        mockSource = MockUpdateSource()
        checker = AppUpdateChecker(updateSource = mockSource)
    }

    @Test
    fun checkForUpdate_newVersionAvailable_returnsUpdateAvailable() = runBlocking {
        val json = """
            {
                "versionCode": 2,
                "versionName": "1.0.1",
                "apkUrl": "https://updates.ridedecider.com/ridedecider-1.0.1.apk",
                "apkSha256": "2e10214ef49e776501761e65f6abc43b9dd42a239e8256d28aad9a52794dfc62",
                "releaseNotes": ["Correcciones menores"]
            }
        """.trimIndent()

        mockSource.mockResponse = Result.success(json)

        val result = checker.checkForUpdate(installedVersionCode = 1, installedVersionName = "1.0.0")
        assertTrue(result.isSuccess)

        val evaluation = result.getOrThrow()
        assertTrue(evaluation is UpdateEvaluationResult.UpdateAvailable)
        val available = evaluation as UpdateEvaluationResult.UpdateAvailable
        assertEquals(2, available.manifest.versionCode)
        assertEquals("1.0.1", available.manifest.versionName)
    }

    @Test
    fun checkForUpdate_sameVersion_returnsUpToDate() = runBlocking {
        val json = """
            {
                "versionCode": 1,
                "versionName": "1.0.0",
                "apkUrl": "https://updates.ridedecider.com/ridedecider-1.0.0.apk",
                "apkSha256": "abc123"
            }
        """.trimIndent()

        mockSource.mockResponse = Result.success(json)

        val result = checker.checkForUpdate(installedVersionCode = 1, installedVersionName = "1.0.0")
        assertTrue(result.isSuccess)

        val evaluation = result.getOrThrow()
        assertTrue(evaluation is UpdateEvaluationResult.UpToDate)
    }

    @Test
    fun checkForUpdate_networkError_returnsFailureWithoutCrashing() = runBlocking {
        mockSource.mockResponse = Result.failure(IOException("No connection available"))

        val result = checker.checkForUpdate(installedVersionCode = 1, installedVersionName = "1.0.0")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun checkForUpdate_malformedJson_returnsFailure() = runBlocking {
        mockSource.mockResponse = Result.success("{ not a valid json")

        val result = checker.checkForUpdate(installedVersionCode = 1, installedVersionName = "1.0.0")
        assertTrue(result.isFailure)
    }
}
