# e2e — Tests End-to-End con Cucumber + Appium

Módulo JVM puro (no Android) que ejecuta escenarios Gherkin contra un emulador Android en ejecución, usando Appium (UIAutomator2) como driver de automatización.

## Rationale

**¿Por qué aquí y no en `app/src/androidTest/`?**

- Los tests unitarios de instrumentación (`androidTest`) se ejecutan **dentro del emulador**, usando las APIs de Android directamente.
- Los tests E2E se ejecutan **en la máquina host**, controlando el emulador externamente vía Appium — es como un usuario real tocando la pantalla y observando el resultado.
- Los tests E2E prueban flujos completos de usuario (UI, gestos, integraciones del sistema) sin depender de mocks internos.
- Viven en un módulo separado (`e2e/`) porque:
  1. No pueden ejecutarse en CI (CI no tiene emulador disponible).
  2. Requieren dependencias específicas (Appium cliente, Cucumber, JUnit Platform).
  3. Su ciclo de vida es diferente: se ejecutan localmente o en un agente de QA especializado, no en cada push.

## Stack técnico

- **Lenguaje:** Kotlin (JVM puro, no Android SDK).
- **Runner:** Cucumber-JVM + JUnit Platform.
- **Automatización:** Appium Java Client 9.x + UIAutomator2 driver.
- **Assertions:** JUnit 5.
- **Contenedor:** Gradle, custom task `e2eTest`.

## Estructura

```
e2e/
├── src/test/
│   ├── kotlin/
│   │   ├── runner/
│   │   │   └── CucumberE2ERunner.kt         # Suite JUnit + Cucumber runner
│   │   ├── steps/
│   │   │   └── CallScreeningSteps.kt        # Step definitions Gherkin → Kotlin
│   │   └── support/
│   │       ├── ScenarioContext.kt           # Estado compartido entre steps
│   │       ├── AppiumDriverFactory.kt       # Conexión a Appium server
│   │       ├── AdbHelper.kt                 # Comandos ADB (wipe, grant, role)
│   │       └── Hooks.kt                     # @Before/@After de Cucumber
│   └── resources/
│       └── features/
│           └── f1_call_screening_core.feature  # Escenarios Gherkin (F1)
├── build.gradle.kts
└── README.md
```

## Cómo ejecutar

### Pre-requisitos

1. **Android emulador booteado:**
   ```bash
   emulator @<avd-name>  # o abrir desde Android Studio
   ```

2. **Appium server 2.x corriendo en el host:**
   ```bash
   appium --port 4723
   ```

3. **UIAutomator2 driver instalado:**
   ```bash
   appium driver install uiautomator2
   ```

4. **Debug APK instalado en el emulador:**
   ```bash
   ./gradlew :app:installDebug
   ```

5. **ADB en PATH:**
   - Incluido en Android SDK. Ver `LOCAL_AUTOMATION_SETUP.md`.

### Ejecutar tests

```bash
./gradlew :e2e:e2eTest
```

**Con opciones:**

```bash
./gradlew :e2e:e2eTest \
  -Dappium.url=http://127.0.0.1:4723 \
  -Dadb.serial=emulator-5554
```

- `appium.url`: dirección del server Appium (default: `http://127.0.0.1:4723`).
- `adb.serial`: número de serie del dispositivo/emulador (opcional; requerido si hay múltiples).

## Escenarios (F1)

Archivo: `src/test/resources/features/f1_call_screening_core.feature`

### Implementados

1. **`@KnownContact`** — Contacto conocido siempre suena, sin registrar intento.
2. **`@UnknownNumber @FirstAttempt`** — Primer intento de número desconocido se bloquea silenciosamente.
3. **`@UnknownNumber @SecondAttempt`** — Segundo intento del mismo número se permite.

### Pendiente

4. **`@FilterDisabled @PendingUI`** — Cuando el filtro está deshabilitado, todas las llamadas pasan.
   - Depende de F4 (pantalla de Settings para desactivar filtro).

## Ciclo de ejecución por escenario

1. **@Before (Hooks):**
   - `adb shell pm clear com.callbloqued.sift` — limpia app data + settings.
   - Restaura permisos + rol de screening vía ADB.
   - Lanza Sift (Appium `activateApp`).

2. **Given/When/Then steps** — ejecutan comandos ADB, simulan llamadas, validan base de datos Room.

3. **@After (Hooks):**
   - Cierra Appium session.
   - (Opcionalmente) recolecta logs del dispositivo.

## Notas importantes

- **Números de prueba:** todos en rango NANP 555-01xx (ficticio, reservado para testing). Nunca usar números reales.
- **No parallelizable:** Appium + emulador secuencial por ahora. Múltiples emuladores requiere tuning avanzado.
- **Durabilidad:** cada escenario comienza con estado limpio (`pm clear`), así que no hay interdependencias.
- **Debugging:** activar logs Appium con `./gradlew :e2e:e2eTest -i` para verbose output.

## Documentación relacionada

- `ARCHITECTURE.md` — sección 7, "Stack técnico", detalles E2E.
- `AGENTS_WORKFLOW.md` — sección 6.1, justificación de por qué Appium en lugar de Selenium.
- `LOCAL_AUTOMATION_SETUP.md` — setup completo local (Appium, UIAutomator2, ADB, Gradle).
- `CODE_QUALITY_STANDARDS.md` — KDoc + complexity limits aplican a `steps/`, `support/` también.
