package com.ridedecider.app.domain.manager

import com.ridedecider.app.data.update.AppUpdateDownloader
import com.ridedecider.app.data.update.source.MockUpdateSource
import com.ridedecider.app.domain.model.update.AppUpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var mockSource: MockUpdateSource
    private lateinit var updateManager: AppUpdateManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockSource = MockUpdateSource()
        val customDownloader = AppUpdateDownloader(
            customCacheDir = tempFolder.root,
            ioDispatcher = testDispatcher
        )

        updateManager = AppUpdateManager(
            context = null,
            updateSource = mockSource,
            downloader = customDownloader,
            ioDispatcher = testDispatcher,
            scope = testScope
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun checkForUpdates_newVersion_transitionsToAvailable() {
        val json = """
            {
                "versionCode": 2,
                "versionName": "1.0.1",
                "apkUrl": "https://updates.ridedecider.com/app.apk",
                "apkSha256": "2e10214ef49e776501761e65f6abc43b9dd42a239e8256d28aad9a52794dfc62",
                "releaseNotes": ["Novedad 1"]
            }
        """.trimIndent()
        mockSource.mockResponse = Result.success(json)

        updateManager.checkForUpdates(installedVersionCode = 1, installedVersionName = "1.0.0")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = updateManager.updateState.value
        assertTrue(state is AppUpdateState.Available)
        val available = state as AppUpdateState.Available
        assertEquals(2, available.manifest.versionCode)
        assertEquals("1.0.1", available.manifest.versionName)
        assertEquals(1, available.manifest.releaseNotes.size)
    }

    @Test
    fun checkForUpdates_sameVersion_transitionsToUpToDate() {
        val json = """
            {
                "versionCode": 1,
                "versionName": "1.0.0",
                "apkUrl": "https://updates.ridedecider.com/app.apk",
                "apkSha256": "abc"
            }
        """.trimIndent()
        mockSource.mockResponse = Result.success(json)

        updateManager.checkForUpdates(installedVersionCode = 1, installedVersionName = "1.0.0")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = updateManager.updateState.value
        assertTrue(state is AppUpdateState.UpToDate)
    }

    @Test
    fun checkForUpdates_offlineOrNetworkError_transitionsToError() {
        mockSource.mockResponse = Result.failure(IOException("Sin conexión de red"))

        updateManager.checkForUpdates(installedVersionCode = 1, installedVersionName = "1.0.0")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = updateManager.updateState.value
        assertTrue(state is AppUpdateState.Error)
    }

    @Test
    fun resetState_resetsStateToIdle() {
        updateManager.resetState()
        assertEquals(AppUpdateState.Idle, updateManager.updateState.value)
    }

    @Test
    fun cancelDownload_cleansUpAndReturnsToIdle() {
        updateManager.cancelDownload()
        assertEquals(AppUpdateState.Idle, updateManager.updateState.value)
    }
}
