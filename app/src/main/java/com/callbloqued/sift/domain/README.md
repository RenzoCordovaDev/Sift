# domain — Capa de Dominio

La capa de dominio contiene la **lógica de negocio pura**, independiente de cualquier framework de Android o detalles de implementación.

## Principios

- **Sin imports de Android:** ningún paquete bajo `android.*`, `androidx.*` u otros frameworks Android. Eso garantiza que toda la lógica sea testeable en el JVM sin necesidad de emulador.
- **Interfaces, no implementaciones:** los casos de uso (use cases) y las entidades declaran contratos (interfaces) que la capa `data` implementa.
- **Responsabilidad única:** cada caso de uso hace una única cosa; cada interface tiene un propósito bien definido.

## Contenido actual

### `repository/` — Interfaces de contrato (F0-F1)

- **`ContactsRepository`** — consulta la agenda de contactos del dispositivo.
  - Implementación: **F1** (`ContactsRepositoryImpl` + `ContactsContentResolverGateway`).
  - Métodos: `suspend fun isKnownContact(phoneNumber: String): Boolean`

- **`CallAttemptRepository`** — persiste y consulta intentos de llamadas bloqueadas.
  - Implementación: **F1** (`CallAttemptRepositoryImpl` + entidad Room `CallAttemptEntity` + `CallAttemptDao`).
  - Métodos: `suspend fun getAttemptCount(phoneNumber: String): Int`, `suspend fun recordAttempt(phoneNumber: String)`

- **`SettingsRepository`** — lee y escribe preferencias del usuario.
  - Implementación: **F1** (`SettingsRepositoryImpl` + `SettingsDataStore`).
  - Métodos: `isFilterEnabled()`, `setFilterEnabled()`, `getRequiredAttemptCount()`, `setRequiredAttemptCount()`

### `usecase/` — Lógica de negocio (F1)

- **`EvaluateIncomingCallUseCase`** — orquesta las 5 reglas de decisión de bloqueo
  - Verificación secuencial: filtro habilitado → normalización E.164 → contacto conocido → umbral de intentos → registro e intento.
  - Retorna `CallDecision` (sealed class con `Allow` y `DisallowSilently`).
  - Singleton inyectable; todas las dependencias (repositorios, normalizador) son inyectables.

### `model/` — Modelos de dominio (F1)

- **`CallDecision`** — sealed class que representa la salida de `EvaluateIncomingCallUseCase`:
  - `data object Allow` — permitir la llamada.
  - `data object DisallowSilently` — rechazar silenciosamente; el sistema Android no notifica al llamante.

### `util/` — Utilidades (F1)

- **`PhoneNumberNormalizer`** — normalización E.164 usando libphonenumber.
  - Singleton inyectable reutilizado en toda la aplicación.
  - Método: `fun normalize(rawNumber: String, defaultRegion: String = Locale.getDefault().country.ifEmpty { "US" }): String?`
  - Retorna `null` si el número no es válido (caller maneja fail-open).

## Próximas fases

- **F2+:** entidades de dominio adicionales para historial visible (analytics, detalles de llamada bloqueada), si procede.

## Testing

- Todos los tests de esta capa son JVM (sin `androidTest`).
- Se usan MockK para mockear repositorios, Turbine para validar Flows.
- Cobertura objetivo: **≥80%** (JaCoCo).
