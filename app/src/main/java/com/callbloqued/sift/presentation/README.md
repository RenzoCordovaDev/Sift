# presentation — Capa de Presentación

La capa de presentación contiene la **interfaz de usuario** (UI), ViewModels y la navegación.

## Principios

- **Composables y ViewModels:** la UI se construye con Jetpack Compose; cada pantalla tiene un ViewModel que orquesta la lógica de presentación.
- **Sin lógica de negocio:** la toma de decisiones sobre qué bloquear/permitir vive en `domain` (use cases), no en la UI. Los ViewModels solo orquestan.
- **Inyección de dependencias:** todos los ViewModels son inyectables con Hilt (`@HiltViewModel`) y pueden recibir repositories y use cases en sus constructores.

## Estructura

### `MainActivity`

- Actividad única de Sift (MVVM + single-activity architecture).
- Anotada con `@AndroidEntryPoint` para que Hilt inyecte dependencias.
- Aplica edge-to-edge display y configura el contenido Compose.
- Aloja un `SiftTheme` que engloba toda la jerarquía de composables.

### `theme/SiftTheme`

- Tema Material3 base que abstrae colores y tipografía del SDK.
- En **F0** usa valores por defecto de Material3.
- En **F4** se personalizarán colores, tipografía y componentes propios de Sift.

### `placeholder/PlaceholderScreen`

- **F0 temporal** — pantalla de prueba que muestra un mensaje de verificación.
- Sera reemplazada en **F4** por la pantalla principal de estado de bloqueos y configuración.

## Próximas fases

- **F4:** Pantallas finales:
  - `HomeScreen` — estado de filtro (ON/OFF), lista de últimas llamadas bloqueadas/permitidas.
  - `SettingsScreen` — toggle filtro, umbral de intentos, listas manual.
  - `HistoryScreen` — historial completo de llamadas bloqueadas.
  
- **F4:** Navegación:
  - `NavHost` dentro de `MainActivity` con rutas para home, settings, history.
  
- **F4:** ViewModels:
  - `HomeViewModel` — estado del filtro, intentos recientes.
  - `SettingsViewModel` — lectura/escritura de preferencias.
  - `HistoryViewModel` — paginación del historial.

- **F5:** Onboarding:
  - `OnboardingScreen` — solicitud de rol `ROLE_CALL_SCREENING` y permisos.

## Testing

- UI tests (Compose UI Test) en **F4+** para validar interacciones y navegación.
- Cobertura objetivo: **≥60%** para presentación (la cobertura ≥80% se requiere en `domain` y `data`; presentation es más tolerante por su naturaleza visual).

## Recursos (strings, drawables, etc.)

Todos los resource IDs, claves de strings, etc., deben estar en inglés (ver `CODE_QUALITY_STANDARDS.md` sección 2).
