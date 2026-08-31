package com.ridedecider.app.data.accessibility.uber

/**
 * Constantes centralizadas para la integración del servicio de accesibilidad con Uber.
 */
object UberAccessibilityConstants {
    /** Nombre del paquete oficial de la aplicación para conductores de Uber. */
    const val UBER_PACKAGE_NAME = "com.ubercab.driver"

    /** Intervalo mínimo de debounce (milisegundos) para evitar evaluaciones repetidas de la misma oferta. */
    const val DEFAULT_DEBOUNCE_INTERVAL_MS = 300L

    // Resource IDs confirmados mediante auditoría estática de APK y volcados forenses reales
    const val EARNINGS_TRACKER_RES_ID = "com.ubercab.driver:id/ub__earnings_tracker_counter_content_status"
    const val RADAR_PILL_TITLE_RES_ID = "com.ubercab.driver:id/ub__driver_job_offers_pill_title"
    const val RADAR_PILL_BADGE_RES_ID = "com.ubercab.driver:id/ub__driver_job_offers_pill_badge"
    const val RADAR_PILL_TAG_RES_ID = "com.ubercab.driver:id/ub__driver_job_offers_pill_tag"
    const val RADAR_PILL_CONTAINER_RES_ID = "com.ubercab.driver:id/ub__map_controls_bottom_center_container"
    const val RADAR_BROWSE_CONTAINER_RES_ID = "com.ubercab.driver:id/ub_driver_offers_browse_pill_container"
    const val PRIMARY_TOUCH_AREA_RES_ID = "com.ubercab.driver:id/primary_touch_area"
    const val UPFRONT_OFFER_PROGRESS_RES_ID = "com.ubercab.driver:id/ub__upfront_offer_details_progress"
    const val UPFRONT_OFFER_PROGRESS_TEXT_RES_ID = "com.ubercab.driver:id/ub__upfront_offer_details_progress_text"
    const val UPFRONT_OFFER_TRAILING_TEXT_RES_ID = "com.ubercab.driver:id/ub__upfront_offer_generic_info_item_trailing_text"
    const val ONLINE_VIEW_RES_ID = "com.ubercab.driver:id/online_view"
}
