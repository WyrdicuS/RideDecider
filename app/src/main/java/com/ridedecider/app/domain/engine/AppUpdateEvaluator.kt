package com.ridedecider.app.domain.engine

import com.ridedecider.app.domain.model.update.AppReleaseManifest
import com.ridedecider.app.domain.model.update.UpdateEvaluationResult

/**
 * Evaluador de reglas de negocio para determinar si un manifiesto de actualización es aplicable.
 * Es una función pura y desacoplada del framework de Android.
 */
class AppUpdateEvaluator {

    fun evaluate(
        installedVersionCode: Int,
        installedVersionName: String,
        manifest: AppReleaseManifest
    ): UpdateEvaluationResult {
        if (manifest.versionCode <= 0) {
            return UpdateEvaluationResult.IgnoredOrInvalid(
                reason = "El versionCode del manifiesto (${manifest.versionCode}) no es válido."
            )
        }

        if (manifest.apkUrl.isBlank()) {
            return UpdateEvaluationResult.IgnoredOrInvalid(
                reason = "La URL de descarga del APK está vacía."
            )
        }

        if (manifest.apkSha256.isBlank()) {
            return UpdateEvaluationResult.IgnoredOrInvalid(
                reason = "El hash criptográfico SHA-256 no está definido en el manifiesto."
            )
        }

        return if (manifest.versionCode > installedVersionCode) {
            UpdateEvaluationResult.UpdateAvailable(
                manifest = manifest,
                currentVersionCode = installedVersionCode,
                currentVersionName = installedVersionName
            )
        } else {
            UpdateEvaluationResult.UpToDate(
                currentVersionCode = installedVersionCode,
                currentVersionName = installedVersionName
            )
        }
    }
}
