# Autenticación de RMM App

## Objetivo y límites

RMM App utiliza una autenticación propia para pasajeros. Es independiente de la sesión HTTP del
centro de control y de las credenciales MQTT de las máquinas. Una cuenta de pasajero nunca concede
acceso de operador, y una cuenta de operador no puede utilizar los recursos privados de un pasajero.

La API móvil se publica bajo `/api/rmm-app/v1`. Los endpoints privados exigen:

```http
Authorization: Bearer <access-token>
```

El backend conserva únicamente hashes SHA-256 de los tokens opacos. Los valores originales se
entregan al cliente y no deben aparecer en logs, URLs ni almacenamiento sin cifrar.

## Ciclo de la cuenta

1. `POST /api/rmm-app/v1/auth/register` crea la cuenta con estado `PENDING_VERIFICATION`.
2. El backend genera un token de verificación de un solo uso, válido durante 24 horas por defecto.
3. `POST /api/rmm-app/v1/auth/email-verifications` verifica el correo y activa la cuenta.
4. Una cuenta `ACTIVE` puede iniciar sesión; una cuenta pendiente, bloqueada o deshabilitada no.
5. Cinco intentos fallidos consecutivos provocan un bloqueo temporal.

El contenedor local utiliza `RMM_APP_AUTO_VERIFY_REGISTRATION=true` porque no envía correos. En ese
entorno la cuenta pasa a `ACTIVE` dentro de la misma transacción de registro y la respuesta indica
`verificationRequired: false`. La propiedad es `false` por defecto en el backend y no debe activarse
en entornos que dispongan de correo o que representen un despliegue real.

Las solicitudes de reenvío y recuperación no confirman si el correo existe, evitando la enumeración
de cuentas.

## Inicio de sesión y dispositivo móvil

`POST /api/rmm-app/v1/auth/sessions` recibe correo, contraseña y la identidad de la instalación:

```json
{
  "email": "passenger@example.com",
  "password": "una-contraseña-segura",
  "device": {
    "installationId": "0e31c063-7728-492c-bd63-6e78473ebae7",
    "name": "Pixel 8",
    "platform": "ANDROID"
  }
}
```

`installationId` es un UUID generado por la aplicación en su primera ejecución. Identifica la
instalación, pero no es una credencial. El backend registra el dispositivo en
`passenger_mobile_devices` y enlaza cada sesión mediante `mobile_device_id`.

Una instalación solo puede pertenecer a un pasajero. Volver a iniciar sesión desde el mismo
dispositivo revoca sus sesiones anteriores. Un dispositivo revocado no puede reutilizarse.

## Tokens y renovación

Una autenticación correcta entrega un token de acceso válido durante 30 minutos y un token de
renovación válido durante 30 días por defecto, junto con sus fechas de caducidad y el perfil público.

`POST /api/rmm-app/v1/auth/session-refreshes` exige el token de renovación y el `installationId` que
originó la sesión. La renovación rota ambos tokens. Se rechaza si la sesión caducó, fue revocada,
corresponde a otra instalación o la cuenta dejó de estar activa.

Android debe guardar el token de renovación con Android Keystore. El token de acceso se conserva el
menor tiempo posible en memoria y las respuestas de autenticación se tratan como `no-store`.

## Cierre y administración de sesiones

| Método | Ruta | Función |
| --- | --- | --- |
| `DELETE` | `/api/rmm-app/v1/auth/sessions/current` | Cierra la sesión actual. |
| `GET` | `/api/rmm-app/v1/me/sessions` | Lista las sesiones activas de la cuenta. |
| `DELETE` | `/api/rmm-app/v1/me/sessions/{sessionId}` | Revoca una sesión propia. |
| `GET` | `/api/rmm-app/v1/me/devices` | Lista los dispositivos registrados. |
| `DELETE` | `/api/rmm-app/v1/me/devices/{deviceId}` | Revoca un dispositivo y sus sesiones. |

Los identificadores expuestos son UUID públicos. La aplicación elimina sus credenciales locales
incluso si el cierre remoto no puede completarse por falta de conexión.

## Recuperación de acceso

`POST /api/rmm-app/v1/auth/password-recovery-requests` genera un token de un solo uso, válido durante
30 minutos por defecto. `POST /api/rmm-app/v1/auth/password-resets` recibe ese token y una contraseña
nueva de entre 12 y 72 caracteres. El cambio de contraseña revoca las sesiones activas.

Los tokens de verificación y recuperación se almacenan mediante hash y dejan de ser válidos cuando
se usan o caducan. En desarrollo, `RMM_APP_MAIL_ENABLED=false` no entrega ni registra los tokens.
Por eso el entorno local habilita explícitamente la activación automática; si se desactiva, deberá
configurarse un medio real de entrega antes de registrar pasajeros. Las credenciales SMTP se
suministran mediante variables de entorno.

## Autorización y aislamiento

Spring Security asigna `ROLE_PASSENGER` únicamente después de validar el bearer token, su caducidad,
la sesión, el dispositivo y el estado de la cuenta. Todas las rutas `/api/rmm-app/v1/**`, salvo los
flujos públicos de autenticación, requieren ese rol.

Las consultas privadas combinan el código del recurso con la cuenta autenticada. Esto se aplica a
billetes, soportes, compras, sesiones y dispositivos. Si un recurso pertenece a otro pasajero, la API
responde `404 Not Found` para no revelar su existencia. Una sesión de operador no se interpreta como
sesión móvil.

## Respuestas relevantes

| Estado | Significado |
| --- | --- |
| `400 Bad Request` | Formato, UUID o contraseña no válidos. |
| `401 Unauthorized` | Credenciales o sesión móvil no válidas. |
| `403 Forbidden` | Cuenta no activa, rol incorrecto o dispositivo no disponible. |
| `404 Not Found` | Recurso inexistente o perteneciente a otro pasajero. |
| `409 Conflict` | El registro entra en conflicto con una cuenta existente. |

## Configuración

| Variable | Predeterminado | Función |
| --- | --- | --- |
| `RMM_APP_ACCESS_TOKEN_LIFETIME` | `30m` | Vigencia del token de acceso. |
| `RMM_APP_REFRESH_TOKEN_LIFETIME` | `30d` | Vigencia máxima de renovación. |
| `RMM_APP_EMAIL_VERIFICATION_LIFETIME` | `24h` | Vigencia de la verificación. |
| `RMM_APP_PASSWORD_RESET_LIFETIME` | `30m` | Vigencia de recuperación. |
| `RMM_APP_AUTO_VERIFY_REGISTRATION` | `false` | Activa inmediatamente cuentas nuevas; solo se habilita en el Docker local sin correo. |
| `RMM_APP_MAIL_ENABLED` | `false` | Activa la entrega mediante SMTP. |

## Comportamiento de RMM App

Los formularios permanecen editables mientras no exista una petición y se bloquean únicamente
durante el envío. El teclado avanza entre campos y permite finalizar el inicio de sesión sin perder
el contenido introducido.

La aplicación no presenta directamente los textos técnicos del backend. Traduce los resultados a
mensajes diferenciados para credenciales incorrectas, cuenta no disponible, correo ya registrado,
demasiados intentos, datos inválidos, timeout, red inaccesible, backend detenido y respuesta no
interpretable. Al terminar cualquier fallo, el formulario vuelve a quedar disponible.

Después de un registro, el mensaje depende de `verificationRequired`: solicita revisar el correo en
el flujo normal o informa de que ya puede iniciarse sesión en el entorno local autoactivado.

Los ejemplos completos se mantienen en los [contratos REST de RMM App](contratos-rest-rmm-app.md).
La conservación local se describe en los [flujos online y sin conexión](flujos-conectividad.md).
