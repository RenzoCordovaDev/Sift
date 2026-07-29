# Estándares de Desarrollo Colaborativo

## 1. Control de versiones

- **Repositorio:** GitHub, cuenta personal del propietario, **repositorio público**.
- **Modelo de trabajo:** desarrollo mediante **agentes especializados**, cada uno con rama propia. El detalle completo de agentes, responsabilidades y ramas está en `AGENTS_WORKFLOW.md` — este documento define solo las convenciones generales de Git.
- **Estrategia de ramas:** Git Flow adaptado a agentes
  - `main` → siempre desplegable/estable.
  - `develop` → integración de todas las fases/features.
  - `feature/<fase>-<nombre-corto>` → agente Desarrollador Móvil.
  - `test/unit-<fase>-<nombre-corto>` → agente Test Unitarios.
  - `test/e2e-<fase>-<nombre-corto>` → agente Test E2E.
  - `qa/<fase>` → agente QA.
  - `docs/<fase>-<nombre-corto>` → agente Documentación.
  - `integration/<fase>` → agente Orquestador de Merge (temporal).
  - `fix/<nombre-corto>` → corrección de bug puntual.
  - `release/<version>` → estabilización antes de publicar.
- Nunca hacer commit directo a `main` o `develop`. Todo entra vía Pull Request, y solo el agente Orquestador integra hacia `develop`/`main`.
- **Todo commit, en cualquier rama, debe cumplir obligatoriamente [Conventional Commits v1.0.0](https://www.conventionalcommits.org/en/v1.0.0/)** (ver sección 2). Un PR con commits no conformes se rechaza.
- Por ser repositorio **público**: nunca commitear secretos, keystores, claves de firma ni datos reales de contactos/números de prueba (ver `AGENTS_WORKFLOW.md`, sección 8).

## 2. Convención de commits (obligatoria)

Uso **obligatorio** de la especificación **[Conventional Commits v1.0.0](https://www.conventionalcommits.org/en/v1.0.0/)** en todos los commits del repositorio, sin excepción, independientemente del agente que lo genere.

> **Idioma obligatorio: inglés.** El mensaje de commit (tipo, scope y descripción) es parte del desarrollo, no de los documentos de gobierno — por lo tanto se escribe siempre en inglés, sin excepción. Ver `CODE_QUALITY_STANDARDS.md` sección 2.

### Estructura obligatoria

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

### Reglas MUST (no negociables, según el spec)

1. Todo commit **debe** iniciar con un `type` (`feat`, `fix`, etc.), seguido opcionalmente de un `scope` entre paréntesis, opcionalmente `!`, y obligatoriamente `: ` (dos puntos + espacio) antes de la descripción.
2. `feat` se usa **únicamente** cuando el commit agrega una nueva funcionalidad.
3. `fix` se usa **únicamente** cuando el commit corrige un bug.
4. La descripción va inmediatamente después de `type(scope):`, **en inglés**, en minúscula, modo imperativo, sin punto final. Ej.: `fix: normalize phone numbers with +51 prefix`.
5. Un cambio que **rompe compatibilidad** (breaking change) debe indicarse con `!` antes de los dos puntos (`feat(api)!: ...`) y/o con un footer `BREAKING CHANGE: <description>` (en inglés). Esto aplica sin importar el `type` usado.
6. El cuerpo (si existe) va separado de la descripción por una línea en blanco, en inglés.
7. Los footers (si existen) van separados del cuerpo por una línea en blanco, con formato `Token: value` (ej. `Refs: #123`, `Reviewed-by: name`).

### Tipos permitidos en este proyecto

| Tipo | Uso |
|---|---|
| `feat` | Nueva funcionalidad |
| `fix` | Corrección de bug |
| `docs` | Cambios de documentación de código (KDoc, README de módulo) — no de los documentos de gobierno, que se editan libremente |
| `test` | Agregar o corregir tests unitarios o E2E (Gherkin/Cucumber/Appium) |
| `refactor` | Cambio de código que no agrega funcionalidad ni corrige bugs |
| `chore` | Tareas de mantenimiento (dependencias, configuración de build) |
| `ci` | Cambios en pipelines de CI/CD |
| `style` | Formato de código, sin cambios de lógica |
| `perf` | Mejoras de rendimiento |
| `revert` | Reversión de un commit anterior (con footer `Refs: <sha>`) |

### Scope recomendado (mapeo a agentes/módulos)

Usar como scope el módulo o la fase afectada, en inglés: `feat(f1-call-screening): ...`, `test(unit-attempt-history): ...`, `docs(kdoc-call-evaluator): ...`.

### Ejemplos válidos para este proyecto

```
feat(f1-call-screening): add blocking logic for first call attempt
fix(contacts-repository): normalize phone numbers with +51 prefix
docs(call-evaluator): document evaluateIncomingCall return values
test(unit-attempt-history): add tests for CallAttemptRepository
test(e2e-call-screening): add Gherkin scenario for second attempt allowed
refactor(domain): extract UseCase for incoming call evaluation
chore(deps): update Room dependencies
feat(api)!: change attempt persistence format

BREAKING CHANGE: Room schema changed and requires a manual migration of existing data
```

### Granularidad y atomicidad de commits (obligatoria — trazabilidad histórica)

El objetivo de esta regla es que el **arquitecto/propietario del proyecto** pueda, en cualquier momento, revisar el historial de Git y entender la evolución real del proyecto commit por commit, sin depender de que alguien se lo explique.

1. **Un commit = un cambio lógico.** No se mezclan en un mismo commit, por ejemplo, una corrección de bug con un refactor no relacionado, o un cambio de lógica con un reformateo masivo de archivos.
2. **Prohibidos los commits "paraguas"** que agrupan trabajo de varios días o de toda una fase completa en un solo commit (ej. `feat: implementar fase 1 completa`). Cada avance verificable (una función, una clase, un caso de uso) es su propio commit.
3. **Tamaño de referencia:** un commit que modifique más de ~300-400 líneas o más de 8-10 archivos no relacionados es señal de que debía dividirse; se prefiere dividir en varios commits pequeños dentro del mismo PR antes que uno grande.
4. **Historial legible como changelog:** el conjunto de commits de un PR, leído en orden, debe poder reconstruir la historia del cambio (qué se intentó primero, qué se corrigió, qué se documentó), no ser una sucesión de `fix: fix`, `wip`, `更新` sin sentido.
5. **Squash controlado, no automático:** no se usa "squash merge" que colapse todo el PR en un solo commit al integrar a `develop`/`main`, ya que eso destruye la traza histórica granular. **Estrategia de integración definitiva del proyecto: `rebase + fast-forward`**, preservando cada commit individual del PR (siempre que cada uno cumpla Conventional Commits y esta sección de granularidad).
6. Si durante el desarrollo se generan commits intermedios desordenados (ej. `wip`, `fix typo`), el agente autor debe limpiarlos con `git rebase -i` **antes de solicitar revisión**, dejando el historial final ya ordenado y conforme a esta sección — no se limpia después del merge.
7. Esta regla aplica a todos los agentes por igual y es verificada por el **Agente de Revisión de Código** como parte de su revisión (ver `AGENTS_WORKFLOW.md`), no solo por el Orquestador.

**Ejecución práctica:** en GitHub, esto corresponde a habilitar únicamente la opción **"Allow rebase merging"** en la configuración del repositorio, y **deshabilitar** "Allow squash merging" y "Allow merge commits". El **Agente Orquestador de Merge** es quien ejecuta este rebase al integrar (manualmente o vía el botón "Rebase and merge"), asegurándose antes de que la rama esté actualizada contra `develop`/`main` para evitar conflictos silenciosos.

- Se debe configurar **commitlint** (con `@commitlint/config-conventional`) + **husky** (`commit-msg` hook) para rechazar localmente cualquier commit que no cumpla el spec.
- El pipeline de CI debe incluir un **paso de validación de commits** en cada PR (ej. `commitlint --from` sobre el rango del PR); un PR con commits no conformes **no puede mergearse**.
- Esta regla aplica igual para todos los agentes (Dev Móvil, Test Unitario, Test E2E, QA, **Revisión de Código**, Documentación, Orquestador, Reporte) y para el propietario del proyecto.
- El **Agente de Revisión de Código** verifica el cumplimiento de Conventional Commits y de la granularidad/atomicidad de commits (ver más abajo) como parte de su revisión, antes de aprobar.
- El **Agente Orquestador de Merge** es responsable de rechazar cualquier PR que no cumpla esta convención antes de integrarlo a `develop`/`main`.

## 3. Pull Requests

- Título descriptivo, referenciando el issue/tarea si existe.
- Descripción: qué cambia, por qué, cómo probarlo.
- **2 gates obligatorios antes de merge:** (1) status check automatizado `code-review-agent` del Agente de Revisión de Código, (2) aprobación + merge del Agente Orquestador (única aprobación humana/de cuenta real, ya que GitHub no permite contar 2 aprobaciones de la misma cuenta). Ver detalle completo en `AGENTS_WORKFLOW.md` ("Regla de aprobación mínima") y `LOCAL_AUTOMATION_SETUP.md` sección 1.
- Ningún PR se mergea si el status check `code-review-agent` está en rojo, sin importar la urgencia percibida.
- CI debe pasar en verde (build + tests + lint + detekt + **validación de Conventional Commits** + gate de cobertura ≥80%) antes de mergear.
- Todos los commits del PR deben cumplir Conventional Commits v1.0.0; de lo contrario, el PR se rechaza o se solicita rebase/squash.
- PRs pequeños y enfocados; evitar mezclar features no relacionadas.
- **Idioma obligatorio: inglés**, para el título del PR, su descripción, y todo comentario o review que se publique sobre él (incluidos los del Agente de Revisión de Código y del Agente de QA) — es parte del desarrollo, no de los documentos de gobierno. Ver `CODE_QUALITY_STANDARDS.md` sección 2.

## 4. Estilo de código

> Los principios de diseño, límites de complejidad, documentación a nivel de función y anti-patrones prohibidos están definidos en **`CODE_QUALITY_STANDARDS.md`** — de cumplimiento obligatorio. Esta sección cubre solo convenciones de formato y nombres.

- Seguir la **guía oficial de estilo de Kotlin** (kotlinlang.org/docs/coding-conventions.html).
- Formateo automático con `ktlint` integrado en CI; análisis estático adicional con `detekt` (reglas en `CODE_QUALITY_STANDARDS.md` sección 7).
- Nombres:
  - Clases/Interfaces → `PascalCase`
  - Funciones/variables → `camelCase`
  - Constantes → `UPPER_SNAKE_CASE`
  - Archivos de recursos XML → `snake_case`
- Evitar comentarios obvios; comentar el **por qué**, no el **qué**, cuando la lógica no sea autoevidente.

## 5. Arquitectura y capas (reglas obligatorias)

- El módulo `domain` **no puede** importar clases de Android Framework (`android.*`) ni de librerías de UI.
- Toda comunicación entre capas se hace mediante interfaces definidas en `domain`, implementadas en `data`.
- Los `ViewModel` no acceden directamente a Room ni a `ContactsContract`; siempre pasan por un `UseCase`/`Repository`.
- Nueva funcionalidad de negocio → primero un `UseCase`, con su test unitario, antes de tocar UI.

## 6. Testing

- **Cobertura mínima obligatoria: 80%** en `domain` y `data`, a cargo del **Agente Test Unitarios**. Esta regla es un **gate de CI**, no solo un objetivo (ver sección 10).
- Toda lógica de decisión de bloqueo/permiso de llamada debe tener test unitario (es el corazón del producto, no puede tener regresiones silenciosas).
- Nomenclatura de tests: `` `metodo should hacer X cuando condicion Y`() ``
- **Certificación E2E/QA:** a cargo del **Agente Test E2E** + **Agente de QA**, con escenarios en **Gherkin**, ejecutados vía **Cucumber-JVM + Appium** (equivalente a Selenium/WebDriver para apps nativas Android). Ver `AGENTS_WORKFLOW.md` sección 6.1 para estructura y ejemplos. Ninguna fase se cierra sin esta certificación.
- Tests de UI (Compose/Espresso) para flujos críticos: onboarding de permisos, pantalla de historial, además del flujo E2E de screening de llamadas.
- Una fase no se integra a `develop` sin ambas certificaciones (unitaria con cobertura ≥80% + QA) aprobadas.

## 7. Manejo de dependencias

- Toda librería nueva se agrega vía `libs.versions.toml` (Version Catalog de Gradle), no hardcoded en `build.gradle`.
- Antes de agregar una dependencia nueva, evaluar: mantenimiento activo, tamaño, licencia compatible.

## 8. Documentación de código

- Toda clase pública en `domain` y `data` lleva KDoc breve explicando su responsabilidad.
- **Toda función** pública (y privada no trivial) debe llevar KDoc completo con descripción, `@param`, `@return` y `@throws` según aplique — regla obligatoria, no opcional. El detalle, plantilla y ejemplos están en **`CODE_QUALITY_STANDARDS.md`** sección 5.
- Un KDoc desactualizado tras un cambio de comportamiento se trata como un bug de documentación y bloquea el PR (ver `CODE_QUALITY_STANDARDS.md` sección 5, regla 5).
- Cambios de arquitectura relevantes se reflejan en `ARCHITECTURE.md` en el mismo PR que los introduce.
- El **Agente de Documentación** (ver `AGENTS_WORKFLOW.md`) es responsable de auditar el cumplimiento de KDoc a nivel de función en cada PR relevante, no solo de mantener los documentos de alto nivel (`README.md`, `ARCHITECTURE.md`, etc.).

## 9. Seguridad y privacidad (crítico para este proyecto)

- La app maneja datos sensibles (contactos, historial de llamadas). Reglas obligatorias:
  - Nunca enviar números de teléfono ni contactos a servidores externos sin consentimiento explícito y documentado.
  - Toda la lógica de v1 es **100% local** (on-device), sin backend, salvo que se decida lo contrario explícitamente y se actualice este documento.
  - No loggear números de teléfono completos en logs de producción (usar máscara, ej. `***1234`).
- Cualquier cambio que implique enviar datos fuera del dispositivo requiere revisión y aprobación explícita, y actualización de la política de privacidad.

## 10. CI/CD

Pipeline obligatorio (GitHub Actions o equivalente), ejecutado en **cada PR**:

1. `commitlint` — valida que todos los commits del PR cumplan Conventional Commits v1.0.0 (ver sección 2).
2. `ktlint check` / `detekt` — lint de estilo de código.
3. **Build** del proyecto (compilación limpia).
4. **Ejecución automática de tests unitarios** (`./gradlew test` o equivalente) sobre `domain` y `data`.
5. **Gate de cobertura de código ≥ 80%**, generado con **JaCoCo** (herramienta de cobertura oficial y definitiva del proyecto), aplicado sobre `domain` y `data`:
   - El pipeline **falla automáticamente** si la cobertura total del PR cae por debajo del 80%, o si el código nuevo/modificado introducido por el PR queda por debajo de ese umbral (cobertura diferencial).
   - El reporte de cobertura se publica como artefacto del PR (HTML/XML) y, si es posible, como comentario automático del bot de CI en el PR.
   - Este gate **bloquea el merge**: el Agente Orquestador no puede integrar un PR con el pipeline en rojo por cobertura insuficiente.
6. Ejecución de suite E2E (Gherkin + Cucumber + Appium) — puede ejecutarse en un job separado/nocturno si el tiempo de emulador lo amerita, pero es obligatoria antes de cerrar una fase (no necesariamente en cada PR individual).
7. En merge a `main`: generar `release APK`/`AAB` firmado como artefacto.

**Configuración sugerida (Gradle):**
```kotlin
// build.gradle.kts (módulo domain/data)
jacoco {
    toolVersion = "0.8.11"
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
```

Con `tasks.check` dependiendo de `jacocoTestCoverageVerification`, cualquier ejecución de `./gradlew check` (parte del pipeline de CI) falla automáticamente si no se cumple el 80%.

## 11. Gestión de tareas

- Issues/tareas en el gestor elegido (GitHub Projects, Jira, Trello, etc.) organizadas según las fases descritas en `ARCHITECTURE.md`.
- Cada tarea debe tener criterio de aceptación claro antes de comenzar a codificarla.
