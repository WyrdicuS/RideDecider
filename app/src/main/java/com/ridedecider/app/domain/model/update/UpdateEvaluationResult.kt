package com.ridedecider.app.domain.model.update

/**
 * Resultado de la evaluación de un manifiesto de versión respecto a la versión instalada.
 */
sealed class UpdateEvaluationResult {
    data class UpdateAvailable(
        val manifest: AppReleaseManifest,
        val currentVersionCode: Int,
        val currentVersionName: String
    ) : UpdateEvaluationResult()

    data class UpToDate(
        val currentVersionCode: Int,
        val currentVersionName: String
    ) : UpdateEvaluationResult()

    data class IgnoredOrInvalid(
        val reason: String
    ) : UpdateEvaluationResult()
}
