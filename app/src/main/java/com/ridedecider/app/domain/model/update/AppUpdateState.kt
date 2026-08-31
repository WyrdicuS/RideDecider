package com.ridedecider.app.domain.model.update

import java.io.File

/**
 * Estados del ciclo de vida del flujo de actualización privada.
 */
sealed class AppUpdateState {
    object Idle : AppUpdateState()
    object Checking : AppUpdateState()
    data class Available(val manifest: AppReleaseManifest) : AppUpdateState()
    object UpToDate : AppUpdateState()
    data class Downloading(
        val progressPercentage: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : AppUpdateState()
    data class Downloaded(val apkFile: File, val manifest: AppReleaseManifest) : AppUpdateState()
    data class Installing(val apkFile: File) : AppUpdateState()
    data class Error(val message: String, val throwable: Throwable? = null) : AppUpdateState()
}
