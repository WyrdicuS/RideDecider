package com.ridedecider.app.data.update.parser

import com.ridedecider.app.domain.model.update.AppReleaseManifest

/**
 * Parser nativo, puro y ultraligero de manifiestos JSON de release (latest.json).
 * Diseñado en Kotlin puro sin dependencias externas para garantizar ejecución
 * determinista tanto en Android Runtime como en JVM Unit Tests.
 */
class JsonReleaseManifestParser {

    fun parse(jsonContent: String): Result<AppReleaseManifest> {
        return runCatching {
            val trimmed = jsonContent.trim()
            require(trimmed.startsWith("{") && trimmed.endsWith("}")) { "El contenido no es un objeto JSON válido" }

            val versionCode = extractInt(trimmed, "versionCode")
                ?: throw IllegalArgumentException("Campo 'versionCode' requerido y debe ser un número entero mayor que 0")
            require(versionCode > 0) { "Campo 'versionCode' requerido y debe ser mayor que 0" }

            val versionName = extractString(trimmed, "versionName")
                ?: throw IllegalArgumentException("Campo 'versionName' requerido no puede estar vacío")
            require(versionName.isNotBlank()) { "Campo 'versionName' requerido no puede estar vacío" }

            val apkUrl = extractString(trimmed, "apkUrl") ?: extractString(trimmed, "downloadUrl")
                ?: throw IllegalArgumentException("Campo 'apkUrl' o 'downloadUrl' requerido")
            require(apkUrl.isNotBlank()) { "Campo 'apkUrl' o 'downloadUrl' requerido" }

            val apkSha256 = extractString(trimmed, "apkSha256") ?: extractString(trimmed, "sha256")
                ?: throw IllegalArgumentException("Campo 'apkSha256' o 'sha256' requerido")
            require(apkSha256.isNotBlank()) { "Campo 'apkSha256' o 'sha256' requerido" }

            val minSupportedVersionCode = extractInt(trimmed, "minSupportedVersionCode") ?: 1
            val isMandatory = extractBoolean(trimmed, "mandatory") ?: extractBoolean(trimmed, "isMandatory") ?: false
            val apkSizeBytes = extractLong(trimmed, "apkSizeBytes") ?: extractLong(trimmed, "sizeBytes") ?: 0L
            val releaseDate = extractString(trimmed, "releaseDate")

            val releaseNotes = extractStringArray(trimmed, "releaseNotes").ifEmpty {
                extractStringArray(trimmed, "notes")
            }

            AppReleaseManifest(
                versionCode = versionCode,
                versionName = versionName,
                minSupportedVersionCode = minSupportedVersionCode,
                isMandatory = isMandatory,
                apkUrl = apkUrl,
                apkSha256 = apkSha256,
                apkSizeBytes = apkSizeBytes,
                releaseDate = releaseDate,
                releaseNotes = releaseNotes
            )
        }
    }

    private fun extractString(json: String, key: String): String? {
        val pattern = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"")
        return pattern.find(json)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun extractInt(json: String, key: String): Int? {
        val pattern = Regex("\"$key\"\\s*:\\s*(-?\\d+)")
        return pattern.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun extractLong(json: String, key: String): Long? {
        val pattern = Regex("\"$key\"\\s*:\\s*(-?\\d+)")
        return pattern.find(json)?.groupValues?.get(1)?.toLongOrNull()
    }

    private fun extractBoolean(json: String, key: String): Boolean? {
        val pattern = Regex("\"$key\"\\s*:\\s*(true|false)")
        return pattern.find(json)?.groupValues?.get(1)?.toBooleanStrictOrNull()
    }

    private fun extractStringArray(json: String, key: String): List<String> {
        val arrayPattern = Regex("\"$key\"\\s*:\\s*\\[([^\\]]*)\\]")
        val match = arrayPattern.find(json) ?: return emptyList()
        val arrayContent = match.groupValues[1]

        val itemPattern = Regex("\"([^\"]*)\"")
        return itemPattern.findAll(arrayContent).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()
    }
}
