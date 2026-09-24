# Tarea Actual

> Ultima actualizacion: 2026-09-05

## Fase activa

**8.16 — Automatic Decision Architecture**

## Estado de sub-tareas

| Sub-tarea | Estado | Resultado |
|-----------|--------|-----------|
| 8.16-R1 | CLOSED | Thresholds SPE desacoplados de metas |
| 8.16-R2 | CLOSED | Diseno conceptual del modo automatico |
| 8.16-R2.1 | CLOSED | Arquitectura B aprobada (SPE frozen, OE interpreta) |
| 8.16-R3 | CLOSED | Contratos de datos Opportunity Layer definidos |
| 8.16-R4 | CLOSED | DefaultOpportunityEvaluator implementado |
| 8.16-R4.1 | CLOSED | Auditoria quirurgica completada |
| 8.16-R4.2 | CLOSED | Historical upgrade protegido por sampleSize; net profit ratios eliminados |
| 8.16-R4.3 | CLOSED / VALIDATED | Matriz de calibracion 27 casos, multidimensionalidad documentada |

## Tests

- DefaultOpportunityEvaluatorTest: 17/17 PASS
- OpportunityEvaluatorCalibrationTest: 27/27 PASS
- Full testDebugUnitTest: PASS
- assembleDebug: PASS

## Estado actual

- OpportunityEvaluator implementado y validado pero SIN integrar en pipeline.
- SPE 2.0 permanece congelado.
- Contratos R3 permanecen congelados.
- No modificar todavia la mono-dimensionalidad de R4 (dominancia de grossPerHour documentada en R4.3).
- No implementar todavia las opciones A/B propuestas en R4.3.
- No commit/push autorizado.

## Siguiente tarea

Disenar y ejecutar integracion minima de OpportunityEvaluator en el flujo real, sin HUD ni automatizacion.
