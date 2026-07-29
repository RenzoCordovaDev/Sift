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
└── core/                # Utilidades, extensiones, DI, constantes
```

Reglas de dependencia: `presentation → domain ← data`. El dominio no conoce Android Framework directamente (se abstrae vía interfaces).

## 4. Componentes principales

| Componente | Responsabilidad |
|---|---|
| `IncomingCallScreeningService` | Extiende `CallScreeningService`. Punto de entrada del sistema. Delega la decisión al dominio. |
| `EvaluateIncomingCallUseCase` | Lógica central: ¿está en contactos? ¿ya llamó antes? ¿está en lista blanca/negra manual? |
| `ContactsRepository` | Consulta `ContactsContract` para saber si el número está guardado. |
| `CallAttemptRepository` | Persiste (Room) cada intento de llamada de números desconocidos: número, timestamp, cantidad de intentos. |
| `CallLogRepository` | Historial de llamadas bloqueadas/permitidas, mostrado en UI. |
| `RoleRequestManager` | Gestiona la solicitud del rol `ROLE_CALL_SCREENING` al usuario. |
| `SettingsRepository` (DataStore) | Preferencias: activar/desactivar filtro, número de intentos requeridos, listas manuales. |

## 5. Flujo de decisión (lógica de negocio central)

```
Llamada entrante → onScreenCall(callDetails)
  1. ¿Número en agenda de contactos?
       SÍ → permitir (ALLOW)
  2. ¿Número en lista negra manual del usuario?
       SÍ → rechazar (DISALLOW + marcar como spam)
  3. ¿Número en lista blanca manual del usuario?
       SÍ → permitir (ALLOW)
  4. ¿Es la primera vez que este número llama (según historial local)?
       SÍ → rechazar silenciosamente (DISALLOW) y registrar el intento
       NO (ya llamó antes ≥ N veces, configurable, default 1 reintento) → permitir (ALLOW)
```

Este historial de intentos se guarda en Room y sobrevive reinicios de la app.

## 6. Persistencia de datos

**Room Database** con las siguientes entidades:

- `CallAttemptEntity(number, firstAttemptAt, lastAttemptAt, attemptCount, resolvedAsAllowed)`
- `BlockedCallLogEntity(number, timestamp, reason)`
- `ManualListEntity(number, type: BLACKLIST|WHITELIST, addedAt)`

Los números se normalizan (formato E.164) antes de guardarse y compararse, usando `libphonenumber` (librería de Google) para evitar falsos negativos por formato (+57 vs 057 vs sin prefijo, etc.).

## 7. Stack técnico propuesto

- **Lenguaje:** Kotlin 100%
- **UI:** Jetpack Compose (recomendado) o Views con ViewBinding (a decidir en fase de setup)
- **DI:** Hilt
- **Persistencia:** Room + DataStore (preferencias)
- **Concurrencia:** Kotlin Coroutines + Flow
- **Formato de números:** libphonenumber-android
- **Testing unitario:** JUnit5 + MockK + Turbine (para Flow) + Espresso/Compose UI Test para UI
- **Cobertura de código:** JaCoCo, gate obligatorio ≥80% en `domain` y `data` (ver `DEVELOPMENT_STANDARDS.md` secciones 6 y 10)
- **Testing E2E/QA:** Gherkin + Cucumber-JVM + Appium (driver UIAutomator2) — ver `AGENTS_WORKFLOW.md` sección 6.1 para el detalle y la justificación técnica (Selenium no aplica a apps nativas Android; Appium es su equivalente para este contexto)
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
| **F1** | Núcleo: `CallScreeningService` funcionando + lógica de contactos | `feature/f1-call-screening-core` |
| **F2** | Historial de intentos: Room + lógica de "segunda llamada permite entrar" | `feature/f2-attempt-history` |
| **F3** | Listas manuales: blacklist/whitelist editable por el usuario | `feature/f3-manual-lists` |
| **F4** | UI: pantalla de estado, historial de bloqueados, configuración | `feature/f4-ui` |
| **F5** | Onboarding: solicitud de rol y permisos, tutorial de batería | `feature/f5-onboarding` |
| **F6** | Pulido y publicación | `feature/f6-release` |

Ver `AGENTS_WORKFLOW.md` para el detalle del pipeline de trabajo entre agentes dentro de cada fase.
