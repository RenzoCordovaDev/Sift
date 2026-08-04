# Arquitectura del Proyecto — Sift

## 1. Visión general

Aplicación Android nativa (Kotlin) que actúa como filtro de llamadas entrantes. Se registra ante el sistema como **Call Screening Service**, evalúa cada llamada entrante contra la agenda de contactos y un historial local de intentos, y decide si la deja pasar, la silencia o la rechaza.

## 2. Mecanismo del sistema Android a usar

Android no permite a una app de terceros "capturar" todas las llamadas de forma arbitraria. Existen dos caminos oficiales, y el proyecto usará el primero:

### Opción elegida: `CallScreeningService` + rol `ROLE_CALL_SCREENING`
- API disponible desde Android 10 (API 29).
- El usuario debe asignar la app como **"App de identificador de llamadas y spam"** desde `RoleManager`.
- No requiere reemplazar el marcador (dialer) predeterminado del teléfono.
- Permite: permitir la llamada, rechazarla silenciosamente, rechazarla con razón visible, o marcarla como spam.
- Se implementa sobrescribiendo `CallScreeningService.onScreenCall(Call.Details)`.

### Opción alternativa (no usada en v1, documentada para referencia futura)
- Ser **Default Phone/Dialer App** (`ROLE_DIALER`): da más control pero obliga a implementar toda la UI de marcado/recepción de llamadas, mucho mayor superficie y complejidad. Se descarta para la v1.

> **Nota de plataforma:** en fabricantes con capas agresivas de gestión de batería (Xiaomi, Huawei, Oppo, etc.) el servicio puede ser detenido en segundo plano. Se debe documentar en la app instrucciones para whitelisting/autostart.

## 3. Patrón de arquitectura de software

**Clean Architecture + MVVM**, organizada en 3 capas:

```
app/
├── presentation/       # UI (Activities/Fragments o Compose), ViewModels
├── domain/             # Casos de uso, modelos de negocio, interfaces de repos
├── data/                # Implementación de repos, Room, ContactsProvider, DataStore
├── core/                # Utilidades, extensiones, DI, constantes
└── SiftApplication      # Punto de entrada, @HiltAndroidApp
```

Reglas de dependencia: `presentation → domain ← data`. El dominio no conoce Android Framework directamente (se abstrae vía interfaces).

### Estado post-F0 (implementado)

En **F0** se estableció la estructura base:

- **`core/di/`:** módulos Hilt:
  - `DatabaseModule` — provee singleton `AppDatabase` en `SingletonComponent`.
  - `DataStoreModule` — provee singleton `DataStore<Preferences>` en `SingletonComponent`.
  
- **`domain/repository/`:** interfaces (sin imports de Android):
  - `ContactsRepository` — contrato para consultar agenda.
  - `CallAttemptRepository` — contrato para historial de intentos de llamadas.
  - `SettingsRepository` — contrato para preferencias del usuario.
  
- **`data/local/db/`:** Room database:
  - `AppDatabase` — raíz del esquema, configura exportación de JSON schema.
  - `PlaceholderEntity` — entidad temporal F0 (requisito de KSP; se reemplaza en F1/F2 con entidades reales).
  
- **`data/local/datastore/`:** wrapper DataStore:
  - `SettingsDataStore` — expone `Flow<Preferences>` del DataStore de Hilt.
  
- **`presentation/`:** UI Compose:
  - `MainActivity` — actividad única (@AndroidEntryPoint, Hilt inyectable).
  - `theme/SiftTheme` — tema Material3 base.
  - `placeholder/PlaceholderScreen` — pantalla temporal F0 (se reemplaza en F4 con navegación real).
  
- **`SiftApplication`** — @HiltAndroidApp, punto de entrada de Hilt.

### Estado post-F1 (implementado)

En **F1** se completó el **núcleo funcional de screening** (por qué se implementó en F1, no en F2: la regla "bloquear primer intento, permitir segundo" requiere tracking de intentos para existir en absoluto, así que no tenía sentido diferirlo):

- **`domain/usecase/EvaluateIncomingCallUseCase`** — orquesta las 5 reglas de decisión (filtro habilitado, normalización E.164, contacto conocido, umbral de intentos, registro de intento).

- **`domain/model/CallDecision`** — sealed class: `Allow` y `DisallowSilently`. Sin lógica, solo representación.

- **`domain/util/PhoneNumberNormalizer`** — normalización E.164 con libphonenumber (singleton inyectable).

- **`data/local/db/CallAttemptEntity`** + **`CallAttemptDao`** — persistencia Room de intentos. Migración 1→2 completada; `PlaceholderEntity` eliminada.

- **`data/repository/CallAttemptRepositoryImpl`** — implementa `CallAttemptRepository` con lógica insert/update según si es primer intento o reintento.

- **`data/local/contacts/ContactsContentResolverGateway`** — aislamiento de `ContentResolver.query()` sobre `ContactsContract` para poder testear la fail-safe logic sin pisar estáticos de Android.

- **`data/repository/ContactsRepositoryImpl`** — implementa `ContactsRepository`. Devuelve `true` (tratar como conocido) si `READ_CONTACTS` no está concedido, garantizando que jamás se bloquee por falta de permiso (fail-safe para regla no negociable #1).

- **`data/repository/SettingsRepositoryImpl`** — implementa `SettingsRepository` con acceso tipado a `SettingsDataStore`. Valida que `requiredAttemptCount ≥ 1`.

- **`data/service/IncomingCallScreeningService`** — extiende `CallScreeningService` de Android, delega a `EvaluateIncomingCallUseCase`, traduce `CallDecision` a `CallResponse`.

- **`core/di/RepositoryModule`** — bindings Hilt (`@Binds`) de las 3 interfaces de repositorio a sus implementaciones.

- **`e2e/`** — nuevo módulo JVM puro: Cucumber-JVM + Appium (UIAutomator2). 4 escenarios Gherkin para F1: 3 implementados (`@KnownContact`, `@UnknownNumber @FirstAttempt`, `@UnknownNumber @SecondAttempt`), 1 pendiente (`@FilterDisabled @PendingUI` — depende de F4 UI de configuración).

**F2 (Historial de intentos visible)** ahora se refiere específicamente a **la UI y funcionalidad de historial** (pantalla de llamadas bloqueadas, detalles, exportación), **no** a la persistencia básica de intentos que ya existe en F1.

### Estado post-F2 (implementado)

En **F2** se completó la **capa de persistencia de eventos de bloqueo** (data + domain, sin UI — la pantalla Compose se entrega en F4):

- **`domain/model/BlockReason`** — enum extensible de motivos de bloqueo (hoy solo `ATTEMPT_THRESHOLD`; F3 agregará `MANUAL_BLACKLIST`).

- **`domain/model/BlockedCallLogEntry`** — modelo de dominio que representa un evento individual de bloqueo (id, phoneNumber, timestamp, reason). Distinto de `CallAttemptEntity` que es un agregado por número.

- **`domain/repository/CallLogRepository`** — contrato sin imports de Android: `logBlockedCall(phoneNumber, reason)` suspend, `observeBlockedCallLog()` Flow ordenado newest-first.

- **`data/local/db/BlockedCallLogEntity`** + **`BlockedCallLogDao`** — tabla Room `blocked_call_log`; almacena uno registro por cada bloqueo individual.

- **`data/repository/CallLogRepositoryImpl`** — implementación Room; degrada motivos desconocidos a `ATTEMPT_THRESHOLD` (forward-compatible para fases futuras).

- **`core/di/RepositoryModule`** — binding Hilt nuevo: `bindCallLogRepository(impl)` como singleton.

- **`domain/usecase/EvaluateIncomingCallUseCase`** — modificado: recibe `CallLogRepository` como 5º parámetro; nuevo método privado `recordAndLogBlock()` que persiste tanto en `CallAttemptRepository` (agregado) como en `CallLogRepository` (evento individual) cuando se decide `DisallowSilently`. Las dos escrituras son secuenciales, no transaccionales (decisión de alcance F2).

- **`data/local/db/AppDatabase`** — versión 3; `MIGRATION_2_3` agrega `blocked_call_log` sin modificar `CallAttemptEntity`.

- **`detekt.yml`** — corregido `constructorThreshold` de 5→6 (límite efectivo 5 params; detekt dispara en `>= threshold`, no en `>`); agregado `'3'` a `ignoreNumbers` para `Migration(2, 3)`.

- **`e2e/f2_attempt_history.feature`** — 2 escenarios: `@BlockedCallLogged` (implementado; verifica via `adb shell run-as ... sqlite3`), `@HistoryScreen @PendingUI` (depende de F4 Compose).

- **Cobertura JaCoCo:** 92.1% INSTRUCTION (gate ≥80%). **QA Certification:** PASS (PR #8, formal certification en comentario del PR). **Revisión de Código:** APPROVE (tras REQUEST_CHANGES resueltos).

**Decisiones conscientes F2:** persistencia de evento exclusivamente data/domain; no hay pantalla Compose en F2 (se construye en F4 consumiendo `CallLogRepository`). Las dos bases de datos (`CallAttemptEntity` y `BlockedCallLogEntity`) coexisten: la primera (agregado) alimenta la lógica de decisión; la segunda (evento) alimentará la pantalla de historial del usuario.

## 4. Componentes principales

| Componente | Estado | Responsabilidad |
|---|---|---|
| `IncomingCallScreeningService` | **F1** | Extiende `CallScreeningService`. Punto de entrada del sistema. Delega la decisión al dominio. |
| `EvaluateIncomingCallUseCase` | **F1→F2** | Lógica central de decisión (F1). F2: ahora delega persistencia de evento a `CallLogRepository`. |
| `ContactsRepository` | **F1** | Contrato para consultar `ContactsContract`. Implementación en F1 (gateway + repository). |
| `CallAttemptRepository` | **F1** | Contrato para persistencia (Room) de intentos agregados. Implementación en F1 (entidad + DAO). |
| `CallLogRepository` | **F2** | Contrato para eventos de bloqueo individual. Implementación en F2 (entidad + DAO + repository). |
| `SettingsRepository` | **F1** | Contrato para preferencias. Implementación en F1 (wrapper tipado de `SettingsDataStore`). |
| `SettingsDataStore` | **F0** | Wrapper del DataStore Jetpack; expone `Flow<Preferences>`. |
| `AppDatabase` | **F1→F2** | Raíz Room. F1: agregó `CallAttemptEntity`. F2: agregó `BlockedCallLogEntity`; versión 3. |
| `RoleRequestManager` | **F5** | Gestiona la solicitud del rol `ROLE_CALL_SCREENING` al usuario. |
| `SiftApplication` | **F0** | @HiltAndroidApp, inicializa DI. |
| `MainActivity` | **F0** | Actividad única con Compose. Aloja `PlaceholderScreen` (temporal); NavHost en F4. |
| `SiftTheme` | **F0** | Tema Material3 base. Personalizaciones en F4. |

## 5. Flujo de decisión (lógica de negocio central — implementado en F1)

Implementado en `EvaluateIncomingCallUseCase`. El flujo se evalúa en orden; la primera regla que coincida determina la decisión:

```
Llamada entrante → IncomingCallScreeningService.onScreenCall(callDetails)
  ↓
  1. ¿Filtro de screening deshabilitado por el usuario?
       SÍ → ALLOW (permitir la llamada sin más verificación)
  ↓
  2. ¿Se normaliza el número a E.164 correctamente?
       NO → ALLOW (fail open: no podemos identificar el número de forma segura,
              así que evitamos bloquear por error)
  ↓
  3. ¿Está el número en la agenda de contactos del dispositivo?
       SÍ → ALLOW (regla no negociable: nunca bloquear contactos conocidos)
  ↓
  4. ¿Intentos de bloqueo previos ≥ umbral configurado?
       (default: 1, significa que en el 2º intento se permite)
       SÍ → ALLOW (la persona ha insistido; tratar como legítima)
  ↓
  5. Si no coincidió ninguna regla anterior:
       → Registrar el intento en la base de datos Room
       → DISALLOW_SILENTLY (rechazar sin sonido, sin enviar tone al llamante)
```

El historial de intentos se guarda en Room (`CallAttemptEntity`) y sobrevive reinicios de la app.

**Fuera de alcance F1:** listas manuales blanca/negra (implementadas en F3).

## 6. Persistencia de datos

**Room Database** con las siguientes entidades:

- **`CallAttemptEntity(phoneNumber, firstAttemptAt, lastAttemptAt, attemptCount)`** — **F1 (implementada)**
  - Almacena el historial agregado de intentos bloqueados por número (una fila por número). `phoneNumber` es la PK.
  - `firstAttemptAt` y `lastAttemptAt` son timestamps de Unix (ms) de la primera y última vez que se bloqueó.
  - `attemptCount` es el contador total de intentos bloqueados silenciosamente.
  - Usado por `EvaluateIncomingCallUseCase` para la regla "permitir en Nth intento".
  - Mapeo: `CallAttemptRepositoryImpl` ↔ `CallAttemptDao` ↔ Room.

- **`BlockedCallLogEntity(id, phoneNumber, timestamp, reason)`** — **F2 (implementada)**
  - Almacena eventos individuales de bloqueo (una fila por cada bloqueo). Distinto de `CallAttemptEntity`: esta tabla crece monotónicamente y es la fuente de datos para la pantalla de historial del usuario (F4).
  - `id` es un auto-incremento (no una PK natural), `phoneNumber` en E.164, `timestamp` en ms epoch, `reason` es el nombre del enum `BlockReason` (almacenado como string para forward-compatibility).
  - Mapeo: `CallLogRepositoryImpl` ↔ `BlockedCallLogDao` ↔ Room.

- **`ManualListEntity(number, type: BLACKLIST|WHITELIST, addedAt)`** — **F3 (pendiente)**
  - Listas manuales editables por el usuario.

En **F0**, `AppDatabase` contenía solo `PlaceholderEntity` (temporal). En **F1** fue reemplazada por `CallAttemptEntity`, que es la primera entidad real requerida por el núcleo de screening. En **F2** se agregó `BlockedCallLogEntity` (migración 2→3) sin modificar `CallAttemptEntity`.

Los números se normalizan (formato E.164) antes de guardarse y compararse, usando `libphonenumber` (librería de Google) para evitar falsos negativos por formato (+57 vs 057 vs sin prefijo, etc.). La normalización se centraliza en `PhoneNumberNormalizer`.

## 7. Stack técnico propuesto

- **Lenguaje:** Kotlin 100%
- **UI:** Jetpack Compose + Material3 (decidido en F0)
- **DI:** Hilt
- **Persistencia:** Room + DataStore (preferencias)
- **Concurrencia:** Kotlin Coroutines + Flow
- **Formato de números:** libphonenumber-android
- **Testing unitario:** JUnit5 + MockK + Turbine (para Flow) + Espresso/Compose UI Test para UI
- **Cobertura de código:** JaCoCo, gate obligatorio ≥80% en `domain` y `data` (ver `DEVELOPMENT_STANDARDS.md` secciones 6 y 10)
- **Testing E2E/QA:** Gherkin + Cucumber-JVM + Appium (driver UIAutomator2)
  - Módulo independiente `e2e/` (JVM puro, no Android): `src/test/resources/features/` (archivos Gherkin), `src/test/kotlin/` (step definitions y support).
  - Se ejecuta contra un emulador Android booteado localmente. CI no ejecuta E2E (requiere emulador); ver `LOCAL_AUTOMATION_SETUP.md` para instrucciones de setup local.
  - Justificación: Selenium no aplica a apps nativas Android; Appium (con driver UIAutomator2) es el estándar de facto. Ver `AGENTS_WORKFLOW.md` sección 6.1.
  - Implementado en F1: 4 escenarios Gherkin, 3 completados + 1 pending (espera F4 UI).
- **Min SDK:** 29 (Android 10), dado el requisito de `CallScreeningService` con rol
- **Target SDK:** el más reciente estable al momento de compilar

## 8. Permisos requeridos

| Permiso | Uso |
|---|---|
| `READ_CONTACTS` | Verificar si el número entrante está en agenda |
| `READ_CALL_LOG` | (opcional) mostrar historial nativo de llamadas |
| `ANSWER_PHONE_CALLS` / rol de screening | Requisito del sistema para `CallScreeningService` |
| `POST_NOTIFICATIONS` (API 33+) | Notificar llamadas bloqueadas |

Todos los permisos deben solicitarse en tiempo de ejecución con explicación clara del motivo (requisito además de buenas prácticas, para Play Store).

## 9. Consideraciones de Play Store

- Los permisos `READ_CALL_LOG`/`READ_CONTACTS` y el rol de screening están sujetos a políticas estrictas de Google Play (declaración de uso sensible, formulario de declaración de permisos). Debe documentarse y prepararse con anticipación al publicar.
- Alternativa: distribución inicial fuera de Play Store (APK directo) para fase de pruebas, mientras se prepara la documentación de cumplimiento.

## 10. Diagrama de módulos (alto nivel)

```
[Sistema Android] --incoming call--> [IncomingCallScreeningService]
                                              |
                                              v
                                  [EvaluateIncomingCallUseCase]
                                    /            |             \
                        [ContactsRepository] [CallAttemptRepository] [SettingsRepository]
                                                  |
                                            [Room Database]
```

## 11. Roadmap técnico sugerido (fases)

> El proyecto se organiza en features/fases, cada una desarrollada por el conjunto de agentes especializados descrito en `AGENTS_WORKFLOW.md` (Dev Móvil, Test Unitario, Test E2E, QA, Documentación, Orquestador). Cada fase requiere código + tests unitarios + certificación E2E/QA + documentación antes de darse por cerrada.

| Fase | Nombre | Rama base sugerida |
|---|---|---|
| **F0** | Setup: proyecto base, DI, arquitectura de carpetas, CI básico | `feature/f0-setup` |
| **F1** | Núcleo de screening: `CallScreeningService` + `EvaluateIncomingCallUseCase` + persistencia básica de intentos + contactos + settings | `feature/f1-call-screening-core` |
| **F2** | Historial de eventos: capa data/domain para persistencia de eventos individuales de bloqueo (Room + `CallLogRepository`). **Sin UI** — la pantalla Compose se entrega en F4. | `feature/f2-attempt-history` |
| **F3** | Listas manuales: blacklist/whitelist editable por el usuario (data/domain; UI en F4) | `feature/f3-manual-lists` |
| **F4** | UI: pantalla de historial de bloqueados, configuración, listas manuales; consumo de `CallLogRepository` y `ManualListRepository`; eliminación de `PlaceholderScreen` | `feature/f4-ui` |
| **F5** | Onboarding: solicitud de rol y permisos, tutorial de batería | `feature/f5-onboarding` |
| **F6** | Pulido y publicación | `feature/f6-release` |

Ver `AGENTS_WORKFLOW.md` para el detalle del pipeline de trabajo entre agentes dentro de cada fase.
