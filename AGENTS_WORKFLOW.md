# Flujo de Trabajo Multi-Agente

## 1. Contexto

El desarrollo se organiza como un **repositorio público en GitHub**, bajo la cuenta personal del propietario del proyecto, trabajado mediante **agentes especializados** (Claude u otros), cada uno con una responsabilidad acotada y una rama propia. Un agente orquestador es responsable de integrar el trabajo de todos los demás.

> **Nota sobre repo público:** al ser público desde el día 1, aplica una regla estricta adicional: **nunca** commitear secretos, claves de firma, keystores, tokens de CI/CD ni datos reales de contactos/números de prueba. Ver sección 8.

## 2. Roster de agentes y responsabilidades

| Agente | Responsabilidad | Rama base | Entrada | Salida |
|---|---|---|---|---|
| **Agente de Entorno (Environment Setup)** | Verifica e instala todas las herramientas necesarias para el desarrollo Android en Windows (JDK, Android SDK, ADB, Node.js, commitlint, ktlint, Appium). Corre **una sola vez** antes de la fase F0 y produce un reporte de entorno. Ver `.claude/agents/environment-setup.md`. | *(no tiene rama propia; corre localmente sobre la máquina)* | Máquina Windows con Android Studio ya instalado | Reporte de entorno con `[✓]`/`[✗]` por herramienta |
| **Agente Desarrollador Móvil** | Implementa features de la app (Kotlin/Android) según lo definido en `ARCHITECTURE.md` y el feature/fase asignado. | `feature/<fase>-<nombre>` | Especificación de feature + criterios de aceptación | Código + PR hacia `develop` |
| **Agente Especialista en Test Unitarios** | Escribe y mantiene tests unitarios (`domain`, `data`) para certificar la lógica funcional de cada feature. | `test/unit-<fase>-<nombre>` | Código del feature ya implementado (o interfaces acordadas) | Suite de tests unitarios + reporte de cobertura |
| **Agente Desarrollador Test E2E** | Automatiza pruebas end-to-end en **Gherkin + Cucumber + Appium** (flujo completo: llamada simulada → decisión → registro en historial). | `test/e2e-<fase>-<nombre>` | Feature integrado en `develop` | Archivos `.feature` (Gherkin) + step definitions (Cucumber-JVM) + ejecución vía Appium |
| **Agente de QA Especializado** | Ejecuta y valida test unitarios + E2E, certifica la fase como "lista para merge", reporta bugs/regresiones. | `qa/<fase>` | Resultados de tests unitarios + E2E | Reporte de certificación QA (pass/fail + hallazgos) |
| **Agente de Revisión de Código (Code Review)** | Revisa cada PR **antes** que el Orquestador: cumplimiento de `CODE_QUALITY_STANDARDS.md` (KDoc, complejidad, anti-patrones), cumplimiento de `ARCHITECTURE.md` (capas, dependencias), granularidad/calidad de los commits (Conventional Commits + atomicidad, sección 7 de este documento), y que **todo el desarrollo esté en inglés** (identificadores, KDoc, comentarios, commits, título/descripción del PR) sin comentarios innecesarios (ver `CODE_QUALITY_STANDARDS.md` sección 2). Opera como **status check automatizado** (no como una segunda cuenta de GitHub aprobando), publicando pass/fail + hallazgos sobre el PR. Ver implementación en `LOCAL_AUTOMATION_SETUP.md` sección 5. | *(no tiene rama propia; opera como check automatizado sobre cualquier PR abierto)* | PR abierto por cualquier agente | Commit status (`success`/`failure`) + comentario con hallazgos en el PR |
| **Agente Orquestador de Merge** | Segunda y última aprobación. Integra ramas de feature/test/qa hacia `develop` y de `develop` hacia `main`, resuelve conflictos, valida que CI pase y que exista aprobación previa del Agente de Revisión antes de mergear. | `integration/<fase>` | PRs con aprobación del Agente de Revisión + certificación QA | Rama `develop`/`main` actualizada |
| **Agente de Documentación** | Mantiene actualizados `ARCHITECTURE.md`, `PROJECT_CONTEXT.md`, `DEVELOPMENT_STANDARDS.md`, `CODE_QUALITY_STANDARDS.md`, y genera documentación de cada feature (KDoc, README de módulo). Audita que **cada función** cumpla el estándar de documentación de `CODE_QUALITY_STANDARDS.md` (sección 5) y bloquea el PR si no se cumple. | `docs/<fase>-<nombre>` | Cambios de código/arquitectura mergeados | Documentación actualizada + PR + reporte de auditoría de KDoc |
| **Agente de Reporte/Seguimiento** | Da seguimiento al estado de todas las fases/features, agentes activos, bloqueos, y genera un reporte de avance periódico. | *(no escribe código, opera sobre issues/PRs)* | Estado de todas las ramas y PRs | Reporte de avance (issue fijado o `STATUS.md`) |

### Regla de aprobación mínima de un PR (obligatoria)

> **Nota de implementación:** GitHub no permite que una misma cuenta autenticada cuente como 2 aprobaciones de PR review distintas. Por eso el modelo real (ver `LOCAL_AUTOMATION_SETUP.md` sección 1) es de **2 gates**, no 2 reviews formales:

1. **Gate 1 — Agente de Revisión de Código:** corre como **status check automatizado** (`code-review-agent`), verificando cumplimiento de calidad de código y granularidad de commits. Debe estar en verde antes de poder mergear. Si falla, el PR regresa al agente autor (Dev Móvil, Test Unitario, Test E2E, etc.).
2. **Gate 2 — Agente Orquestador de Merge:** es la **única aprobación humana/de cuenta real** requerida por `branch protection` — verifica que el Gate 1 esté en verde, que el resto del pipeline de CI pase (commitlint, lint, tests, cobertura ≥80%) y que no haya conflictos, y **solo entonces ejecuta el merge** (rebase).

Ningún PR se mergea sin que ambos gates estén satisfechos.

## 3. Estrategia de ramas

```
main                                  → producción, siempre estable
 └── develop                          → integración de todas las fases
       ├── feature/f1-contacts-check       (Agente Dev Móvil)
       ├── test/unit-f1-contacts-check     (Agente Test Unitario)
       ├── test/e2e-f1-contacts-check      (Agente Test E2E)
       ├── qa/f1                          (Agente QA)
       ├── docs/f1-contacts-check          (Agente Documentación)
       └── integration/f1                 (Agente Orquestador — temporal, se borra tras merge)
```

**Convención de nombres de rama:** `<tipo>/<fase>-<slug-descriptivo>`
Tipos válidos: `feature`, `test` (con sufijo `unit` o `e2e`), `qa`, `docs`, `integration`, `fix`.

**Reglas:**
1. Ninguna rama de agente hace merge directo a `develop` o `main`. Todo pasa por PR revisado.
2. El **Agente Orquestador** es el único con permiso conceptual de mergear hacia `develop` y `main`, y solo después de que el status check del Agente de Revisión de Código esté en verde (ver "Regla de aprobación mínima" más abajo).
3. Una rama de feature no se considera "lista" hasta tener: código + tests unitarios asociados + PR de documentación abierto.
4. El **Agente de QA** debe aprobar explícitamente (comentario/label en el PR) antes de que el Agente de Revisión de Código inicie su chequeo.
5. Todo PR requiere pasar **2 gates**: el status check del Agente de Revisión de Código, y la aprobación + merge del Agente Orquestador. Ver detalle completo en la tabla de agentes y la sección 7.1.

## 4. Estructura del proyecto por features/fases

Se reutilizan las fases ya definidas en `ARCHITECTURE.md` (sección 11), mapeadas 1:1 a carpetas de trabajo y ramas:

```
F0 - Setup                  → feature/f0-setup
F1 - Núcleo screening       → feature/f1-call-screening-core
F2 - Historial de intentos  → feature/f2-attempt-history
F3 - Listas manuales        → feature/f3-manual-lists
F4 - UI                     → feature/f4-ui
F5 - Onboarding             → feature/f5-onboarding
F6 - Pulido y publicación   → feature/f6-release
```

Cada fase, al completarse, debe tener:
- [ ] Código mergeado en `develop`
- [ ] Tests unitarios con cobertura ≥80% en la lógica nueva (gate automático de CI, ver `DEVELOPMENT_STANDARDS.md` sección 10)
- [ ] Test E2E del flujo principal de la fase
- [ ] Certificación QA (pass)
- [ ] Aprobación del Agente de Revisión de Código + merge del Agente Orquestador (2 aprobaciones)
- [ ] Documentación actualizada (arquitectura + KDoc)
- [ ] Cumplimiento de `CODE_QUALITY_STANDARDS.md`: KDoc por función, límites de complejidad, sin anti-patrones de la sección 8, y **desarrollo 100% en inglés** (identificadores, comentarios, commits, PR) sin comentarios innecesarios

## 5. Pipeline de trabajo (handoff entre agentes)

```
1. Agente Documentación / Orquestador define el alcance de la fase (issue + criterios de aceptación)
        ↓
2. Agente Desarrollador Móvil implementa el feature en su rama
        ↓
3. Agente Test Unitario escribe tests sobre el código entregado
        ↓
4. Agente Test E2E automatiza el flujo completo de la fase
        ↓
5. Agente QA ejecuta todo, certifica o rechaza (si rechaza, vuelve al paso 2 con reporte de bugs)
        ↓
6. Agente de Revisión de Código revisa el/los PR(s): calidad, arquitectura, granularidad de commits
   → si solicita cambios, vuelve al agente autor correspondiente (paso 2, 3 o 4)
   → si aprueba (1ra aprobación), continúa
        ↓
7. Agente Orquestador valida CI en verde + aprobación previa (2da aprobación) e integra feature + tests + e2e a develop
        ↓
8. Agente Documentación actualiza docs con lo realmente implementado
        ↓
9. Agente de Reporte actualiza el estado general del proyecto
```

## 6. Certificación de calidad (doble capa)

- **Certificación funcional (Test Unitarios):** garantiza que la lógica de negocio (ej. "segunda llamada permite entrar") funciona de forma aislada y correcta.
- **Certificación de QA (Test E2E):** garantiza que el flujo completo, desde que "llega" una llamada simulada hasta que se refleja en el historial/UI, funciona de punta a punta en un entorno real o emulado.

Ninguna fase se considera cerrada sin ambas certificaciones.

## 6.1 Stack de Test E2E: Gherkin + Cucumber + Appium

Se solicitó explícitamente trabajar con **Gherkin y Selenium**. Nota técnica importante: **Selenium está diseñado para navegadores web**, no controla apps nativas Android. El equivalente real para apps móviles nativas —construido sobre el mismo protocolo (WebDriver) y con una API de cliente muy similar a la de Selenium— es **Appium**. Por eso el stack definido es:

| Componente | Rol |
|---|---|
| **Gherkin** | Lenguaje de especificación de escenarios (`Given/When/Then`), legible por no-programadores (QA, product owner). |
| **Cucumber-JVM** | Motor que interpreta los archivos `.feature` y los conecta con step definitions en Kotlin. |
| **Appium** | Driver que ejecuta las acciones sobre la app Android real o emulada (tap, verificar textos, simular estados), usando el mismo paradigma WebDriver de Selenium. |
| **UIAutomator2 driver** (plugin de Appium) | Driver específico recomendado para Android dentro de Appium. |

**Estructura sugerida:**
```
e2e/
├── src/test/resources/features/
│   ├── f1_call_screening_core.feature
│   ├── f2_attempt_history.feature
│   └── f3_manual_lists.feature
├── src/test/kotlin/steps/
│   ├── CallScreeningSteps.kt
│   ├── AttemptHistorySteps.kt
│   └── ManualListsSteps.kt
└── src/test/kotlin/support/
    ├── AppiumDriverFactory.kt
    └── Hooks.kt   (setup/teardown por escenario)
```

**Ejemplo de escenario Gherkin (referencia, no vinculante):**
```gherkin
Feature: Bloqueo de llamadas de números desconocidos

  Scenario: Primer intento de un número desconocido es bloqueado
    Given el número "+51999999999" no está en la agenda de contactos
    And es la primera vez que ese número llama
    When llega una llamada entrante de ese número
    Then la llamada debe ser rechazada silenciosamente
    And debe quedar registrada en el historial de bloqueados

  Scenario: Segundo intento del mismo número desconocido es permitido
    Given el número "+51999999999" no está en la agenda de contactos
    And ese número ya llamó una vez anteriormente
    When llega una llamada entrante de ese número
    Then la llamada debe ser permitida
```

**Consideración de simulación de llamadas:** Android no permite simular llamadas telefónicas reales de forma trivial desde fuera del dispositivo. El Agente Test E2E deberá evaluar, en la fase F0/F1, el mecanismo más viable: emulador con `adb emu gsm call <numero>` (funciona en emuladores, no en dispositivos físicos) o mocks del `CallScreeningService` a nivel de instrumentación. Esto debe quedar resuelto y documentado como parte del setup de la fase F0.

## 7. Reglas de coordinación entre agentes

1. Ningún agente modifica archivos fuera de su responsabilidad declarada (ej. el agente de test unitarios no reescribe lógica de producción; si detecta un bug, lo reporta, no lo "arregla" silenciosamente).
2. Todo agente debe dejar su trabajo documentado en el PR: qué hizo, qué asume, qué queda pendiente.
3. Si dos agentes trabajan sobre archivos que probablemente colisionen (ej. Dev Móvil y Test Unitario sobre el mismo módulo), el Agente Orquestador coordina el orden de merge.
4. El Agente de Reporte no bloquea ni aprueba nada; solo informa.
5. El **Agente de Revisión de Código nunca revisa su propio trabajo**: no puede aprobar un PR que él mismo haya generado (no aplica en la práctica, ya que no tiene rama propia, pero se deja explícito como regla de independencia de revisión).
6. El **Agente Orquestador no puede saltarse la revisión de código**: si intenta mergear un PR sin la aprobación previa del Agente de Revisión, el pipeline de CI/la regla de branch protection debe impedirlo (ver sección 7.1).
7. Toda solicitud de cambios (`REQUEST_CHANGES`) del Agente de Revisión debe quedar registrada como comentario en el PR, referenciando la sección específica de `CODE_QUALITY_STANDARDS.md` o `ARCHITECTURE.md` que motiva el cambio — no observaciones genéricas sin fundamento documental.

## 7.1 Configuración de branch protection (obligatoria en GitHub)

Para que la regla de los "2 gates" no dependa solo de la disciplina de los agentes, se configura como regla dura del repositorio (detalle de implementación en `LOCAL_AUTOMATION_SETUP.md`):

- `develop` y `main` protegidas con **"Require pull request reviews before merging"** → **1 aprobación** (la del Orquestador/propietario — GitHub no permite contar 2 aprobaciones de la misma cuenta).
- **"Require status checks to pass before merging"**, incluyendo obligatoriamente el check `code-review-agent` (Gate 1, sección 5), además de: commitlint, ktlint, detekt, tests unitarios, gate de cobertura ≥80% (ver `DEVELOPMENT_STANDARDS.md` sección 10).
- **"Dismiss stale pull request approvals when new commits are pushed"** activado, para que un nuevo commit tras la aprobación obligue a re-revisión.
- **Estrategia de merge única: "Allow rebase merging" habilitada; "Allow squash merging" y "Allow merge commits" deshabilitadas** — para preservar cada commit individual y la traza histórica (ver `DEVELOPMENT_STANDARDS.md` sección 2, "Granularidad y atomicidad de commits").
- Solo el usuario propietario (arquitecto) y el Agente Orquestador tienen permiso de merge habilitado; el resto de agentes solo puede abrir PRs y comentar.

## 8. Consideraciones de seguridad por ser repositorio público

- `.gitignore` debe excluir: `local.properties`, `*.jks`/`*.keystore`, archivos `google-services.json` si contienen claves reales, cualquier `.env`.
- Nunca usar números de teléfono o contactos reales en tests o datos de ejemplo; usar datasets ficticios (ej. números de prueba estilo `555-XXXX` o rangos reservados para testing).
- Los reportes del Agente de QA/Reporte no deben incluir capturas o logs con datos personales reales de quien prueba la app.
- Revisar licencia del repositorio (ej. MIT, Apache 2.0) desde el inicio, ya que es público.

## 9. Implementación técnica: agentes como subagentes de Claude Code

Cada agente del roster (sección 2) se implementa como un **subagente de Claude Code**: un archivo Markdown con frontmatter YAML en `.claude/agents/<nombre>.md`, versionado en el repositorio (por eso vive en el proyecto, no en `~/.claude/agents/` personal). Esto permite que el equipo crezca simplemente agregando nuevos archivos, sin tocar los existentes.

### Mapeo agente → archivo

| Agente (rol) | Archivo | Tools | Permisos |
|---|---|---|---|
| Desarrollador Móvil | `.claude/agents/dev-movil.md` | Read, Write, Edit, Bash, Grep, Glob | Escribe código de producción |
| Test Unitarios | `.claude/agents/test-unitario.md` | Read, Write, Edit, Bash, Grep, Glob | Escribe tests, no toca producción |
| Test E2E | `.claude/agents/test-e2e.md` | Read, Write, Edit, Bash, Grep, Glob | Escribe Gherkin/Cucumber/Appium |
| QA | `.claude/agents/qa.md` | Read, Bash, Grep, Glob (sin Write/Edit) | Solo lectura — certifica, no modifica |
| Revisión de Código | `.claude/agents/revision-codigo.md` | Read, Bash, Grep, Glob (sin Write/Edit) | Solo lectura — implementa el Gate 1 |
| Orquestador | `.claude/agents/orquestador.md` | Read, Bash, Grep, Glob (sin Write/Edit) | Solo lectura + `gh pr merge --rebase` |
| Documentación | `.claude/agents/documentacion.md` | Read, Write, Edit, Grep, Glob | Edita `.md` y KDoc, no lógica de negocio |
| Reporte | `.claude/agents/reporte.md` | Read, Write, Bash, Grep, Glob (sin Edit) | Solo actualiza `STATUS.md` |

Cada archivo precarga la skill `project-standards` (`.claude/skills/project-standards/SKILL.md`), que resume las reglas de los 6 documentos del proyecto para que ningún agente empiece "en blanco".

### Diferencia con una skill

Un **agente** (subagente) es un trabajador con contexto propio, aislado, con permisos de herramientas acotados a su rol. Una **skill** es conocimiento empaquetado que se inyecta como contexto — no ejecuta nada por sí sola ni tiene permisos propios. `project-standards` es la skill; los 8 archivos de `.claude/agents/` son los agentes que la consumen.

### Cómo crece el equipo

Agregar un nuevo agente (ej. un futuro "Agente de Seguridad") es crear un nuevo archivo `.claude/agents/seguridad.md` con su propio `name`, `description`, `tools` y system prompt — sin modificar los agentes existentes. Se documenta en la tabla de roster (sección 2) y en esta tabla de implementación.

## 10. Artefacto de seguimiento sugerido

Se recomienda mantener un `STATUS.md` en la raíz del repo, actualizado por el Agente de Reporte, con una tabla simple:

| Fase | Estado | Rama activa | Certificación QA | Última actualización |
|---|---|---|---|---|
| F0 - Setup | ✅ Completo | — | ✅ | 2026-07-29 |
| F1 - Screening core | 🔄 En progreso | feature/f1-call-screening-core | ⏳ Pendiente | 2026-07-29 |
