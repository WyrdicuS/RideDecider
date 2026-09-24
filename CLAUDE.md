# CLAUDE.md — RideDecider

> **Inicio de sesion**: Leer este archivo, luego `knowledge/CURRENT_STATE.md` y `knowledge/CURRENT_TASK.md`.
> Solo inspeccionar codigo cuando la tarea lo requiera. No escanear el repositorio completo.

---

## Proyecto

- **RideDecider** — App Android nativa (Kotlin, Jetpack Compose), API 24+
- Conductores Uber/Bolt — analiza ofertas en tiempo real para decisiones economicas
- Version actual: v1.0.1

---

## Arquitectura (mapa de componentes)

**Pipeline**: UberAccessibilityService → UberAccessibilityProcessor (11 pasos) → UberAccessibilityParser (Route A) / OCR (Route B) → UberOfferValidator → RawUberTripOfferMapper → DecisionEngine → HUD

**Motor economico**: DecisionEngine (7 hard rules + metricas) → TripProfitabilityClassifier (SPE 2.0: 4 niveles + score 0-100) → TripEvaluation

**Seguimiento**: EarningsTracker → TripLifecycleStateMachine → TargetProgressCalculator → EarningsProgress

**Metas**: DriverGoals (daily/weekly/monthly targetEur + plannedHours) → GoalContextEvaluator → GoalContextMetrics

**Persistencia**: Room v4 — RecordedTripEntity, DriverGoalsEntity, DecisionSnapshotEntity (38 cols, Learning Data Foundation)

**HUD**: HudStateHolder (StateFlow) → HudUiModelMapper → HudUiModel → HudCardOverlay (Compose, WCAG AAA, click-through)

**Otros**: KinematicsSource (6 niveles de confianza), deduplicacion por firma, instanceId, OTA, release signing

---

## Fases cerradas (8.10–8.15) — NO releer salvo dependencia directa

Todas CERRADAS Y CONGELADAS. Resumen en `knowledge/DECISIONS.md`.

| Fase | Nombre | Restriccion principal |
|------|--------|-----------------------|
| 8.10 | SPE 2.0 | No modificar formulas, pesos, thresholds, hard rules, score, contrato |
| 8.11 | Learning Data Foundation | No alterar schema ni logica de snapshot |
| 8.12 | Trip Reconciliation | No modificar logica de reconciliacion |
| 8.13 | Ingestion Protection | No redisenar ingesta. Solo inspeccionar si hay dependencia directa |
| 8.14 | Audit | Informativo |
| 8.15 | Goal Context | No modificar evaluador ni metricas de meta |

---

## Restricciones permanentes

### SPE 2.0 — CONGELADO
- No modificar: formulas, pesos (35/25/20/10/10), thresholds, 7 hard rules, profitabilityScore, profitabilityLevel, contrato TripEvaluation
- **No crear un segundo profitability score**

### Ingestion (8.13) — CONGELADO
- No redisenar: package filtering, active trip blocking, screen classification, OCR suppression, deduplicacion, consumedOfferSignature

### Metas — Principio fundamental
- Las metas son seguimiento/progreso. Son **informativas**
- NO deben decidir si una oferta es buena
- Una meta de 200 EUR no debe aceptar una mala oferta porque falte dinero

### Demanda / Contexto
- No integrar Waze ni APIs externas sin autorizacion explicita
- No hardcodear horarios de trafico como reglas
- Senales de demanda solo de datos observables propios (decision_snapshots)

### Historial
- No convertir en copia de Uber Earnings
- Datos para: analisis, aprendizaje, auditoria, reconciliacion

---

## Reglas de trabajo

### Inspeccion
1. Leer CLAUDE.md + knowledge/CURRENT_STATE.md + knowledge/CURRENT_TASK.md
2. Identificar archivos/simbolos necesarios para la tarea
3. NO escanear repositorio completo, NO releer fases cerradas, NO revisar archivos no relacionados

### Git
- No commit ni push sin autorizacion explicita
- No modificar archivos no relacionados con la tarea actual
- No refactors oportunistas

### Tests
- Fases cerradas: tests validados, no re-ejecutar salvo que una modificacion actual les afecte
- Nuevas fases: tests especificos + build + validacion fisica cuando corresponda

### Cierre de fase
1. Documentar resultado, tests, build, validacion fisica
2. Actualizar CLAUDE.md y knowledge/CURRENT_STATE.md
3. Marcar CERRADA en CLAUDE.md

### UI
- Primero arquitectura, logica, datos, tests. Diseno/UX despues

---

## Dispositivos fisicos

| Dispositivo | Modelo | Android | ADB | Uso |
|-------------|--------|---------|-----|-----|
| TCL 10 Pro | T770H | 11 / API 30 | `f77a3a40` | Principal |
| Realme | RMX3771 | 15 / API 35 | — | Solo si requiere API 35 |
