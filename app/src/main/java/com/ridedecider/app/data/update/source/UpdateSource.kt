package com.ridedecider.app.data.update.source

/**
 * Interfaz para la obtención del manifiesto de actualizaciones (latest.json).
 * Permite alternar limpiamente entre fuentes remotas HTTP/HTTPS y fuentes de pruebas (Mock).
 */
interface UpdateSource {
    suspend fun fetchManifest(): Result<String>
}
