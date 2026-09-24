# R5 — Architectural Specification: Manual vs Automatic

> Fecha: 2026-09-07
> Fase: 8.16 — Automatic Decision Architecture
> Tipo: Analisis arquitectonico — SIN MODIFICACION DE CODIGO
> Prerequisitos: R4.10 completado (classify() descontaminado), SPE 2.0 FROZEN, OpportunityEvaluator FROZEN R4

---

## 1. Executive Conclusion

OpportunityAssessment ES SUFICIENTE para R5. No se necesita un segundo score ni un segundo evaluador.

R5 implementa la bifurcacion en la capa de **presentacion**, no en la capa de **evaluacion**. El pipeline compartido (SPE → OpportunityEvaluator) produce los mismos datos para ambos modos. Lo que cambia es como la presentacion selecciona, prioriza y muestra esos datos.

La implementacion se reduce a:
1. Un enum `DecisionMode` (MANUAL/AUTOMATIC) con persistencia ligera.
2. Un `HudUiModelMapper` bifurcado que selecciona distintos datos primarios segun el modo.
3. Un `HudUiModel` extendido con campos de OpportunityAssessment para el modo Automatic.
4. Un mecanismo UI para cambiar de modo.

No se crea ningun evaluador nuevo. No se modifica el pipeline de evaluacion. No se introduce ningun score nuevo.

---

## 2. Product Definition

**RideDecider recomienda el mejor viaje posible.**

Dos modos de operacion responden a dos preguntas diferentes:

| | Manual | Automatic |
|---|---|---|
| **Pregunta** | "¿Que viaje me ayuda mejor a alcanzar MI objetivo?" | "¿Es esta una buena oportunidad economica intrinseca?" |
| **Paradigma** | Objective-driven optimization | Intrinsic economic opportunity evaluation |
| **Consume Goals** | Si — como contexto informativo separado | No — completamente independiente |
| **Senyal primaria** | profitabilityLevel → tier/color (informativo) | Recommendation → accion (TAKE/EVALUATE/SKIP) |
| **Senyal secundaria** | GoalContextMetrics (ritmo, contribucion, estado) | OpportunityQuality → color (EXCEPTIONAL/GOOD/MARGINAL/POOR) |
| **Audiencia** | Conductor con objetivo definido que quiere contexto | Conductor que quiere una senyal de accion rapida |

Ambos modos comparten exactamente la misma evaluacion economica. La diferencia es exclusivamente de presentacion.

---

## 3. Manual Definition

Manual muestra la valoracion intrinseca como informacion primaria, con contexto de Goals como informacion separada adicional.

**Datos presentados:**
1. **Tier visual** (color): derivado de profitabilityLevel (EXCELLENT/GOOD/ACCEPTABLE/BAD) — refleja calidad intrinseca.
2. **Economia**: fare, grossPerHour, grossPerKm, netProfit.
3. **Contexto de objetivo**: goalPaceText (ritmo vs objetivo), goalContributionText (cuanto aporta al restante), goalStatusText (adelantado/retrasado/en ritmo/alcanzado).
4. **Decision SPE**: ACCEPT/REJECT/UNKNOWN con razones.
5. **Detalles de recogida y viaje**: pickup summary, trip summary, total distance, total duration.

**Lo que Manual NO hace:**
- No modifica OpportunityQuality basandose en Goals.
- No modifica Recommendation basandose en Goals.
- No modifica Confidence basandose en Goals.
- No modifica profitabilityLevel basandose en Goals (R4.10 elimino esta contaminacion).
- No modifica profitabilityScore basandose en Goals.
- No muestra Recommendation como senyal de accion primaria.

**Manual sin Goals configurados:** Muestra exactamente la misma informacion economica intrinseca (tier, fare, metricas). Los campos de goalContext aparecen vacios/ocultos. Funciona como un modo analitico puro.

**GoalContextEvaluator:** ES SUFICIENTE para Manual. No se necesita ManualContextEvaluator. GoalContextEvaluator ya produce targetPaceRatio, estimatedGoalContribution, estimatedTimeConsumption, progressStatus, remainingEur, remainingHours, requiredHourlyRate — todos los datos que Manual necesita para contextualizar.

---

## 4. Automatic Definition

Automatic evalua la calidad intrinseca de una oferta: maximizar dinero generado minimizando tiempo y distancia. Independiente de Goals.

**Datos presentados:**
1. **Recommendation** (senyal primaria de accion): TAKE / EVALUATE / SKIP.
2. **OpportunityQuality** → color/tier visual: EXCEPTIONAL / GOOD / MARGINAL / POOR.
3. **Confidence**: HIGH / MEDIUM / LOW — visible cuando no es HIGH.
4. **Economia clave**: fare, grossPerHour, grossPerKm.
5. **Override**: si speOverridden, mostrar overrideJustification.

**Lo que Automatic NO muestra:**
- NO goalPaceText, goalContributionText, goalStatusText.
- NO profitabilityLevel como etiqueta primaria (OpportunityQuality toma ese rol).
- NO informacion de progreso de jornada.
- NO contexto de cuanto falta para el objetivo.

**Automatic con Goals configurados:** Ignora completamente los Goals. La valoracion es identica a la de un conductor sin Goals. El conductor puede cambiar a Manual si quiere ver contexto de objetivo.

**Propiedad de determinismo:** Para la misma oferta + misma ProfitabilityConfig + mismo kinematicsSource, dos conductores con Goals diferentes obtienen exactamente la misma Recommendation, OpportunityQuality, y Confidence en Automatic.

---

## 5. Shared Architecture

```
Trip
 ↓
SPE 2.0 (DecisionEngine.evaluate)        ← FROZEN
 ├── 7 hard rules → ACCEPT/REJECT/UNKNOWN   ← FROZEN
 ├── calculateScore() → profitabilityScore   ← FROZEN
 ├── classify() → profitabilityLevel         ← CLEAN (R4.10)
 ├── GoalContextEvaluator → goalContext      ← FROZEN
 └── Context reasons (INFORMATIONAL)         ← FROZEN
 ↓
TripEvaluation (metrics, decision, reasons, profitabilityLevel, goalContext)
 ↓
OpportunityEvaluator.evaluate              ← FROZEN R4
 ↓
OpportunityAssessment (quality, recommendation, confidence, speDecision, speOverridden)
 ↓
┌──────────────────────────────────────────┐
│              DecisionMode                │
├──────────────┬───────────────────────────┤
│   MANUAL     │       AUTOMATIC           │
├──────────────┼───────────────────────────┤
│ HUD Mapper   │     HUD Mapper            │
│ (Manual)     │     (Automatic)           │
├──────────────┼───────────────────────────┤
│ HudUiModel   │     HudUiModel            │
│ Manual       │     Automatic             │
├──────────────┼───────────────────────────┤
│ HUD Overlay  │     HUD Overlay           │
│ (analitico)  │     (decisional)          │
└──────────────┴───────────────────────────┘
```

**Punto critico:** La bifurcacion ocurre DESPUES de OpportunityAssessment. Todo lo anterior es compartido e invariante entre ambos modos.

El pipeline produce los mismos TripEvaluation y OpportunityAssessment independientemente del modo. El modo solo afecta como el mapper selecciona y formatea los datos para el HUD.

---

## 6. Manual Branch

**Flujo de datos:**

```
TripEvaluation ──────────────────────────┐
  .profitabilityLevel → HudVisualTier    │
  .metrics → fare, €/h, €/km            │──► HudUiModel (Manual)
  .goalContext → pace, contribucion      │
  .reasons → texto formateado            │
  .decision → ACCEPT/REJECT/UNKNOWN      │
OpportunityAssessment ───────────────────┘
  (disponible pero NO es senyal primaria)
```

**Tier visual (color):** `profitabilityLevel` → `HudVisualTier` (como actualmente).
- EXCELLENT → Kinetic Iris
- GOOD → Mint Emerald
- ACCEPTABLE → Warm Amber
- BAD → Coral Crimson

**Goal context (separado):**
- `goalPaceText`: "138% ritmo" — comparacion con requiredHourlyRate
- `goalContributionText`: "12.5% del restante" — cuanto aporta esta oferta
- `goalStatusText`: "Adelantado" / "En ritmo" / "Retrasado" / "Objetivo alcanzado"

**Sin Goals configurados:** Los tres campos de goal context son null → no se renderizan. El HUD muestra solo economia intrinseca. Visualmente identico a una version "solo economia".

---

## 7. Automatic Branch

**Flujo de datos:**

```
OpportunityAssessment ───────────────────┐
  .recommendation → TAKE/EVALUATE/SKIP  │
  .quality → tier visual (color)         │──► HudUiModel (Automatic)
  .confidence → badge/indicador          │
  .speOverridden → indicador override    │
  .reasoning → texto                     │
TripEvaluation ──────────────────────────┘
  .metrics → fare, €/h, €/km
  (.goalContext → IGNORADO)
  (.profitabilityLevel → NO usado)
```

**Senyal primaria de accion:** `Recommendation`
- TAKE → "TOMAR" — la oferta pasa todos los criterios economicos y de confianza
- EVALUATE → "EVALUAR" — la oferta tiene merito pero requiere juicio humano (override operacional, confianza baja, calidad marginal)
- SKIP → "PASAR" — la oferta no alcanza el suelo economico o tiene datos insuficientes

**Tier visual (color):** `OpportunityQuality` → `HudVisualTier`
- EXCEPTIONAL → EXCELLENT (Kinetic Iris)
- GOOD → GOOD (Mint Emerald)
- MARGINAL → ACCEPTABLE (Warm Amber)
- POOR → BAD (Coral Crimson)

**Confidence:** Visible cuando es MEDIUM o LOW.
- HIGH → no mostrado (es el caso normal)
- MEDIUM → indicador discreto "Confianza media"
- LOW → indicador prominente "Confianza baja"

**Override:** Si `speOverridden == true`, mostrar indicador de reconsideracion con `overrideJustification`.

---

## 8. OpportunityAssessment Contract

**Estado actual — FROZEN R4:**

```kotlin
data class OpportunityAssessment(
    val quality: OpportunityQuality,        // EXCEPTIONAL, GOOD, MARGINAL, POOR
    val recommendation: Recommendation,     // TAKE, EVALUATE, SKIP
    val confidence: Confidence,             // HIGH, MEDIUM, LOW
    val speDecision: Decision,              // ACCEPT, REJECT, UNKNOWN
    val speOverridden: Boolean,
    val overrideJustification: String?,
    val reasoning: List<String>
)
```

**Analisis de suficiencia para R5:**

| Campo | Necesario para Automatic | Necesario para Manual | Suficiente |
|---|---|---|---|
| quality | SI — tier visual | Disponible pero no primario | SI |
| recommendation | SI — senyal primaria | Disponible pero no primario | SI |
| confidence | SI — modificador | Disponible | SI |
| speDecision | SI — invariante | SI — invariante | SI |
| speOverridden | SI — indicador | SI — indicador | SI |
| overrideJustification | SI — explicacion | SI — explicacion | SI |
| reasoning | SI — detalle | SI — detalle | SI |

**Veredicto:** OpportunityAssessment contiene TODA la informacion necesaria para R5 Automatic. No requiere extension ni nuevo contrato.

---

## 9. GoalContext Contract

**Estado actual — FROZEN 8.15:**

```kotlin
data class GoalContextMetrics(
    val targetPaceRatio: Double?,           // grossPerHour / requiredHourlyRate
    val estimatedGoalContribution: Double?, // rawFare / remainingEur
    val estimatedTimeConsumption: Double?,   // totalDurationMinutes / (remainingHours * 60)
    val progressStatus: ProgressStatus,      // TARGET_REACHED, AHEAD, ON_TRACK, BEHIND
    val remainingEur: Double,
    val remainingHours: Double,
    val requiredHourlyRate: Double
)
```

**Uso en R5:**

| Campo | Manual | Automatic |
|---|---|---|
| targetPaceRatio | SI — "138% ritmo" | NO |
| estimatedGoalContribution | SI — "12.5% del restante" | NO |
| estimatedTimeConsumption | Disponible, no necesariamente mostrado | NO |
| progressStatus | SI — "Adelantado" | NO |
| remainingEur | Disponible para detalle | NO |
| remainingHours | Disponible para detalle | NO |
| requiredHourlyRate | Disponible para detalle | NO |

**Veredicto:** GoalContextMetrics es suficiente para Manual. No se necesita ManualContextEvaluator. GoalContextEvaluator ya produce toda la informacion necesaria.

---

## 10. Metrics Used by Automatic

### Analisis de metricas disponibles

| Metrica | Clasificacion | Razon |
|---|---|---|
| grossPerHour | **PRIMARIA** | Eficiencia del tiempo invertido. Metrica principal de OpportunityEvaluator para determinar quality. Determina cuanto dinero genera el conductor por cada hora de trabajo. |
| grossPerKm | **PRIMARIA** | Eficiencia de la distancia recorrida. Usada por OpportunityEvaluator como co-requisito para GOOD. Determina cuanto dinero genera por km de desgaste del vehiculo. |
| netProfit | **SECUNDARIA** | Beneficio absoluto despues de costes. Validador: una oferta puede tener buen ratio pero beneficio total insuficiente. |
| netPerHour | **SECUNDARIA** | Eficiencia neta por hora. Ya capturada implicitamente en las hard rules (REJECT_LOW_NET_HOURLY_RATE). |
| pickupDistanceRatio | **SECUNDARIA** | Fraccion de distancia total en recogida no remunerada. Penaliza ofertas con pickup proporcionalmente grande. |
| pickupTimeRatio | **SECUNDARIA** | Fraccion de tiempo total en recogida no remunerada. Ya capturada en profitabilityScore (componente timeUtilization). |
| effectiveGrossPerKm | **SECUNDARIA** | Variante de grossPerKm que penaliza pickup. Capturada en profitabilityScore. |
| totalDistance | **REDUNDANTE** | Ya capturada implicitamente en grossPerKm (fare/totalDistance). |
| totalDuration | **REDUNDANTE** | Ya capturada implicitamente en grossPerHour (fare/totalDuration * 60). |
| pickupDistance | **REDUNDANTE** | Ya capturada en pickupDistanceRatio y hard rules (REJECT_EXCESSIVE_PICKUP_DISTANCE). |
| pickupDuration | **REDUNDANTE** | Ya capturada en pickupTimeRatio y hard rules (REJECT_EXCESSIVE_PICKUP_TIME). |
| pickupSpeed | **INFORMATIVA** | Derivada de pickupDistance/pickupDuration. Usada en profitabilityScore. No determina quality directamente. |
| grossProfit | **INFORMATIVA** | Raw fare antes de costes. Se muestra al conductor pero no determina quality (grossPerHour y grossPerKm ya lo normalizan). |
| estimatedOperatingCost | **INFORMATIVA** | Ya restada en netProfit. |
| profitabilityScore | **INFORMATIVA** | Score cuantitativo 0-100 del SPE. Disponible pero OE no lo usa para quality. Puede mostrarse como detalle. |

### Riesgo de doble penalizacion

**pickupDistance:** Aparece en (a) hard rule REJECT_EXCESSIVE_PICKUP_DISTANCE y (b) effectiveGrossPerKm penalizacion y (c) pickupRatio en profitabilityScore.
- **NO es doble penalizacion real:** (a) es binaria — dispara REJECT y corta el flujo antes de quality. (b) y (c) son graduales y solo aplican a ofertas aceptadas. Diferentes mecanismos para diferentes propositos.

**grossPerHour:** Aparece en (a) hard rule REJECT_LOW_HOURLY_RATE y (b) OpportunityEvaluator quality y (c) profitabilityScore hourlyComponent y (d) classify() hourlyRatio.
- **Riesgo bajo:** (a) corta antes de quality. (b), (c), (d) son evaluaciones graduales sobre metricas aceptadas. Sin embargo, la dominancia de grossPerHour en multiples capas refuerza la **mono-dimensionalidad documentada en R4.3**. Esto no es un defecto — es una decision de diseno consciente: grossPerHour es la metrica mas relevante para la eficiencia temporal del conductor.

### Metricas que Automatic MUESTRA pero no evalua

Automatic muestra fare, grossPerHour, grossPerKm como datos informativos. La evaluacion de quality ya fue hecha por OpportunityEvaluator internamente. El HUD solo presenta el resultado (quality, recommendation, confidence) junto con los numeros clave para que el conductor pueda verificar visualmente.

---

## 11. Metrics Used by Manual

Manual usa todas las metricas que Automatic usa, MAS:

| Metrica adicional | Fuente | Proposito |
|---|---|---|
| targetPaceRatio | GoalContextMetrics | "¿Este viaje me acerca al ritmo necesario?" |
| estimatedGoalContribution | GoalContextMetrics | "¿Cuanto aporta al objetivo restante?" |
| progressStatus | GoalContextMetrics | "¿Voy adelantado o retrasado?" |
| profitabilityLevel | TripEvaluation | Tier visual primario (color) |
| reasons (formateadas) | TripEvaluation | Detalle de por que se acepto/rechazo |
| totalDistance, totalDuration | EvaluationMetrics | Detalle analitico |
| pickupSummary, tripSummary | Trip | Contexto de recorrido |
| passengerRating | Trip | Informacion de pasajero |

La diferencia clave: Manual muestra MAS informacion, no informacion DIFERENTE en la evaluacion subyacente.

---

## 12. Recommendation Semantics

| Recommendation | Significado | Contexto Automatic | Contexto Manual |
|---|---|---|---|
| **TAKE** | La oferta pasa todos los criterios economicos, confidence >= MEDIUM, calidad >= GOOD | Senyal primaria: "Tomar esta oferta" | Disponible pero no senyal primaria |
| **EVALUATE** | La oferta tiene merito pero requiere juicio humano: override operacional, confianza baja, calidad marginal, o SPE ACCEPT con calidad no suficiente para TAKE | Senyal: "Considerar — revisar detalles" | Disponible como dato |
| **SKIP** | La oferta no alcanza el suelo economico o tiene datos insuficientes | Senyal: "Pasar — no vale la pena" | Disponible como dato |

**Invariante critico:** Recommendation es identica en ambos modos. No se modifica basandose en DecisionMode. Es un producto de OpportunityEvaluator que no conoce el modo.

**Reglas de produccion de Recommendation (de OpportunityEvaluator, FROZEN):**

Para SPE ACCEPT:
- quality == POOR → SKIP
- quality == MARGINAL → EVALUATE
- confidence == LOW → EVALUATE
- else → TAKE

Para SPE REJECT (operacional, override):
- Override exitoso → EVALUATE (nunca TAKE)
- Override fallido → SKIP

Para SPE REJECT (economico/data_quality):
- Siempre SKIP

Para SPE UNKNOWN:
- Siempre SKIP

---

## 13. Quality Semantics

| OpportunityQuality | Criterio actual (OE FROZEN) | Significado |
|---|---|---|
| **EXCEPTIONAL** | hourlyRatio >= 2.0 (>=48 €/h con min=24) | Oportunidad extraordinaria — rentabilidad muy por encima de lo normal |
| **GOOD** | hourlyRatio >= 1.25 AND kmRatio >= 1.0 (>=30 €/h y >= 1.10 €/km) | Buena oportunidad — supera thresholds con margen |
| **MARGINAL** | hourlyRatio >= 1.0 (>=24 €/h) | Aceptable pero ajustada — cumple minimos |
| **POOR** | hourlyRatio < 1.0 | Insuficiente — no alcanza thresholds |

**Mono-dimensionalidad (R4.3):** La quality esta fuertemente dominada por grossPerHour. kmRatio solo es co-requisito para GOOD (no para EXCEPTIONAL). Esto significa que un viaje con 50 €/h y 0.90 €/km seria EXCEPTIONAL aunque la eficiencia por km es mala.

**Implicacion:** Esta mono-dimensionalidad es una limitacion conocida y documentada. R5 NO la corrige porque:
1. No hay datos reales de produccion para calibrar mejor.
2. grossPerHour ES la metrica mas relevante para un conductor (su tiempo es el recurso mas valioso).
3. Corregir sin evidencia introduce riesgo de regresion.
4. La calibracion debe hacerse con datos historicos reales (R6/futuro).

---

## 14. Confidence Semantics

| Confidence | Criterio actual (OE FROZEN) | Efecto |
|---|---|---|
| **HIGH** | EXPLICIT_DUAL kinematics | Datos fiables de ambas distancias. Recommendation no degradada |
| **MEDIUM** | UNIFIED_INFERRED, EXPLICIT_ZERO_PICKUP, LEGACY_UNSPECIFIED. O HIGH degradada por sampleSize < 20 | Datos razonables pero incompletos. Recommendation no degradada |
| **LOW** | OCR_SUSPECT, MISSING. O MEDIUM degradada por sampleSize < 20 | Datos poco fiables. Recommendation degradada a EVALUATE (nunca TAKE con LOW) |

**Invariante:** Confidence NO depende de DecisionMode. Es identica para ambos modos.

**Presentacion:**
- Manual: Confidence disponible como dato pero no prominente.
- Automatic: Confidence visible como badge cuando es MEDIUM o LOW. LOW activa advertencia prominente.

---

## 15. Override Semantics

**Regla invariante (FROZEN):**

| Tipo de rechazo SPE | Override posible | Razon |
|---|---|---|
| Economic (REJECT_LOW_KM_RATE, etc.) | **NUNCA** | El suelo economico es irrecuperable |
| Data quality (UNKNOWN_MISSING_*) | **NUNCA** | Datos insuficientes para valorar |
| Operational (REJECT_EXCESSIVE_PICKUP_*) | **SI, si economics compensan** | La oferta es intrinsecamente buena, solo el pickup es largo |

**Condiciones para override operacional (OE FROZEN):**
1. Todas las metricas economicas pasan (hourlyRatio >= 1.0, kmRatio >= 1.0, netProfitRatio >= 1.0, netHourlyRatio >= 1.0)
2. hourlyRatio >= OVERRIDE_MIN_HOURLY_RATIO (1.5 → >= 36 €/h con min=24)

**Resultado del override:**
- speOverridden = true
- recommendation = EVALUATE (NUNCA TAKE)
- quality = determinada por metricas (puede ser EXCEPTIONAL)

**Presentacion en ambos modos:**
- Manual: "Rechazo operativo reconsiderado — rentabilidad compensa recogida" + overrideJustification
- Automatic: recommendation=EVALUATE + indicador de override + overrideJustification

**Invariante critico:** Override nunca produce TAKE. Override nunca se convierte en auto-ejecucion, ni ahora ni en la futura Execution Gate.

---

## 16. Null / Unknown / Missing Data

| Escenario | OpportunityAssessment | Presentacion Manual | Presentacion Automatic |
|---|---|---|---|
| SPE UNKNOWN | POOR / SKIP / LOW | BAD tier + razones de data quality | SKIP + POOR tier + "Datos insuficientes" |
| metrics == null | MARGINAL / EVALUATE / LOW (ACCEPT) o POOR / SKIP / LOW (other) | Tier segun profitabilityLevel + "Sin metricas" | EVALUATE o SKIP + tier + "Sin metricas" |
| OpportunityAssessment == null | NO DEBERIA OCURRIR (OE siempre produce resultado) | Fallback: mostrar solo TripEvaluation | Fallback: no mostrar recommendation |
| kinematicsSource == OCR_SUSPECT | confidence = LOW → recommendation degradada a EVALUATE | Tier + "Confianza baja (OCR)" | EVALUATE + POOR/MARGINAL tier + badge LOW |
| kinematicsSource == MISSING | confidence = LOW | Tier + "Datos cinemáticos ausentes" | EVALUATE o SKIP + badge LOW |
| goalContext == null | N/A (no afecta OA) | Sin campos de goal (ocultos) | N/A (nunca se muestran) |

**Principio conservador:** Ante datos insuficientes, la presentacion debe degradar la senyal, no inventar una recomendacion. SKIP y LOW son las respuestas seguras.

---

## 17. DecisionMode

### Definicion

```kotlin
enum class DecisionMode {
    MANUAL,
    AUTOMATIC
}
```

### Persistencia

- **Mecanismo:** SharedPreferences (clave simple, no Room).
- **Razon:** Es una preferencia de UI, no un dato de dominio. No necesita migraciones ni schema.
- **Default:** MANUAL.
- **Persiste entre:** reinicios de app, reinicios de servicio, actualizaciones.

### Comportamiento

| Evento | Comportamiento |
|---|---|
| App primera instalacion | MANUAL |
| Reinicio de app | Lee de SharedPreferences → ultimo modo seleccionado |
| Reinicio de AccessibilityService | Lee de SharedPreferences → ultimo modo seleccionado |
| Cambio de modo durante evaluacion | La evaluacion en curso usa el modo vigente al inicio de esa evaluacion. El nuevo modo aplica a la siguiente oferta |
| Cambio de modo sin oferta activa | Inmediato. Si hay un HUD visible, se re-renderiza con el nuevo modo |

### Localizacion del estado

```
UberAccessibilityService
  └── lee DecisionMode de SharedPreferences
  └── pasa a HudStateHolder

HudStateHolder
  └── StateFlow<DecisionMode>
  └── combina con TripEvaluation + OpportunityAssessment
  └── produce HudUiModel segun modo
```

El pipeline de evaluacion (DecisionEngine, OpportunityEvaluator) NO conoce DecisionMode. La bifurcacion ocurre exclusivamente en la capa de presentacion.

---

## 18. Historical Context

**Decision: B — FUERA DE R5.**

**Estado actual:** `historicalContext = null` en ambas rutas del processor (accessibility tree y OCR fallback).

**Razon para excluir de R5:**
1. No hay datos reales de produccion para calcular medianas historicas.
2. La tabla `decision_snapshots` existe (Learning Data Foundation, fase 8.11) pero no tiene queries de agregacion implementadas.
3. Introducir historico sin evidencia real introduce riesgo: una mediana calculada sobre pocas muestras podria degradar (o inflar) quality incorrectamente.
4. OE ya tiene proteccion: `MIN_RELIABLE_SAMPLE_SIZE = 20` y la regla de degradeConfidence.
5. El contrato ya acepta historico — cuando haya datos suficientes, se integra sin cambios en OE.

**Plan futuro (R6+):**
1. Implementar query de agregacion sobre decision_snapshots.
2. Acumular >= 100 viajes evaluados.
3. Calcular medianGrossPerHour, medianGrossPerKm por franja horaria.
4. Validar que la integracion historica mejora la quality (comparar con vs sin).
5. Solo entonces conectar `historicalContext` en UberAccessibilityProcessor.

---

## 19. Presentation Contract

### HudUiModel extension

El HudUiModel actual necesita campos adicionales para Automatic:

```
Campos existentes (Manual):
  tier, decision, fareText, grossPerKmText, grossPerHourText,
  totalDistanceText, totalDurationText, pickupSummaryText, tripSummaryText,
  mainReasonText, reasons, offerType, offerTypeText, isCashPayment,
  passengerRating, goalPaceText, goalContributionText, goalStatusText

Campos nuevos (Automatic):
  decisionMode: DecisionMode
  recommendationText: String?         -- "TOMAR" / "EVALUAR" / "PASAR"
  qualityText: String?                -- "EXCEPCIONAL" / "BUENA" / "MARGINAL" / "POBRE"
  confidenceText: String?             -- null (HIGH) / "Confianza media" / "Confianza baja"
  isOverride: Boolean                 -- speOverridden
  overrideText: String?               -- overrideJustification formateado
```

### HudUiModelMapper bifurcation

El mapper actual (`HudUiModelMapper.map()`) se bifurca internamente:

```
fun map(evaluation, assessment, mode):
  campos_compartidos = fare, grossPerHour, grossPerKm, ...

  if (mode == MANUAL):
    tier = profitabilityLevel → HudVisualTier
    goalPaceText = formatGoalPace(evaluation.goalContext)
    goalContributionText = formatGoalContribution(...)
    goalStatusText = formatGoalStatus(...)
    recommendationText = null
    qualityText = null
    confidenceText = null

  if (mode == AUTOMATIC):
    tier = assessment.quality → HudVisualTier
    goalPaceText = null
    goalContributionText = null
    goalStatusText = null
    recommendationText = formatRecommendation(assessment.recommendation)
    qualityText = formatQuality(assessment.quality)
    confidenceText = formatConfidence(assessment.confidence)
```

**Invariante:** El mapper NO modifica evaluation ni assessment. Solo selecciona que datos presentar.

### Semantica visual por modo

**Manual:**

| Elemento | Fuente | Semantica |
|---|---|---|
| Color de fondo | profitabilityLevel → tier | "Calidad intrinseca de esta oferta" |
| Texto principal | fare | "Cuanto paga" |
| Metricas | grossPerHour, grossPerKm | "Eficiencia" |
| Seccion goal | goalPaceText, goalContributionText, goalStatusText | "Relacion con tu objetivo" |

**Automatic:**

| Elemento | Fuente | Semantica |
|---|---|---|
| Color de fondo | assessment.quality → tier | "Calidad de la oportunidad" |
| Texto de accion | recommendation → "TOMAR"/"EVALUAR"/"PASAR" | "Que deberia hacer" |
| Texto principal | fare | "Cuanto paga" |
| Metricas | grossPerHour, grossPerKm | "Eficiencia" |
| Badge confidence | confidence (si no HIGH) | "Fiabilidad de la recomendacion" |

---

## 20. Execution Boundary

**NO IMPLEMENTAR EN R5.**

**Contrato conceptual para futura Execution Gate (R6+):**

```
Elegible para ejecucion automatica SI Y SOLO SI:
  mode == AUTOMATIC
  AND recommendation == TAKE
  AND !speOverridden
  AND confidence != LOW
```

**Analisis de suficiencia del contrato:**

| Condicion | Razon | Suficiente |
|---|---|---|
| mode == AUTOMATIC | Solo Automatic puede auto-ejecutar | SI |
| recommendation == TAKE | Solo ofertas que pasan todos los criterios economicos y de confianza | SI |
| !speOverridden | Override operacional requiere juicio humano (pickup largo) | SI |
| confidence != LOW | Datos OCR o ausentes no son fiables para auto-ejecucion | SI |

**Condiciones adicionales a considerar en R6:**

| Condicion candidata | Analisis |
|---|---|
| quality >= GOOD | **REDUNDANTE** — Si recommendation == TAKE, quality ya es >= GOOD (por la logica de OE: POOR→SKIP, MARGINAL→EVALUATE) |
| profitabilityScore >= X | **NO DETERMINADO** — Podria anyadir un suelo cuantitativo. Requiere calibracion con datos reales |
| decision == ACCEPT | **REDUNDANTE** — Si recommendation == TAKE y !speOverridden, entonces speDecision == ACCEPT (override produce EVALUATE, no TAKE) |
| kinematicsSource == EXPLICIT_DUAL | **IMPLICITAMENTE CUBIERTA** — EXPLICIT_DUAL → confidence=HIGH. OCR_SUSPECT/MISSING → confidence=LOW → bloqueado. Pero UNIFIED_INFERRED → confidence=MEDIUM → no bloqueado. Evaluar si MEDIUM es suficiente para auto-ejecucion en R6 |

**Lo que R5 debe dejar preparado:**
- OpportunityAssessment contiene recommendation, quality, confidence, speOverridden → ya disponible.
- DecisionMode contiene AUTOMATIC → se implementa en R5.
- R5 NO anida ningun check de Execution Gate. Solo garantiza que los datos estan disponibles.

---

## 21. Test Matrix

### Invariantes (deben ser identicos en Manual y Automatic)

| ID | Caso | OpportunityAssessment esperado | Invariante verificado |
|---|---|---|---|
| T01 | Automatic sin Goals | quality/recommendation/confidence identicos a T02 | Goals no contaminan OA |
| T02 | Automatic con Goals configurados | Mismo resultado que T01 | Determinismo intrinseco |
| T03 | Misma oferta + Goal 200€ vs Goal 120€ | Mismo OA | Independencia de Goals |
| T09 | Economic REJECT | POOR / SKIP / speOverridden=false | Irrecuperable |
| T10 | Operational REJECT + economics fuertes | quality segun economics / EVALUATE / speOverridden=true | Override nunca TAKE |
| T11 | UNKNOWN | POOR / SKIP / LOW | Conservador |
| T23 | 137.05€ (grossPerKm≈1.09) | POOR / SKIP (economic REJECT) | Suelo irrecuperable |
| T24 | 140€ (grossPerKm≈1.11, pickup excesivo) | EXCEPTIONAL / EVALUATE / speOverridden=true | Override correcto |

### Variables por modo (diferente presentacion, misma evaluacion)

| ID | Caso | Manual HUD | Automatic HUD |
|---|---|---|---|
| T04 | Manual sin Goals | Tier economico + goal fields null | N/A (modo incorrecto) |
| T05 | Manual con Goals | Tier + goalPace + goalContribution + goalStatus | N/A |
| T06 | SPE ACCEPT + TAKE | GOOD/EXCELLENT tier + razones positivas | "TOMAR" + GOOD/EXCEPTIONAL tier |
| T07 | SPE ACCEPT + EVALUATE | MARGINAL/ACCEPTABLE tier | "EVALUAR" + MARGINAL tier + razon |
| T08 | SPE ACCEPT + SKIP | POOR tier (raro: ACCEPT+POOR) | "PASAR" + POOR tier |
| T12 | OCR_SUSPECT | Tier segun profitabilityLevel + LOW | "EVALUAR" + tier + badge LOW |
| T13 | LOW confidence | Tier + indicador | "EVALUAR" + tier + badge LOW |
| T14 | Target reached (Goals) | Tier + "Objetivo alcanzado" | Tier + (sin goal info) |
| T15 | requiredHourlyRate muy alto | Tier + "Retrasado" | Tier (sin goal info) |
| T16 | requiredHourlyRate muy bajo | Tier + "Adelantado" | Tier (sin goal info) |

### Casos de oferta (validacion funcional)

| ID | Caso | Metricas clave | Quality esperada | Recommendation esperada |
|---|---|---|---|---|
| T17 | Viaje largo excepcional (140€, 125km, 128min) | grossPerHour≈65.6, grossPerKm≈1.11 | EXCEPTIONAL | EVALUATE (pickup excesivo) |
| T18 | Viaje corto excelente (25€, 5km, 16min) | grossPerHour≈93.75, grossPerKm=5.0 | EXCEPTIONAL | TAKE |
| T19 | Alta €/h + mala €/km (20€, 24km, 22min) | grossPerHour≈54.5, grossPerKm≈0.83 | POOR (economic REJECT) | SKIP |
| T20 | Alta €/km + mala €/h (10€, 5km, 26min) | grossPerHour≈23.1, grossPerKm=2.0 | POOR (economic REJECT) | SKIP |
| T21 | Pickup largo (fare=40€, pickup=6km) | grossPerHour alto, pickup excesivo | EXCEPTIONAL | EVALUATE (override) |
| T22 | Pickup corto (15€, 11.5km, 27min, pickup 1.5km) | grossPerHour≈33.3, grossPerKm≈1.30 | GOOD | TAKE |

### Verificacion de modo

| ID | Caso | Verificacion |
|---|---|---|
| T25 | Default al instalar | mode == MANUAL |
| T26 | Cambio MANUAL → AUTOMATIC | Persiste en SharedPreferences |
| T27 | Reinicio de servicio | Restaura ultimo modo |
| T28 | HUD re-render al cambiar modo | Misma oferta, diferente presentacion |

---

## 22. Risk Analysis

| # | Riesgo | Severidad | Mitigacion |
|---|---|---|---|
| 1 | **Segundo score** — introducir un score de Automatic que compita con profitabilityScore | HIGH | PROHIBIDO. R5 no introduce ningun score nuevo. OpportunityQuality es una clasificacion categorica (enum), no un score numerico. profitabilityScore permanece como unico score cuantitativo. |
| 2 | **Duplicacion del SPE** — crear un segundo motor de evaluacion | HIGH | PROHIBIDO. El pipeline compartido (SPE→OE) es unico. DecisionMode solo bifurca la presentacion, no la evaluacion. |
| 3 | **Doble penalizacion tiempo/distancia** — pickup penalizado multiples veces | LOW | Analizado en seccion 10. Hard rules son binarias (cortan antes de quality). Las penalizaciones graduales son mecanismos complementarios, no redundantes. No se introduce ningun nuevo punto de penalizacion en R5. |
| 4 | **Mono-dimensionalidad €/h** — quality dominada por grossPerHour | MEDIUM | Documentada en R4.3, aceptada como limitacion conocida. No se corrige en R5 sin datos reales. grossPerHour es la metrica mas relevante para eficiencia temporal. Mitigacion futura: calibracion con datos historicos (R6+). |
| 5 | **Dependencia accidental de Goals** | MEDIUM | Mitigacion: R4.10 elimino contaminacion de classify(). OE no recibe Goals. El mapper Automatic ignora goalContext. Test T01/T02/T03 verifican independencia. |
| 6 | **Recomendacion basada en jornada personal** | HIGH | PROHIBIDO. Recommendation se produce en OE que no conoce Goals ni DecisionMode. Invariante verificada por diseno. |
| 7 | **Override operacional confuso** | MEDIUM | speOverridden + overrideJustification se muestran explicitamente en ambos modos. Recommendation siempre EVALUATE (nunca TAKE). Conducto claro: "la app recomienda que lo evalues tu, no que lo tomes automaticamente". |
| 8 | **Confidence OCR** | MEDIUM | OCR_SUSPECT → LOW → recommendation degradada a EVALUATE. Nunca TAKE con LOW. Badge prominente en Automatic. Indicador en Manual. |
| 9 | **Datos faltantes** | LOW | UNKNOWN/MISSING → POOR/SKIP/LOW. Presentacion conservadora. No se inventa oportunidad. |
| 10 | **Historical context prematuro** | MEDIUM | Decision: fuera de R5. No se integra sin datos reales. Contrato ya preparado. |
| 11 | **Estabilidad de la recomendacion** — misma oferta produce diferente resultado en re-evaluaciones rapidas | LOW | Pipeline es determinista: mismos inputs → mismos outputs. El debounce por firma evita re-evaluaciones de la misma oferta. |
| 12 | **Diferencias Manual/Automatic confusas** | MEDIUM | Mitigacion: tier visual usa los mismos 4 colores. La diferencia es semantica (Manual: "calidad intrinseca" vs Automatic: "oportunidad"). El indicador visual de modo activo debe ser claro. |
| 13 | **Futura autoejecucion** | HIGH | R5 NO implementa Execution Gate. Solo garantiza que los datos estan disponibles. El contrato conceptual (seccion 20) establece las 4 condiciones minimas. Override NUNCA es elegible. |
| 14 | **Goals contaminan SPE** | LOW (post R4.10) | classify() ya no recibe economicContext. Hard rules y calculateScore() nunca lo recibieron. Riesgo residual: alguien podria re-introducir el parametro. Mitigacion: tests de independencia existentes + review. |

---

## 23. R5 IMPLEMENTAR

1. **DecisionMode enum** — `MANUAL`, `AUTOMATIC`. Archivo nuevo en `domain/model/`.

2. **DecisionMode persistencia** — SharedPreferences con clave `decision_mode`, default `MANUAL`. Lectura en `UberAccessibilityService` al iniciar. Flow observable para cambios reactivos.

3. **HudUiModel extension** — Anyadir campos: `decisionMode`, `recommendationText`, `qualityText`, `confidenceText`, `isOverride`, `overrideText`.

4. **HudUiModelMapper bifurcacion** — `map()` recibe `OpportunityAssessment` y `DecisionMode`. Selecciona datos segun modo:
   - MANUAL: tier desde profitabilityLevel, goalContext visible.
   - AUTOMATIC: tier desde quality, recommendation visible, goalContext null.

5. **HudStateHolder** — Integrar `DecisionMode` como StateFlow. Combinar con evaluacion para producir HudUiModel segun modo.

6. **HudCardOverlay** — Renderizar campos condicionales segun decisionMode:
   - MANUAL: goalPace, goalContribution, goalStatus visibles.
   - AUTOMATIC: recommendation prominente, confidence badge, goal fields ocultos.

7. **UI de cambio de modo** — Mecanismo minimo (toggle/switch) accesible desde el HUD o settings para cambiar entre MANUAL y AUTOMATIC. Sin diseno pixel-perfect — funcional.

8. **Tests** — Implementar los 28 casos de la Test Matrix (seccion 21). Minimo:
   - T01-T03: independencia de Goals en Automatic.
   - T06-T08: presentacion de Recommendation.
   - T09-T10: invariantes de economic REJECT y override.
   - T23-T24: casos 137/140.
   - T25-T28: persistencia y cambio de modo.

---

## 24. R5 NO IMPLEMENTAR

| Concepto | Razon |
|---|---|
| Execution Gate (auto-accept, auto-click) | R6+ — requiere validacion con datos reales y confianza en el sistema |
| Historical Context integration | R6+ — no hay datos reales para calibrar |
| Calibracion de OE (corregir mono-dimensionalidad) | R6+ — requiere datos historicos de produccion |
| ContextualRelevanceEvaluator | ELIMINADO en R4.8 — no resucitar |
| ManualContextEvaluator | Innecesario — GoalContextEvaluator es suficiente |
| Segundo profitability score | PROHIBIDO por CLAUDE.md |
| Goals como criterio de decision | PROHIBIDO por principio fundamental |
| Ranking de multiples ofertas | Fuera de alcance — solo se procesa una oferta cada vez |
| Integracion Waze / Bolt | PROHIBIDO sin autorizacion |
| Demand API | PROHIBIDO sin autorizacion |
| Quick cancel | Fuera de alcance R5 |
| Cambios en SPE 2.0 | FROZEN |
| Cambios en OpportunityEvaluator | FROZEN R4 |
| Cambios en GoalContextEvaluator | FROZEN 8.15 |
| Cambios en pipeline de ingestion | FROZEN 8.13 |
| Diseno UI pixel-perfect | R5 solo define semantica. Diseno visual despues |
| Room changes | No necesarios para DecisionMode (SharedPreferences) |
| Persistencia de OpportunityAssessment | Futuro — decision_snapshots ya existe pero no almacena OA |

---

## 25. R6 / FUTURO

| Concepto | Prerequisito | Descripcion |
|---|---|---|
| Execution Gate | R5 completado + validacion con datos reales | Implementar el check de 4 condiciones + mecanismo de click |
| Historical Context | >= 100 decision_snapshots acumulados | Agregar medianas por franja horaria, integrar en OE |
| Calibracion multidimensional | Historical Context integrado | Evaluar si kmRatio debe tener mas peso en quality |
| Persistencia de OpportunityAssessment | R5 completado | Extender decision_snapshots para almacenar quality/recommendation/confidence |
| Demand signals | Datos observables acumulados | Senales de demanda derivadas de decision_snapshots (no APIs externas) |
| Advanced Manual presentation | R5 completado + feedback de usuario | Mejorar como Manual presenta goalContext basandose en uso real |
| CONFIDENCE == MEDIUM en Execution Gate | R5 completado + datos | Determinar si MEDIUM es suficiente para auto-ejecucion |

---

## 26. Final Architecture Diagram

```
┌──────────────────────────────────────────────────────────┐
│                    UBER OFFER                            │
│              (AccessibilityService)                      │
└──────────────────────┬───────────────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────────────────┐
│              UberAccessibilityProcessor                  │
│                  (Pipeline 11 pasos)                     │
│                    FROZEN 8.13                           │
└──────────────────────┬───────────────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────────────────┐
│                 DecisionEngine (SPE 2.0)                 │
│                      FROZEN                              │
│                                                          │
│  ┌─────────────┐  ┌───────────────┐  ┌────────────────┐ │
│  │ 7 Hard Rules│  │calculateScore │  │   classify()   │ │
│  │  FROZEN     │  │   FROZEN      │  │  CLEAN (R4.10) │ │
│  └──────┬──────┘  └───────┬───────┘  └───────┬────────┘ │
│         │                 │                   │          │
│  ┌──────┴──────┐  ┌───────┴───────┐  ┌───────┴────────┐ │
│  │  Decision   │  │profitability  │  │profitability   │ │
│  │ACCEPT/REJECT│  │   Score 0-100 │  │    Level       │ │
│  └─────────────┘  └───────────────┘  └────────────────┘ │
│                                                          │
│  ┌──────────────────────┐  ┌───────────────────────────┐ │
│  │ GoalContextEvaluator │  │    Context Reasons        │ │
│  │     FROZEN 8.15      │  │    (INFORMATIONAL)        │ │
│  └──────────┬───────────┘  └───────────────────────────┘ │
│             │                                            │
│  ┌──────────┴───────────┐                                │
│  │  GoalContextMetrics  │                                │
│  └──────────────────────┘                                │
└──────────────────────┬───────────────────────────────────┘
                       │
                       ▼
              ┌────────────────┐
              │ TripEvaluation │
              │  (immutable)   │
              └────────┬───────┘
                       │
                       ▼
┌──────────────────────────────────────────────────────────┐
│            OpportunityEvaluator (FROZEN R4)              │
│                                                          │
│  quality ← hourlyRatio, kmRatio, historicalContext       │
│  recommendation ← quality, confidence, speDecision       │
│  confidence ← kinematicsSource, historicalContext         │
│                                                          │
│  NO recibe: Goals, economicContext, DecisionMode          │
└──────────────────────┬───────────────────────────────────┘
                       │
                       ▼
           ┌───────────────────────┐
           │ OpportunityAssessment │
           │     (immutable)       │
           └───────────┬───────────┘
                       │
           ╔═══════════╧═══════════╗
           ║    DecisionMode       ║  ← SharedPreferences
           ║   MANUAL / AUTOMATIC  ║
           ╚═══════════╤═══════════╝
                       │
          ┌────────────┴────────────┐
          │                         │
          ▼                         ▼
┌──────────────────┐      ┌──────────────────┐
│   MANUAL MODE    │      │ AUTOMATIC MODE   │
│                  │      │                  │
│ Primary:         │      │ Primary:         │
│  profitability   │      │  Recommendation  │
│  Level → Tier    │      │  → TAKE/EVAL/SKIP│
│                  │      │                  │
│ Context:         │      │ Secondary:       │
│  GoalPace        │      │  Quality → Tier  │
│  GoalContrib     │      │  Confidence      │
│  GoalStatus      │      │                  │
│                  │      │ NO Goals         │
│ Shared:          │      │                  │
│  fare, €/h, €/km │      │ Shared:          │
│  reasons         │      │  fare, €/h, €/km │
│  override info   │      │  override info   │
└──────────────────┘      └──────────────────┘
          │                         │
          ▼                         ▼
┌──────────────────────────────────────────────────────────┐
│                  HudCardOverlay (Compose)                │
│                   WCAG AAA, click-through                │
└──────────────────────────────────────────────────────────┘
                       │
                       ▼ (futuro R6+)
           ┌───────────────────────┐
           │   Execution Gate      │
           │   NO IMPLEMENTAR R5   │
           │                       │
           │   mode==AUTOMATIC     │
           │   && rec==TAKE        │
           │   && !speOverridden   │
           │   && conf!=LOW        │
           └───────────────────────┘
```

---

## 27. Final Verdict

**R5 APROBADO PARA IMPLEMENTACION.**

La especificacion cumple las 15 condiciones de exito:

| # | Condicion | Estado |
|---|---|---|
| 1 | Manual y Automatic son conceptualmente distintos | **SI** — Manual: objective-driven, Automatic: intrinsic evaluation |
| 2 | Manual puede utilizar objetivos personales | **SI** — via GoalContextMetrics (separado de quality) |
| 3 | Automatic NO utiliza objetivos personales | **SI** — OE no recibe Goals, mapper Automatic ignora goalContext |
| 4 | Automatic evalua calidad intrinseca | **SI** — OpportunityQuality derivada de ratios economicos puros |
| 5 | Misma oferta = misma valoracion Automatic | **SI** — OE es determinista: mismos metrics+config+kinematics → mismo resultado |
| 6 | Goals no contaminan SPE | **SI** — R4.10 elimino contaminacion de classify(). Hard rules y calculateScore() nunca fueron contaminados |
| 7 | Goals no contaminan OpportunityAssessment | **SI** — OE no recibe Goals, economicContext, ni DecisionMode |
| 8 | Economic REJECT irrecuperable | **SI** — hasEconomicBlock → POOR/SKIP/speOverridden=false |
| 9 | Operational override nunca TAKE | **SI** — Override exitoso → EVALUATE, nunca TAKE |
| 10 | No existe segundo SPE | **SI** — pipeline unico compartido |
| 11 | No existe segundo score sin justificacion | **SI** — OpportunityQuality es enum categorico, no score numerico |
| 12 | No se introduce ContextualRelevanceEvaluator | **SI** — eliminado en R4.8, no resucitado |
| 13 | No se implementa ejecucion automatica | **SI** — Execution Gate explicitamente R6+ |
| 14 | R5 suficientemente especificado para implementacion | **SI** — contrato de presentacion, tests, riesgos, alcance definidos |
| 15 | Partes no determinadas marcadas explicitamente | **SI** — Historical Context: "fuera de R5". Calibracion multidimensional: "requiere datos reales". MEDIUM confidence en Execution Gate: "NO DETERMINADO — REQUIERE DATOS" |

---

**FIN DEL INFORME R5**
**CLASIFICACION: APROBADO PARA IMPLEMENTACION**
**SIGUIENTE: R5 Implementation**
