# Configuración de Ejecución Local y Autonomía del Flujo Multi-Agente

> Este documento complementa a `AGENTS_WORKFLOW.md` y `DEVELOPMENT_STANDARDS.md`. Describe qué necesitas configurar **en tu propia PC/terminal** para que el flujo de agentes corra de forma prácticamente autónoma contra GitHub. Ningún agente (ni Claude) debe manejar tus credenciales directamente; tú las configuras una vez en tu entorno y los procesos las usan localmente.

## 1. Decisión tomada sobre las "2 aprobaciones"

GitHub no permite que una misma cuenta autenticada cuente como 2 aprobaciones distintas de PR review. Se adopta la opción **simple**:

- **Gate 1 — Agente de Revisión de Código:** corre como un **status check automatizado** (job de CI/script), no como una "review" formal de GitHub. Publica su resultado (pass/fail + comentario con hallazgos) sobre el PR.
- **Gate 2 — Agente Orquestador:** es la **única aprobación humana/de cuenta real** requerida por `branch protection`, y quien ejecuta el merge (rebase) una vez que el Gate 1 está en verde.

Esto se refleja como actualización en `AGENTS_WORKFLOW.md`:
- `branch protection` → **"Require pull request reviews before merging" = 1 aprobación** (la del Orquestador/propietario).
- `branch protection` → **"Require status checks to pass before merging"** incluye un check obligatorio llamado, por ejemplo, `code-review-agent`, que debe estar en verde antes de habilitar el botón de merge.

## 2. Autenticación con GitHub (configuración única, la haces tú)

**Recomendado: GitHub CLI (`gh`)**, por ser lo más simple de operar desde terminal y scripts.

```bash
gh auth login
# Selecciona: GitHub.com → HTTPS → autenticar con navegador o con un token
```

Esto guarda el token de forma segura en el gestor de credenciales del sistema operativo (Keychain en macOS, Credential Manager en Windows, `libsecret`/`pass` en Linux vía `gh`), **no en texto plano dentro del repo**.

### Alternativa: Personal Access Token (fine-grained) manual
Si prefieres no usar `gh auth login` interactivo:
1. Crear un **fine-grained PAT** en GitHub (Settings → Developer settings → Fine-grained tokens), con:
   - Acceso limitado **solo a este repositorio**.
   - Permisos: `Contents: Read and write`, `Pull requests: Read and write`, `Commit statuses: Read and write`, `Metadata: Read-only`. No se necesitan permisos de administración de cuenta ni de otros repos.
2. Guardarlo como variable de entorno local, **nunca en un archivo versionado**:
   ```bash
   export GH_TOKEN="tu_token_aqui"   # en tu perfil de shell (~/.zshrc, ~/.bashrc), no en el repo
   ```
3. `gh` y `git` (vía credential helper) lo usan automáticamente.

### Reglas de seguridad (obligatorias)
- El token **jamás** se commitea, ni se pega en un archivo del repo, ni se imprime en logs de CI.
- `.gitignore` debe excluir cualquier archivo `.env`, `*.token`, `secrets.*`.
- Si el proceso corre completamente local (sin GitHub Actions), el token vive únicamente en variables de entorno de tu máquina.

## 3. Creación inicial del repositorio (comandos de referencia)

Estos comandos los ejecutas tú (o un script que tú disparas) una sola vez:

```bash
# Crear el repo público bajo tu cuenta personal
gh repo create <tu-usuario>/sift --public --description "App Android de bloqueo de llamadas" --clone
cd sift

# Rama develop a partir de main
git checkout -b develop
git push -u origin develop

# Ramas de fase F0 (ejemplo)
git checkout -b feature/f0-setup develop
git push -u origin feature/f0-setup
```

## 4. Identidad de commit por agente (resuelve tu preocupación de trazabilidad)

Aunque **toda la autenticación con GitHub use una sola cuenta/token tuyo**, el **autor de cada commit es un campo independiente** que sí se puede diferenciar por agente. Esto es lo que te permite, como arquitecto, hacer `git log --author="Agente Dev Movil"` y ver exactamente qué escribió cada uno.

### Configuración recomendada: `user.name`/`user.email` por agente, vía variables de entorno al commitear

En vez de cambiar la configuración global de Git, cada agente commitea usando variables de entorno puntuales (no persistentes, no requiere reconfigurar nada global):

```bash
GIT_AUTHOR_NAME="Agente Dev Movil" \
GIT_AUTHOR_EMAIL="agente-dev-movil@sift.local" \
GIT_COMMITTER_NAME="Agente Dev Movil" \
GIT_COMMITTER_EMAIL="agente-dev-movil@sift.local" \
git commit -m "feat(f1-call-screening): agregar lógica de bloqueo por primer intento"
```

### Tabla de identidades sugeridas por agente

| Agente | `GIT_AUTHOR_NAME` | `GIT_AUTHOR_EMAIL` |
|---|---|---|
| Desarrollador Móvil | `Agente Dev Movil` | `agente-dev-movil@sift.local` |
| Test Unitarios | `Agente Test Unitario` | `agente-test-unitario@sift.local` |
| Test E2E | `Agente Test E2E` | `agente-test-e2e@sift.local` |
| QA | `Agente QA` | `agente-qa@sift.local` |
| Revisión de Código | `Agente Revision Codigo` | `agente-revision@sift.local` |
| Documentación | `Agente Documentacion` | `agente-documentacion@sift.local` |
| Orquestador | `Agente Orquestador` | `agente-orquestador@sift.local` |
| Reporte | `Agente Reporte` | `agente-reporte@sift.local` |

Esto es **cosmético/de trazabilidad**, no de autenticación: GitHub seguirá mostrando que el **push** lo hizo tu cuenta (o el token configurado), pero cada commit individual queda firmado con el nombre del agente que lo generó, visible en `git log`, `git blame` y en la vista de historial de GitHub (aunque el ícono de "verified" del push sea el tuyo).

> **Nota:** si en el futuro quieres que además el ícono de autor en la interfaz de GitHub (avatar) corresponda a cada agente, eso sí requeriría cuentas GitHub reales por agente (con su email verificado) — no es necesario para tu objetivo actual de trazabilidad en el historial de Git.

## 5. Implementación del "Agente de Revisión de Código" como status check

Dado que decidiste la opción simple, este agente se implementa como un **script/job que se ejecuta antes de que el PR se considere mergeable**, y publica su resultado como *commit status* vía `gh`:

```bash
# Ejemplo simplificado de lo que ejecutaría este agente sobre la rama del PR
./gradlew detekt ktlintCheck testDebugUnitTest jacocoTestCoverageVerification

if [ $? -eq 0 ]; then
  gh api repos/<tu-usuario>/sift/statuses/$(git rev-parse HEAD) \
    -f state="success" -f context="code-review-agent" \
    -f description="Cumple CODE_QUALITY_STANDARDS.md y ARCHITECTURE.md"
else
  gh api repos/<tu-usuario>/sift/statuses/$(git rev-parse HEAD) \
    -f state="failure" -f context="code-review-agent" \
    -f description="Revisar hallazgos de detekt/cobertura"
fi
```

Si se ejecuta con GitHub Actions en vez de 100% local, este mismo bloque se coloca como un job de un workflow `.github/workflows/code-review-agent.yml`, y GitHub lo reconoce automáticamente como *status check* del PR.

## 6. Branch protection final (ajustado a la decisión de la sección 1)

- `develop` y `main`:
  - **Require pull request reviews before merging → 1 aprobación** (Orquestador/propietario).
  - **Require status checks to pass before merging** → incluir: `commitlint`, `ktlint`, `detekt`, `unit-tests`, `jacoco-coverage-80`, **`code-review-agent`**.
  - **Dismiss stale pull request approvals when new commits are pushed** → activado.
  - **Estrategia de merge: solo "Allow rebase merging"** (ya definido en `DEVELOPMENT_STANDARDS.md`).

## 7. Trabajo en paralelo/semi-paralelo: `git worktree`

Para que varios agentes trabajen simultáneamente sin pisarse (cada uno necesita su propio checkout de rama), se usa **`git worktree`** en vez de clonar el repo varias veces:

```bash
# Desde el repo principal ya clonado
git worktree add ../sift-dev-movil feature/f1-call-screening-core
git worktree add ../sift-test-unit test/unit-f1-call-screening-core
git worktree add ../sift-test-e2e test/e2e-f1-call-screening-core
git worktree add ../sift-docs docs/f1-call-screening-core
```

Cada carpeta (`../sift-dev-movil`, etc.) es un directorio de trabajo independiente sobre su propia rama, pero comparten el mismo historial de Git subyacente — no duplican el repo completo en disco. Esto permite:
- Que cada agente (o cada sesión de Claude Code) opere en su propia carpeta/terminal, en paralelo real.
- Que no haya conflictos de "checkout" entre agentes trabajando al mismo tiempo.

### Grado de paralelismo real por fase

No todo puede correr en paralelo: hay dependencias naturales (ver pipeline de `AGENTS_WORKFLOW.md` sección 5). Paralelismo recomendado:

| Puede correr en paralelo | Debe ser secuencial |
|---|---|
| Agente Documentación (redactando criterios de aceptación) mientras Dev Móvil aún no empieza | Test Unitario necesita código de Dev Móvil ya disponible |
| Agente Test E2E preparando escenarios Gherkin (sin depender del código aún) | QA necesita Test Unitario + Test E2E ya ejecutados |
| Agente de Reporte actualizando `STATUS.md` en cualquier momento | Revisión de Código y Orquestador necesitan PR abierto y CI en verde |

## 8. Orquestación desde terminal (opción práctica para correr "casi al 100%" autónomo)

Dado que todo corre en tu PC, la forma más simple de operarlo es:

1. **Una terminal/pestaña (o `tmux`/`screen` con paneles) por agente activo**, cada una posicionada en su propio `git worktree` (sección 7).
2. Un **script orquestador** (`run_phase.sh`) que, por fase, dispara en orden: Dev Móvil → Test Unitario → Test E2E → QA → (status check de Revisión) → Orquestador (merge) → Documentación → Reporte, respetando el pipeline definido.
3. Si usas Claude Code (u otra herramienta de agentes en terminal), cada worktree puede tener su propia sesión de Claude Code abierta, apuntando solo a esa carpeta — así "cada agente" es literalmente una sesión distinta, con su propio contexto e instrucciones (el rol específico definido en `AGENTS_WORKFLOW.md` se le da como prompt/system message de esa sesión).

Ejemplo simplificado de `run_phase.sh` (referencia, no un producto terminado):
```bash
#!/bin/bash
FASE=$1  # ej: f1-call-screening-core

echo "== Ejecutando pipeline para $FASE =="
(cd ../sift-dev-movil && ./agente_dev_movil.sh "$FASE") &&
(cd ../sift-test-unit && ./agente_test_unitario.sh "$FASE") &&
(cd ../sift-test-e2e && ./agente_test_e2e.sh "$FASE") &&
(cd ../sift-qa && ./agente_qa.sh "$FASE") &&
(cd ../sift-dev-movil && ./agente_revision_codigo.sh "$FASE") &&
gh pr merge "feature/$FASE" --rebase --repo <tu-usuario>/sift
```
Cada `agente_*.sh` es, en la práctica, donde invocas a Claude/Claude Code con el prompt de rol correspondiente a ese agente (definido en `AGENTS_WORKFLOW.md`), operando sobre esa carpeta/rama.

## 9. Qué NO se puede automatizar al 100% (límites honestos)

- **La creación inicial del repo, la primera autenticación (`gh auth login`) y la configuración de `branch protection`** las debes ejecutar tú una vez; no son delegables a un agente por razones de seguridad de credenciales.
- **Decisiones de arquitectura mayores** (cambios de alcance, romper compatibilidad, cambiar de stack) deben pasar por ti como arquitecto, no solo por los agentes.
- **Publicación en Google Play** (declaración de permisos sensibles, firma de release) requiere tu cuenta de desarrollador y no es automatizable por completo por políticas de Google.
- **Rate limits de la API de GitHub** aplican igual a un token personal usado de forma intensiva por varios agentes en paralelo; si el volumen de llamadas crece, conviene espaciar ejecuciones o usar un GitHub App con límites más altos.

## 10. Checklist de setup inicial (una sola vez)

- [ ] `gh auth login` ejecutado y verificado (`gh auth status`).
- [ ] Repositorio creado (`gh repo create`) y clonado localmente.
- [ ] Ramas `main`/`develop` creadas y protegidas (sección 6).
- [ ] `branch protection` configurada con el check `code-review-agent` y 1 aprobación humana.
- [ ] `.gitignore` con exclusión de secretos/tokens.
- [ ] `git worktree` creados para las ramas de la primera fase (F0).
- [ ] Variables `GIT_AUTHOR_NAME`/`GIT_AUTHOR_EMAIL` probadas con un commit de prueba por cada agente.
