package com.ridedecider.app.domain.model.update

/**
 * Manifiesto inmutable que describe una versión de lanzamiento de RideDecider.
 * Contiene metadatos de versión, URL de descarga, tamaño y hash criptográfico SHA-256.
 */
data class AppReleaseManifest(
    val versionCode: Int,
    val versionName: String,
    val minSupportedVersionCode: Int = 1,
    val isMandatory: Boolean = false,
    val apkUrl: String,
    val apkSha256: String,
    val apkSizeBytes: Long = 0L,
    val releaseDate: String? = null,
    val releaseNotes: List<String> = emptyList()
)
