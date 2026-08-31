package com.ridedecider.app.data.update.security

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Verificador criptográfico de integridad para archivos APK descargados.
 * Calcula el digest SHA-256 mediante streaming para optimizar consumo de memoria.
 */
class ApkIntegrityVerifier {

    /**
     * Calcula el hash SHA-256 en minúsculas de un archivo local.
     */
    fun calculateSha256(file: File): Result<String> {
        return runCatching {
            require(file.exists() && file.isFile) { "El archivo no existe o es un directorio: ${file.absolutePath}" }
            require(file.length() > 0) { "El archivo está vacío (0 bytes): ${file.absolutePath}" }

            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)

            FileInputStream(file).use { fis ->
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }

            digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    /**
     * Comprueba si el archivo coincide exactamente con el hash SHA-256 esperado.
     */
    fun verifyIntegrity(file: File, expectedSha256: String): Boolean {
        if (expectedSha256.isBlank()) return false
        val computedResult = calculateSha256(file)
        val computedHash = computedResult.getOrNull() ?: return false
        return computedHash.equals(expectedSha256.trim(), ignoreCase = true)
    }
}
