# core — Capa Núcleo

La capa `core` contiene **utilidades, constantes, configuración global y dependency injection (DI)**.

## Estructura

### `di/` — Inyección de Dependencias (Hilt)

Módulos Hilt que proveen singletons al componente `SingletonComponent`:

#### `DatabaseModule`

Provee la instancia singleton de `AppDatabase`:

```kotlin
@Provides
@Singleton
fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase
```

- Crea la base de datos Room en el path estándar de la aplicación.
- Las migraciones se declaran aquí a medida que evolucionan los esquemas.
- En F2 se agregarán DAOs y se expondrán a través de este módulo.

#### `DataStoreModule`

Provee la instancia singleton de `DataStore<Preferences>`:

```kotlin
@Provides
@Singleton
fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences>
```

- Usa la propiedad delegada top-level `Context.settingsDataStore` para garantizar singleton.
- El archivo de preferencias se guarda en `sift_settings.preferences_pb` en el directorio de datos de la app.
- Preferencias tipadas e implementación de `SettingsRepository` vienen en F1.

## Próximas fases

- **F1:** `RepositoryModule` — módulos que proporcionan implementaciones de `ContactsRepository` y `SettingsRepository`.
- **F2:** Extensiones de `DatabaseModule` para exponer DAOs.
- **F3+:** Módulos adicionales según nueva infraestructura (ej. `NotificationModule` en F5).

## Patrones

- Todos los módulos usan `@InstallIn(SingletonComponent::class)` para que los singletons persistan toda la vida de la aplicación.
- Inyección de contexto: se usa `@ApplicationContext` para garantizar acceso al contexto de aplicación (no de activity, que es efímero).

## Testing

- Los tests unitarios que necesiten Hilt usan `@HiltAndroidTest`.
- En F4 se agregan tests UI que validen la inyección en activities y composables.
