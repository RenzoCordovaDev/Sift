# data — Capa de Datos

La capa de datos contiene las **implementaciones de los repositorios** y la acceso a fuentes de datos locales (Room, DataStore, ContactsProvider).

## Principios

- **Implementación concreta:** aquí viven las dependencias de Android (Room, DataStore, ContactsContract).
- **Mapeo transparente:** los modelos de `data` (entities, data classes) se convierten a modelos de dominio (si aplica) en el boundary con `domain`.
- **Responsabilidad única:** cada repositorio implementa un solo contrato (interface) del dominio.

## Estructura

### `local/db/` — Persistencia Room (F1+)

- **`AppDatabase`** — clase raíz que define el esquema.
  - Configurada con `@Database(entities = [...], version = 2, exportSchema = true)`.
  - Migración 1→2: reemplazó `PlaceholderEntity` (F0) con `CallAttemptEntity` (F1).
  
- **Entidades** (Entities):
  - **`CallAttemptEntity(phoneNumber, firstAttemptAt, lastAttemptAt, attemptCount)`** — **F1 (implementada)**
    - `phoneNumber` (PK): número en E.164.
    - `firstAttemptAt`, `lastAttemptAt`: timestamps de Unix (ms).
    - `attemptCount`: contador total de intentos bloqueados.
  - `ManualListEntity` — **F3** (número, tipo: BLACKLIST|WHITELIST).

- **DAOs** (Data Access Objects):
  - **`CallAttemptDao`** — **F1 (implementada)**
    - `suspend fun findByPhoneNumber(phoneNumber: String): CallAttemptEntity?`
    - `suspend fun insert(entity: CallAttemptEntity)`
    - `suspend fun update(entity: CallAttemptEntity)`
  - `ManualListDao` — **F3**

### `local/datastore/` — Preferencias con DataStore (F1)

- **`SettingsDataStore`** — wrapper alrededor de `DataStore<Preferences>`.
  - Métodos públicos (F1):
    - `fun filterEnabled(): Flow<Boolean>` — flujo del estado del filtro.
    - `suspend fun setFilterEnabled(enabled: Boolean)` — activa/desactiva screening.
    - `fun requiredAttemptCount(): Flow<Int>` — flujo del umbral de intentos.
    - `suspend fun setRequiredAttemptCount(count: Int)` — establece umbral; valida `count >= 1`.
  - Singleton inyectado por Hilt desde `DataStoreModule`.

### `local/contacts/` — Acceso a agenda (F1)

- **`ContactsContentResolverGateway`** — wrapper fino sobre `ContentResolver` + `ContactsContract`.
  - Aislado para permitir testing unitario de `ContactsRepositoryImpl` sin pisar estáticos de Android.
  - Método: `fun countMatchingContacts(phoneNumber: String): Int` — usa `PhoneLookup.CONTENT_FILTER_URI`.
  - Lanza `SecurityException` si `READ_CONTACTS` no está concedido.

### `repository/` — Implementaciones de repositorios (F1+)

- **`SettingsRepositoryImpl`** — **F1 (implementada)**
  - Implementa `SettingsRepository` con acceso tipado a `SettingsDataStore`.

- **`ContactsRepositoryImpl`** — **F1 (implementada)**
  - Implementa `ContactsRepository` usando `ContactsContentResolverGateway`.
  - Fail-safe: devuelve `true` (tratar como contacto conocido) si `READ_CONTACTS` no está concedido.

- **`CallAttemptRepositoryImpl`** — **F1 (implementada)**
  - Implementa `CallAttemptRepository` usando `AppDatabase` + `CallAttemptDao`.
  - Lógica insert/update: si es primer intento, inserta con `attemptCount = 1`; si reintento, incrementa contador.

### `service/` — Adaptadores del sistema (F1+)

- **`IncomingCallScreeningService`** — **F1 (implementada)**
  - Extiende `CallScreeningService` de Android.
  - Punto de entrada del sistema: intercepta llamadas entrantes.
  - Delega a `EvaluateIncomingCallUseCase`.
  - Traduce `CallDecision` → `CallResponse` (Android telecom API).

## Próximas fases

- **F2:** `BlockedCallLogEntity` y funcionalidad de historial visible.
- **F3:** `ManualListRepositoryImpl` — implementa manejo de listas negra/blanca.

## Testing

- Tests unitarios (JVM) para lógica de mapeo y formateo.
- Tests de integración (androidTest) para Room DAOs y DataStore usando Room in-memory y DataStore testing libs.
- Cobertura objetivo: **≥80%** (JaCoCo).

## Migraciones Room

La exportación de esquema (`exportSchema = true`) genera archivos JSON en `app/schemas/` que deben versionarse en git para validar migraciones en CI.
