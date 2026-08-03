# data — Capa de Datos

La capa de datos contiene las **implementaciones de los repositorios** y la acceso a fuentes de datos locales (Room, DataStore, ContactsProvider).

## Principios

- **Implementación concreta:** aquí viven las dependencias de Android (Room, DataStore, ContactsContract).
- **Mapeo transparente:** los modelos de `data` (entities, data classes) se convierten a modelos de dominio (si aplica) en el boundary con `domain`.
- **Responsabilidad única:** cada repositorio implementa un solo contrato (interface) del dominio.

## Estructura

### `local/db/` — Persistencia Room

- **`AppDatabase`** — clase raíz que define el esquema.
  - Configurada con `@Database(entities = [...], version = 1, exportSchema = true)`.
  - Las migraciones se versionar en esta clase.
  
- **Entidades** (Entities):
  - **`PlaceholderEntity`** — **F0 temporal** (requerida para que KSP compile, sin datos reales).
    - Sera eliminada cuando la primera entidad real (F2/F3) se agregue.
  - `CallAttemptEntity` — **F2** (número, intentos, timestamps).
  - `ManualListEntity` — **F3** (número, tipo: BLACKLIST|WHITELIST).

- **DAOs** (Data Access Objects):
  - `CallAttemptDao` — **F2**
  - `ManualListDao` — **F3**

### `local/datastore/` — Preferencias con DataStore

- **`SettingsDataStore`** — wrapper alrededor de `DataStore<Preferences>`.
  - Expone `val data: Flow<Preferences>` sin procesar (keys e implementación tipada vienen en F1 con `SettingsRepositoryImpl`).
  - Singleton inyectado por Hilt desde `DataStoreModule`.

## Próximas fases

- **F1:** `SettingsRepositoryImpl` — implementa `SettingsRepository` usando `SettingsDataStore`.
- **F1:** `ContactsRepositoryImpl` — implementa `ContactsRepository` usando `ContactsContract`.
- **F2:** `CallAttemptRepositoryImpl` — implementa `CallAttemptRepository` usando `AppDatabase` + `CallAttemptDao`.
- **F3:** `ManualListRepositoryImpl` — implementa manejo de listas negra/blanca.

## Testing

- Tests unitarios (JVM) para lógica de mapeo y formateo.
- Tests de integración (androidTest) para Room DAOs y DataStore usando Room in-memory y DataStore testing libs.
- Cobertura objetivo: **≥80%** (JaCoCo).

## Migraciones Room

La exportación de esquema (`exportSchema = true`) genera archivos JSON en `app/schemas/` que deben versionarse en git para validar migraciones en CI.
