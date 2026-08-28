# Contratos REST para RMM App

## Objetivo

Este documento define la API HTTPS que utilizará RMM App para autenticación, consulta de la red,
planificación de trayectos, compra, recarga, cartera, vinculación de billetes físicos e historial de
desplazamientos.

Los contratos son de diseño y se implementarán progresivamente. No sustituyen ni modifican todavía
los endpoints utilizados por la aplicación web del centro de control.

## Principios

- El prefijo inicial es `/api/rmm-app/v1`.
- Todas las comunicaciones utilizan HTTPS fuera del entorno local.
- Los recursos privados requieren una sesión de pasajero, nunca una sesión de operador.
- Los códigos públicos son opacos, estables y no secuenciales.
- Las operaciones que modifican derechos son idempotentes.
- Importes y fechas tienen representaciones inequívocas.
- Los clientes no deducen reglas de negocio a partir de textos traducidos.
- El backend valida de nuevo todos los parámetros recibidos.
- Las respuestas no exponen identificadores internos, hashes ni secretos.

## Convenciones generales

### Formato y codificación

- Cuerpo de petición y respuesta: `application/json; charset=UTF-8`.
- Errores: `application/problem+json`.
- Nombres de propiedades: `camelCase` en inglés.
- Fechas: ISO 8601 con desplazamiento, normalizadas a UTC.
- Importes: texto decimal con dos posiciones, nunca números de coma flotante.
- Moneda: código ISO 4217; inicialmente `EUR`.
- Códigos de estaciones, líneas, productos, billetes y operaciones: texto opaco.

Ejemplo:

```json
{
  "amount": "3.50",
  "currency": "EUR",
  "createdAt": "2026-08-07T14:32:18Z"
}
```

### Cabeceras

| Cabecera | Uso |
| --- | --- |
| `Authorization: Bearer <token>` | Acceso a recursos privados del pasajero. |
| `Idempotency-Key` | Identifica de forma única una operación modificadora reintentable. |
| `Accept-Language` | Idioma preferido para textos informativos. |
| `X-Request-Id` | Correlación opcional proporcionada por el cliente. |
| `Retry-After` | Espera recomendada en límites de frecuencia o indisponibilidad. |

El backend genera un `X-Request-Id` cuando el cliente no lo envía y lo devuelve en la respuesta. No
se aceptan tokens, códigos QR ni contraseñas en parámetros de URL.

### Idempotencia

`Idempotency-Key` es obligatorio en compras, recargas, vinculaciones y otras operaciones que puedan
alterar derechos. Será un UUID nuevo por intención del usuario.

- Repetir la misma clave y el mismo cuerpo devuelve el resultado persistido.
- Repetir la clave con un cuerpo distinto devuelve `409 Conflict`.
- Las claves se aíslan por pasajero, operación y entorno.
- Una pérdida de conexión no autoriza a generar otra clave para la misma intención.

### Paginación

Las colecciones variables utilizan cursor:

```http
GET /api/rmm-app/v1/tickets?limit=20&cursor=eyJ...
```

```json
{
  "items": [],
  "page": {
    "limit": 20,
    "nextCursor": null,
    "hasMore": false
  }
}
```

`limit` tendrá un valor predeterminado y un máximo definidos por el backend. El cursor es opaco y no
debe interpretarse ni fabricarse en Android.

### Respuestas vacías

Una acción completada sin contenido devuelve `204 No Content`. Una colección sin resultados devuelve
`200 OK` con `items: []`; no se considera un error.

## Errores

Ejemplo:

```json
{
  "type": "https://rmm.example/problems/insufficient-balance",
  "title": "No se puede completar la operación",
  "status": 422,
  "code": "INSUFFICIENT_BALANCE",
  "detail": "El billete no dispone de saldo suficiente.",
  "instance": "/api/rmm-app/v1/tickets/RMM-TKT-01.../recharges",
  "requestId": "c699b376-36b5-486f-9214-a54baab2a9a9",
  "fieldErrors": []
}
```

`code` es estable y permite traducir o decidir el comportamiento de la interfaz. `title` y `detail`
son informativos y no se utilizan como condición lógica.

| Estado HTTP | Uso |
| --- | --- |
| `400 Bad Request` | JSON, cabeceras o parámetros con formato inválido. |
| `401 Unauthorized` | Sesión ausente, vencida o no renovable. |
| `403 Forbidden` | Sesión válida sin permiso sobre el recurso. |
| `404 Not Found` | Recurso inexistente o no visible para ese pasajero. |
| `409 Conflict` | Estado incompatible, duplicado o clave idempotente reutilizada incorrectamente. |
| `422 Unprocessable Content` | Regla funcional incumplida. |
| `429 Too Many Requests` | Límite de frecuencia superado. |
| `503 Service Unavailable` | Dependencia temporalmente indisponible. |

Los errores de validación incluyen:

```json
{
  "fieldErrors": [
    {
      "field": "email",
      "code": "INVALID_EMAIL"
    }
  ]
}
```

## Autenticación de pasajeros

La autenticación móvil es independiente de las cookies y roles del centro de control.

### Registrar una cuenta

```http
POST /api/rmm-app/v1/auth/register
```

```json
{
  "email": "passenger@example.com",
  "password": "una-contraseña-segura",
  "firstName": "Nombre",
  "lastName": "Apellidos",
  "locale": "es-ES",
  "termsVersion": "2026-01"
}
```

Respuesta `201 Created`:

```json
{
  "user": {
    "publicId": "acec96e3-7cac-48cb-8ec7-b09b2cedb850",
    "email": "passenger@example.com",
    "firstName": "Nombre",
    "lastName": "Apellidos",
    "status": "PENDING_VERIFICATION",
    "locale": "es-ES"
  },
  "verificationRequired": true
}
```

Registrar la cuenta no inicia sesión automáticamente mientras la verificación sea obligatoria.

### Verificar correo

```http
POST /api/rmm-app/v1/auth/email-verifications
```

```json
{
  "verificationToken": "valor-recibido-fuera-de-la-url-de-la-api"
}
```

Respuesta: `204 No Content`.

### Iniciar sesión

```http
POST /api/rmm-app/v1/auth/sessions
```

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

Respuesta `201 Created`:

```json
{
  "accessToken": "token-de-acceso",
  "accessTokenExpiresAt": "2026-08-07T15:02:18Z",
  "refreshToken": "token-de-renovación",
  "refreshTokenExpiresAt": "2026-09-06T14:32:18Z",
  "user": {
    "publicId": "acec96e3-7cac-48cb-8ec7-b09b2cedb850",
    "email": "passenger@example.com",
    "firstName": "Nombre",
    "lastName": "Apellidos",
    "status": "ACTIVE",
    "locale": "es-ES"
  }
}
```

El token de acceso tiene duración breve. El token de renovación rota en cada uso y se guarda mediante
Android Keystore; nunca se registra en logs ni copias de seguridad.

### Renovar la sesión

```http
POST /api/rmm-app/v1/auth/session-refreshes
```

```json
{
  "refreshToken": "token-de-renovación-actual",
  "installationId": "0e31c063-7728-492c-bd63-6e78473ebae7"
}
```

Devuelve un nuevo par de tokens e invalida el token de renovación anterior.

### Cerrar la sesión actual

```http
DELETE /api/rmm-app/v1/auth/sessions/current
```

Respuesta: `204 No Content`. La aplicación elimina siempre sus credenciales locales, incluso si la
petición remota no puede completarse.

### Consultar la cuenta

```http
GET /api/rmm-app/v1/me
```

Devuelve el perfil asociado al token. Una cuenta bloqueada o eliminada no puede renovar la sesión.

## Red de transporte

Estas consultas pueden almacenarse temporalmente en caché. El backend devuelve `ETag` y admite
`If-None-Match`; una red sin cambios responde `304 Not Modified`.

### Consultar líneas

```http
GET /api/rmm-app/v1/network/lines
```

```json
{
  "items": [
    {
      "code": "L1",
      "name": "Línea 1",
      "color": "#DF252B",
      "terminals": ["ST030", "ST045"],
      "active": true
    }
  ]
}
```

### Consultar estaciones

```http
GET /api/rmm-app/v1/network/stations?query=teatro&lineCode=L1
```

```json
{
  "items": [
    {
      "code": "ST016",
      "name": "Teatro Nacional",
      "lineCodes": ["L1", "L5"],
      "accessible": true,
      "active": true
    }
  ]
}
```

### Consultar una estación

```http
GET /api/rmm-app/v1/network/stations/{stationCode}
```

Incluye sus líneas, correspondencias y datos públicos necesarios para planificar un recorrido; no
expone inventario técnico de máquinas ni información administrativa.

### Calcular un trayecto

```http
GET /api/rmm-app/v1/network/journeys?origin=ST046&destination=ST002
```

```json
{
  "origin": { "code": "ST046", "name": "El Espigón" },
  "destination": { "code": "ST002", "name": "HUB Industrial Norte" },
  "estimatedDurationSeconds": 1440,
  "stationCount": 13,
  "transferCount": 1,
  "segments": [
    {
      "lineCode": "L6",
      "lineName": "Línea 6",
      "lineColor": "#F57900",
      "directionTerminal": { "code": "ST049", "name": "HUB Industrial Este" },
      "stopCount": 2,
      "travelSeconds": 360,
      "stations": [
        { "code": "ST046", "name": "El Espigón" },
        { "code": "ST020", "name": "La Galería" },
        { "code": "ST027", "name": "Plaza de la Merced" }
      ]
    }
  ]
}
```

La última estación de un segmento coincide con la primera del siguiente cuando existe transbordo.
`directionTerminal` siempre identifica el terminal real de la línea en ese sentido.

RMM App conserva recientes y favoritos como referencias locales por pasajero. Al seleccionarlos
vuelve a consultar este endpoint para no presentar una ruta obsoleta. Consulta
[Consulta de la red y planificación de trayectos](consulta-red-rmm-app.md).

## Catálogo de títulos

### Consultar productos activos

```http
GET /api/rmm-app/v1/ticket-products
```

Cada producto incluye código, nombre localizado, tipo, reglas, límites, precios y moneda. La
respuesta no obliga al cliente a reproducir el cálculo definitivo; los importes mostrados se
confirman nuevamente al solicitar la compra.

### Simular un precio (contrato futuro)

```http
POST /api/rmm-app/v1/ticket-products/{productCode}/quotes
```

Cabecera `Idempotency-Key`: no obligatoria porque la operación no modifica estado.

Ejemplo para un billete sencillo:

```json
{
  "configuration": {
    "originStationCode": "ST046",
    "destinationStationCode": "ST002"
  }
}
```

Respuesta:

```json
{
  "quoteId": "8ed95103-2ee3-4d89-88de-86c063c8f930",
  "productCode": "SINGLE_TRIP",
  "configuration": {
    "originStationCode": "ST046",
    "destinationStationCode": "ST002",
    "stationCount": 13
  },
  "subtotalAmount": "1.15",
  "totalAmount": "1.15",
  "currency": "EUR",
  "expiresAt": "2026-08-07T14:47:18Z"
}
```

Este endpoint todavía no forma parte de la implementación. La versión actual calcula una estimación
en Android con el catálogo y el backend recalcula el importe definitivo al comprar. Cuando se
incorpore la cotización remota, será temporal y una compra rechazará un `quoteId` vencido o
perteneciente a otro usuario.

## Compras

### Crear una compra

```http
POST /api/rmm-app/v1/purchases
Idempotency-Key: 39463e20-110c-4a6f-9f09-74943422cc11
```

```json
{
  "productCode": "SINGLE_TRIP",
  "configuration": {
    "originStationCode": "ST046",
    "destinationStationCode": "ST002"
  },
  "paymentMethod": "SIMULATED"
}
```

Los campos permitidos en `configuration` dependen del producto:

| Producto | Configuración |
| --- | --- |
| `SINGLE_TRIP` | `originStationCode`, `destinationStationCode` |
| `MULTI_TRIP` | `tripCount` |
| `TIME_PASS` | `dayCount` |
| `SMART_BALANCE` | `rechargeAmount` |

Una propiedad ajena al producto se rechaza. El backend vuelve a comprobar las reglas y calcula el
importe definitivo antes de emitir.

Respuesta `201 Created` cuando finaliza inmediatamente:

```json
{
  "code": "RMM-PUR-01J8YSQVNQR0J0D8M3FK1J5B6V",
  "status": "COMPLETED",
  "productCode": "SINGLE_TRIP",
  "totalAmount": "1.15",
  "currency": "EUR",
  "ticketCode": "RMM-TKT-01J8YQ7V4F6V2X0K8M3P9N5C2A",
  "requestedAt": "2026-08-07T14:32:18Z",
  "completedAt": "2026-08-07T14:32:19Z"
}
```

La implementación actual completa la emisión de forma síncrona. Una evolución asíncrona podrá
devolver `202 Accepted`, estado `PROCESSING` y una cabecera `Location`.

### Consultar una compra

```http
GET /api/rmm-app/v1/purchases/{purchaseCode}
```

Solo el pasajero propietario puede consultarla. Los estados corresponden al ciclo de compra
documentado: `REQUESTED`, `PENDING_PAYMENT`, `PROCESSING`, `COMPLETED`, `FAILED` o `CANCELLED`.

### Consultar compras propias (contrato futuro)

```http
GET /api/rmm-app/v1/purchases?status=COMPLETED&limit=20&cursor=...
```

## Cartera de billetes

### Listar billetes

```http
GET /api/rmm-app/v1/tickets?status=ACTIVE&productType=MULTI_TRIP&limit=20&cursor=...
```

Cada elemento incluye únicamente el resumen necesario:

```json
{
  "code": "RMM-TKT-01J8YQ7V4F6V2X0K8M3P9N5C2A",
  "product": {
    "code": "MULTI_TRIP",
    "name": "Billete multiviaje",
    "type": "MULTI_TRIP"
  },
  "medium": "DIGITAL",
  "status": "ACTIVE",
  "remainingTrips": 8,
  "balanceAmount": null,
  "validFrom": null,
  "validUntil": null,
  "openJourney": false,
  "issuedAt": "2026-08-07T14:32:19Z"
}
```

Los campos no aplicables se devuelven como `null` o se omiten de forma consistente durante toda la
versión. La implementación elegirá una única política y la documentará en el esquema OpenAPI.

### Consultar un billete

```http
GET /api/rmm-app/v1/tickets/{ticketCode}
```

Incluye configuración, derechos disponibles, vigencia, último uso y trayecto abierto, pero no el QR.
Separar el QR permite aplicar controles de caché y exposición más estrictos.

### Obtener el QR

```http
GET /api/rmm-app/v1/tickets/{ticketCode}/qr
```

```json
{
  "ticketCode": "RMM-TKT-01J8YQ7V4F6V2X0K8M3P9N5C2A",
  "qrValue": "RMM:TICKET:1:eyJ...",
  "credentialId": "b8915833-4199-4d21-9b5e-270b587d30aa",
  "expiresAt": null
}
```

La respuesta utiliza `Cache-Control: no-store` y nunca se incluye en notificaciones ni enlaces.

### Vincular un billete físico

```http
POST /api/rmm-app/v1/ticket-links
Idempotency-Key: 208ec31f-e386-409c-821b-a5c3a5499a77
```

```json
{
  "qrValue": "RMM:TICKET:1:eyJ...",
  "linkCode": "7K4P-9M2Q"
}
```

Respuesta `201 Created` con el resumen del billete vinculado. El código de vinculación es de un solo
uso, se limita por intentos y no se devuelve en ninguna consulta.

Respuestas relevantes:

- `409 TICKET_ALREADY_LINKED` si ya pertenece a otra cuenta;
- `409 TICKET_ALREADY_IN_WALLET` si ya pertenece a la misma cuenta;
- `422 INVALID_LINK_CODE` sin revelar qué parte de la prueba falló;
- `422 TICKET_NOT_LINKABLE` si su estado no permite asociarlo.

### Consultar el historial de un billete

```http
GET /api/rmm-app/v1/tickets/{ticketCode}/history?limit=20&cursor=...
```

La consulta exige una sesión de pasajero y aplica la propiedad antes de resolver el cursor. Devuelve
las operaciones desde la más reciente y no expone referencias externas ni identificadores de
máquinas:

```json
{
  "items": [
    {
      "type": "ENTRY_ACCEPTED",
      "resultingStatus": "ACTIVE",
      "station": {
        "code": "ST038",
        "name": "Acueducto"
      },
      "operationAmount": 0.00,
      "balanceAfter": null,
      "remainingTripsAfter": 7,
      "validFromAfter": null,
      "validUntilAfter": null,
      "currency": "EUR",
      "occurredAt": "2026-08-11T12:14:05"
    }
  ],
  "nextCursor": "RMM-TOP-01K2..."
}
```

`limit` debe estar entre 1 y 100. `nextCursor` es `null` cuando no quedan operaciones. Un cursor que
no pertenece al mismo billete devuelve `400 Bad Request`; un billete ajeno conserva la respuesta de
recurso no encontrado definida por el aislamiento de pasajeros.

## Recargas

### Cotizar una recarga

```http
POST /api/rmm-app/v1/tickets/{ticketCode}/recharge-quotes
```

```json
{
  "configuration": {
    "tripCount": 10
  }
}
```

Para `SINGLE_TRIP` se proporciona un nuevo origen y destino; para `MULTI_TRIP`, viajes; para
`TIME_PASS`, días; y para `SMART_BALANCE`, importe.

### Aplicar una recarga

```http
POST /api/rmm-app/v1/tickets/{ticketCode}/recharges
Idempotency-Key: 908108a6-acbf-4d35-866d-09ed555fb619
```

```json
{
  "quoteId": "d75777ee-80d4-46c2-b4a5-9afdddb3fb01",
  "paymentMethod": "SIMULATED"
}
```

Respuesta `201 Created` con código de recarga, estado, importe y resumen actualizado del billete. La
recarga y la modificación de derechos se confirman en una única transacción.

## Historial de desplazamientos

### Consultar trayectos propios

```http
GET /api/rmm-app/v1/journeys/history?limit=20&cursor=RMM-JRN-...
```

```json
{
  "items": [
    {
      "code": "RMM-JRN-01J8Z1YQ7PT8MF5Q2A0R9G6B3K",
      "status": "CLOSED",
      "ticketCode": "RMM-TKT-01J8YQ7V4F6V2X0K8M3P9N5C2A",
      "productName": "Saldo inteligente",
      "productType": "SMART_BALANCE",
      "origin": { "code": "ST016", "name": "Teatro Nacional" },
      "destination": { "code": "ST049", "name": "HUB Industrial Este" },
      "stationCount": 7,
      "fareAmount": "0.60",
      "currency": "EUR",
      "openedAt": "2026-08-07T08:14:03",
      "endedAt": "2026-08-07T08:39:41",
      "durationSeconds": 1538,
      "anomalous": false
    }
  ],
  "nextCursor": null
}
```

La respuesta contiene los datos utilizados por el listado y por el detalle local de RMM App. No
expone identificadores ni datos técnicos privados de las máquinas.

## Cuenta y dispositivos móviles

### Actualizar preferencias de cuenta

```http
PATCH /api/rmm-app/v1/me
```

```json
{
  "firstName": "Nombre",
  "lastName": "Apellidos",
  "locale": "es-ES"
}
```

El correo y la contraseña utilizan flujos específicos con verificación adicional.

### Listar sesiones móviles

```http
GET /api/rmm-app/v1/me/sessions
```

### Revocar una sesión

```http
DELETE /api/rmm-app/v1/me/sessions/{sessionId}
```

Un pasajero solo puede consultar y revocar sus propias sesiones.

### Listar dispositivos registrados

```http
GET /api/rmm-app/v1/me/devices
```

Devuelve las instalaciones Android asociadas a la cuenta, su estado y sus fechas de registro y
última actividad.

### Revocar un dispositivo

```http
DELETE /api/rmm-app/v1/me/devices/{deviceId}
```

Revoca el dispositivo y todas sus sesiones activas. Un dispositivo ajeno no se revela y responde
como un recurso inexistente. Las garantías completas se detallan en
[Autenticación de RMM App](autenticacion-rmm-app.md).

## Límites de frecuencia

Se aplican límites independientes por dirección, cuenta, instalación y operación, especialmente a:

- inicio de sesión y renovación;
- verificación y recuperación de cuenta;
- lectura y vinculación de QR;
- cotizaciones y compras;
- obtención repetida de credenciales QR.

Un límite superado devuelve `429`, un código estable y `Retry-After`. La respuesta no confirma si un
correo, billete o código de vinculación existe.

## Caché y conectividad

La política transversal se define en los [flujos online y sin conexión del
ecosistema](flujos-conectividad.md).

- Red y catálogo admiten caché condicional mediante `ETag`.
- Cuenta, cartera, compras, QR e historial son privados y no se almacenan en cachés compartidas.
- Las respuestas con tokens o QR utilizan `Cache-Control: no-store`.
- Android puede mostrar datos previamente sincronizados indicando su antigüedad.
- Las operaciones modificadoras permanecen pendientes localmente solo con su `Idempotency-Key` y sin
  guardar contraseñas ni QR completos.
- Una respuesta de red ambigua se resuelve consultando el recurso antes de repetir la operación.

## Evolución y OpenAPI

- Los cambios incompatibles crean una versión nueva del prefijo.
- Añadir campos opcionales no cambia la versión.
- El backend publicará una especificación OpenAPI generada o validada en el pipeline.
- Android generará o comprobará sus modelos contra esa especificación para detectar incompatibilidades.
- Los ejemplos de este documento no son credenciales, tokens ni identificadores reales.

Los endpoints futuros mantendrán los estados definidos en el ciclo de vida de billetes y transportarán
los QR sin reinterpretar ni registrar su valor completo.
