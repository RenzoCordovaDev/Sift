# Estado del Proyecto Sift

Actualizado por el Agente de Reporte. Ver `AGENTS_WORKFLOW.md` sección 10.

| Fase | Estado | Rama activa | Certificación QA | Última actualización |
|---|---|---|---|---|
| F0 - Setup | ✅ Completo (mergeado a develop) | — | Cobertura domain/data: 100% (JaCoCo) | 2026-08-03 |
| F1 - Núcleo screening | ⏳ Pendiente | — | — | — |
| F2 - Historial de intentos | ⏳ Pendiente | — | — | — |
| F3 - Listas manuales | ⏳ Pendiente | — | — | — |
| F4 - UI | ⏳ Pendiente | — | — | — |
| F5 - Onboarding | ⏳ Pendiente | — | — | — |
| F6 - Pulido y publicación | ⏳ Pendiente | — | — | — |

## Infraestructura de CI local

Los 4 status checks requeridos por los GitHub rulesets (`protect-develop` / `protect-main`) están ahora operativos a través de scripts locales en `scripts/status-checks/`:
- `code-review-agent`: publica el veredicto (pass/fail) de la revisión del agente `revision-codigo` contra `CODE_QUALITY_STANDARDS.md`/`ARCHITECTURE.md` — el script recibe el veredicto como argumento, ya que la revisión en sí es un juicio, no algo calculable por script.
- `commitlint`: valida Conventional Commits v1.0.0 sobre el rango de commits del PR.
- `unit-tests`: corre `./gradlew test`.
- `jacoco-coverage-80`: verifica el gate de cobertura ≥80% (scope: `domain/**` + `data/**`, excluyendo `AppDatabase.class`).

No corren automáticamente vía git hook — se ejecutan manualmente (o por un agente) con `scripts/run-status-checks.sh` para los 3 checks deterministas, y `scripts/status-checks/code-review.sh <success|failure> "<descripción>"` para el veredicto de revisión, antes de que un PR pueda mergearse.

## Notas y pendientes menores

- **Room database schema** (`app/schemas/`): generado y sin rastrear en git. Debería commitearse según KDoc de `AppDatabase.kt`; pendiente en siguiente sync.
- **Worktrees** (`.claude/worktrees/`): directorio sin agregar a `.gitignore`. Deberá incluirse en próxima limpieza de git ignore.
