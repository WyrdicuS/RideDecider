package com.ridedecider.app.data.update

import android.content.Context
import com.ridedecider.app.data.update.security.ApkIntegrityVerifier
import com.ridedecider.app.domain.model.update.AppReleaseManifest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Gestor de descarga por streaming de paquetes APK de actualización.
 * Garantiza aislamiento en directorio privado, seguimiento de progreso,
 * cancelación cooperativa, verificación criptográfica SHA-256 y limpieza automática.
 */
class AppUpdateDownloader(
    private val context: Context? = null,
    private val customCacheDir: File? = null,
    private val integrityVerifier: ApkIntegrityVerifier = ApkIntegrityVerifier(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val connectTimeoutMs: Int = 15000,
    private val readTimeoutMs: Int = 20000
) {

    private val updatesDirectory: File
        get() {
            val baseDir = customCacheDir ?: context?.cacheDir ?: File(System.getProperty("java.io.tmpdir") ?: ".")
            return File(baseDir, "updates").apply {
                if (!exists()) mkdirs()
            }
        }

    /**
     * Descarga el APK especificado en el manifiesto, reportando el progreso.
     */
    suspend fun downloadApk(
        manifest: AppReleaseManifest,
        onProgress: (progressPercentage: Int, downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _, _ -> }
    ): Result<File> = withContext(ioDispatcher) {
        val tempFile = File(updatesDirectory, "download.tmp")
        val finalApkFile = File(updatesDirectory, "ridedecider-update.apk")

        try {
            if (tempFile.exists()) tempFile.delete()
            if (finalApkFile.exists()) finalApkFile.delete()

            val url = URL(manifest.apkUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                requestMethod = "GET"
                setRequestProperty("User-Agent", "RideDecider-Android-UpdateDownloader")
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException("Error en la descarga del APK: HTTP $responseCode")
            }

            val contentLength = connection.contentLengthLong.takeIf { it > 0 } ?: manifest.apkSizeBytes

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesDownloaded = 0L
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (!coroutineContext.isActive) {
                            tempFile.delete()
                            throw kotlinx.coroutines.CancellationException("Descarga de actualización cancelada por el usuario.")
                        }

                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead

                        val progressPercent = if (contentLength > 0) {
                            ((bytesDownloaded * 100) / contentLength).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }

                        onProgress(progressPercent, bytesDownloaded, contentLength)
                    }
                    output.flush()
                }
            }

            // 1. Verificación obligatoria de integridad criptográfica SHA-256
            val isValid = integrityVerifier.verifyIntegrity(tempFile, manifest.apkSha256)
            if (!isValid) {
                tempFile.delete()
                val computedHash = integrityVerifier.calculateSha256(tempFile).getOrNull() ?: "desconocido"
                throw SecurityException(
                    "Fallo de integridad criptográfica: El SHA-256 del APK descargado no coincide con el manifiesto (Esperado: ${manifest.apkSha256}, Calculado: $computedHash)."
                )
            }

            // 2. Renombrar archivo verificado al nombre final para instalación
            if (tempFile.renameTo(finalApkFile)) {
                Result.success(finalApkFile)
            } else {
                tempFile.copyTo(finalApkFile, overwrite = true)
                tempFile.delete()
                Result.success(finalApkFile)
            }
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            Result.failure(e)
        }
    }

    /**
     * Limpia los archivos temporales de actualización.
     */
    fun cleanUp() {
        val tempFile = File(updatesDirectory, "download.tmp")
        val finalApkFile = File(updatesDirectory, "ridedecider-update.apk")
        if (tempFile.exists()) tempFile.delete()
        if (finalApkFile.exists()) finalApkFile.delete()
    }
}
