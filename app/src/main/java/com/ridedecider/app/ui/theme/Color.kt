package com.ridedecider.app.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// RIDEDECIDER DESIGN SYSTEM — CHROMATIC TOKENS (V2 PROFESSIONAL)
// Identidad: Deep Telemetry Obsidian & Kinetic Iris
// ============================================================================

// ----------------------------------------------------------------------------
// 1. FONDOS Y SUPERFICIES ESTRUCTURALES (Profundidad multinivel OLED)
// ----------------------------------------------------------------------------
val RdBgBase = Color(0xFF070A0F)             // Fondo base ultra oscuro (OLED deep obsidian)
val RdBgCanvas = Color(0xFF0A0F16)           // Lienzo principal de pantalla
val RdBgSubtle = Color(0xFF0E141E)           // Fondo secundario / agrupadores suaves

val RdSurface = Color(0xFF111824)            // Superficie base de tarjetas principales
val RdSurfaceElevated = Color(0xFF162030)    // Superficie elevada para subcontenedores y métricas destacadas
val RdSurfaceInteractive = Color(0xFF1C273A) // Superficie para elementos interactivos / inputs / pills
val RdSurfaceGlass = Color(0xF20B111A)       // Superficie traslúcida de alta densidad (HUD / Overlays)
val RdSurfaceCard = RdSurface                // Alias de compatibilidad

// ----------------------------------------------------------------------------
// 2. BORDES, DELIMITADORES Y SEPARADORES DE PRECISIÓN
// ----------------------------------------------------------------------------
val RdBorderSubtle = Color(0xFF1A2434)       // Borde sutil para delimitar tarjetas en reposo
val RdBorderProminent = Color(0xFF26354D)    // Borde prominente para foco, inputs o contenedor activo
val RdBorderHighlight = Color(0xFF3B5072)    // Borde iluminado para elementos interactivos
val RdBorderSubtleAlias = RdBorderSubtle
val RdSurfaceBorder = RdBorderSubtle         // Alias de compatibilidad
val RdSurfaceBorderSubtle = RdBorderSubtle   // Alias de compatibilidad
val RdDivider = Color(0xFF1E2B3E)            // Separadores verticales / horizontales

// ----------------------------------------------------------------------------
// 3. TIPOGRAFÍA Y LECTURAS NUMÉRICAS (Jerarquía de alto contraste WCAG AAA)
// ----------------------------------------------------------------------------
val RdTextPrimary = Color(0xFFF8FAFC)        // 100% contraste legible (Titanium White / Slate 50)
val RdTextSecondary = Color(0xFF94A3B8)      // Texto descriptivo / etiquetas de métricas (Slate 400)
val RdTextTertiary = Color(0xFF64748B)       // Microcopia / metadatos / unidades secundarias (Slate 500)
val RdTextDisabled = Color(0xFF475569)       // Texto deshabilitado o inactivo

// ----------------------------------------------------------------------------
// 4. IDENTIDAD DE MARCA: KINETIC IRIS (Bespoke Tech Identity)
// ----------------------------------------------------------------------------
val RdBrandPrimary = Color(0xFF6366F1)       // Iris eléctrico tecnológico
val RdBrandPrimaryDark = Color(0xFF4338CA)   // Estado presionado / contenedor profundo
val RdBrandPrimaryLight = Color(0xFF818CF8)  // Highlight / Iconos activos / Badges
val RdBrandGlow = Color(0x2E6366F1)          // Resplandor ambiental sutil de marca

// Compatibilidad de marca anterior
val RdBrandPurple = RdBrandPrimary
val RdBrandPurpleDark = RdBrandPrimaryDark
val RdBrandPurpleLight = RdBrandPrimaryLight
val RdBrandPurpleGlow = RdBrandGlow

// ----------------------------------------------------------------------------
// 5. NIVELES DE RECOMENDACIÓN ECONÓMICA (Semántica Funcional Estricta)
// ----------------------------------------------------------------------------
val RdTierExcellent = Color(0xFF818CF8)      // Kinetic Iris (Oportunidad cumbre / Máxima rentabilidad)
val RdTierGood = Color(0xFF34D399)           // Mint Emerald (Viaje altamente rentable)
val RdTierAcceptable = Color(0xFFFBBF24)     // Warm Amber (Marginal / Aceptable con cautela)
val RdTierBad = Color(0xFFF87171)            // Coral Crimson (Poco rentable / Rechazar)

val RdTierExcellentBg = Color(0x26818CF8)
val RdTierGoodBg = Color(0x2634D399)
val RdTierAcceptableBg = Color(0x26FBBF24)
val RdTierBadBg = Color(0x26F87171)

// ----------------------------------------------------------------------------
// 6. ESTADOS DE PROGRESO ECONÓMICO Y RITMO OBJETIVO
// ----------------------------------------------------------------------------
val RdStatusTargetReached = Color(0xFF818CF8)// Meta completada (Iris Milestone)
val RdStatusAhead = Color(0xFF34D399)        // Por delante del ritmo (Mint Emerald)
val RdStatusOnTrack = Color(0xFF38BDF8)      // En ritmo planificado (Cyan Telemetry)
val RdStatusBehind = Color(0xFFFB923C)       // Por debajo del ritmo (Tangerine Alert)

// ----------------------------------------------------------------------------
// 7. ESTADOS DEL SISTEMA, CONEXIÓN Y SERVICIOS
// ----------------------------------------------------------------------------
val RdStatusActive = Color(0xFF34D399)       // Activo / Conectado / Concedido (Emerald)
val RdStatusInactive = Color(0xFFF87171)     // Inactivo / Desconectado / Rechazado (Crimson)
val RdStatusWarning = Color(0xFFFBBF24)      // Advertencia / Permiso pendiente (Amber)
val RdStatusInfo = Color(0xFF38BDF8)         // Informativo neutral (Cyan)

// ----------------------------------------------------------------------------
// 8. ESTADOS DEL CICLO DE VIDA DEL VIAJE (Telemetry State Machine)
// ----------------------------------------------------------------------------
val RdTripStateIdle = Color(0xFF64748B)      // En Espera (Slate)
val RdTripStateOffer = Color(0xFF818CF8)     // Oferta Detectada (Iris)
val RdTripStateAssigned = Color(0xFF38BDF8)  // Viaje Asignado (Cyan)
val RdTripStateActive = Color(0xFFA78BFA)    // En Ruta Activa (Violet)
val RdTripStateCompleted = Color(0xFF34D399) // Viaje Completado (Emerald)
val RdTripStateCancelled = Color(0xFFF87171) // Viaje Cancelado (Crimson)
val RdTripStateExpired = Color(0xFFFB923C)   // Oferta Expirada (Tangerine)

// ----------------------------------------------------------------------------
// 9. DISTINTIVOS DE OFERTA DE UBER
// ----------------------------------------------------------------------------
val RdBadgeRadar = Color(0xFF38BDF8)         // Trip Radar (Cyan Telemetry)
val RdBadgeDirect = Color(0xFF818CF8)        // Oferta Directa (Kinetic Iris)
val RdBadgeCash = Color(0xFFFBBF24)          // Pago en Efectivo (Warm Amber)

// ----------------------------------------------------------------------------
// 10. COMPATIBILIDAD CON MATERIAL THEME 3
// ----------------------------------------------------------------------------
val RdBackground = RdBgBase
val Purple80 = RdBrandPrimaryLight
val PurpleGrey80 = Color(0xFFCBD5E1)
val Pink80 = Color(0xFFFDA4AF)

val Purple40 = RdBrandPrimaryDark
val PurpleGrey40 = Color(0xFF475569)
val Pink40 = Color(0xFF9F1239)