package com.ridedecider.app.data.update

import com.ridedecider.app.data.update.parser.JsonReleaseManifestParser
import com.ridedecider.app.data.update.source.UpdateSource
import com.ridedecider.app.domain.engine.AppUpdateEvaluator
import com.ridedecider.app.domain.model.update.UpdateEvaluationResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Orquestador para la comprobación de nuevas versiones disponibles de RideDecider.
 * Conecta el proveedor de red (UpdateSource), el parser y el evaluador de negocio.
 */
class AppUpdateChecker(
    private val updateSource: UpdateSource,
    private val parser: JsonReleaseManifestParser = JsonReleaseManifestParser(),
    private val evaluator: AppUpdateEvaluator = AppUpdateEvaluator(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun checkForUpdate(
        installedVersionCode: Int,
        installedVersionName: String
    ): Result<UpdateEvaluationResult> = withContext(ioDispatcher) {
        runCatching {
            val jsonResult = updateSource.fetchManifest()
            val jsonContent = jsonResult.getOrThrow()

            val manifestResult = parser.parse(jsonContent)
            val manifest = manifestResult.getOrThrow()

            evaluator.evaluate(
                installedVersionCode = installedVersionCode,
                installedVersionName = installedVersionName,
                manifest = manifest
            )
        }
    }
}
