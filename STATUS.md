# Estado del Proyecto Sift

Actualizado por el Agente de Reporte. Ver `AGENTS_WORKFLOW.md` sección 10.

| Fase | Estado | Rama activa | Certificación QA | Última actualización |
|---|---|---|---|---|
| F0 - Setup | ✅ Completo (mergeado a develop) | — | N/A (fase de setup) | 2026-08-03 |
| F1 - Núcleo screening | ⏳ Pendiente | — | — | — |
| F2 - Historial de intentos | ⏳ Pendiente | — | — | — |
| F3 - Listas manuales | ⏳ Pendiente | — | — | — |
| F4 - UI | ⏳ Pendiente | — | — | — |
| F5 - Onboarding | ⏳ Pendiente | — | — | — |
| F6 - Pulido y publicación | ⏳ Pendiente | — | — | — |

## Notas y pendientes menores

- **Room database schema** (`app/schemas/`): generado y sin rastrear en git. Debería commitearse según KDoc de `AppDatabase.kt`; pendiente en siguiente sync.
- **Worktrees** (`.claude/worktrees/`): directorio sin agregar a `.gitignore`. Deberá incluirse en próxima limpieza de git ignore.
