package com.ridedecider.app.domain.manager

import android.content.Context
import com.ridedecider.app.BuildConfig
import com.ridedecider.app.data.update.AppUpdateChecker
import com.ridedecider.app.data.update.AppUpdateDownloader
import com.ridedecider.app.data.update.AppUpdateInstaller
import com.ridedecider.app.data.update.source.HttpUpdateSource
import com.ridedecider.app.data.update.source.UpdateSource
import com.ridedecider.app.domain.model.update.AppReleaseManifest
import com.ridedecider.app.domain.model.update.AppUpdateState
import com.ridedecider.app.domain.model.update.UpdateEvaluationResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

import com.ridedecider.app.data.update.UpdateConfig

/**
 * Gestor centralizado del ciclo de vida de actualizaciones privadas de RideDecider.
 * Coordina comprobación, descarga asíncrona, verificación e instalación.
 */
class AppUpdateManager(
    private val context: Context? = null,
    private var updateSource: UpdateSource = HttpUpdateSource(UpdateConfig.LATEST_MANIFEST_URL),
    private val downloader: AppUpdateDownloader = AppUpdateDownloader(context = context),
    private val installer: AppUpdateInstaller = AppUpdateInstaller(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {

    private val _updateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val updateState: StateFlow<AppUpdateState> = _updateState.asStateFlow()

    private var activeDownloadJob: Job? = null
    private var lastAvailableManifest: AppReleaseManifest? = null
    private var lastDownloadedApk: File? = null

    /**
     * Permite sustituir la fuente de actualización (ej. para pruebas locales con MockUpdateSource).
     */
    fun setUpdateSource(source: UpdateSource) {
        this.updateSource = source
    }

    /**
     * Comprueba si existe una nueva versión disponible.
     */
    fun checkForUpdates(
        installedVersionCode: Int = BuildConfig.VERSION_CODE,
        installedVersionName: String = BuildConfig.VERSION_NAME
    ) {
        scope.launch {
            _updateState.value = AppUpdateState.Checking

            val checker = AppUpdateChecker(
                updateSource = updateSource,
                ioDispatcher = ioDispatcher
            )
            val result = checker.checkForUpdate(installedVersionCode, installedVersionName)

            result.fold(
                onSuccess = { evaluation ->
                    when (evaluation) {
                        is UpdateEvaluationResult.UpdateAvailable -> {
                            lastAvailableManifest = evaluation.manifest
                            _updateState.value = AppUpdateState.Available(evaluation.manifest)
                        }
                        is UpdateEvaluationResult.UpToDate -> {
                            lastAvailableManifest = null
                            _updateState.value = AppUpdateState.UpToDate
                        }
                        is UpdateEvaluationResult.IgnoredOrInvalid -> {
                            _updateState.value = AppUpdateState.Error(evaluation.reason)
                        }
                    }
                },
                onFailure = { error ->
                    _updateState.value = AppUpdateState.Error(
                        message = error.localizedMessage ?: "No se pudo comprobar si hay actualizaciones.",
                        throwable = error
                    )
                }
            )
        }
    }

    /**
     * Inicia la descarga del APK disponible.
     */
    fun startDownload() {
        val manifest = lastAvailableManifest ?: return
        activeDownloadJob?.cancel()

        activeDownloadJob = scope.launch {
            _updateState.value = AppUpdateState.Downloading(
                progressPercentage = 0,
                bytesDownloaded = 0L,
                totalBytes = manifest.apkSizeBytes
            )

            val downloadResult = downloader.downloadApk(manifest) { progress, downloaded, total ->
                _updateState.value = AppUpdateState.Downloading(
                    progressPercentage = progress,
                    bytesDownloaded = downloaded,
                    totalBytes = total
                )
            }

            downloadResult.fold(
                onSuccess = { apkFile ->
                    lastDownloadedApk = apkFile
                    _updateState.value = AppUpdateState.Downloaded(apkFile, manifest)
                },
                onFailure = { error ->
                    if (error is kotlinx.coroutines.CancellationException) {
                        _updateState.value = AppUpdateState.Idle
                    } else {
                        _updateState.value = AppUpdateState.Error(
                            message = error.localizedMessage ?: "Error durante la descarga del APK",
                            throwable = error
                        )
                    }
                }
            )
        }
    }

    /**
     * Cancela la descarga en curso y limpia archivos temporales.
     */
    fun cancelDownload() {
        activeDownloadJob?.cancel()
        downloader.cleanUp()
        _updateState.value = AppUpdateState.Idle
    }

    /**
     * Lanza la instalación del APK descargado.
     */
    fun installApk(activityContext: Context): Result<Unit> {
        val apkFile = lastDownloadedApk ?: return Result.failure(IllegalStateException("No hay ningún APK descargado."))
        _updateState.value = AppUpdateState.Installing(apkFile)

        val result = installer.installApk(activityContext, apkFile)
        if (result.isFailure) {
            _updateState.value = AppUpdateState.Error(
                message = result.exceptionOrNull()?.localizedMessage ?: "No se pudo iniciar el instalador de Android"
            )
        }
        return result
    }

    /**
     * Comprueba permiso de instalación de fuentes desconocidas.
     */
    fun canRequestPackageInstalls(activityContext: Context): Boolean {
        return installer.canRequestPackageInstalls(activityContext)
    }

    /**
     * Obtiene el Intent para conceder permiso de fuentes desconocidas.
     */
    fun getManageUnknownAppSourcesIntent(activityContext: Context) = installer.getManageUnknownAppSourcesIntent(activityContext)

    /**
     * Cierra o restablece el estado.
     */
    fun resetState() {
        _updateState.value = AppUpdateState.Idle
    }
}
