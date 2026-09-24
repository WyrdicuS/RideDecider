# Estado Actual de RideDecider

> Ultima actualizacion: 2026-09-05

## Fase actual

**8.16 — Automatic Decision Architecture**

## Estado del proyecto

- Fases 8.10–8.15: CERRADAS Y CONGELADAS
- Version: v1.0.1
- Room DB: version 4 (3 entidades)
- El sistema opera exclusivamente en modo advisory (no existe modo automatico)

## Hallazgos pendientes de resolver

### R1 — Contaminacion meta → SPE (CRITICO)
En `UberAccessibilityService.kt` (lineas ~182-192), los umbrales de SPE se sincronizan con las metas del conductor:
```
minGrossHourlyRate = activeHourlyTarget  // = targetEur / plannedHours
minNetHourlyRate = maxOf(10.0, hourlyTarget * 0.75)
```
La misma oferta puede ser ACCEPT o REJECT segun la meta configurada. Debe desacoplarse antes de implementar el motor automatico.

## Lo que existe

- SPE 2.0 funcional (7 hard rules + score 0-100 + 4 niveles)
- decision_snapshots en Room (38 columnas, Learning Data Foundation) — fuente potencial de contexto historico
- GoalContextEvaluator con soporte daily/weekly/monthly
- HUD con 4 niveles visuales (EXCELENTE/BUENO/ACEPTABLE/MALO)
- TripLifecycleStateMachine completa
- Reconciliacion estimado vs real (8.12)

## Lo que NO existe todavia

- Modo automatico (ni enum DecisionMode, ni config, ni logica)
- OpportunityEvaluator
- Separacion de umbrales SPE vs metas
- HUD de modo automatico
- Senales de demanda procesadas (los datos brutos estan en snapshots pero sin analisis)
- Historial consultable para decision (snapshots se registran pero no se leen para decidir)

## Arquitectura conceptual aprobada (pendiente de implementacion)

4 capas separadas:
1. **SPE 2.0** (congelado) — Rentabilidad y seguridad economica
2. **OpportunityEvaluator** (nuevo) — Decision categorica ACCEPT/CONSIDER/DECLINE, sin score numerico
3. **Goals/Progress** (existente) — Informativo, desacoplado de la decision automatica
4. **Presentacion** (HUD) — Bifurcado segun modo manual/automatico
