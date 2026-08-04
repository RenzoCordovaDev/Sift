# app — Módulo Principal de Sift

Este es el módulo Android principal (`app/build.gradle.kts`) que contiene todo el código fuente de Sift: UI, lógica de negocio y persistencia.

## Estructura de carpetas

```
app/
├── src/
│   ├── main/
│   │   ├── java/com/callbloqued/sift/
│   │   │   ├── domain/               # Lógica de negocio pura (sin Android)
│   │   │   ├── data/                 # Implementación de repos, Room, DataStore
│   │   │   ├── presentation/         # UI Compose, ViewModels, navegación
│   │   │   ├── core/                 # Utilidades, DI (Hilt)
│   │   │   └── SiftApplication.kt    # @HiltAndroidApp, punto de entrada
│   │   ├── res/                      # Recursos (strings, drawables, temas)
│   │   └── AndroidManifest.xml       # Permisos, servicios, actividades
│   ├── test/                         # Tests unitarios JVM
│   ├── androidTest/                  # Tests instrumentados (requieren emulador/device)
│   └── ...
├── build.gradle.kts                  # Configuración de build del módulo
└── README.md                          # Este archivo
```

## Capas de Clean Architecture

### 1. `domain/` — Lógica de Negocio

- **Sin imports de Android.** Testeable en JVM puro.
- Contiene **interfaces de repositorio** que definen contratos.
- Próximamente (F1+): **casos de uso** (use cases) que orquestan la lógica de decisión de bloqueos.

📖 Ver [`domain/README.md`](src/main/java/com/callbloqued/sift/domain/README.md)

### 2. `data/` — Persistencia e Integraciones

- Implementa las interfaces de `domain`.
- Acceso a Room, DataStore, ContactsProvider (Android APIs).
- Mapeo de entidades Room a modelos de dominio.

📖 Ver [`data/README.md`](src/main/java/com/callbloqued/sift/data/README.md)

### 3. `presentation/` — Interfaz de Usuario

- Jetpack Compose + Material3.
- ViewModels (Hilt + Jetpack Lifecycle).
- Navegación entre pantallas.
- En F0: `PlaceholderScreen` temporal; en F4: pantallas reales (home, settings, history).

📖 Ver [`presentation/README.md`](src/main/java/com/callbloqued/sift/presentation/README.md)

### 4. `core/` — Infraestructura

- **DI (Hilt):** módulos que proveen singletons (`AppDatabase`, `DataStore`).
- Utilidades, constantes, extensiones.

📖 Ver [`core/README.md`](src/main/java/com/callbloqued/sift/core/README.md)

## Configuración y Dependencias

- **Gradle:** version catalog en `gradle/libs.versions.toml`
  - Hilt, Room, DataStore, Compose, JUnit5, MockK, Turbine, JaCoCo, detekt.
- **Min SDK:** 29 (Android 10, requisito de `CallScreeningService`)
- **Target SDK:** últimas versiones estables

## Testing

- **Unitarios (JVM):** bajo `src/test/`
  - JUnit5, MockK, Turbine
  - Cobertura: ≥80% en `domain` y `data` (JaCoCo)
- **Instrumentados (Android):** bajo `src/androidTest/`
  - Room smoke tests, comprobaciones de permisos
  - Requieren emulador/dispositivo: `./gradlew connectedDebugAndroidTest`

## Estado tras F1

En **F1** se implementó el **núcleo funcional completo de screening**:
- `EvaluateIncomingCallUseCase` + 5 reglas de decisión.
- Persistencia Room de intentos (`CallAttemptEntity` + `CallAttemptDao`).
- Integración ContactsContract (contactos del dispositivo).
- Settings persistentes (filtro habilitado, umbral de intentos).
- `IncomingCallScreeningService` registrado en `AndroidManifest.xml`.
- Tests unitarios (≥80% cobertura en `domain` y `data`).
- Tests E2E (Gherkin/Cucumber + Appium): 3 escenarios completados + 1 pendiente.

## Próximas fases

| Fase | Cambios principales |
|---|---|
| **F2** | Historial visible (pantalla de llamadas bloqueadas, detalles, análisis) |
| **F3** | Listas manuales (blacklist/whitelist), entidades y DAOs |
| **F4** | Pantallas reales (home, settings, history), navegación, eliminación de `PlaceholderScreen` |
| **F5** | Onboarding, solicitud de rol y permisos |
| **F6** | Pulido, release |

## Documentos de referencia

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md) — visión técnica completa del proyecto
- [`CODE_QUALITY_STANDARDS.md`](../../CODE_QUALITY_STANDARDS.md) — estándares de código y KDoc
- [`DEVELOPMENT_STANDARDS.md`](../../DEVELOPMENT_STANDARDS.md) — commits, CI/CD, cobertura
- [`PROJECT_CONTEXT.md`](../../PROJECT_CONTEXT.md) — contexto de negocio y reglas de producto
