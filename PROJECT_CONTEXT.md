# Contexto del Proyecto

## 1. Nombre del proyecto
**Sift** — nombre definitivo. Se recomienda verificar disponibilidad de dominio y de nombre en Google Play antes de publicar.

## 2. Problema que resuelve
Las llamadas de números desconocidos (publicidad, cobranza, entidades no deseadas, posible spam/fraude) interrumpen constantemente al usuario. No existe forma nativa en Android de bloquear automáticamente a *cualquier* número no guardado en la agenda, permitiendo excepciones inteligentes.

## 3. Objetivo general
Desarrollar una app Android que, actuando como servicio de screening de llamadas, bloquee automáticamente las llamadas entrantes de números que no están en la agenda de contactos del usuario, **salvo** que ese número ya haya intentado llamar previamente (indicio de que podría tratarse de una persona real insistiendo en comunicarse).

## 4. Objetivos específicos
1. Registrar la app como opción para el rol de "identificador de llamadas y spam" del sistema (`ROLE_CALL_SCREENING`).
2. Verificar en tiempo real si el número entrante existe en la agenda de contactos.
3. Si no existe en la agenda, comparar contra un historial local de intentos previos.
4. Permitir el paso de la llamada si es el segundo intento (o el número de intentos configurado) del mismo número.
5. Bloquear silenciosamente en el primer intento de un número no reconocido.
6. Permitir al usuario gestionar manualmente listas blancas/negras.
7. Mostrar un historial de llamadas bloqueadas y permitidas.
8. Garantizar que toda la lógica funcione **100% local**, sin enviar datos personales a servidores externos.

## 5. Alcance de la v1 (MVP)
**Incluye:**
- Bloqueo automático basado en agenda + reintento.
- Listas manuales blanca/negra.
- Historial visible de llamadas bloqueadas.
- Pantalla de configuración básica (activar/desactivar, número de intentos requeridos).
- Onboarding para solicitar el rol de screening y permisos necesarios.

**No incluye (fuera de alcance v1):**
- Base de datos colaborativa/en la nube de números spam reportados por otros usuarios.
- Identificación de nombre de llamante tipo "Truecaller" (requeriría datos externos).
- Reemplazo del marcador telefónico predeterminado (`ROLE_DIALER`).
- Sincronización multi-dispositivo.
- Backend propio.

Estas exclusiones pueden revisarse en versiones futuras, pero deben ser una decisión explícita, no un "scope creep" no planeado.

## 6. Usuario objetivo
Persona con teléfono Android que recibe llamadas frecuentes no deseadas (telemercadeo, cobranza, posible fraude) y quiere reducir drásticamente las interrupciones sin perder llamadas legítimas de números nuevos que insisten (ej. citas médicas, delivery, trámites).

## 7. Reglas de producto (no negociables)

1. **Nunca bloquear un número que está en la agenda de contactos**, sin excepción.
2. **Nunca bloquear silenciosamente sin dejar registro**: toda llamada bloqueada debe quedar visible en el historial para que el usuario pueda revisar y corregir falsos positivos.
3. El criterio de "segunda llamada permite entrar" debe ser **configurable** por el usuario (no hardcodeado a un único intento), con un valor por defecto razonable (1 reintento).
4. El usuario siempre debe poder **desactivar el filtro completamente** con un toque.
5. El usuario siempre debe poder **agregar manualmente un número a lista blanca o negra** desde el historial (con un solo tap sobre el registro).
6. **Privacidad ante todo:** ningún dato de contactos o números se transmite fuera del dispositivo en la v1.
7. La app debe explicar claramente, en el onboarding, por qué necesita cada permiso (transparencia, y requisito de Play Store).

## 8. Consideraciones técnicas relevantes

- **Fragmentación de Android:** distintos fabricantes limitan procesos en segundo plano de forma agresiva; el onboarding debe guiar al usuario a desactivar la optimización de batería para esta app.
- **Cambios de política de Google Play:** los permisos de `READ_CALL_LOG`/`READ_CONTACTS` para apps de bloqueo de llamadas están sujetos a revisión manual de Google. Se debe investigar el proceso de "Permissions Declaration Form" antes de publicar.
- **Min SDK 29 (Android 10):** decisión consciente porque `CallScreeningService` con rol dedicado no existe en versiones anteriores; se sacrifica compatibilidad con dispositivos muy antiguos a cambio de una implementación robusta y no invasiva (no reemplazar el dialer completo).
- **Normalización de números telefónicos:** imprescindible usar una librería como `libphonenumber` para evitar que el mismo número en distintos formatos (con/sin código de país) sea tratado como "diferente".

## 9. Riesgos identificados

| Riesgo | Mitigación |
|---|---|
| El sistema operativo mata el servicio en background (fabricantes agresivos) | Guía de onboarding + `foreground service` de apoyo si aplica |
| Rechazo en revisión de Google Play por permisos sensibles | Investigar y completar declaración de permisos con anticipación; considerar distribución APK directa en fase beta |
| Falsos positivos (bloquear a alguien legítimo en su primer intento) | Historial visible + opción rápida de whitelist + notificación de "llamada bloqueada, toca para permitir" |
| Spammers que llaman más de una vez a propósito para evadir el filtro | Documentar como limitación conocida de la v1; explorar en versiones futuras señales adicionales (frecuencia, patrones) |

## 10. Glosario rápido

- **Screening service:** servicio del sistema Android que intercepta la llamada antes de que timbre, permitiendo a una app decidir si se permite, rechaza o marca como spam.
- **Rol de screening (`ROLE_CALL_SCREENING`):** permiso especial de Android que el usuario asigna a una sola app a la vez desde ajustes del sistema.
- **Lista blanca/negra manual:** números que el usuario decide explícitamente permitir o bloquear siempre, sin pasar por la lógica automática.
