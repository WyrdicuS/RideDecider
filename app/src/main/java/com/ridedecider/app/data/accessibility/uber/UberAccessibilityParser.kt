package com.ridedecider.app.data.accessibility.uber

/**
 * Parser independiente, conservador y determinista para extraer señales de la interfaz de Uber
 * a partir de un [UberNodeSnapshot] y generar un [RawUberTripOffer].
 *
 * Emplea un modelo de detección basado en evidencia combinada y reconocimiento de identificadores
 * de recursos reales de Uber Driver para clasificar pantallas y extraer magnitudes cinemáticas,
 * direcciones, métodos de pago y tarifas económicas con máxima fidelidad.
 *
 * Clase pura en Kotlin sin dependencias del SDK de Android ni lógica económica.
 */
class UberAccessibilityParser {

    companion object {
        // Regex de precios y divisas (soporta enteros "5 €", "5$", y decimales "5,02 €", "5.02 €", "$14.20")
        private val PRICE_PREFIX_REGEX = Regex("""(?i)(€|\$|EUR|USD)\s*(\d+(?:[.,]\d{1,2})?)""")
        private val PRICE_SUFFIX_REGEX = Regex("""(?i)\b(\d+(?:[.,]\d{1,2})?)\s*(€|\$|EUR|USD)""")

        // Regex de distancias (soporta "3.5 km", "3,5 km", "500 m")
        private val DISTANCE_KM_REGEX = Regex("""(?i)(\d+(?:[.,]\d{1,2})?)\s*(?:km|kil[oó]metros?)\b""")
        private val DISTANCE_METERS_REGEX = Regex("""(?i)\b(\d+)\s*(?:m|metros)\b(?!in|h)""")

        // Regex de tiempos (soporta "6 min", "1 h 15 min", "1.5 h")
        private val DURATION_HOURS_MINS_REGEX = Regex("""(?i)(?:(\d+)\s*(?:h|horas?|hours?)\s*(?:y|and)?\s*)?(\d+)\s*(?:min|mins|minutos?|minutes?)\b""")
        private val DURATION_HOURS_ONLY_REGEX = Regex("""(?i)\b(\d+(?:[.,]\d+)?)\s*(?:h|horas?|hours?)\b(?!/|in)""")

        // Regex de valoración de pasajeros (ej. "★ 4,91" o "4.91 ★" o "4,91")
        private val PASSENGER_RATING_REGEX = Regex("""(?i)(?:★|\*)\s*(\d+[.,]\d{1,2})|(\d+[.,]\d{1,2})\s*(?:★|\*)""")

        // Indicadores de demanda del mapa (ej. "1-18 min", "1-15 min") que NO son duración de viaje
        private val DEMAND_RANGE_REGEX = Regex("""(?i)\b\d+\s*[-–]\s*\d+\s*(?:min|mins|minutos?)\b""")

        // Palabras clave para descartar números que pertenecen a saldos o acumulados
        private val DAILY_EARNINGS_KEYWORDS = listOf(
            "hoy", "today", "ganancias", "earnings", "saldo", "balance",
            "semana", "desconectar", "offline", "propina", "tip",
            "promocion", "promoción", "tarifa de cancelación", "cancelacion"
        )

        // Señales explícitas de cancelación por el pasajero o sistema (Español e Inglés)
        private val RIDER_CANCELLATION_KEYWORDS = listOf(
            "cancelado por el pasajero",
            "cancelado por el usuario",
            "cancelado por el cliente",
            "el usuario canceló el viaje",
            "el pasajero canceló el viaje",
            "el cliente canceló el viaje",
            "viaje cancelado por el pasajero",
            "viaje cancelado por el usuario",
            "the trip was cancelled by the rider",
            "the rider cancelled the trip",
            "rider cancelled the trip",
            "trip cancelled by rider",
            "rider cancelled",
            "pasajero canceló",
            "usuario canceló"
        )

        private val DRIVER_CANCELLATION_KEYWORDS = listOf(
            "has cancelado el viaje",
            "cancelaste el viaje",
            "cancelado por el conductor",
            "conductor canceló",
            "you cancelled the trip",
            "cancelled by driver",
            "driver cancelled"
        )

        private val UBER_CANCELLATION_KEYWORDS = listOf(
            "cancelado por uber",
            "trip cancelled by uber",
            "cancelled by uber",
            "cancelado por el sistema",
            "system cancelled",
            "problema con el viaje"
        )

        private val NO_SHOW_CANCELLATION_KEYWORDS = listOf(
            "no se ha presentado",
            "el pasajero no se presentó",
            "el usuario no se presentó",
            "rider didn't show up",
            "rider no-show",
            "no-show",
            "tiempo de espera agotado",
            "tiempo de espera finalizado"
        )

        private val GENERIC_CANCELLATION_KEYWORDS = listOf(
            "viaje cancelado",
            "trip cancelled",
            "cancelación del viaje",
            "trip cancellation"
        )

        // Señales de finalización del viaje
        private val COMPLETION_KEYWORDS = listOf(
            "viaje completado",
            "trip completed",
            "has ganado",
            "you earned",
            "resumen del viaje",
            "trip summary",
            "tarifa final",
            "final fare",
            "has completado el viaje"
        )

        // Señales de navegación activa (giros y maniobras en ruta)
        private val ACTIVE_TRIP_KEYWORDS = listOf(
            "recogiendo a", "picking up",
            "dirígete a", "dirigete a", "heading to",
            "llegando a", "arriving at",
            "gire a la derecha", "gire a la izquierda",
            "turn right", "turn left",
            "sigue recto", "seguir recto", "continúe recto", "continua recto", "continue straight",
            "límite de velocidad", "limite de velocidad", "speed limit",
            "min restantes", "mins restantes", "min remaining", "mins remaining",
            "km restantes", "km remaining",
            "desliza para iniciar", "slide to start",
            "desliza para completar", "slide to complete"
        )

        // Categorías reconocidas de Uber
        private val SUPPORTED_CATEGORIES = listOf(
            "UberX Priority",
            "UberX and Share",
            "UberX Share",
            "Uber Metropolitan",
            "Uber Black",
            "Uber Pet",
            "UberVAN",
            "Uber Van",
            "Uber Comfort",
            "Uber Green",
            "Uber Flash",
            "Uber Moto",
            "Comfort",
            "UberX",
            "Black",
            "Green",
            "Van",
            "Package",
            "Connect",
            "Taxi"
        )

        // Frases de interfaz conocidas que no son direcciones
        private val NON_ADDRESS_PHRASES = listOf(
            "tarifa", "tasa de servicio", "pago en efectivo", "aceptar", "emparejar",
            "exclusiva", "exclusivo", "radar", "conectar", "desconectar", "uber", "buscar",
            "inicio", "ganancias", "detalles", "cancelar", "rechazar", "toca para aceptar"
        )
    }

    /**
     * Analiza el snapshot del árbol de nodos y extrae los datos de la oferta mediante evidencia combinada.
     *
     * @param rootNode Nodo raíz del árbol capturado.
     * @param timestamp Timestamp de captura (por defecto tiempo actual).
     * @return [RawUberTripOffer] con los datos detectados o campos en `null` si no hay evidencia.
     */
    fun parse(
        rootNode: UberNodeSnapshot?,
        timestamp: Long = System.currentTimeMillis()
    ): RawUberTripOffer {
        if (rootNode == null) {
            return RawUberTripOffer(
                detectedOfferType = UberOfferScreenType.NO_OFFER,
                sourceTimestamp = timestamp
            )
        }

        val allNodes = rootNode.flatten()
        val allTexts = allNodes.mapNotNull { it.text?.trim() }.filter { it.isNotBlank() }
        val allDescriptions = allNodes.mapNotNull { it.contentDescription?.trim() }.filter { it.isNotBlank() }
        val combinedContent = allTexts + allDescriptions

        if (combinedContent.isEmpty() && allNodes.all { it.viewIdResourceName == null }) {
            return RawUberTripOffer(
                detectedOfferType = UberOfferScreenType.NO_OFFER,
                sourceTimestamp = timestamp
            )
        }

        // 1. Extraer entidades candidatas
        val fareResult = extractFare(allNodes)
        val allDistances = extractDistances(combinedContent)
        val allDurations = extractDurations(combinedContent)
        val category = extractCategory(combinedContent)
        val isCash = extractCashPayment(combinedContent)
        val rating = extractPassengerRating(combinedContent)
        val (pickupAddr, dropoffAddr) = extractAddresses(allNodes)

        // 2. Extraer cinemática contextual (diferenciando recogida vs viaje)
        val contextualKinematics = extractContextualKinematics(combinedContent, allDistances, allDurations)

        // 3. Extraer compensación por cancelación o finalización si está presente
        val cancellationFee = extractCancellationFee(combinedContent)
        val cancellationReason = extractCancellationReason(combinedContent)
        val cancellationReasonText = extractCancellationReasonText(combinedContent)
        val hasCompletion = hasCompletionMessage(combinedContent)
        val finalEarnings = if (hasCompletion) fareResult?.first else null

        // 4. Clasificar tipo de pantalla mediante evidencia combinada y Resource IDs oficiales
        val detectedScreenType = detectScreenTypeWithEvidence(
            allNodes = allNodes,
            content = combinedContent,
            fareResult = fareResult,
            hasKinematics = contextualKinematics.hasAnyKinematics(),
            hasCategory = category != null,
            hasCancellationMessage = cancellationReasonText != null || cancellationReason != null,
            hasCompletionMessage = hasCompletion
        )

        return RawUberTripOffer(
            rawFare = fareResult?.first,
            currency = fareResult?.second,
            pickupDistanceKm = contextualKinematics.pickupDistanceKm,
            pickupDurationMinutes = contextualKinematics.pickupDurationMinutes,
            tripDistanceKm = contextualKinematics.tripDistanceKm,
            tripDurationMinutes = contextualKinematics.tripDurationMinutes,
            pickupAddress = pickupAddr,
            dropoffAddress = dropoffAddr,
            category = category,
            isCashPayment = isCash,
            passengerRating = rating,
            detectedOfferType = detectedScreenType,
            cancellationFee = cancellationFee,
            cancellationReasonText = cancellationReasonText,
            cancellationReason = cancellationReason,
            finalEarningsEur = finalEarnings,
            sourceTimestamp = timestamp
        )
    }

    private fun extractCancellationFee(content: List<String>): Double? {
        val feeRegex = Regex("""(?i)(?:tarifa\s*de\s*cancelaci[oó]n|cancellation\s*fee)[^\d]*(\d+(?:[.,]\d{1,2})?)""")
        for (text in content) {
            val match = feeRegex.find(text)
            if (match != null) {
                val amountStr = match.groupValues[1].replace(',', '.')
                val amount = amountStr.toDoubleOrNull()
                if (amount != null && amount > 0.0) {
                    return amount
                }
            }
            if (text.contains("cancelaci", ignoreCase = true) || text.contains("cancellation", ignoreCase = true)) {
                val suffixMatch = PRICE_SUFFIX_REGEX.find(text)
                if (suffixMatch != null) {
                    val amountStr = suffixMatch.groupValues[1].replace(',', '.')
                    val amount = amountStr.toDoubleOrNull()
                    if (amount != null && amount > 0.0) return amount
                }
                val prefixMatch = PRICE_PREFIX_REGEX.find(text)
                if (prefixMatch != null) {
                    val amountStr = prefixMatch.groupValues[2].replace(',', '.')
                    val amount = amountStr.toDoubleOrNull()
                    if (amount != null && amount > 0.0) return amount
                }
            }
        }
        return null
    }

    private fun extractCancellationReason(content: List<String>): com.ridedecider.app.domain.model.CancellationReason? {
        for (text in content) {
            val lower = text.lowercase()
            if (RIDER_CANCELLATION_KEYWORDS.any { lower.contains(it) }) return com.ridedecider.app.domain.model.CancellationReason.RIDER
            if (DRIVER_CANCELLATION_KEYWORDS.any { lower.contains(it) }) return com.ridedecider.app.domain.model.CancellationReason.DRIVER
            if (UBER_CANCELLATION_KEYWORDS.any { lower.contains(it) }) return com.ridedecider.app.domain.model.CancellationReason.UBER
            if (NO_SHOW_CANCELLATION_KEYWORDS.any { lower.contains(it) }) return com.ridedecider.app.domain.model.CancellationReason.NO_SHOW
            if (GENERIC_CANCELLATION_KEYWORDS.any { lower.contains(it) }) return com.ridedecider.app.domain.model.CancellationReason.UNKNOWN
        }
        return null
    }

    private fun hasCompletionMessage(content: List<String>): Boolean {
        return content.any { text ->
            val lower = text.lowercase()
            COMPLETION_KEYWORDS.any { lower.contains(it) }
        }
    }

    private fun extractCancellationReasonText(content: List<String>): String? {
        for (text in content) {
            val lower = text.lowercase()
            if (RIDER_CANCELLATION_KEYWORDS.any { lower.contains(it) }) {
                return text
            }
            if (DRIVER_CANCELLATION_KEYWORDS.any { lower.contains(it) }) {
                return text
            }
            if (UBER_CANCELLATION_KEYWORDS.any { lower.contains(it) }) {
                return text
            }
            if (NO_SHOW_CANCELLATION_KEYWORDS.any { lower.contains(it) }) {
                return text
            }
            if (GENERIC_CANCELLATION_KEYWORDS.any { lower.contains(it) }) {
                return text
            }
        }
        return null
    }

    private fun detectScreenTypeWithEvidence(
        allNodes: List<UberNodeSnapshot>,
        content: List<String>,
        fareResult: Pair<Double, String>?,
        hasKinematics: Boolean,
        hasCategory: Boolean,
        hasCancellationMessage: Boolean,
        hasCompletionMessage: Boolean = false
    ): UberOfferScreenType {
        // Prioridad máxima: Notificación o pantalla explícita de cancelación por el pasajero / Uber
        if (hasCancellationMessage) {
            return UberOfferScreenType.TRIP_CANCELLED
        }

        var hasTripOfferAction = false
        var hasRadarOfferAction = false
        var hasRadarPill = false
        var hasRadarBrowseContainer = false
        var hasExclusiveLabel = false
        var hasActiveTripTrigger = false

        for (node in allNodes) {
            val text = node.text?.trim()
            val desc = node.contentDescription?.trim()
            val resId = node.viewIdResourceName?.trim()

            // Detección estructural de componentes de Trip Radar por Resource ID
            if (resId == UberAccessibilityConstants.RADAR_PILL_TITLE_RES_ID ||
                resId == UberAccessibilityConstants.RADAR_PILL_BADGE_RES_ID) {
                hasRadarPill = true
            }

            if (resId == UberAccessibilityConstants.RADAR_BROWSE_CONTAINER_RES_ID) {
                hasRadarBrowseContainer = true
            }

            if (resId == UberAccessibilityConstants.UPFRONT_OFFER_PROGRESS_RES_ID) {
                hasTripOfferAction = true
            }

            val textList = listOfNotNull(text, desc)
            for (t in textList) {
                val lower = t.lowercase()

                // Detección precisa de Trip Radar
                if (lower == "radar de viaje" || lower == "radar de viajes" || lower == "trip radar" ||
                    lower.startsWith("radar de viaje") || lower.startsWith("radar de viajes") || lower.startsWith("trip radar") ||
                    lower.contains("radar de viaje") || lower.contains("radar de viajes") || lower.contains("trip radar")) {
                    hasRadarPill = true
                }
                if (lower == "emparejar" || lower == "match" || lower.contains("emparejar") || lower.contains("match")) {
                    hasRadarOfferAction = true
                }
                if (lower == "aceptar" || lower == "accept" || lower.contains("toca para aceptar") || lower.contains("tap to accept")) {
                    hasTripOfferAction = true
                }
                if (lower == "exclusiva" || lower == "exclusive" || lower.contains("oferta exclusiva") || lower.contains("exclusive offer")) {
                    hasExclusiveLabel = true
                }
                if (ACTIVE_TRIP_KEYWORDS.any { lower.contains(it) }) {
                    hasActiveTripTrigger = true
                }
            }
        }

        val hasFare = fareResult != null

        // 1. Prioridad: Notificación de finalización de viaje
        if (hasCompletionMessage && !hasTripOfferAction && !hasRadarOfferAction && !hasRadarPill) {
            return UberOfferScreenType.TRIP_COMPLETED
        }

        // 2. Prioridad: ACTIVE_TRIP si existen señales claras de navegación sin acciones de oferta ni tarifa
        if (hasActiveTripTrigger && !hasFare && !hasTripOfferAction && !hasRadarOfferAction && !hasRadarPill) {
            return UberOfferScreenType.ACTIVE_TRIP
        }

        // 2. Prioridad: TRIP_OFFER (Viaje Directo) requiere acción "Aceptar" con datos o tarjeta directa sin señales de radar
        val isTripOffer = when {
            hasTripOfferAction && (hasFare || hasKinematics || hasExclusiveLabel) -> true
            (hasFare || hasKinematics) && !hasRadarOfferAction && !hasRadarPill && !hasRadarBrowseContainer -> true
            else -> false
        }

        if (isTripOffer) {
            return UberOfferScreenType.TRIP_OFFER
        }

        // 3. Prioridad: RADAR_OFFER (Viaje Radar) requiere acción "Emparejar" con datos o componentes de Trip Radar con datos
        val isRadarOffer = when {
            hasRadarOfferAction && (hasFare || hasKinematics || hasRadarPill || hasRadarBrowseContainer) -> true
            (hasRadarPill || hasRadarBrowseContainer) && (hasFare || hasKinematics || hasRadarOfferAction) -> true
            else -> false
        }

        if (isRadarOffer) {
            return UberOfferScreenType.RADAR_OFFER
        }

        // 4. Sin evidencia suficiente de oferta
        return UberOfferScreenType.NO_OFFER
    }

    private data class KinematicsResult(
        val pickupDistanceKm: Double?,
        val pickupDurationMinutes: Double?,
        val tripDistanceKm: Double?,
        val tripDurationMinutes: Double?
    ) {
        fun hasAnyKinematics(): Boolean {
            return pickupDistanceKm != null || pickupDurationMinutes != null || tripDistanceKm != null || tripDurationMinutes != null
        }
    }

    private fun extractContextualKinematics(
        content: List<String>,
        allDistances: List<Double>,
        allDurations: List<Double>
    ): KinematicsResult {
        var pickupDist: Double? = null
        var pickupDur: Double? = null
        var tripDist: Double? = null
        var tripDur: Double? = null

        // Búsqueda contextual de bloques de texto (ej. "A 6 min (3.5 km) de distancia" vs "Viaje de 6 min (3.1 km)")
        for (text in content) {
            val lower = text.lowercase()
            val textDistances = extractDistancesFromSingleText(text)
            val textDurations = extractDurationsFromSingleText(text)

            val isPickupContext = lower.contains("de distancia") || lower.contains("recogida") || lower.contains("pickup") || lower.startsWith("a ")
            val isTripContext = lower.contains("viaje de") || lower.contains("viaje") || lower.contains("trip") || lower.contains("destino") || lower.contains("dropoff")

            if (isPickupContext && !lower.contains("viaje de")) {
                if (pickupDist == null && textDistances.isNotEmpty()) pickupDist = textDistances.first()
                if (pickupDur == null && textDurations.isNotEmpty()) pickupDur = textDurations.first()
            } else if (isTripContext && !lower.contains("de distancia")) {
                if (tripDist == null && textDistances.isNotEmpty()) tripDist = textDistances.first()
                if (tripDur == null && textDurations.isNotEmpty()) tripDur = textDurations.first()
            } else if (isPickupContext && isTripContext) {
                // Caso combinado en un único texto
                if (textDistances.size >= 2) {
                    if (pickupDist == null) pickupDist = textDistances[0]
                    if (tripDist == null) tripDist = textDistances[1]
                }
                if (textDurations.size >= 2) {
                    if (pickupDur == null) pickupDur = textDurations[0]
                    if (tripDur == null) tripDur = textDurations[1]
                }
            }
        }

        // Fallback ordenado si no se resolvió contextualmente
        val finalPickupDist = pickupDist ?: if (allDistances.size > 1) allDistances[0] else if (allDistances.isNotEmpty()) 0.0 else null
        val finalTripDist = tripDist ?: if (allDistances.size > 1) allDistances[1] else allDistances.firstOrNull()

        val finalPickupDur = pickupDur ?: if (allDurations.size > 1) allDurations[0] else if (allDurations.isNotEmpty()) 0.0 else null
        val finalTripDur = tripDur ?: if (allDurations.size > 1) allDurations[1] else allDurations.firstOrNull()

        return KinematicsResult(
            pickupDistanceKm = finalPickupDist,
            pickupDurationMinutes = finalPickupDur,
            tripDistanceKm = finalTripDist,
            tripDurationMinutes = finalTripDur
        )
    }

    private fun cleanText(raw: String): String {
        return raw
            .replace('\u00a0', ' ')
            .replace('\u202f', ' ')
            .replace('\u2009', ' ')
            .replace('\u2007', ' ')
            .replace('\u200b', ' ')
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace('\t', ' ')
            .trim()
    }

    private fun extractFare(allNodes: List<UberNodeSnapshot>): Pair<Double, String>? {
        // 1. Escaneo nodo a nodo con normalización exhaustiva
        for (node in allNodes) {
            val resId = (node.viewIdResourceName ?: "").lowercase()
            if (resId == UberAccessibilityConstants.EARNINGS_TRACKER_RES_ID ||
                resId.contains("earnings_tracker") ||
                resId.contains("today")) {
                continue
            }

            val rawList = listOfNotNull(node.text, node.contentDescription)
            for (raw in rawList) {
                val text = cleanText(raw)
                val lower = text.lowercase()

                // Exclusión secundaria por palabras clave de acumulados, saldos o propinas
                if (DAILY_EARNINGS_KEYWORDS.any { lower.contains(it) }) {
                    continue
                }

                // Patrón sufijo flexible: "5,02 €", "5,02€", "5 €", "14.20 EUR", "12 €"
                val suffixRegex = Regex("""(?i)[+~]?\s*(\d{1,3}(?:[.,]\d{1,2})?)\s*(€|\$|EUR|USD)""")
                val suffixMatch = suffixRegex.find(text)
                if (suffixMatch != null) {
                    val amountStr = suffixMatch.groupValues[1].replace(',', '.')
                    val currencyRaw = suffixMatch.groupValues[2].uppercase()
                    val amount = amountStr.toDoubleOrNull()
                    if (amount != null && amount >= 0.50 && amount <= 500.0) {
                        val currency = when (currencyRaw) {
                            "$", "USD" -> "USD"
                            else -> "EUR"
                        }
                        return Pair(amount, currency)
                    }
                }

                // Patrón prefijo flexible: "€5,02", "€ 5,02", "$14.20", "EUR 12"
                val prefixRegex = Regex("""(?i)(€|\$|EUR|USD)\s*(\d{1,3}(?:[.,]\d{1,2})?)""")
                val prefixMatch = prefixRegex.find(text)
                if (prefixMatch != null) {
                    val symbol = prefixMatch.groupValues[1].uppercase()
                    val amountStr = prefixMatch.groupValues[2].replace(',', '.')
                    val amount = amountStr.toDoubleOrNull()
                    if (amount != null && amount >= 0.50 && amount <= 500.0) {
                        val currency = if (symbol == "$" || symbol == "USD") "USD" else "EUR"
                        return Pair(amount, currency)
                    }
                }

                // Patrón número aislado en tarjeta (ej. "5,02", "12,45", "5" con el símbolo € en nodo adyacente)
                val numberMatch = Regex("""^\s*(\d{1,3}(?:[.,]\d{1,2})?)\s*$""").find(text)
                if (numberMatch != null) {
                    val amountStr = numberMatch.groupValues[1].replace(',', '.')
                    val amount = amountStr.toDoubleOrNull()
                    if (amount != null && amount >= 0.50 && amount <= 500.0) {
                        val isRatingOrKinematics = allNodes.any { n ->
                            val nt = n.text ?: ""
                            (nt.contains("★") || nt.contains("*") || nt.contains("min") || nt.contains("km")) &&
                            nt.contains(text)
                        }
                        if (!isRatingOrKinematics) {
                            return Pair(amount, "EUR")
                        }
                    }
                }
            }
        }

        // 2. Escaneo sobre el texto combinado de toda la tarjeta (excluyendo nodos de ganancias diarias o acumulados)
        val candidateNodes = allNodes.filter { n ->
            val resId = (n.viewIdResourceName ?: "").lowercase()
            val text = (n.text ?: "").lowercase()
            !resId.contains("earnings_tracker") &&
            !resId.contains("today") &&
            resId != UberAccessibilityConstants.EARNINGS_TRACKER_RES_ID &&
            !DAILY_EARNINGS_KEYWORDS.any { text.contains(it) }
        }
        val combinedText = candidateNodes.mapNotNull { it.text?.trim() }.joinToString(" ")
            .replace('\u00a0', ' ').replace('\u202f', ' ').replace('\n', ' ')
        val fallbackMatch = Regex("""(?i)(\d{1,3}(?:[.,]\d{1,2})?)\s*(€|\$|EUR|USD)""").find(combinedText)
        if (fallbackMatch != null) {
            val amountStr = fallbackMatch.groupValues[1].replace(',', '.')
            val amount = amountStr.toDoubleOrNull()
            if (amount != null && amount >= 0.50 && amount <= 500.0) {
                return Pair(amount, "EUR")
            }
        }

        // 3. Estrategia Espacial (Bounds Y en 1100..1750 con ranking por área visual)
        val boundsCandidates = allNodes.mapNotNull { n ->
            val boundsStr = n.boundsInScreen ?: return@mapNotNull null
            val match = Regex("""\[(\d+),(\d+)\]\[(\d+),(\d+)\]""").find(boundsStr) ?: return@mapNotNull null
            val left = match.groupValues[1].toIntOrNull() ?: 0
            val top = match.groupValues[2].toIntOrNull() ?: 0
            val right = match.groupValues[3].toIntOrNull() ?: 0
            val bottom = match.groupValues[4].toIntOrNull() ?: 0
            val centerY = (top + bottom) / 2
            val width = right - left
            val height = bottom - top
            val area = width * height
            if (centerY in 1100..1750 && height >= 30) {
                Pair(n, area)
            } else null
        }.sortedByDescending { it.second }.map { it.first }

        for (cand in boundsCandidates) {
            val raw = cand.text ?: cand.contentDescription ?: continue
            val text = raw.replace('\u00a0', ' ').replace('\n', ' ').trim()
            val numMatch = Regex("""\b(\d{1,3}(?:[.,]\d{1,2})?)\b""").find(text)
            if (numMatch != null) {
                val amountStr = numMatch.groupValues[1].replace(',', '.')
                val amount = amountStr.toDoubleOrNull()
                if (amount != null && amount >= 2.0 && amount <= 500.0) {
                    val isKinematicsOrRating = text.contains("min") || text.contains("km") || text.contains("★") || text.contains("*")
                    if (!isKinematicsOrRating) {
                        return Pair(amount, "EUR")
                    }
                }
            }
        }

        return null
    }

    private fun extractDistances(content: List<String>): List<Double> {
        val distances = mutableListOf<Double>()
        for (text in content) {
            distances.addAll(extractDistancesFromSingleText(text))
        }
        return distances
    }

    private fun extractDistancesFromSingleText(text: String): List<Double> {
        val lower = text.lowercase()
        if (lower.contains("km/h")) return emptyList()

        val results = mutableListOf<Double>()
        for (match in DISTANCE_KM_REGEX.findAll(text)) {
            val numStr = match.groupValues[1].replace(',', '.')
            numStr.toDoubleOrNull()?.let { results.add(it) }
        }
        for (match in DISTANCE_METERS_REGEX.findAll(text)) {
            val numStr = match.groupValues[1]
            numStr.toDoubleOrNull()?.let { meters ->
                results.add(meters / 1000.0)
            }
        }
        return results
    }

    private fun extractDurations(content: List<String>): List<Double> {
        val durations = mutableListOf<Double>()
        for (text in content) {
            durations.addAll(extractDurationsFromSingleText(text))
        }
        return durations
    }

    private fun extractDurationsFromSingleText(text: String): List<Double> {
        val lower = text.lowercase()
        if (lower.contains("km/h")) return emptyList()
        if (DEMAND_RANGE_REGEX.containsMatchIn(text)) return emptyList()
        if (lower.contains("restantes")) return emptyList()

        val results = mutableListOf<Double>()

        // Formato horas + minutos o solo minutos: ej "1 h 15 min" o "6 min"
        for (match in DURATION_HOURS_MINS_REGEX.findAll(text)) {
            val hoursStr = match.groupValues[1]
            val minsStr = match.groupValues[2]

            val hours = hoursStr.toDoubleOrNull() ?: 0.0
            val mins = minsStr.toDoubleOrNull() ?: 0.0

            val totalMins = (hours * 60.0) + mins
            if (totalMins > 0.0) {
                results.add(totalMins)
            }
        }

        // Formato solo horas: ej "1 h" o "1.5 h"
        if (results.isEmpty()) {
            for (match in DURATION_HOURS_ONLY_REGEX.findAll(text)) {
                val hoursStr = match.groupValues[1].replace(',', '.')
                hoursStr.toDoubleOrNull()?.let { hours ->
                    results.add(hours * 60.0)
                }
            }
        }
        return results
    }

    private fun extractCategory(content: List<String>): String? {
        for (text in content) {
            for (cat in SUPPORTED_CATEGORIES) {
                if (text.equals(cat, ignoreCase = true) || text.contains(Regex("""(?i)\b${Regex.escape(cat)}\b"""))) {
                    return cat
                }
            }
        }
        return null
    }

    private fun extractCashPayment(content: List<String>): Boolean {
        return content.any {
            val lower = it.lowercase()
            lower.contains("pago en efectivo") || lower.contains("efectivo") || lower.contains("cash")
        }
    }

    private fun extractPassengerRating(content: List<String>): Double? {
        for (text in content) {
            val match = PASSENGER_RATING_REGEX.find(text)
            if (match != null) {
                val ratingStr = (match.groupValues[1].ifEmpty { match.groupValues[2] }).replace(',', '.')
                val rating = ratingStr.toDoubleOrNull()
                if (rating != null && rating in 1.0..5.0) {
                    return rating
                }
            }
        }
        return null
    }

    private fun extractAddresses(allNodes: List<UberNodeSnapshot>): Pair<String?, String?> {
        var pickup: String? = null
        var dropoff: String? = null

        val textNodes = allNodes.mapNotNull { it.text?.trim() }.filter { it.isNotBlank() }

        for (i in textNodes.indices) {
            val text = textNodes[i]
            val lower = text.lowercase()

            // 1. Extracción posicional basada en la estructura visual de la tarjeta de Uber:
            // El nodo siguiente a "A X min (Y km) de distancia" es la dirección de recogida
            if (lower.contains("de distancia") || (lower.startsWith("a ") && lower.contains("min"))) {
                if (pickup == null && i + 1 < textNodes.size) {
                    val candidate = textNodes[i + 1]
                    if (isValidAddressCandidate(candidate)) {
                        pickup = candidate
                    }
                }
            }

            // El nodo siguiente a "Viaje de X min (Y km)" es la dirección de destino
            if (lower.contains("viaje de") || lower.contains("viaje") || lower.contains("trip")) {
                if (dropoff == null && i + 1 < textNodes.size) {
                    val candidate = textNodes[i + 1]
                    if (isValidAddressCandidate(candidate)) {
                        dropoff = candidate
                    }
                }
            }

            // 2. Soporte para prefijos explícitos
            if (lower.contains("recogida:") || lower.contains("pickup:")) {
                pickup = text.substringAfter(':').trim().ifBlank { null }
            }
            if (lower.contains("destino:") || lower.contains("dropoff:") || lower.contains("hacia:")) {
                dropoff = text.substringAfter(':').trim().ifBlank { null }
            }
        }

        return Pair(pickup, dropoff)
    }

    private fun isValidAddressCandidate(text: String): Boolean {
        val lower = text.lowercase().trim()
        if (lower.length < 3) return false
        if (NON_ADDRESS_PHRASES.any { lower.contains(it) }) return false
        if (DISTANCE_KM_REGEX.containsMatchIn(text) || DISTANCE_METERS_REGEX.containsMatchIn(text)) return false
        if (DURATION_HOURS_MINS_REGEX.containsMatchIn(text)) return false
        if (PRICE_PREFIX_REGEX.containsMatchIn(text) || PRICE_SUFFIX_REGEX.containsMatchIn(text)) return false
        return true
    }
}
