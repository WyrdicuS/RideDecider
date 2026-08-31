package com.ridedecider.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Instalador nativo de paquetes APK para RideDecider.
 * Gestiona permisos de fuentes desconocidas, obtención de URIs seguras mediante FileProvider
 * y delegación al instalador oficial de Android.
 */
class AppUpdateInstaller {

    /**
     * Comprueba si la aplicación tiene permiso para solicitar la instalación de paquetes.
     * En Android 8.0+ (API 26+) consulta canRequestPackageInstalls().
     */
    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Genera el Intent para abrir la pantalla de ajustes de fuentes desconocidas para RideDecider.
     */
    fun getManageUnknownAppSourcesIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Lanza el instalador nativo del sistema operativo Android para el APK especificado.
     */
    fun installApk(context: Context, apkFile: File): Result<Unit> {
        return runCatching {
            require(apkFile.exists() && apkFile.isFile) { "El archivo APK no existe: ${apkFile.absolutePath}" }
            require(apkFile.length() > 0) { "El archivo APK está vacío" }

            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
        }
    }
}
