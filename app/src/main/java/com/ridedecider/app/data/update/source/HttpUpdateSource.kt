package com.ridedecider.app.data.update.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

import com.ridedecider.app.data.update.UpdateConfig

/**
 * Proveedor de actualizaciones remoto mediante peticiones HTTPS directas sin dependencias pesadas.
 */
class HttpUpdateSource(
    private val manifestUrl: String = UpdateConfig.LATEST_MANIFEST_URL,
    private val connectTimeoutMs: Int = 10000,
    private val readTimeoutMs: Int = 10000
) : UpdateSource {

    override suspend fun fetchManifest(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(manifestUrl.isNotBlank()) { "La URL del manifiesto no puede estar vacía" }
            val url = URL(manifestUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                requestMethod = "GET"
                setRequestProperty("User-Agent", "RideDecider-Android-OTAClient")
                setRequestProperty("Accept", "application/json")
            }

            try {
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw IllegalStateException("El servidor respondió con código HTTP $responseCode")
                }

                BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            } finally {
                connection.disconnect()
            }
        }
    }
}
