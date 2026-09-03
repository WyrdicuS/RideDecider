package com.ridedecider.app.data.accessibility.uber

import android.os.Build
import com.ridedecider.app.data.accessibility.uber.diagnostic.AccessibilityDiagnosticLogger
import com.ridedecider.app.data.accessibility.uber.diagnostic.DiagnosticMetrics
import com.ridedecider.app.data.accessibility.uber.diagnostic.UberFullNodeDumper
import com.ridedecider.app.data.accessibility.uber.diagnostic.UberOfferLightDiagnostic
import com.ridedecider.app.data.accessibility.uber.diagnostic.UberTreeDumper
import com.ridedecider.app.data.accessibility.uber.ocr.OcrResult
import com.ridedecider.app.domain.engine.DecisionEngine
import com.ridedecider.app.domain.model.TripEvaluation
import com.ridedecider.app.domain.repository.ProfitabilityConfigProvider
import com.ridedecider.app.domain.usecase.EvaluateIncomingTripUseCase
import java.util.Locale

/**
 * Procesador puro en Kotlin que coordina el pipeline completo de análisis de accesibilidad:
 * 1. Filtrado por nombre de paquete.
 * 2. Diagnóstico ligero no invasivo con throttling ([UberOfferLightDiagnostic]).
 * 3. Volcado inmediato extraordinario ante saltos estructurales bruscos (ej. entrada/salida de tarjetas de 5s).
 * 4. Volcado exhaustivo rate-limited de 5 segundos ([UberFullNodeDumper]).
 * 5. Extracción textual ([UberAccessibilityParser]).
 * 6. Validación y clasificación ([UberOfferValidator]).
 * 7. Captura inmediata extraordinaria [OFFER_TRANSITION_FULL_DUMP] ante transiciones a oferta/radar.
 * 8. Control de estabilidad y debounce por firma de oferta.
 * 9. Mapeo a entidad de dominio ([RawUberTripOfferMapper]).
 * 10. Evaluación económica ([EvaluateIncomingTripUseCase]).
 * 11. Notificación a [TripEvaluationListener] y registro de diagnóstico mediante [AccessibilityDiagnosticLogger].
 */
class UberAccessibilityProcessor(
    private val parser: UberAccessibilityParser = UberAccessibilityParser(),
    private val validator: UberOfferValidator = UberOfferValidator(),
    private val mapper: RawUberTripOfferMapper = RawUberTripOfferMapper(),
    private val evaluateUseCase: EvaluateIncomingTripUseCase = EvaluateIncomingTripUseCase(DecisionEngine()),
    private val configProvider: ProfitabilityConfigProvider = InMemoryProfitabilityConfigProvider(),
    var evaluationListener: TripEvaluationListener? = null,
    private val debounceIntervalMs: Long = UberAccessibilityConstants.DEFAULT_DEBOUNCE_INTERVAL_MS,
    var diagnosticLogger: AccessibilityDiagnosticLogger? = null,
    val metrics: DiagnosticMetrics = DiagnosticMetrics()
) {

    /**
     * Estado actual clasificado de la pantalla de Uber.
     */
    var currentScreenType: UberOfferScreenType = UberOfferScreenType.UNKNOWN
        private set

    private var lastSignature: String? = null
    private var consumedOfferSignature: String? = null
    private var lastEvaluationTimestamp: Long = 0L

    // Throttling del diagnóstico ligero (150 ms)
    private val diagnosticThrottleIntervalMs: Long = 150L
    private var lastDiagnosticTimestamp: Long = -diagnosticThrottleIntervalMs

    // Detección de cambios estructurales bruscos (ej. 128 -> 27-39 nodos al saltar la tarjeta)
    private var lastNodeCount: Int = 0
    private var lastStructuralDumpTimestamp: Long = 0L
    private val structuralDumpCooldownMs: Long = 500L

    // Cooldown para el volcado exhaustivo [FULL_NODE_DUMP] periódico (5000 ms)
    private val fullDumpCooldownMs: Long = 5000L
    private var lastFullDumpTimestamp: Long = -fullDumpCooldownMs

    // Cooldown para el disparador de Fallback OCR (2000 ms)
    private val ocrDebounceIntervalMs: Long = 2000L
    private var lastOcrAttemptTimestamp: Long = -ocrDebounceIntervalMs

    /**
     * Procesa un snapshot de accesibilidad y ejecuta el pipeline de decisión si corresponde.
     *
     * @param snapshot Árbol de nodos inmutable capturado.
     * @param packageName Paquete origen del evento de accesibilidad.
     * @param currentTime Timestamp del evento (para facilitar pruebas de debounce).
     * @return [ProcessResult] con el resultado de la operación.
     */
    fun processSnapshot(
        snapshot: UberNodeSnapshot?,
        packageName: CharSequence? = UberAccessibilityConstants.UBER_PACKAGE_NAME,
        currentTime: Long = System.currentTimeMillis()
    ): ProcessResult {
        metrics.eventsReceived.incrementAndGet()

        // 1. Filtrar por paquete
        if (packageName == null || !packageName.startsWith("com.ubercab")) {
            return ProcessResult.IgnoredPackage(packageName?.toString())
        }

        metrics.uberEventsProcessed.incrementAndGet()

        // 2. Comprobar existencia del árbol de nodos
        if (snapshot == null) {
            metrics.nullRootsCount.incrementAndGet()
            diagnosticLogger?.logEvent("ROOT_UNAVAILABLE", "rootInActiveWindow o snapshot es null")
            return ProcessResult.NullSnapshot
        }

        metrics.snapshotsCreated.incrementAndGet()

        val nodeCount = snapshot.flatten().size

        // 3. Diagnóstico ligero no invasivo con throttling de 150 ms
        val shouldRunDiagnostic = (currentTime - lastDiagnosticTimestamp) >= diagnosticThrottleIntervalMs
        if (shouldRunDiagnostic) {
            lastDiagnosticTimestamp = currentTime
            diagnosticLogger?.logEvent("LIGHT_SCAN", "snapshotNodes=$nodeCount")

            val candidates = UberOfferLightDiagnostic.scanCandidates(snapshot)
            for (candidate in candidates) {
                diagnosticLogger?.logPipelineResult(candidate)
            }
        }

        // 4. Volcado inmediato extraordinario ante cambio estructural brusco
        val isStructuralJump = lastNodeCount > 0 && Math.abs(nodeCount - lastNodeCount) >= 15
        val canRunStructuralDump = (currentTime - lastStructuralDumpTimestamp) >= structuralDumpCooldownMs
        var ranStructuralDump = false
        if (isStructuralJump && canRunStructuralDump) {
            lastStructuralDumpTimestamp = currentTime
            lastFullDumpTimestamp = currentTime
            ranStructuralDump = true
            diagnosticLogger?.logEvent("STRUCTURAL_CHANGE_FULL_DUMP", "prevNodes=$lastNodeCount | newNodes=$nodeCount")
            val dumpResult = UberFullNodeDumper.dump(snapshot)
            diagnosticLogger?.logFullNodeDump(dumpResult.dumpText)
        }
        lastNodeCount = nodeCount

        // 5. Volcado exhaustivo periódico con cooldown estricto de 5 segundos
        val shouldRunFullDump = !ranStructuralDump && (currentTime - lastFullDumpTimestamp) >= fullDumpCooldownMs
        if (shouldRunFullDump) {
            lastFullDumpTimestamp = currentTime
            val dumpResult = UberFullNodeDumper.dump(snapshot)
            diagnosticLogger?.logFullNodeDump(dumpResult.dumpText)
        }

        if (diagnosticLogger?.isTreeDebugEnabled == true) {
            diagnosticLogger?.logTreeDump(UberTreeDumper.dump(snapshot))
        }

        // 6. Extraer datos en bruto mediante el parser
        val rawOffer = parser.parse(snapshot, timestamp = currentTime)

        diagnosticLogger?.logEvent(
            "RAW_OFFER_EXTRACTED",
            "nodes=$nodeCount | screen=${rawOffer.detectedOfferType} | fare=${rawOffer.rawFare} ${rawOffer.currency} | pickup=${rawOffer.pickupDistanceKm}km/${rawOffer.pickupDurationMinutes}m | trip=${rawOffer.tripDistanceKm}km/${rawOffer.tripDurationMinutes}m | cash=${rawOffer.isCashPayment}"
        )

        // 7. Validar y clasificar estructuralmente la oferta
        val validationResult = validator.validate(rawOffer)

        val screenType = if (rawOffer.detectedOfferType != null && rawOffer.detectedOfferType != UberOfferScreenType.UNKNOWN) {
            rawOffer.detectedOfferType!!
        } else {
            validationResult.screenType
        }

        // Notificar cambio de estado de pantalla si procede
        if (screenType != currentScreenType) {
            val previousScreenType = currentScreenType
            currentScreenType = screenType
            evaluationListener?.onScreenStateChanged(currentScreenType)
            diagnosticLogger?.logEvent("SCREEN_TYPE_CHANGED", "from=$previousScreenType to=$currentScreenType")
            diagnosticLogger?.logEvent("TRIP_LIFECYCLE_TRANSITION", "from=$previousScreenType to=$currentScreenType | nodes=${snapshot.flatten().size}")

            if (currentScreenType == UberOfferScreenType.NO_OFFER ||
                currentScreenType == UberOfferScreenType.TRIP_CANCELLED ||
                currentScreenType == UberOfferScreenType.TRIP_COMPLETED ||
                currentScreenType == UberOfferScreenType.ACTIVE_TRIP) {
                consumedOfferSignature = null
            }

            if (currentScreenType == UberOfferScreenType.TRIP_CANCELLED) {
                val reason = rawOffer.cancellationReason ?: com.ridedecider.app.domain.model.CancellationReason.UNKNOWN
                evaluationListener?.onTripCancelled(reason, rawOffer.cancellationFee)
            } else if (currentScreenType == UberOfferScreenType.TRIP_COMPLETED) {
                evaluationListener?.onTripCompleted(rawOffer.finalEarningsEur)
            }

            if (currentScreenType == UberOfferScreenType.RADAR_OFFER || currentScreenType == UberOfferScreenType.TRIP_OFFER) {
                diagnosticLogger?.logEvent("OFFER_TRANSITION_FULL_DUMP", "screenType=$currentScreenType")
                if (!ranStructuralDump && lastFullDumpTimestamp != currentTime) {
                    lastFullDumpTimestamp = currentTime
                    val dumpResult = UberFullNodeDumper.dump(snapshot)
                    diagnosticLogger?.logFullNodeDump(dumpResult.dumpText)
                }
            }
        }

        if (!validationResult.isValidOffer) {
            if (shouldTriggerOcrFallback(rawOffer, nodeCount, isStructuralJump, currentTime)) {
                recordOcrAttempt(currentTime)
                diagnosticLogger?.logEvent("OCR_FALLBACK_TRIGGERED", "prevNodes=$lastNodeCount | newNodes=$nodeCount | isJump=$isStructuralJump")
                return ProcessResult.RequiresOcrFallback(rawOffer, nodeCount, isStructuralJump)
            }
            metrics.invalidOffersCount.incrementAndGet()
            return ProcessResult.InvalidOffer(currentScreenType, validationResult.reasons)
        }

        val signature = generateOfferSignature(rawOffer)
        if (signature == consumedOfferSignature || (signature == lastSignature && (currentTime - lastEvaluationTimestamp) < debounceIntervalMs)) {
            metrics.debouncedCount.incrementAndGet()
            return ProcessResult.Debounced(signature)
        }

        val mappingResult = mapper.mapToDomain(rawOffer)
        if (mappingResult is TripMappingResult.Failure) {
            metrics.mappingFailuresCount.incrementAndGet()
            diagnosticLogger?.logPipelineResult("MAPPING_FAILURE: SCREEN_TYPE=$currentScreenType | REASON=${mappingResult.reason}")
            return ProcessResult.MappingFailure(mappingResult.reason)
        }

        val trip = (mappingResult as TripMappingResult.Success).trip
        val config = configProvider.getConfig()
        val evaluation = evaluateUseCase(trip, config)

        metrics.validOffersCount.incrementAndGet()
        metrics.evaluationsCount.incrementAndGet()
        lastSignature = signature
        consumedOfferSignature = signature
        lastEvaluationTimestamp = currentTime

        val totalKm = evaluation.metrics?.totalDistanceKm?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "N/A"
        val grossKm = evaluation.metrics?.grossPerKm?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "N/A"
        val grossH = evaluation.metrics?.grossPerHour?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "N/A"
        val cashTag = if (trip.isCashPayment) " | EFECTIVO" else ""
        val ratingTag = trip.passengerRating?.let { " | Val: $it" } ?: ""
        val pickupInfo = "${trip.pickupDistanceKm} km (${trip.pickupDurationMinutes?.toInt()} min) -> ${trip.pickupAddress ?: "N/A"}"
        val tripInfo = "${trip.tripDistanceKm} km (${trip.tripDurationMinutes?.toInt()} min) -> ${trip.dropoffAddress ?: "N/A"}"
        diagnosticLogger?.logPipelineResult(
            "EVALUATION: SCREEN_TYPE=$currentScreenType | TARIFA=${trip.rawFare} ${trip.currency} | RECOGIDA=[$pickupInfo] | VIAJE=[$tripInfo] | TOTAL=$totalKm km | DECISION=${evaluation.decision} | RATIO=$grossKm €/km ($grossH €/h)$cashTag$ratingTag | REASONS=${evaluation.reasons}"
        )

        evaluationListener?.onTripEvaluation(evaluation)

        return ProcessResult.Evaluated(evaluation)
    }

    fun shouldTriggerOcrFallback(
        rawOffer: RawUberTripOffer,
        nodeCount: Int,
        isStructuralJump: Boolean,
        currentTime: Long = System.currentTimeMillis(),
        sdkVersion: Int = Build.VERSION.SDK_INT
    ): Boolean {
        if (sdkVersion < Build.VERSION_CODES.R) {
            return false
        }

        if (rawOffer.rawFare != null) {
            return false
        }

        if (rawOffer.detectedOfferType == UberOfferScreenType.TRIP_CANCELLED ||
            rawOffer.detectedOfferType == UberOfferScreenType.TRIP_COMPLETED ||
            rawOffer.detectedOfferType == UberOfferScreenType.ACTIVE_TRIP ||
            rawOffer.detectedOfferType == UberOfferScreenType.RESERVATION_SCREEN ||
            rawOffer.detectedOfferType == UberOfferScreenType.HISTORY_SCREEN) {
            return false
        }

        if ((currentTime - lastOcrAttemptTimestamp) < ocrDebounceIntervalMs) {
            return false
        }

        val hasOfferStructureEvidence = isStructuralJump || (nodeCount in 10..250)
        return hasOfferStructureEvidence
    }

    fun recordOcrAttempt(currentTime: Long = System.currentTimeMillis()) {
        lastOcrAttemptTimestamp = currentTime
    }

    fun processOcrResult(
        ocrResult: OcrResult,
        currentTime: Long = System.currentTimeMillis()
    ): ProcessResult {
        if (!ocrResult.hasText) {
            diagnosticLogger?.logEvent("OCR_FALLBACK_SKIPPED", "Status=${ocrResult.status} | Err=${ocrResult.errorMessage}")
            return ProcessResult.InvalidOffer(currentScreenType, listOf(UberValidationReason.INCOMPLETE_OFFER))
        }

        val rawOffer = parser.parseFromText(ocrResult.rawText, timestamp = currentTime)

        diagnosticLogger?.logEvent(
            "OCR_RAW_OFFER_EXTRACTED",
            "latency=${ocrResult.totalLatencyMs}ms (cap=${ocrResult.screenshotLatencyMs}ms + ocr=${ocrResult.ocrLatencyMs}ms) | screen=${rawOffer.detectedOfferType} | fare=${rawOffer.rawFare} ${rawOffer.currency} | pickup=${rawOffer.pickupDistanceKm}km/${rawOffer.pickupDurationMinutes}m | trip=${rawOffer.tripDistanceKm}km/${rawOffer.tripDurationMinutes}m"
        )

        val validationResult = validator.validate(rawOffer)
        if (!validationResult.isValidOffer) {
            metrics.invalidOffersCount.incrementAndGet()
            return ProcessResult.InvalidOffer(validationResult.screenType, validationResult.reasons)
        }

        val signature = generateOfferSignature(rawOffer)
        if (signature == consumedOfferSignature || (signature == lastSignature && (currentTime - lastEvaluationTimestamp) < debounceIntervalMs)) {
            metrics.debouncedCount.incrementAndGet()
            return ProcessResult.Debounced(signature)
        }

        val mappingResult = mapper.mapToDomain(rawOffer)
        if (mappingResult is TripMappingResult.Failure) {
            metrics.mappingFailuresCount.incrementAndGet()
            diagnosticLogger?.logPipelineResult("OCR_MAPPING_FAILURE: REASON=${mappingResult.reason}")
            return ProcessResult.MappingFailure(mappingResult.reason)
        }

        val trip = (mappingResult as TripMappingResult.Success).trip
        val config = configProvider.getConfig()
        val evaluation = evaluateUseCase(trip, config)

        metrics.validOffersCount.incrementAndGet()
        metrics.evaluationsCount.incrementAndGet()
        lastSignature = signature
        consumedOfferSignature = signature
        lastEvaluationTimestamp = currentTime

        val totalKm = evaluation.metrics?.totalDistanceKm?.let { String.format(Locale.US, "%.1f", it) } ?: "N/A"
        val grossKm = evaluation.metrics?.grossPerKm?.let { String.format(Locale.US, "%.2f", it) } ?: "N/A"
        val grossH = evaluation.metrics?.grossPerHour?.let { String.format(Locale.US, "%.2f", it) } ?: "N/A"
        diagnosticLogger?.logPipelineResult(
            "EVALUATION (OCR FALLBACK): LATENCY=${ocrResult.totalLatencyMs}ms (cap=${ocrResult.screenshotLatencyMs}ms + ocr=${ocrResult.ocrLatencyMs}ms) | TARIFA=${trip.rawFare} ${trip.currency} | DECISION=${evaluation.decision} | RATIO=$grossKm €/km ($grossH €/h)"
        )

        evaluationListener?.onTripEvaluation(evaluation)

        return ProcessResult.Evaluated(evaluation)
    }

    private fun generateOfferSignature(rawOffer: RawUberTripOffer): String {
        val pickup = rawOffer.pickupAddress?.trim()?.lowercase() ?: ""
        val dropoff = rawOffer.dropoffAddress?.trim()?.lowercase() ?: ""
        val rating = rawOffer.passengerRating?.let { String.format(Locale.US, "%.2f", it) } ?: ""
        val category = rawOffer.category?.trim()?.uppercase() ?: ""
        return "${rawOffer.detectedOfferType?.name}_${rawOffer.rawFare}_${rawOffer.pickupDistanceKm}_${rawOffer.pickupDurationMinutes}_${rawOffer.tripDistanceKm}_${rawOffer.tripDurationMinutes}_${pickup}_${dropoff}_${rating}_${category}_${rawOffer.isCashPayment}"
    }

    /**
     * Resetea la caché interna de debounce, métricas y estado de pantalla.
     */
    fun reset() {
        currentScreenType = UberOfferScreenType.UNKNOWN
        lastSignature = null
        consumedOfferSignature = null
        lastEvaluationTimestamp = 0L
        lastDiagnosticTimestamp = -diagnosticThrottleIntervalMs
        lastFullDumpTimestamp = -fullDumpCooldownMs
        lastNodeCount = 0
        lastStructuralDumpTimestamp = 0L
        lastOcrAttemptTimestamp = -ocrDebounceIntervalMs
        metrics.reset()
    }

    /**
     * Resultados tipados del procesamiento de un evento de accesibilidad.
     */
    sealed class ProcessResult {
        data class IgnoredPackage(val packageReceived: String?) : ProcessResult()
        object NullSnapshot : ProcessResult()
        data class InvalidOffer(val screenType: UberOfferScreenType, val reasons: List<UberValidationReason>) : ProcessResult()
        data class RequiresOcrFallback(val rawOffer: RawUberTripOffer, val nodeCount: Int, val isStructuralJump: Boolean) : ProcessResult()
        data class Debounced(val signature: String) : ProcessResult()
        data class MappingFailure(val reason: TripMappingFailureReason) : ProcessResult()
        data class Evaluated(val evaluation: TripEvaluation) : ProcessResult()
    }
}
