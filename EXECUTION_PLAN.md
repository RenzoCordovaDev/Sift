# Plan de Ejecución Autónoma — Sift

> Cómo arrancar y ejecutar el proyecto completo desde la terminal en Windows usando Claude Code y los agentes definidos en `.claude/agents/`. Este documento es la guía operativa para el arquitecto (tú), no para los agentes.

## Prerequisitos

- Git clonado y repo configurado (ya hecho).
- Claude Code instalado y funcionando (`claude --version`).
- `gh` instalado y autenticado (`gh auth status`).
- Branch protection configurada en GitHub (`develop` y `main`).

## Fase 0 — Preparación del entorno (ANTES de cualquier otra cosa)

### Paso 0.1 — Copiar el agente de entorno al repo

Si aún no está en tu repo local, copia `.claude/agents/environment-setup.md` a la carpeta del proyecto.

### Paso 0.2 — Invocar el agente de entorno

```powershell
cd G:\CallBloqued\Sift
claude
```

Dentro de Claude Code:
```
Use the environment-setup agent to verify and prepare the development environment
```

El agente recorre el checklist completo y produce un reporte. **No avances hasta que todos los ítems muestren `[✓]`.**

### Paso 0.3 — Commitear el agente de entorno

```powershell
git add .claude/agents/environment-setup.md
git commit -m "chore(agents): add environment setup agent"
git push origin main
```

---

## Cómo invocar cada agente desde Claude Code

La forma de invocar un subagente en Claude Code desde la terminal es mediante el prefijo `/agent` o mencionándolo por nombre en el prompt. El patrón general:

```
Use the <agent-name> agent to <task>
```

Ejemplos reales:
```
Use the dev-movil agent to implement phase F0: Android project setup with Gradle, Hilt, and Clean Architecture folder structure
```
```
Use the test-unitario agent to write unit tests for the EvaluateIncomingCallUseCase implemented in F1
```
```
Use the revision-codigo agent to review the open PR for feature/f1-call-screening-core
```

---

## Pipeline de ejecución por fase

Ejecuta los pasos de cada fase en este orden. Cada comando se corre dentro de Claude Code (abre una sesión con `claude` en la carpeta del repo).

### FASE F0 — Setup del proyecto Android

```
# Abrir Claude Code
claude

# Paso 1: Dev Móvil crea la estructura base
Use the dev-movil agent to initialize phase F0: create the Android project structure with Gradle (libs.versions.toml), Hilt DI, Clean Architecture layers (domain/data/presentation), Room database setup, and DataStore. Branch: feature/f0-setup

# Paso 2: Test Unitario verifica que el setup es testeable
Use the test-unitario agent to verify F0 setup is testable and write smoke unit tests for the base structure

# Paso 3: Agente de Revisión (gate automático)
Use the revision-codigo agent to review the PR for feature/f0-setup

# Paso 4: Documentación
Use the documentacion agent to update ARCHITECTURE.md and write README.md for each module created in F0

# Paso 5: Orquestador mergea
Use the orquestador agent to merge feature/f0-setup into develop after all gates pass

# Paso 6: Reporte
Use the reporte agent to update STATUS.md marking F0 as complete
```

### FASE F1 — Núcleo de screening

```
# Crear rama
git checkout develop && git checkout -b feature/f1-call-screening-core && git push -u origin feature/f1-call-screening-core

# Dev Móvil
Use the dev-movil agent to implement F1: IncomingCallScreeningService, EvaluateIncomingCallUseCase, ContactsRepository, CallAttemptRepository, and phone number normalization with libphonenumber. Branch: feature/f1-call-screening-core

# Test Unitario
Use the test-unitario agent to write unit tests for EvaluateIncomingCallUseCase and ContactsRepository, verifying ≥80% coverage with JaCoCo

# Test E2E
Use the test-e2e agent to write Gherkin scenarios for F1: first call blocked, second call allowed, known contact always allowed. Use adb emu gsm call for simulation.

# QA
Use the qa agent to run all F1 tests and produce a certification report

# Revisión de código
Use the revision-codigo agent to review the F1 PR

# Documentación
Use the documentacion agent to update KDoc for all F1 public functions and update ARCHITECTURE.md decision flow

# Orquestador
Use the orquestador agent to merge feature/f1-call-screening-core into develop

# Reporte
Use the reporte agent to update STATUS.md for F1
```

Repite el mismo patrón para **F2, F3, F4, F5, F6** ajustando el nombre de rama y la tarea del Dev Móvil.

---

## Ejecución en paralelo con git worktree

Para que varios agentes trabajen simultáneamente sin bloquearse, usa `git worktree` (ver `LOCAL_AUTOMATION_SETUP.md` sección 7). Ejemplo para F1:

```powershell
# Desde la raíz del repo
git worktree add ..\sift-dev feature/f1-call-screening-core
git worktree add ..\sift-test-unit test/unit-f1-call-screening-core
git worktree add ..\sift-test-e2e test/e2e-f1-call-screening-core
git worktree add ..\sift-docs docs/f1-call-screening-core

# Terminal 1 (Dev Móvil)
cd ..\sift-dev && claude
# → Use the dev-movil agent to implement F1...

# Terminal 2 (Docs, puede arrancar en paralelo)
cd ..\sift-docs && claude
# → Use the documentacion agent to prepare KDoc templates for F1 interfaces...
```

**Qué puede correr en paralelo y qué no:**

| En paralelo ✓ | Secuencial (espera al anterior) |
|---|---|
| Documentación (templates) mientras Dev Móvil codifica | Test Unitario necesita código de Dev Móvil |
| Reporte en cualquier momento | QA necesita Tests Unitario + E2E completos |
| Preparar escenarios Gherkin (sin código aún) | Revisión de Código necesita PR abierto |

---

## Identidad de commit por agente

Antes de que cada agente haga su primer commit, configura su identidad en el worktree correspondiente (ver `LOCAL_AUTOMATION_SETUP.md` sección 4):

```powershell
# En el worktree del Dev Móvil
cd ..\sift-dev
git config user.name "Agente Dev Movil"
git config user.email "agente-dev-movil@sift.local"

# En el worktree de Test Unitario
cd ..\sift-test-unit
git config user.name "Agente Test Unitario"
git config user.email "agente-test-unitario@sift.local"
```

Configura los 8 agentes la primera vez que uses cada worktree. Esto solo se necesita hacer una vez por worktree.

---

## Comandos de diagnóstico rápido

```powershell
# Ver estado de todas las ramas
gh pr list --repo RenzoCordovaDev/Sift

# Ver el historial por agente
git log --all --oneline --author="Agente Dev Movil"

# Ver el historial completo con autor
git log --all --pretty=format:"%h %an %s" | head -30

# Verificar el gate de cobertura manualmente
./gradlew test jacocoTestCoverageVerification

# Verificar lint y detekt
./gradlew ktlintCheck detekt
```

---

## Orden de ejecución completo (resumen)

```
[Entorno]     environment-setup     → reporte verde, todos [✓]
     ↓
[F0]          dev-movil             → estructura base Android
              test-unitario         → smoke tests
              revision-codigo       → gate code-review-agent
              documentacion         → README de módulos
              orquestador           → merge a develop
              reporte               → STATUS.md actualizado
     ↓
[F1]          dev-movil             → CallScreeningService + UseCases
              test-unitario         → unit tests ≥80%
              test-e2e              → Gherkin + Appium
              qa                    → certificación
              revision-codigo       → gate
              documentacion         → KDoc + ARCHITECTURE.md
              orquestador           → merge
              reporte               → STATUS.md
     ↓
[F2 → F6]     mismo patrón
     ↓
[Release]     orquestador           → merge develop → main
              reporte               → STATUS.md final
```

---

## Agregar un nuevo agente al proyecto

Si en el futuro necesitas un nuevo agente (ej. "Agente de Seguridad"):
1. Crea `.claude/agents/seguridad.md` siguiendo la estructura de los agentes existentes.
2. Agrégalo al roster en `AGENTS_WORKFLOW.md` sección 2 y a la tabla de implementación (sección 9).
3. Commitea: `chore(agents): add security review agent`.
4. Crea su worktree y configura su identidad de commit.
