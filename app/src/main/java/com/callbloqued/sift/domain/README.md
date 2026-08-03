# domain — Capa de Dominio

La capa de dominio contiene la **lógica de negocio pura**, independiente de cualquier framework de Android o detalles de implementación.

## Principios

- **Sin imports de Android:** ningún paquete bajo `android.*`, `androidx.*` u otros frameworks Android. Eso garantiza que toda la lógica sea testeable en el JVM sin necesidad de emulador.
- **Interfaces, no implementaciones:** los casos de uso (use cases) y las entidades declaran contratos (interfaces) que la capa `data` implementa.
- **Responsabilidad única:** cada caso de uso hace una única cosa; cada interface tiene un propósito bien definido.

## Contenido actual (F0)

### `repository/`

Interfaces de contrato para los tres repositorios que el proyecto necesita:

- **`ContactsRepository`** — consulta la agenda de contactos del dispositivo.
  - Implementación: **F1** (acceso a `ContactsContract`).
  - Métodos: `suspend fun isKnownContact(phoneNumber: String): Boolean`

- **`CallAttemptRepository`** — persiste y consulta intentos de llamadas bloqueadas.
  - Implementación: **F2** (entidad Room `CallAttemptEntity` + DAO).
  - Métodos: `getAttemptCount(phoneNumber)`, `recordAttempt(phoneNumber)`

- **`SettingsRepository`** — lee y escribe preferencias del usuario.
  - Implementación: **F1** (wrapper de `SettingsDataStore` con claves tipadas).
  - Métodos: `isFilterEnabled()`, `setFilterEnabled()`, `getRequiredAttemptCount()`, `setRequiredAttemptCount()`

## Próximas fases

- **F1:** `EvaluateIncomingCallUseCase` — orquesta las decisiones de bloqueo usando los repositorios.
- **F2+:** entidades de dominio (value objects) que modelen decisiones y resultados.

## Testing

- Todos los tests de esta capa son JVM (sin `androidTest`).
- Se usan MockK para mockear repositorios, Turbine para validar Flows.
- Cobertura objetivo: **≥80%** (JaCoCo).
