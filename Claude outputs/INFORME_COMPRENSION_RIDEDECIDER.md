# INFORME DE COMPRENSIÓN DEL PROYECTO — RideDecider

> Modo AUDITORÍA. No se ha modificado, creado ni tocado ningún archivo del repositorio. Este documento se ha generado fuera del proyecto y se entrega únicamente como lectura. Fuente de verdad: el código real en `HEAD = 80af3f1` (Fase 8.13).

---

## 1. Qué es RideDecider

Aplicación Android nativa (Kotlin + Jetpack Compose) para conductores de plataformas VTC. Observa en tiempo real la interfaz de **Uber Driver** mediante un `AccessibilityService` (con *fallback* a OCR), extrae los datos de cada oferta de viaje, calcula sus métricas económicas y emite una **recomendación** (ACEPTAR / RECHAZAR / DESCONOCIDO) que se muestra en un **HUD flotante** superpuesto sobre la app de Uber. No es un simple registrador de ganancias: su núcleo es un motor de análisis y decisión económica.

Datos técnicos verificados: `applicationId = com.ridedecider.app`, `versionName = 1.0.1`, `versionCode = 2`, `minSdk = 24`, `targetSdk = 37`, `compileSdk = 37`. Módulo único Gradle (`:app`). ~11.881 líneas de Kotlin en `main`. Dependencias clave: Compose (BOM), Room (con KSP), ML Kit Text Recognition (OCR on-device). Sin Hilt/Dagger: la DI es un `ServiceLocator` manual.

## 2. Qué problema resuelve

Ayuda al conductor a decidir, en los pocos segundos que dura una oferta, si le conviene económicamente aceptarla, considerando facturación, distancias/tiempos de recogida y de viaje, coste operativo, beneficio neto, €/km, €/h y €/h neto, frente a los umbrales que el propio conductor configura mediante un objetivo económico (diario / semanal / mensual). Automatiza un cálculo que de otro modo el conductor tendría que hacer mentalmente y bajo presión.

## 3. Cómo funciona de extremo a extremo

Flujo real reconstruido a partir del código:

`Uber Driver` → `UberAccessibilityService` (captura multi-fuente del árbol de nodos) → `UberNodeSnapshotConverter` (snapshot inmutable) → `UberAccessibilityProcessor` (pipeline) → `UberAccessibilityParser` (extracción de texto → `RawUberTripOffer`) → `UberOfferValidator` (validación + clasificación de pantalla) → *[debounce por firma / consumo semántico]* → `RawUberTripOfferMapper` (→ `Trip` de dominio) → `EvaluateIncomingTripUseCase` → `DecisionEngine` (+ `TripProfitabilityClassifier`) → `TripEvaluation` → `TripEvaluationListener` → **(A)** `HudStateHolder` → `HudUiModelMapper` → `HudOverlayManager` (pinta el HUD) **y (B)** `EarningsTracker.recordEvaluatedOffer` → `TripLifecycleStateMachine` + persistencia en Room (`RecordedTripEntity` + `DecisionSnapshotEntity`).

Cuando el árbol de accesibilidad no basta (sin tarifa detectable, API ≥ 30), el servicio dispara `takeScreenshot()` → ML Kit OCR → `processor.processOcrResult()`, que reentra al mismo pipeline vía `parser.parseFromText()`.

## 4. Arquitectura real encontrada

Clean Architecture por capas dentro del único módulo `:app`, paquete raíz `com.ridedecider.app`:

- **`domain/`** — Kotlin puro, sin dependencias de Android. `model/` (entidades inmutables: `Trip`, `TripEvaluation`, `EvaluationMetrics`, `Decision`, `DecisionReason`, `ProfitabilityConfig`, `DriverEconomicContext`, `DriverGoals`, `EarningsProgress`, `RecordedTrip`, `TripLifecycleState`, `ReconciliationMetrics`, etc.), `engine/` (`DecisionEngine`, `TripProfitabilityClassifier`, `EarningsTracker`, `TargetProgressCalculator`, `TripLifecycleStateMachine`, `AppUpdateEvaluator`), `usecase/` (`EvaluateIncomingTripUseCase`), `repository/` (interfaces), `manager/` (`AppUpdateManager`).
- **`data/`** — implementaciones. `accessibility/uber/` (servicio, parser, processor, validator, mapper, snapshot, OCR y `diagnostic/`), `local/room/` (DB v4, DAOs y entidades), `repository/` (implementaciones Room e InMemory), `update/` (OTA), `di/ServiceLocator`.
- **`ui/`** — Compose. `screens/` (Home, Live, Goals, Settings), `components/`, `theme/`, y `overlay/` (el HUD: `HudOverlayManager`, `HudStateHolder`, `HudUiModelMapper`, modelos `HudState`/`HudUiModel`/`HudVisualTier`).

Observaciones: separación de responsabilidades muy limpia; el dominio es determinista y testeable de forma aislada; Android queda confinado a `data/accessibility`, `data/local`, `data/update` y `ui`.

## 5. Principales módulos (responsabilidad real, no supuesta)

| Componente | Responsabilidad verificada en código |
|---|---|
| `UberAccessibilityService` | Adaptador Android↔dominio. Filtra paquete, *throttling* (16 ms) y protección de reentrada, captura multi-fuente (`rootInActiveWindow` + `windows` + `event.source`), detección inmediata de clics en Aceptar/Emparejar/Rechazar/✕ para ocultar el HUD, dispara OCR. Registra el listener que puentea evaluación→HUD→Room. |
| `UberAccessibilityProcessor` | Orquesta el pipeline: diagnóstico, *dumps*, parseo, validación, transición de `UberOfferScreenType`, **debounce por firma** y **consumo semántico de oferta** (`consumedOfferSignature`), mapeo y evaluación. |
| `UberAccessibilityParser` (884 líneas) | Extracción textual de tarifa, cinemática, direcciones, categoría, efectivo, rating, y clasificación de tipo de pantalla. Pieza más compleja y sensible. |
| `UberOfferValidator` | Validación estructural/física determinista (rangos de tarifa 0,50–500 €, distancia 0–300 km, duración 0–360 min) y clasificación de pantalla. |
| `DecisionEngine` (227 líneas) | **SPE 2.0**: valida datos, calcula coste operativo, neto, €/km, €/h, €/h neto, ratios de recogida, y aplica *hard rules* → `Decision`. |
| `TripProfitabilityClassifier` (158) | Clasifica en EXCELLENT/GOOD/ACCEPTABLE/BAD y calcula el `profitabilityScore` (0–100) con 5 componentes ponderados. |
| `TripLifecycleStateMachine` (281) | Máquina de estados: Idle→PendingAcceptance→Assigned→ActiveTrip→Completed / Cancelled / IgnoredOrExpired. |
| `EarningsTracker` (353) | Coordina lifecycle + persistencia, congela el **DecisionSnapshot** en t₀, calcula progreso diario/semanal/mensual y `DriverEconomicContext`. |
| `TargetProgressCalculator` | Cálculo puro del progreso y del ritmo horario requerido. |
| `HudOverlayManager` / `HudStateHolder` / `HudUiModelMapper` | HUD flotante `TYPE_APPLICATION_OVERLAY`, 100% *click-through*, reactivo vía `StateFlow`. |
| `AppUpdateManager` + `data/update/*` | OTA privado (manifiesto JSON, descarga, verificación SHA-256, instalación vía FileProvider). |

## 6. Flujo de una oferta (detalle)

1. Uber muestra una tarjeta de oferta directa o de radar.
2. El servicio recibe eventos de accesibilidad y captura un snapshot priorizando ventanas con señales de oferta (`€`, "aceptar", "emparejar", "radar"…).
3. El `Parser` produce un `RawUberTripOffer` (tarifa, cinemática, direcciones, categoría, efectivo, rating, `detectedOfferType`, `KinematicsSource`).
4. El `Validator` decide si es oferta válida (RADAR_OFFER/TRIP_OFFER con todos los campos obligatorios presentes y coherentes) o una pantalla no-oferta (NO_OFFER, ACTIVE_TRIP, TRIP_CANCELLED, TRIP_COMPLETED, RESERVATION, HISTORY, UNKNOWN).
5. Si es válida, se genera una **firma** (tipo+tarifa+cinemática+direcciones+rating+categoría+efectivo). Si coincide con `consumedOfferSignature`, o con la última firma dentro del intervalo de *debounce*, se descarta → **una oferta válida se registra una sola vez** (regla 6 del contexto, implementada aquí).
6. Se mapea a `Trip`, se obtiene la `ProfitabilityConfig` vigente y se evalúa.
7. La `TripEvaluation` se emite al HUD y se persiste (registro + snapshot t₀).

## 7. Cómo se toma una decisión (SPE 2.0)

En `DecisionEngine.evaluate`:

- **Validación de datos**: si falta tarifa, distancia/duración de recogida o de viaje → `Decision.UNKNOWN` con razones `UNKNOWN_MISSING_*`. Si hay valores físicamente imposibles (tarifa ≤ 0, negativos, totales ≤ 0) → `UNKNOWN_INVALID_DATA`.
- **Cálculos**: `totalDistance = pickup + trip`; `coste = totalKm·costPerKm + totalHoras·costPerHour`; `neto = tarifa − coste`; `€/km`, `€/h`, `€/h neto`; `effectiveGrossPerKm` (recogida ponderada por `pickupDistanceWeight`, por defecto 1,5); ratios y velocidad de recogida.
- **Hard rules** (cualquiera dispara REJECT, acumulando razones): recogida > `maxPickupDistanceKm`; tiempo de recogida > `maxPickupTimeMinutes`; `€/km` < `minGrossPerKmRate`; `€/km efectivo` < umbral; `€/h` < `minGrossHourlyRate`; neto < `minNetTripProfit`; `€/h neto` < `minNetHourlyRate`.
- **Decisión**: sin *reject reasons* → `ACCEPT` (`ACCEPT_HIGH_PROFITABILITY`); en otro caso `REJECT`.
- **Clasificación cualitativa** (`TripProfitabilityClassifier`): EXCELLENT/GOOD/ACCEPTABLE/BAD según ratios respecto a umbrales, con dos vías a EXCELLENT (pura o contextual) y un caso donde un REJECT marginal puede quedar ACCEPTABLE.
- **`profitabilityScore` (0–100)**: media ponderada de 5 componentes (horario 0,35 · distancia 0,25 · neto 0,20 · recogida 0,10 · utilización/velocidad 0,10). Es **informativo** para el HUD; no cambia por sí mismo el ACCEPT/REJECT (las hard rules mandan).

## 8. Cómo se registra la decisión

Cada evaluación dispara `EarningsTracker.recordEvaluatedOffer`, que:
- Empuja el lifecycle a `PendingAcceptance`.
- Persiste un `RecordedTrip` con estado `EVALUATED`.
- **Congela un `DecisionSnapshotEntity`** (Learning Data Foundation, Fase 8.11): identidad (`snapshotId` UUID, `instanceId`, `tripId`), versión (`appVersion=1.0.1`, `engineVersion=2.0`), oferta estimada, métricas SPE 2.0 en t₀, decisión + razones + nivel, contexto temporal (`dayOfWeek`, `hourOfDay`, `minuteOfHour`, `timeBucket`), campos Waze preparados (nulos) y campos de resultado real (nulos hasta reconciliar). Tabla `decision_snapshots`, `@Insert(onConflict = IGNORE)`.

## 9. Qué sucede cuando el conductor acepta

No hay una detección explícita y fiable del "Aceptar" que promueva el estado a `Assigned` en producción: al pulsar Aceptar/Emparejar/✕ el servicio solo **oculta el HUD** (`hideImmediately()`). La transición a viaje se infiere del **cambio de pantalla a `ACTIVE_TRIP`**, que llama a `earningsTracker.markActiveTripStarted()` → `stateMachine.onActiveTripStarted()`. La máquina permite `PendingAcceptance → ActiveTrip` directamente (además de `Assigned → ActiveTrip`), por lo que el camino real habitual es `PendingAcceptance → ActiveTrip → Completed`. Las rutas `onOfferAcceptedManually` / `onRadarAutoAssigned` (Assigned) existen y están cubiertas por tests, pero **no están cableadas al pipeline de accesibilidad** (ver Riesgos, §16).

## 10. Cómo se sigue el viaje

Mientras `currentScreenType == ACTIVE_TRIP`, los datos de navegación (km/min/ETA) **no** generan nuevas ofertas ni evaluaciones (regla 7 del contexto): al entrar en ACTIVE_TRIP se resetea `consumedOfferSignature` y el validador clasifica esa pantalla como no-oferta (`isValidOffer=false`). El lifecycle se mantiene en `ActiveTrip`, que rechaza nuevas ofertas. El HUD se oculta de inmediato en ACTIVE_TRIP/CANCELLED/COMPLETED.

## 11. Cómo se obtiene el resultado real

Por transición de pantalla detectada por el parser/validador:
- `TRIP_COMPLETED` → `onTripCompleted(finalEarningsEur)` → `EarningsTracker.completeTrip(tripId, finalEarnings, durationMinutes)`. Si no hay importe final leído, cae al `rawFare` estimado del viaje activo (fallback).
- `TRIP_CANCELLED` → `onTripCancelled(reason, feeEur)` → `EarningsTracker.cancelTrip(...)` con mapeo de `CancellationReason` (RIDER/DRIVER/UBER/NO_SHOW/UNKNOWN) y registro separado de la compensación por cancelación.

Solo `completeTrip` (estado `Completed`) computa para el progreso económico. La máquina prohíbe explícitamente transicionar a `Completed` desde `Cancelled`/`IgnoredOrExpired`.

## 12. Cómo se reconcilia con la estimación (Fase 8.12)

La reconciliación **no es un motor separado**: vive como método `calculateReconciliationMetrics()` dentro de `DecisionSnapshotEntity`. Al completar/cancelar, `EarningsTracker` llama a `earningsRepository.updateSnapshotActuals(...)`, que rellena los campos reales del snapshot (distancia, duración, tarifa base, espera, compensación, propina, ganancia final) vía un `UPDATE ... COALESCE(...)` en el DAO. Después, `calculateReconciliationMetrics()` deriva `durationErrorMinutes`, `distanceErrorKm`, `fareErrorEur`, `earningsDeltaEur` (real − estimado). `profitErrorEur` queda `null` (no se calcula el neto real). La estimación t₀ nunca se sobrescribe: real y estimado conviven en columnas distintas.

## 13. Cómo funciona actualmente el sistema de objetivos

- `DriverGoals` mantiene **un único objetivo activo** (`activePeriod`: DAILY/WEEKLY/MONTHLY) con `targetEur` y `plannedHours` por periodo. `activeHourlyTarget = targetEur / plannedHours` (o 24 €/h por defecto). Es configurable; no hay cantidades fijas impuestas (defaults DAILY 120 €/5 h, resto 0). Modo libre = `targetEur == 0`, modo objetivo = `targetEur > 0` (gestionado por `TargetProgressCalculator`: si `target ≤ 0`, `completion=100%` cuando hay ingresos y estado no fuerza BEHIND por meta).
- Persistencia en Room (`DriverGoalsEntity` + `RoomDriverGoalsRepository`, expuesto como `goalsFlow`).
- **Vía de influencia real en la decisión**: en `onServiceConnected`, el servicio **colecciona `goalsFlow`** y reconstruye la `ProfitabilityConfig` fijando `minGrossHourlyRate = activeHourlyTarget` (y `minNetHourlyRate = max(10, target·0,75)`). Es decir, el objetivo endurece el umbral horario del SPE 2.0. Los costes (`costPerKm=0,20`, `costPerHour=4,0`) y otros umbrales están **hardcodeados** en ese punto.
- `TargetProgressCalculator` calcula `remaining`, `completion%`, `currentHourlyRate`, `requiredHourlyRate` y `ProgressStatus` (TARGET_REACHED/AHEAD/ON_TRACK/BEHIND). `EarningsTracker` produce `DriverEconomicContext` (diario+semanal+mensual).

## 14. Qué partes están cerradas / protegidas

Según el contexto del proyecto y confirmado por su presencia y tests en el código, deben tratarse como **FROZEN**:
- **8.10 SPE 2.0** — `DecisionEngine` + `TripProfitabilityClassifier`.
- **8.11 Learning Data Foundation** — `DecisionSnapshotEntity` + DAO + congelado en t₀.
- **8.12 Actual Trip Reconciliation** — `calculateReconciliationMetrics()` + `updateSnapshotActuals`.
- **8.13 Offer Ingestion Protection & Semantic Offer Consumption** — parser/validator/processor y `consumedOfferSignature` (HEAD actual, `80af3f1`).
- **OTA / firma / `applicationId` / versionado** — `data/update/*`, `signingConfigs`, `latest.json`.
- `docs/DECISIONS.md` ADR-001 fija invariantes de HUD (dismiss 0 ms, flags de toque, anti-flicker 100 ms, radar activo vs pill, fallback cinemático de un segmento, *safety timeout* 5–6 s).

## 15. Qué partes están abiertas a evolución

- **Fase 8.15** (objetivos económicos y optimización contextual de decisiones) — pendiente de auditar; **no implementar aún**.
- El *hook* `DriverEconomicContext` en `DecisionEngine`/`Classifier` (razones `CONTEXT_*`) está construido y testeado pero **no alimentado** en el pipeline real — candidato natural de 8.15.
- Campos **Waze** en el snapshot (preparados, siempre nulos) — infraestructura latente; el contexto (regla 14) prohíbe integrar Waze como fuente.
- **Bolt** — apartado (regla 15); sin código de plataforma Bolt.
- `profitErrorEur` de reconciliación (sin calcular).
- UI/HUD, categorías Uber, y afinado de umbrales/pesos.

## 16. Riesgos arquitectónicos encontrados (documentados, no corregidos)

1. **`DriverEconomicContext` desconectado del flujo real.** `UberAccessibilityProcessor` invoca `evaluateUseCase(trip, config)` sin `economicContext`. Por tanto las razones `CONTEXT_*` y la clasificación contextual **nunca se activan en producción**; el objetivo solo influye indirectamente vía `minGrossHourlyRate`. Es coherente con "informativo vs decisivo", pero conviene tenerlo explícito de cara a 8.15.
2. **Detección de aceptación heurística.** No se distingue de forma fiable "el conductor aceptó" de "la oferta expiró"; se infiere por pantalla `ACTIVE_TRIP`. Las rutas `Assigned` (manual/radar) no están cableadas. Si aparece `ACTIVE_TRIP` cuando el estado ya cayó a `IgnoredOrExpired`, `onActiveTripStarted` no promueve (else→state) y ese viaje podría no seguirse.
3. **`ProfitabilityConfig` con valores mixtos.** Umbrales horarios derivan del objetivo, pero costes y otros límites están **hardcodeados** en `UberAccessibilityService.onServiceConnected` (y otro bloque distinto en el `BroadcastReceiver` de prueba). Duplicación de configuración con valores no idénticos.
4. **`finalEarningsEur` con fallback a estimado.** Si `TRIP_COMPLETED` no expone importe real, se usa `rawFare` estimado como ganancia real → la reconciliación mostraría error 0 falso. Depende de la fiabilidad del parser en la pantalla de resumen.
5. **`fallbackToDestructiveMigration()`** en Room: además de la `MIGRATION_3_4` explícita, cualquier otro salto de versión **borra los datos** (incluidos snapshots de aprendizaje). Riesgo para la Learning Data Foundation.
6. **`kinematicsSource` se persiste como `"LEGACY_UNSPECIFIED"` fijo** en el snapshot (no se propaga el valor real de `RawUberTripOffer.kinematicsSource`) → se pierde señal para el aprendizaje.
7. **Servicio de accesibilidad sin filtro de paquete a nivel de sistema** (`info.packageNames = null`, `TYPES_ALL_MASK`): recibe todos los eventos y filtra en código. Correcto para capturar modales, pero coste de rendimiento y superficie amplia.
8. **`instanceId == tripId`**: el id determinista de oferta y el id de viaje coinciden; `generateDeterministicId` del mapper existe pero **no se usa** (el mapper toma `rawOffer.instanceId`). Código muerto menor.

## 17. Dependencias importantes

Compose (BOM) + Material3 + material-icons-extended; AndroidX Core/Activity/Lifecycle; **Room** (`runtime`, `ktx`, `compiler` vía KSP; `room.generateKotlin=true`); **ML Kit Text Recognition** (OCR on-device); JUnit4, `androidx.room.testing`, `kotlinx-coroutines-test 1.8.0`, Espresso/Compose UI test. Java 11. Sin framework de DI (ServiceLocator manual). OTA propio sin librerías externas.

## 18. Tests existentes

**44 archivos de test unitario** con **427 métodos `@Test`** en `app/src/test` (más `ExampleInstrumentedTest` en `androidTest`). Cobertura por área: motor/dominio (DecisionEngine, TripProfitabilityClassifier, EarningsTracker + integridad financiera + integración lifecycle, TripLifecycle completo y comprensivo, TargetProgressCalculator, LearningDataFoundation, ActualTripReconciliation, DriverEconomicContext, AppUpdateEvaluator/Manager, EvaluateIncomingTripUseCase); ingesta Uber (Parser, Parser lifecycle, Processor, Validator, Mapper, NodeSnapshot, EventThrottling, OfferIngestionProtection, HudActionShield, IdentityAndTimestampAudit, OCR fallback + diagnósticos); Room (persistencia, colisiones); update (checker, parser manifiesto, verificador APK, config); HUD (mapper, compatibilidad). **No se ejecutaron** tests ni `build` (modo auditoría, sin modificaciones y con `device_bash` de ~45 s por llamada); quedan listos para ejecutar con `./gradlew testDebugUnitTest` bajo tu aprobación.

## 19. Estado actual de Git

- Rama `main`, *up to date* con `origin/main`. `HEAD = 80af3f1` — "feat: complete phase 8.13 offer ingestion protection and semantic offer consumption" (coincide con el commit de referencia congelado del contexto).
- Historial: `80af3f1` (8.13) ← `d0e569f` (8.12) ← `29919b9` (release setup v1.0.1) ← `a92be92` (init).
- `git status` marca **todos** los archivos como *modified* y aparece `warning: unable to unlink .git/index.lock: Operation not permitted`. Verificado con `git diff --numstat`: cada archivo figura como N líneas añadidas / N borradas (fichero completo), es decir **conversión de fin de línea CRLF↔LF** al leer el *checkout* de Windows a través del montaje, no cambios de contenido. El árbol de trabajo es funcionalmente idéntico a `80af3f1`. Único añadido no rastreado real: `.artifacts/` (herramientas del entorno, no del proyecto).
- No se ha hecho `add`, `commit` ni `push` (modo auditoría).

## 20. Discrepancias entre documentación/contexto y código real

1. **Docs vacíos**: `docs/ARCHITECTURE.md`, `docs/CHANGELOG.md` y `docs/MASTER_SPEC.md` están a **0 líneas**. La narrativa de fases (8.10–8.15) vive solo en el contexto/instrucciones del proyecto, **no** en el repo. La única documentación real con contenido es `docs/DECISIONS.md` (ADR-001, 40 líneas) y `docs/RELEASE_CHECKLIST.md` (99).
2. **Reconciliación no es una clase/motor** como podría sugerir "Actual Trip Reconciliation": es un método dentro de la entidad Room (`DecisionSnapshotEntity`).
3. **Contexto económico en la decisión**: el contexto describe el objetivo influyendo en la decisión; en el código el `DriverEconomicContext` está presente pero **no se pasa** al motor en el flujo real (solo mueve el umbral horario vía config). Coherente con la distinción informativo/decisivo, pero es una diferencia de matiz importante para 8.15.
4. **Aceptación manual / radar auto-asignado**: modelados y testeados, pero **no cableados** a la ingesta real (la promoción a viaje se infiere por pantalla ACTIVE_TRIP).
5. **`engineVersion`/`appVersion` hardcodeados** ("2.0"/"1.0.1") en `EarningsTracker` y en la entidad, en vez de derivarse de `BuildConfig`.
6. **Waze**: hay columnas Waze en el snapshot (contra la regla 14 que lo excluye de la arquitectura), aunque permanecen siempre nulas/`false` (preparadas, no integradas).
7. **`Bolt`**: la única aparición de "Bolt" en `main` es el **icono Material `Icons.Rounded.Bolt`** (rayo) en `MainActivity`, no la plataforma Bolt — consistente con "Bolt apartado".

---

### Conclusión

RideDecider es un sistema de decisión económica en tiempo real bien estratificado (Clean Architecture, dominio puro y determinista, Android confinado a los bordes), con las fases 8.10–8.13 presentes y ampliamente cubiertas por 427 tests. El HUD y la ingesta de Uber están endurecidos y documentados como invariantes. Las mayores oportunidades y riesgos de cara a **8.15** son: (a) el `DriverEconomicContext` construido pero desconectado del flujo real de decisión, (b) la detección heurística de aceptación/viaje, y (c) la configuración económica parcialmente hardcodeada y duplicada. Nada de esto se ha modificado: queda documentado para la auditoría específica de 8.15, que **no** debe iniciarse hasta tu aprobación.
