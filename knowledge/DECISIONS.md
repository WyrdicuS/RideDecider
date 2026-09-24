# Decisiones Arquitectonicas

> Registro de decisiones que deben mantenerse entre sesiones.

---

## Fases cerradas — Resumen historico

### 8.10 — Smart Profitability Engine 2.0
- TripProfitabilityClassifier: 4 niveles (EXCELLENT/GOOD/ACCEPTABLE/BAD) + score 0-100
- 5 componentes ponderados: hourly 35%, distance 25%, netProfit 20%, pickup 10%, timeUtilization 10%
- DecisionEngine: 7 hard rules de rechazo (pickup distance, pickup time, km rate, effective km rate, hourly rate, net profit, net hourly rate)
- Archivos clave: `TripProfitabilityClassifier.kt`, `DecisionEngine.kt`, `ProfitabilityConfig.kt`

### 8.11 — Learning Data Foundation
- DecisionSnapshotEntity (Room, 38 columnas): fotografia inmutable de la decision en t0
- Contexto temporal incluido: dayOfWeek, hourOfDay, minuteOfHour, timeBucket
- Campos Waze preparados pero vacios
- Archivos clave: `DecisionSnapshotEntity.kt`, `DecisionSnapshotDao.kt`

### 8.12 — Actual Trip Reconciliation
- updateSnapshotActuals() actualiza campos actual* en snapshot tras completar viaje
- calculateReconciliationMetrics() calcula deltas (duracion, distancia, fare)
- Archivos clave: `DecisionSnapshotEntity.kt`, `EarningsTracker.kt`

### 8.13 — Offer Ingestion Protection
- Deduplicacion semantica por firma de oferta
- consumedOfferSignature evita re-procesamiento
- Clasificacion de pantalla (UberOfferScreenType)
- OCR suppression cuando Route A es suficiente
- Archivos clave: `UberAccessibilityProcessor.kt`, `UberOfferValidator.kt`

### 8.15 — Goal Context
- GoalContextEvaluator: targetPaceRatio, goalContribution, timeConsumption
- DriverGoals: daily/weekly/monthly con targetEur + plannedHours
- activeHourlyTarget = targetEur / plannedHours (fallback 24.0)
- Archivos clave: `GoalContextEvaluator.kt`, `GoalContextMetrics.kt`, `DriverGoals.kt`, `TargetProgressCalculator.kt`

---

## Decisiones de 8.16 (auditoria)

### D1 — Separacion en 4 capas
SPE (congelado) → OpportunityEvaluator (nuevo, categorico) → Goals (informativo) → Presentacion (bifurcada)

### D2 — OpportunityEvaluator: sin score numerico
Emite decision categorica (ACCEPT/CONSIDER/DECLINE) con confianza (HIGH/MEDIUM/LOW) y razon textual. No crea otro profitabilityScore.

### D3 — Independencia de metas
OpportunityEvaluator NO lee targetEur, plannedHours ni EarningsProgress. Las metas se muestran en paralelo como informacion.

### D4 — Hard rules inviolables en modo manual
En modo manual, las hard rules de SPE no se flexibilizan. En modo automatico, el OpportunityEvaluator puede recomendar CONSIDER para oportunidades excepcionales que SPE rechaza por pickup.

### D5 — Punto de insercion
OpportunityEvaluator se invoca DESPUES de DecisionEngine.evaluate(), no dentro de el. Preferiblemente en UberAccessibilityProcessor o un wrapper.

### D6 — Caso de validacion: viaje 137,05 EUR
Pickup 9.4km/12min, trip 116.3km/1h44min, ~70.9 EUR/h. SPE rechaza por pickup. OpportunityEvaluator debe poder recomendar CONSIDER comparando contra historial del conductor.

### D7 — R1 debe resolverse antes de implementar
Desacoplar minGrossHourlyRate de activeHourlyTarget en UberAccessibilityService antes de implementar el motor automatico.

### D8 — Manual vs Automatico: modelos distintos
El HUD automatico no es el manual recoloreado. Diferente modelo de datos, diferente mapper, diferente semantica (accion vs rentabilidad).
