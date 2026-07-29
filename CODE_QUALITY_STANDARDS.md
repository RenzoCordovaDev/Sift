# Estándares de Calidad de Código

> Este documento **complementa** a `DEVELOPMENT_STANDARDS.md` (que cubre Git, commits, CI/CD y testing) y a `AGENTS_WORKFLOW.md` (que cubre el modelo multi-agente). Aquí se define **cómo debe verse y comportarse el código en sí**, para que el proyecto se sienta y funcione como un proyecto profesional controlado, sin importar qué agente o persona lo escriba.

## 1. Objetivo

Garantizar que todo el código del proyecto:
1. Sea legible y mantenible por cualquier agente o persona que lo retome después.
2. Siga principios de diseño consistentes, no solo "que funcione".
3. Esté **documentado a nivel de función**, no solo a nivel de módulo/README.
4. Sea medible y verificable automáticamente en CI (no dependa solo de buena voluntad).

## 2. Idioma del desarrollo (obligatorio, transversal — no confundir con el idioma de este documento)

> **Alcance de esta regla:** aplica únicamente al **desarrollo** — código, commits y Pull Requests. Los documentos de gobierno del proyecto (`ARCHITECTURE.md`, `PROJECT_CONTEXT.md`, `DEVELOPMENT_STANDARDS.md`, este mismo documento, `AGENTS_WORKFLOW.md`, `LOCAL_AUTOMATION_SETUP.md`, los archivos de `.claude/agents/` y la skill `.claude/skills/project-standards/`) permanecen en español, ya que están dirigidos al arquitecto del proyecto.

**Todo elemento del desarrollo debe estar en inglés, sin excepción:**

- Nombres de paquetes, clases, interfaces, objetos, funciones, métodos, variables, constantes y parámetros.
- Claves de recursos (`strings.xml`, IDs de layout, nombres de archivos de código).
- KDoc completo (descripción, `@param`, `@return`, `@throws`) — ver sección 5.
- Comentarios dentro del código.
- **Mensajes de commit** (Conventional Commits en inglés, ver `DEVELOPMENT_STANDARDS.md` sección 2).
- **Comentarios y revisiones en Pull Requests**, incluidos los que generen el Agente de Revisión de Código y el Agente QA.

### Ejemplo correcto

```kotlin
/**
 * Evaluates whether an incoming call should be allowed, blocked, or flagged as spam,
 * combining contact status, manual lists, and previous call attempt history.
 *
 * @param phoneNumber Phone number already normalized in E.164 format.
 * @param isKnownContact Whether the number exists in the device's contact list.
 * @return A [CallDecision] describing the action to take (ALLOW, DISALLOW, DISALLOW_AS_SPAM).
 * @throws InvalidPhoneNumberException if [phoneNumber] doesn't match the expected E.164 format.
 */
fun evaluateIncomingCall(
    phoneNumber: String,
    isKnownContact: Boolean
): CallDecision
```

### Ejemplo prohibido

```kotlin
// ❌ Identificadores y comentarios en español dentro del código
fun evaluarLlamadaEntrante(numero: String, esContactoConocido: Boolean): Decision {
    // Verifica si el número está en la agenda
    ...
}
```

### Comentarios innecesarios (prohibido, verificable)

Ya se estableció en `DEVELOPMENT_STANDARDS.md` sección 4 evitar comentarios obvios. Se refuerza aquí como **regla obligatoria de bloqueo de PR**: si algo ya está explicado en el KDoc de la función, en el nombre de la variable/función, o en la documentación del módulo, **no se repite como comentario en línea**. La documentación estructurada (KDoc, README de módulo, `ARCHITECTURE.md`) es la única fuente de verdad; el código no debe duplicarla con comentarios redundantes.

```kotlin
// ❌ Prohibido — el comentario no aporta nada que el código no diga ya
// Increment the attempt counter by one
attemptCount++

// ❌ Prohibido — redundante con el KDoc que ya está justo encima
// This function evaluates the incoming call
fun evaluateIncomingCall(...)
```

Un comentario solo se justifica cuando explica el **por qué** de una decisión no evidente (ej. una excepción de negocio, un workaround de una limitación de Android), nunca el **qué** ya visible en el código o ya documentado en el KDoc.

### Enforcement

Esta regla es parte obligatoria del checklist del **Agente de Revisión de Código** (Gate 1, ver `AGENTS_WORKFLOW.md` y `.claude/agents/revision-codigo.md`): un PR con identificadores, comentarios, KDoc o mensajes de commit en español, o con comentarios redundantes, recibe `REQUEST_CHANGES` citando esta sección — no se aprueba "para corregirlo después".

## 3. Principios de diseño obligatorios

- **SOLID** aplicado especialmente en `domain` y `data`:
  - *Single Responsibility:* una clase/función hace una sola cosa (ej. `EvaluateIncomingCallUseCase` solo decide, no persiste ni notifica).
  - *Open/Closed:* nuevas reglas de bloqueo (ej. un futuro "filtro por horario") se agregan extendiendo, no modificando lógica existente ya probada.
  - *Dependency Inversion:* `domain` depende de interfaces (`ContactsRepository`), nunca de implementaciones concretas de Android.
- **DRY (Don't Repeat Yourself):** lógica de normalización de números, validaciones, mapeos, se centralizan en utilidades reutilizables, no se copian entre capas.
- **KISS (Keep It Simple):** preferir la solución simple y explícita sobre la "elegante" pero difícil de seguir.
- **YAGNI (You Aren't Gonna Need It):** no construir abstracciones para escenarios hipotéticos fuera del alcance definido en `PROJECT_CONTEXT.md`.

## 4. Límites de complejidad (obligatorios, verificados por `detekt`)

| Métrica | Límite máximo |
|---|---|
| Longitud de función | 40 líneas |
| Complejidad ciclomática por función | 10 |
| Longitud de clase | 300 líneas |
| Parámetros por función | 5 (más de eso, usar un data class de parámetros) |
| Profundidad de anidamiento (if/for anidados) | 3 niveles |
| Líneas por archivo | 500 |

Si una función/clase excede estos límites, se **refactoriza antes de mergear**, no se justifica como excepción salvo caso documentado y aprobado explícitamente en el PR.

## 5. Documentación a nivel de función (obligatoria, no opcional)

Además del `README.md` por módulo y el `ARCHITECTURE.md` general, **toda función pública** (y toda función privada cuya lógica no sea evidente a simple vista) debe llevar **KDoc** con, como mínimo:

- Qué hace (una o dos líneas, en modo descriptivo, no repitiendo el nombre de la función).
- `@param` por cada parámetro, explicando su propósito (no solo su tipo).
- `@return` explicando qué representa el valor devuelto (no solo su tipo).
- `@throws` si la función puede lanzar excepciones esperadas.

### Plantilla obligatoria

```kotlin
/**
 * <Qué hace la función, en una o dos líneas, en términos de negocio o técnicos claros>
 *
 * @param nombreParametro <qué representa, no solo el tipo>
 * @return <qué representa el valor retornado>
 * @throws NombreExcepcion <cuándo se lanza, si aplica>
 */
```

### Ejemplo aplicado al proyecto

```kotlin
/**
 * Evalúa si una llamada entrante debe permitirse, bloquearse o marcarse como spam,
 * combinando el estado de la agenda de contactos, las listas manuales del usuario
 * y el historial de intentos previos del número.
 *
 * @param phoneNumber Número de teléfono ya normalizado en formato E.164 (ver [PhoneNumberNormalizer]).
 * @param isKnownContact Indica si el número existe en la agenda de contactos del dispositivo.
 * @return Una instancia de [CallDecision] con la acción a tomar (ALLOW, DISALLOW, DISALLOW_AS_SPAM).
 * @throws InvalidPhoneNumberException si [phoneNumber] no cumple el formato E.164 esperado.
 */
fun evaluateIncomingCall(
    phoneNumber: String,
    isKnownContact: Boolean
): CallDecision
```

### Reglas específicas

1. **Prohibido** documentar solo con frases vacías (ej. `/** Evalúa la llamada */` sin `@param`/`@return`) cuando la función tiene parámetros o valor de retorno.
2. Las funciones triviales de una línea (getters simples, mappers directos evidentes) pueden omitir KDoc completo, pero **no** funciones de lógica de negocio, aunque sean cortas.
3. El idioma de la documentación de código (KDoc, comentarios, nombres de variables) es **español**, consistente con el resto del proyecto, salvo términos técnicos estándar en inglés (ej. `callback`, `use case`).
4. Toda clase pública lleva KDoc de clase explicando su responsabilidad (ya definido en `DEVELOPMENT_STANDARDS.md` sección 8; este documento lo extiende a nivel de función).
5. Si una función cambia de comportamiento en un PR, su KDoc **debe actualizarse en el mismo commit/PR** — un KDoc desactualizado se trata como un bug de documentación.

## 6. Checklist de Definition of Done (a nivel de código)

Ninguna función/clase se considera terminada sin:
- [ ] Cumple los límites de complejidad de la sección 4.
- [ ] Tiene KDoc completo según la sección 5 (si no es trivial).
- [ ] Tiene test unitario asociado (si contiene lógica de negocio).
- [ ] No duplica lógica existente en el proyecto (revisar antes de escribir código nuevo).
- [ ] Pasa `detekt`/`ktlint` sin advertencias nuevas.
- [ ] Nombres de variables/funciones son autoexplicativos (evitar `data`, `temp`, `aux`, `x1`, etc.).

## 7. Análisis estático (configuración obligatoria)

Se usa **detekt** como herramienta principal de análisis estático (complementa a `ktlint`, que solo cubre formato). Configuración base sugerida (`detekt.yml`):

```yaml
complexity:
  LongMethod:
    active: true
    threshold: 40
  ComplexMethod:
    active: true
    threshold: 10
  LongParameterList:
    active: true
    functionThreshold: 6
  NestedBlockDepth:
    active: true
    threshold: 4

style:
  MagicNumber:
    active: true
  MaxLineLength:
    active: true
    maxLineLength: 120

comments:
  UndocumentedPublicFunction:
    active: true
  UndocumentedPublicClass:
    active: true
```

Este análisis se ejecuta en el mismo paso de CI donde corre `ktlint` (ver `DEVELOPMENT_STANDARDS.md` sección 10), y sus hallazgos de severidad alta **bloquean el merge**, igual que el gate de cobertura del 80%.

## 8. Anti-patrones prohibidos explícitamente

- **God classes/objects:** una clase que hace de todo (ej. un `CallManager` que decide, persiste, notifica y actualiza UI a la vez). Se divide según la capa que corresponda (sección 5 de `ARCHITECTURE.md`).
- **Comentarios que reemplazan nombres claros:** si se necesita un comentario para explicar qué hace una variable, el nombre de la variable está mal elegido.
- **Código muerto:** funciones o clases sin uso no se dejan "por si acaso"; se eliminan (Git conserva el historial).
- **Manejo de errores silencioso:** nunca `catch (e: Exception) {}` vacío; toda excepción se maneja explícitamente o se propaga documentada.
- **Lógica de negocio en la UI (`ViewModel`/Composable):** la decisión de bloqueo/permiso vive en `domain`, nunca en capa de presentación.

## 9. Responsabilidad por agente

- El **Agente Desarrollador Móvil** aplica estas reglas al escribir código nuevo.
- El **Agente de Revisión de Código** verifica el cumplimiento de esta guía (secciones 2 a 7) en cada PR, como primera de las dos aprobaciones obligatorias antes del merge (ver `AGENTS_WORKFLOW.md`); solicita cambios citando la sección específica incumplida.
- El **Agente de Documentación** audita, en cada PR relevante, que el KDoc esté completo y actualizado (sección 5), y lo señala como bloqueante si falta.
- El **Agente de QA** puede rechazar una certificación de fase si detecta anti-patrones de la sección 8 no resueltos, aunque los tests pasen.
- El **Agente Orquestador de Merge** verifica que el pipeline de `detekt`/cobertura esté en verde y que exista la aprobación previa del Agente de Revisión antes de integrar (ver `DEVELOPMENT_STANDARDS.md` sección 10).

## 10. Documentos relacionados

- `DEVELOPMENT_STANDARDS.md` — Git, commits, CI/CD, testing y cobertura (JaCoCo ≥80%).
- `AGENTS_WORKFLOW.md` — roles de agentes y pipeline de trabajo por fase.
- `ARCHITECTURE.md` — estructura de capas y stack técnico.
- `PROJECT_CONTEXT.md` — objetivos y reglas de producto que acotan qué "buena práctica" es relevante para este proyecto en particular.
