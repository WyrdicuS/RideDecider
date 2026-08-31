package com.ridedecider.app.data.accessibility.uber

import com.ridedecider.app.domain.model.Trip

/**
 * Resultado tipado e inmutable del mapeo de [RawUberTripOffer] a entidad de dominio [Trip].
 */
sealed class TripMappingResult {
    /** Mapeo exitoso con la entidad [Trip] generada. */
    data class Success(val trip: Trip) : TripMappingResult()

    /** Fallo en el mapeo con la razón estructurada del error. */
    data class Failure(val reason: TripMappingFailureReason) : TripMappingResult()
}
