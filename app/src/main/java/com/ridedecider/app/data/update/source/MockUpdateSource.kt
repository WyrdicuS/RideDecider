package com.ridedecider.app.data.update.source

/**
 * Fuente de actualizaciones simulada para pruebas locales y offline sin dependencias externas.
 */
class MockUpdateSource(
    var mockResponse: Result<String> = Result.failure(IllegalStateException("MockUpdateSource no configurado"))
) : UpdateSource {

    override suspend fun fetchManifest(): Result<String> {
        return mockResponse
    }
}
