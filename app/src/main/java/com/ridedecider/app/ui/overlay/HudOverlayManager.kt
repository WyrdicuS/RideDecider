package com.ridedecider.app.ui.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ridedecider.app.ui.overlay.components.HudCardOverlay
import com.ridedecider.app.ui.overlay.model.HudState
import com.ridedecider.app.ui.overlay.state.HudStateHolder
import com.ridedecider.app.ui.theme.RideDeciderTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Controlador de ciclo de vida y renderizado del HUD flotante mediante WindowManager.
 * Utiliza TYPE_APPLICATION_OVERLAY con banderas no intrusivas para permitir
 * la interacción normal y continua del conductor con la aplicación Uber Driver.
 */
class HudOverlayManager(private val context: Context) {

    companion object {
        private const val TAG = "HudOverlayManager"
        private const val SAFETY_TIMEOUT_MS = 6000L // Timeout de seguridad de 6s

        /**
         * Resuelve el tipo de ventana de WindowManager según la versión del SDK de Android.
         * En API 26+ (Android 8.0+) utiliza TYPE_APPLICATION_OVERLAY.
         * En API 24-25 (Android 7.0-7.1) utiliza TYPE_SYSTEM_ALERT como fallback compatible.
         */
        fun resolveWindowType(sdkVersion: Int = Build.VERSION.SDK_INT): Int {
            return if (sdkVersion >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            }
        }
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var composeView: ComposeView? = null
    private var isViewAttached = false
    private var safetyTimeoutRunnable: Runnable? = null
    private var observationJob: Job? = null

    // Lifecycle y SavedState propietarios para ComposeView en WindowManager
    private val overlayLifecycleOwner = object : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)
        private val viewModelStoreInstance = ViewModelStore()

        init {
            savedStateController.performRestore(Bundle())
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
        override val viewModelStore: ViewModelStore get() = viewModelStoreInstance

        fun destroy() {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            viewModelStoreInstance.clear()
        }
    }

    /**
     * Inicia la observación del estado del HUD y precalienta la ventana del overlay.
     */
    fun start() {
        if (observationJob != null) return

        // Precalentar la ventana en background de la UI para latencia cero
        ensureOverlayAttached()

        observationJob = scope.launch {
            HudStateHolder.state.collect { state ->
                handleState(state)
            }
        }
        Log.i(TAG, "HudOverlayManager iniciado y escuchando estado con ventana precalentada.")
    }

    private fun handleState(state: HudState) {
        when (state) {
            is HudState.Visible -> {
                showOverlay()
                resetSafetyTimeout()
            }
            is HudState.Hidden -> {
                cancelSafetyTimeout()
                hideOverlay()
            }
        }
    }

    private fun ensureOverlayAttached() {
        if (!canDrawOverlay() || windowManager == null) return

        mainHandler.post {
            try {
                if (composeView == null) {
                    composeView = createComposeView()
                }

                if (!isViewAttached && composeView != null) {
                    composeView?.visibility = View.INVISIBLE
                    composeView?.alpha = 0f
                    val params = createLayoutParams()
                    windowManager.addView(composeView, params)
                    isViewAttached = true
                    Log.d(TAG, "HUD Overlay pre-adjuntado al WindowManager con capa GPU precalentada (INVISIBLE + alpha=0).")
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo pre-adjuntar el overlay aún: ${e.message}")
            }
        }
    }

    private fun showOverlay() {
        if (!canDrawOverlay()) {
            Log.w(TAG, "No se puede mostrar el HUD: Falta permiso SYSTEM_ALERT_WINDOW.")
            return
        }
        if (windowManager == null) return

        mainHandler.post {
            try {
                if (!isViewAttached || composeView == null) {
                    ensureOverlayAttached()
                }
                composeView?.visibility = View.VISIBLE
                composeView?.animate()
                    ?.alpha(1f)
                    ?.setDuration(80L)
                    ?.start()
                Log.d(TAG, "HUD Overlay VISIBLE instantáneo con aceleración por hardware.")
            } catch (e: Exception) {
                Log.e(TAG, "Error mostrando HUD: ${e.message}", e)
            }
        }
    }

    private fun hideOverlay() {
        mainHandler.post {
            try {
                composeView?.animate()
                    ?.alpha(0f)
                    ?.setDuration(80L)
                    ?.withEndAction {
                        composeView?.visibility = View.INVISIBLE
                        Log.d(TAG, "HUD Overlay ocultado (INVISIBLE).")
                    }
                    ?.start()
            } catch (e: Exception) {
                Log.e(TAG, "Error ocultando HUD: ${e.message}", e)
            }
        }
    }

    private fun createComposeView(): ComposeView {
        return ComposeView(context).apply {
            setViewTreeLifecycleOwner(overlayLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)
            setViewTreeViewModelStoreOwner(overlayLifecycleOwner)

            setViewCompositionStrategy(
                androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
            )

            setContent {
                val currentState by HudStateHolder.state.collectAsState()
                RideDeciderTheme {
                    if (currentState is HudState.Visible) {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            HudCardOverlay(model = (currentState as HudState.Visible).data)
                        }
                    }
                }
            }
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val windowType = resolveWindowType()

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 140 // Espacio prudente bajo la barra de estado y controles superiores
        }
    }

    private fun resetSafetyTimeout() {
        cancelSafetyTimeout()
        safetyTimeoutRunnable = Runnable {
            Log.d(TAG, "Safety timeout alcanzado. Ocultando HUD.")
            HudStateHolder.hide()
        }
        safetyTimeoutRunnable?.let { mainHandler.postDelayed(it, SAFETY_TIMEOUT_MS) }
    }

    private fun cancelSafetyTimeout() {
        safetyTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        safetyTimeoutRunnable = null
    }

    fun canDrawOverlay(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * Limpia y destruye todos los recursos del overlay de forma segura.
     */
    fun destroy() {
        cancelSafetyTimeout()
        observationJob?.cancel()
        observationJob = null
        hideOverlay()
        composeView = null
        overlayLifecycleOwner.destroy()
        scope.cancel()
        Log.i(TAG, "HudOverlayManager destruido.")
    }
}
