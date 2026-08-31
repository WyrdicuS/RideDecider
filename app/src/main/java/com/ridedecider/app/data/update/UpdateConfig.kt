package com.ridedecider.app.data.update

/**
 * Configuración centralizada de endpoints para el servidor privado de actualizaciones de RideDecider.
 * Utiliza GitHub Releases público sin requerir credenciales ni tokens de autenticación.
 */
object UpdateConfig {
    const val GITHUB_REPO_OWNER = "WyrdicuS"
    const val GITHUB_REPO_NAME = "RideDecider"

    /** URL base del repositorio oficial */
    const val BASE_REPO_URL = "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME"

    /** Endpoint inmutable y estable que el cliente Android consulta para conocer la última versión */
    const val LATEST_MANIFEST_URL = "$BASE_REPO_URL/releases/latest/download/latest.json"

    /**
     * Genera la URL de descarga directa de un paquete APK para un tag de versión específico.
     * Ejemplo: https://github.com/WyrdicuS/RideDecider/releases/download/v1.0.1/RideDecider-1.0.1.apk
     */
    fun getReleaseApkUrl(versionName: String): String {
        return "$BASE_REPO_URL/releases/download/v$versionName/RideDecider-$versionName.apk"
    }
}
