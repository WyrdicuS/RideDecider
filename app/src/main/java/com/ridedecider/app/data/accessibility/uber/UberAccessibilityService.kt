package com.ridedecider.app.data.accessibility.uber

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ridedecider.app.data.accessibility.uber.diagnostic.LogcatAccessibilityDiagnosticLogger
import com.ridedecider.app.data.di.ServiceLocator
import com.ridedecider.app.domain.model.TripEvaluation
import com.ridedecider.app.domain.model.opportunity.OpportunityAssessment
import com.ridedecider.app.ui.overlay.HudOverlayManager
import com.ridedecider.app.ui.overlay.state.HudStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Servicio de accesibilidad de Android para observar la interfaz de Uber Driver.
 *
 * Actúa como adaptador entre el framework de Android y el pipeline de dominio:
 * 1. Filtra eventos por paquete ([UberAccessibilityConstants.UBER_PACKAGE_NAME]).
 * 2. Aplica throttling y protección de reentrada para evitar bloqueos del hilo principal.
 * 3. Configura programáticamente [AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS],
 *    [AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS] y [AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS].
 * 4. Captura multi-fuente simultánea ([AccessibilityEvent.getSource], [AccessibilityService.getWindows],
 *    [AccessibilityService.getRootInActiveWindow]) para fusionar tarjetas emergentes modales y mapas.
 * 5. Delega la lógica de negocio, diagnóstico y procesamiento en [UberAccessibilityProcessor].
 */
class UberAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "RideDeciderDiag"

        /**
         * Bandera global para activar/desactivar la impresión estructurada del árbol de accesibilidad.
         */
        var TREE_DEBUG_ENABLED: Boolean = false

        /**
         * Bandera global para activar/desactivar el logging de diagnóstico del servicio.
         */
        var LOGGING_ENABLED: Boolean = true

        /**
         * Listener global opcional para conectar el servicio con capas superiores (ej. HUD o ViewModel).
         */
        var globalEvaluationListener: TripEvaluationListener? = null

        /** Intervalo mínimo entre procesamiento de eventos de contenido para respuesta inmediata (30 ms). */
        const val CONTENT_CHANGE_THROTTLE_MS: Long = 30L

        /** Intervalo de rate-limiting para logs de diagnóstico de eventos (500 ms). */
        const val LOG_THROTTLE_MS: Long = 500L
    }

    private val snapshotConverter = UberNodeSnapshotConverter()
    private val diagnosticLogger by lazy {
        LogcatAccessibilityDiagnosticLogger(
            isLoggingEnabled = LOGGING_ENABLED,
            isTreeDebugEnabled = TREE_DEBUG_ENABLED,
            tag = TAG,
            context = this
        )
    }
    private lateinit var processor: UberAccessibilityProcessor
    private var isProcessorInitialized: Boolean = false

    private var hudOverlayManager: HudOverlayManager? = null

    private val testReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == "com.ridedecider.app.TEST_HUD") {
                val isRadar = intent.getStringExtra("type") == "radar"
                val sampleTrip = com.ridedecider.app.domain.model.Trip(
                    id = "test_trip_${System.currentTimeMillis()}",
                    timestamp = System.currentTimeMillis(),
                    offerType = if (isRadar) com.ridedecider.app.domain.model.TripOfferType.RADAR_OFFER else com.ridedecider.app.domain.model.TripOfferType.TRIP_OFFER,
                    category = com.ridedecider.app.domain.model.UberCategory.UBER_X,
                    rawFare = if (isRadar) 4.10 else 8.50,
                    currency = "€",
                    pickupDistanceKm = if (isRadar) 3.8 else 1.2,
                    pickupDurationMinutes = if (isRadar) 8.0 else 3.0,
                    pickupAddress = if (isRadar) "Plaza Mayor" else "Calle Gran Vía 12",
                    tripDistanceKm = if (isRadar) 2.5 else 4.8,
                    tripDurationMinutes = if (isRadar) 10.0 else 12.0,
                    dropoffAddress = if (isRadar) "Paseo de la Castellana" else "Aeropuerto T4",
                    isCashPayment = isRadar,
                    passengerRating = if (isRadar) 4.70 else 4.95
                )
                val config = com.ridedecider.app.domain.model.ProfitabilityConfig(
                    costPerKm = 0.20,
                    costPerHour = 5.0,
                    minGrossHourlyRate = 20.0,
                    minGrossPerKmRate = 1.0,
                    minNetTripProfit = 2.0,
                    minNetHourlyRate = 12.0,
                    maxPickupDistanceKm = 5.0,
                    maxPickupTimeMinutes = 10.0
                )
                val evaluation = com.ridedecider.app.domain.usecase.EvaluateIncomingTripUseCase(
                    com.ridedecider.app.domain.engine.DecisionEngine()
                ).invoke(sampleTrip, config)
                HudStateHolder.emitEvaluation(evaluation)
                Log.i(TAG, "[TEST_HUD_TRIGGERED] Oferta simulada enviada al HUD (isRadar=$isRadar).")
            }
        }
    }

    // Control de tiempo y protección de reentrada
    private var lastContentChangeTimestamp: Long = 0L
    private var lastLogTimestamp: Long = 0L
    @Volatile
    private var isProcessing: Boolean = false

    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.packageNames = null
            info.flags = info.flags or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.notificationTimeout = 0
            serviceInfo = info
        } catch (e: Exception) {
            Log.w(TAG, "[SERVICE_CONNECTED] Error reforzando serviceInfo: ${e.message}")
        }

        Log.i(TAG, "[SERVICE_CONNECTED] RideDecider Accessibility Service conectado con flags reforzados.")
        syncDiagnosticFlags()

        ServiceLocator.getAccessibilityServiceStateTracker(this).onConnected()

        hudOverlayManager = HudOverlayManager(this).also { it.start() }

        val configProvider = com.ridedecider.app.data.di.ServiceLocator.getProfitabilityConfigProvider(this)
        val goalsRepo = com.ridedecider.app.data.di.ServiceLocator.getDriverGoalsRepository(this)
        val earningsTracker = com.ridedecider.app.data.di.ServiceLocator.getEarningsTracker(this)

        processor = UberAccessibilityProcessor(
            diagnosticLogger = diagnosticLogger,
            configProvider = configProvider,
            economicContextProvider = {
                val ctx = earningsTracker.cachedContext
                if (ctx != null) {
                    val now = System.currentTimeMillis()
                    val (dayStartNow, _) = earningsTracker.getDayBounds(now)
                    val (dayStartCtx, _) = earningsTracker.getDayBounds(ctx.timestamp)
                    if (dayStartNow != dayStartCtx) {
                        serviceScope.launch { earningsTracker.refreshEconomicContext() }
                        null
                    } else {
                        ctx
                    }
                } else {
                    null
                }
            }
        )
        isProcessorInitialized = true

        serviceScope.launch {
            try {
                earningsTracker.refreshEconomicContext()
            } catch (e: Exception) {
                Log.w(TAG, "Error initializing cached economic context: ${e.message}")
            }
        }

        serviceScope.launch {
            goalsRepo.goalsFlow.collect { goals ->
                Log.i(TAG, "[GOALS_SYNCED] Objetivo Único Activo [${goals.activePeriod}]: Meta=${goals.activeTargetEur.toInt()}€ en ${goals.activePlannedHours.toInt()}h -> ${goals.activeHourlyTarget}€/h.")
                try {
                    earningsTracker.refreshEconomicContext()
                } catch (e: Exception) {
                    Log.w(TAG, "[GOALS_SYNCED] Error refreshing economic context: ${e.message}")
                }
            }
        }

        // R5: HudStateHolder mantiene una copia derivada del DecisionMode activo, sincronizada
        // con la unica fuente de verdad persistida (DecisionModeRepository).
        val decisionModeRepository = com.ridedecider.app.data.di.ServiceLocator.getDecisionModeRepository(this)
        serviceScope.launch {
            decisionModeRepository.modeFlow.collect { mode ->
                HudStateHolder.setDecisionMode(mode)
            }
        }

        try {
            val filter = android.content.IntentFilter("com.ridedecider.app.TEST_HUD")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(testReceiver, filter, android.content.Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(testReceiver, filter)
            }
            Log.i(TAG, "[SERVICE_CONNECTED] BroadcastReceiver TEST_HUD registrado.")
        } catch (e: Exception) {
            Log.w(TAG, "Error registrando testReceiver: ${e.message}")
        }

        val hudEvaluationListener = object : TripEvaluationListener {
            // Cache sincrono de la ultima TripEvaluation: el processor invoca onTripEvaluation()
            // e inmediatamente despues onOpportunityAssessment() para el mismo ciclo de evaluacion
            // (mismo hilo, sin reentrada posible). Se emite al HUD una vez se tienen ambos.
            private var pendingEvaluation: TripEvaluation? = null

            override fun onTripEvaluation(evaluation: TripEvaluation) {
                globalEvaluationListener?.onTripEvaluation(evaluation)
                pendingEvaluation = evaluation
                serviceScope.launch {
                    try {
                        earningsTracker.recordEvaluatedOffer(evaluation.trip, evaluation)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error recording trip evaluation in room: ${e.message}")
                    }
                }
            }

            override fun onOpportunityAssessment(assessment: OpportunityAssessment) {
                globalEvaluationListener?.onOpportunityAssessment(assessment)
                val evaluation = pendingEvaluation ?: return
                HudStateHolder.emitEvaluation(evaluation, assessment)
            }

            override fun onScreenStateChanged(screenType: UberOfferScreenType) {
                globalEvaluationListener?.onScreenStateChanged(screenType)
                when (screenType) {
                    UberOfferScreenType.NO_OFFER -> {
                        // Ocultar de inmediato al desaparecer la tarjeta real (100ms de micro-buffer para estabilidad visual)
                        HudStateHolder.hideWithDelay(100L)
                    }
                    UberOfferScreenType.ACTIVE_TRIP,
                    UberOfferScreenType.TRIP_CANCELLED,
                    UberOfferScreenType.TRIP_COMPLETED -> {
                        // Si se acepta el viaje, se cancela o se completa, ocultar de inmediato
                        HudStateHolder.hideImmediately()
                    }
                    else -> {}
                }
                serviceScope.launch {
                    try {
                        when (screenType) {
                            UberOfferScreenType.NO_OFFER -> {
                                earningsTracker.onOfferDismissed()
                            }
                            UberOfferScreenType.ACTIVE_TRIP -> {
                                earningsTracker.markActiveTripStarted()
                            }
                            else -> {}
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating lifecycle state in room: ${e.message}")
                    }
                }
            }

            override fun onTripCancelled(reason: com.ridedecider.app.domain.model.CancellationReason, feeEur: Double?) {
                globalEvaluationListener?.onTripCancelled(reason, feeEur)
                HudStateHolder.hideImmediately()
                serviceScope.launch {
                    try {
                        val tripId = earningsTracker.currentTripId
                        if (tripId != null) {
                            earningsTracker.cancelTrip(
                                tripId = tripId,
                                reason = reason,
                                cancellationFee = feeEur
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error recording trip cancellation in room: ${e.message}")
                    }
                }
            }

            override fun onTripCompleted(finalFareEur: Double?) {
                globalEvaluationListener?.onTripCompleted(finalFareEur)
                HudStateHolder.hideImmediately()
                serviceScope.launch {
                    try {
                        val tripId = earningsTracker.currentTripId
                        if (tripId != null) {
                            val activeTrip = (earningsTracker.stateMachine.currentState as? com.ridedecider.app.domain.model.TripLifecycleState.ActiveTrip)?.trip
                            val fare = finalFareEur ?: activeTrip?.rawFare ?: 0.0
                            val duration = activeTrip?.tripDurationMinutes ?: 0.0
                            earningsTracker.completeTrip(
                                tripId = tripId,
                                finalEarnings = fare,
                                durationMinutes = duration
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error recording trip completion in room: ${e.message}")
                    }
                }
            }
        }
        processor.evaluationListener = hudEvaluationListener
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString()

        // 1. Filtrar eventos: Descartar inmediatamente systemui
        if (packageName != null && packageName.startsWith("com.android.systemui")) {
            return
        }

        val eventType = event.eventType
        val isPriorityEvent = eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
                eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED

        // 2. Detección inmediata de pulsación sobre Aceptar, Emparejar, Rechazar o botón X (Cerrar / Cancelar)
        if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED || eventType == AccessibilityEvent.TYPE_VIEW_SELECTED) {
            val text = event.text?.joinToString(" ") { it.toString() } ?: ""
            val desc = event.contentDescription?.toString() ?: ""
            val resId = try { event.source?.viewIdResourceName ?: "" } catch (_: Exception) { "" }
            val srcDesc = try { event.source?.contentDescription?.toString() ?: "" } catch (_: Exception) { "" }
            val srcText = try { event.source?.text?.toString() ?: "" } catch (_: Exception) { "" }

            val clickInfo = "$text $desc $resId $srcDesc $srcText".lowercase().trim()

            val isActionClick = clickInfo.contains("aceptar") || clickInfo.contains("accept") ||
                    clickInfo.contains("emparejar") || clickInfo.contains("match") ||
                    clickInfo.contains("rechazar") || clickInfo.contains("decline") ||
                    clickInfo.contains("cerrar") || clickInfo.contains("close") ||
                    clickInfo.contains("cancelar") || clickInfo.contains("cancel") ||
                    clickInfo.contains("descartar") || clickInfo.contains("dismiss") ||
                    clickInfo.contains("cross") || clickInfo.contains("reject") ||
                    clickInfo.contains("btn_close") || clickInfo.contains("button_close") ||
                    clickInfo.contains("action_close") || clickInfo.contains("icon_close") ||
                    clickInfo.contains("ub__close") || clickInfo.contains("ub__action_close") ||
                    text.trim() == "✕" || text.trim().equals("x", ignoreCase = true) ||
                    desc.trim() == "✕" || desc.trim().equals("x", ignoreCase = true) ||
                    srcText.trim() == "✕" || srcText.trim().equals("x", ignoreCase = true) ||
                    srcDesc.trim() == "✕" || srcDesc.trim().equals("x", ignoreCase = true)

            if (isActionClick) {
                diagnosticLogger.logEvent("OFFER_ACTION_CLICKED", "Pulsado [$clickInfo] -> Ocultando HUD de inmediato.")
                HudStateHolder.hideImmediately()
            }
        }

        val now = System.currentTimeMillis()

        // 3. Throttling inteligente: Eventos prioritarios (cambio de ventana) se despachan inmediatamente a 0ms
        if (!isPriorityEvent) {
            if (now - lastContentChangeTimestamp < 16L) { // 1 frame @ 60fps
                return
            }
        }
        lastContentChangeTimestamp = now

        // 4. Protección de reentrada
        if (isProcessing) {
            return
        }

        isProcessing = true
        try {
            val snapshot = captureUberSnapshot(event, isPriorityEvent)

            if (snapshot == null) {
                if (packageName?.startsWith("com.ubercab") == true) {
                    serviceScope.launch {
                        kotlinx.coroutines.delay(35L)
                        val retrySnap = captureUberSnapshot(event, true)
                        if (retrySnap != null) {
                            try {
                                processor.processSnapshot(retrySnap, packageName, currentTime = System.currentTimeMillis())
                            } catch (e: Exception) {
                                Log.e(TAG, "Error en retry snapshot: ${e.message}")
                            }
                        }
                    }
                }
                return
            }

            syncDiagnosticFlags()

            // 5. Procesar el snapshot a través del pipeline
            try {
                val result = processor.processSnapshot(snapshot, packageName ?: UberAccessibilityConstants.UBER_PACKAGE_NAME, currentTime = now)
                if (result is UberAccessibilityProcessor.ProcessResult.RequiresOcrFallback) {
                    executeOcrFallback()
                }
            } catch (e: Exception) {
                diagnosticLogger.logEvent("PROCESSOR_ERROR", "Error no controlado en el pipeline: ${e.message}")
            }
        } finally {
            isProcessing = false
        }
    }

    /**
     * Captura el árbol de nodos de Uber de forma instantánea (< 0.2 ms)
     * priorizando la raíz de ventana activa en cambios de estado o la lista de ventanas interactivas.
     */
    private fun captureUberSnapshot(event: AccessibilityEvent, isPriority: Boolean): UberNodeSnapshot? {
        // 1. Si es cambio de estado de ventana o evento prioritario, leer directamente rootInActiveWindow
        if (isPriority) {
            val activeRoot = try { rootInActiveWindow } catch (_: Exception) { null }
            if (activeRoot != null) {
                val pkg = try { activeRoot.packageName?.toString() } catch (_: Exception) { null }
                if (pkg == null || pkg.startsWith("com.ubercab") || pkg == "android") {
                    val snap = snapshotConverter.createSnapshot(activeRoot)
                    if (snap != null) return snap
                }
            }
        }

        // 2. Prioridad: Revisar ventanas interactivas para capturar modal/popup de oferta
        val windowList = try { windows } catch (_: Exception) { null }
        if (!windowList.isNullOrEmpty()) {
            for (win in windowList) {
                val winRoot = try { win.root } catch (_: Exception) { null }
                if (winRoot != null) {
                    val pkg = try { winRoot.packageName?.toString() } catch (_: Exception) { null }
                    if (pkg == null || pkg.startsWith("com.ubercab") || pkg == "android") {
                        val snap = snapshotConverter.createSnapshot(winRoot)
                        if (snap != null) {
                            val hasOfferSignals = snap.flatten().any { n ->
                                val text = (n.text ?: "").lowercase()
                                val desc = (n.contentDescription ?: "").lowercase()
                                text.contains("€") || text.contains("$") || text.contains("aceptar") ||
                                        text.contains("emparejar") || text.contains("radar") ||
                                        desc.contains("aceptar") || desc.contains("emparejar")
                            }
                            if (hasOfferSignals) {
                                return snap
                            }
                        }
                    }
                }
            }
        }

        // 3. Fallback: Capturar la ventana activa completa
        val activeRoot = try { rootInActiveWindow } catch (_: Exception) { null }
        if (activeRoot != null) {
            val pkg = try { activeRoot.packageName?.toString() } catch (_: Exception) { null }
            if (pkg == null || pkg.startsWith("com.ubercab") || pkg == "android") {
                val snap = snapshotConverter.createSnapshot(activeRoot)
                if (snap != null) return snap
            }
        }

        // 4. Fallback: Capturar la raíz del origen del evento (event.source)
        val sourceNode = try { event.source } catch (_: Exception) { null }
        if (sourceNode != null) {
            var current: AccessibilityNodeInfo = sourceNode
            while (true) {
                val parent = try { current.parent } catch (_: Exception) { null }
                if (parent != null) {
                    current = parent
                } else {
                    break
                }
            }
            return snapshotConverter.createSnapshot(current)
        }

        return null
    }

    private fun syncDiagnosticFlags() {
        diagnosticLogger.isLoggingEnabled = LOGGING_ENABLED
        diagnosticLogger.isTreeDebugEnabled = TREE_DEBUG_ENABLED
    }

    private fun executeOcrFallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        val startCapTime = System.currentTimeMillis()
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        val capLatency = System.currentTimeMillis() - startCapTime
                        val hardwareBuffer = screenshotResult.hardwareBuffer
                        val colorSpace = screenshotResult.colorSpace
                        val bitmap = try {
                            Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error convirtiendo hardware buffer a Bitmap: ${e.message}")
                            null
                        } finally {
                            try { hardwareBuffer.close() } catch (_: Exception) {}
                        }

                        if (bitmap == null) return

                        serviceScope.launch {
                            try {
                                val ocrEngine = ServiceLocator.getUberOfferOcrFallback()
                                val ocrResult = ocrEngine.processImage(
                                    bitmap = bitmap,
                                    screenshotLatencyMs = capLatency
                                )
                                processor.processOcrResult(ocrResult)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error procesando OCR fallback: ${e.message}")
                            } finally {
                                try {
                                    if (!bitmap.isRecycled) bitmap.recycle()
                                } catch (_: Exception) {}
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.w(TAG, "Fallo en takeScreenshot() de accesibilidad: errorCode=$errorCode")
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al solicitar takeScreenshot(): ${e.message}")
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "[SERVICE_INTERRUPTED] Servicio de accesibilidad interrumpido.")
        ServiceLocator.getAccessibilityServiceStateTracker(this).onInterrupted()
        if (isProcessorInitialized) {
            processor.reset()
        }
        isProcessing = false
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "[SERVICE_DESTROYED] Servicio de accesibilidad destruido.")
        ServiceLocator.getAccessibilityServiceStateTracker(this).onDestroyed(this)
        try {
            unregisterReceiver(testReceiver)
        } catch (_: Exception) {}
        hudOverlayManager?.destroy()
        hudOverlayManager = null
        HudStateHolder.hide()
        if (isProcessorInitialized) {
            processor.reset()
            processor.evaluationListener = null
        }
        isProcessing = false
    }
}
