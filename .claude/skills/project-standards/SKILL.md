---
name: project-standards
description: Resumen obligatorio de los estándares del proyecto Sift (arquitectura, calidad de código, Git/CI, y flujo multi-agente). Úsala siempre que trabajes en este repositorio, sin importar el rol.
---

# Estándares del Proyecto Sift

Este proyecto es una app Android de bloqueo de llamadas de números desconocidos. Antes de actuar, ten presente estas reglas — el detalle completo vive en los documentos referenciados al final; esta skill es el resumen que debes aplicar siempre.

## Reglas no negociables

1. **Nunca bloquear un número en la agenda de contactos.** Ver `PROJECT_CONTEXT.md` sección 7.
2. **Arquitectura:** Clean Architecture + MVVM. `domain` nunca importa `android.*`. Ver `ARCHITECTURE.md`.
3. **Commits:** Conventional Commits v1.0.0 obligatorio (`tipo(scope): descripción`), atómicos (un cambio lógico por commit), nunca "paraguas". Ver `DEVELOPMENT_STANDARDS.md` sección 2.
4. **Merge:** solo `rebase + fast-forward`. Nunca squash ni merge commit.
5. **Cobertura de tests:** ≥80% en `domain`/`data`, verificado con JaCoCo — es un gate de CI, no una meta aspiracional.
6. **Documentación de función:** toda función pública (y privada no trivial) lleva KDoc completo (`@param`, `@return`, `@throws`). Un KDoc desactualizado es un bug. Ver `CODE_QUALITY_STANDARDS.md` sección 5.
7. **Complejidad:** máx. 40 líneas/función, complejidad ciclomática ≤10, máx. 5 parámetros, anidamiento ≤3. Ver `CODE_QUALITY_STANDARDS.md` sección 4.
8. **Anti-patrones prohibidos:** god classes, catch vacíos, lógica de negocio en UI, código muerto. Ver `CODE_QUALITY_STANDARDS.md` sección 8.
9. **E2E:** Gherkin + Cucumber-JVM + Appium (no Selenium — no aplica a apps nativas Android). Ver `AGENTS_WORKFLOW.md` sección 6.1.
10. **Privacidad:** toda la lógica es 100% local; nunca se envían contactos/números a servidores externos. Repo público → nunca commitear secretos/keystores/datos reales.
11. **Idioma del desarrollo: 100% inglés, sin excepción** — nombres de clases, funciones, variables, constantes, parámetros, recursos, KDoc, comentarios en código, mensajes de commit y comentarios/reviews en PR (incluidos los tuyos si eres Revisión de Código o QA). Solo los documentos de gobierno del proyecto (este archivo, los `.md` de la raíz, y los demás archivos de `.claude/`) permanecen en español. Ver `CODE_QUALITY_STANDARDS.md` sección 2.
12. **Sin comentarios innecesarios:** si algo ya está explicado por el KDoc o por el nombre de la función/variable, no se repite como comentario en línea. Un comentario solo se justifica para explicar el *por qué* de una decisión no evidente, nunca el *qué* ya visible en el código. Ver `CODE_QUALITY_STANDARDS.md` sección 2.

## Tu rol dentro del flujo

Cada agente tiene una responsabilidad acotada (ver `AGENTS_WORKFLOW.md` sección 2 para el roster completo). No hagas el trabajo de otro rol: si detectas algo fuera de tu responsabilidad, repórtalo en vez de "arreglarlo" tú mismo.

## Documentos de referencia (leer el que aplique antes de trabajar)

- `PROJECT_CONTEXT.md` — objetivos, alcance, reglas de producto, riesgos.
- `ARCHITECTURE.md` — capas, stack técnico, flujo de decisión de bloqueo, roadmap por fases.
- `DEVELOPMENT_STANDARDS.md` — Git, commits, PRs, CI/CD, cobertura.
- `CODE_QUALITY_STANDARDS.md` — SOLID, complejidad, KDoc, anti-patrones.
- `AGENTS_WORKFLOW.md` — roster de agentes, ramas, pipeline, branch protection.
- `LOCAL_AUTOMATION_SETUP.md` — autenticación, identidad de commit por agente, worktrees, orquestación.
