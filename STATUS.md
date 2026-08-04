# Estado del Proyecto Sift

Actualizado por el Agente de Reporte. Ver `AGENTS_WORKFLOW.md` sección 10.

| Fase | Estado | Rama activa | Certificación QA | Última actualización |
|---|---|---|---|---|
| F0 - Setup | ✅ Completo (mergeado a develop) | — | Cobertura domain/data: 100% (JaCoCo) | 2026-08-03 |
| F1 - Núcleo screening | ✅ Completo (mergeado a develop) | — | PASS (certificado en sesión; escenarios E2E escritos, no ejecutados por falta de emulador) | 2026-08-04 |
| F2 - Historial de intentos | ✅ Completo (mergeado a develop) | — | PASS (PR #8, comentario formal publicado) | 2026-08-04 |
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

### F1 - Defectos encontrados y corregidos en sesión
- **SecurityException en `ContactsRepositoryImpl`:** consultas a `ContactsProvider` no capturaban excepciones por falta de permiso `READ_CONTACTS`. Corregido: falla segura hacia "tratar como contacto conocido" (protege regla no negociable #1 en `CLAUDE.md`).
- **Validación faltante en `SettingsDataStore`:** método `setRequiredAttemptCount` no validaba valores ≥1. Corregido: lanzará excepción si se intenta establecer valor inválido.
- **Brecha de proceso QA→PR:** agente QA certificó PASS internamente en la sesión (verificó tests, cobertura JaCoCo ~91%, detekt, compilación, regla de decisión completa), pero no dejó comentario formal en el PR de GitHub. Debe corregirse en F2 para que el orquestador pueda verificar públicamente. [Ver nota en AGENTS_WORKFLOW.md sección 10]
- **E2E Gherkin (F1):** 4 escenarios escritos en `e2e/src/test/resources/features/f1_call_screening_core.feature`: contacto conocido siempre permitido (`@KnownContact`), primer intento de número desconocido bloqueado silenciosamente (`@UnknownNumber @FirstAttempt`), segundo intento del mismo número permitido (`@UnknownNumber @SecondAttempt`), y filtro desactivado permite todo (`@FilterDisabled @PendingUI`, pendiente porque depende de la UI de F4). No ejecutados en emulador (no disponible en este entorno); solo se verificó que compilan y que la sintaxis Gherkin es correcta.

### F2 - Correcciones de configuración y cierre de brecha de proceso
- **Corrección de `detekt.yml`:** hallazgo histórico desde F0. La configuración tenía `constructorThreshold: 5` pero detekt dispara en `>= threshold`, lo que efectivamente limitaba a 4 parámetros en vez de los 5 permitidos por `CODE_QUALITY_STANDARDS.md`. Corregido a `constructorThreshold: 6` durante revisión de código de F2 (commit `fix(f2-attempt-history): align detekt thresholds with F2 code`). Esta configuración regía sobre todos los módulos, por lo que el fix impacta positivamente a F1 y fases futuras.
- **Cierre de brecha QA→PR (F1):** agente QA ahora publica comentario formal en el PR (verificable públicamente en GitHub), cerrando el gap de proceso documentado en F1. Ejemplo: PR #8 de F2 recibió certificación QA formal publicada.
- **E2E Gherkin (F2):** 2 escenarios en `e2e/src/test/resources/features/f2_attempt_history.feature`: primer intento de número desconocido se registra en el log de bloqueados (`@BlockedCallLogged`), implementado y verificable vía adb SQLite. Segundo intento aparece en la pantalla de historial (`@HistoryScreen @PendingUI`), pendiente de F4 porque depende de la UI. No ejecutados en emulador (mismo motivo que F1).

### Deuda técnica heredada
- **ktlint:** no se configuró en F0 ni F1. Mencionado por el revisor de código en F1; se repriorizará en F2 si es necesario.
