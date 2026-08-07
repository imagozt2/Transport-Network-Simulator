# Topics y mensajes MQTT del ecosistema RMM

## Objetivo

Este documento define el contrato MQTT entre el backend de RMM y las máquinas Qt de venta y
validación. Establece la jerarquía de topics, los mensajes intercambiados y las garantías necesarias
para que la comunicación sea segura, idempotente, trazable y tolerante a desconexiones.

Eclipse Mosquitto transporta los mensajes, pero no interpreta reglas tarifarias ni modifica
billetes. Spring Boot sigue siendo la autoridad del negocio y MySQL su fuente de verdad. RMM App y
la aplicación web se comunican con el backend mediante HTTPS y no acceden al broker.

## Alcance

El contrato cubre:

- presencia, estado y telemetría de las máquinas;
- eventos operativos y diagnósticos;
- validaciones de entrada y salida;
- órdenes de emisión y sus confirmaciones;
- distribución de configuración y claves públicas de QR;
- autenticación, autorización, calidad de servicio y reconexión.

## Convenciones

### Namespace y versión

Todos los topics comienzan por:

```text
rmm/v1
```

La versión forma parte del topic. Un cambio incompatible exige un nuevo namespace, por ejemplo
`rmm/v2`; añadir campos opcionales no obliga a cambiar de versión. Los segmentos usan minúsculas y
kebab-case. Los códigos de máquina conservan su representación canónica y se tratan como valores
opacos.

### Formato

- Los payloads usan JSON codificado en UTF-8.
- Las fechas usan ISO 8601 en UTC, por ejemplo `2026-08-07T10:15:30.125Z`.
- Los importes son cadenas decimales acompañadas por una moneda ISO 4217.
- Los identificadores de mensaje y correlación son UUID.
- Los mensajes no contienen contraseñas, claves privadas ni credenciales del broker.
- Los valores QR solo aparecen en el payload que los necesita, nunca en el topic.

### Sobre común

Todo mensaje operativo no retenido utiliza este sobre:

```json
{
  "schemaVersion": 1,
  "messageId": "67ec60c1-9680-4799-b0b0-c28995c3bddf",
  "correlationId": null,
  "type": "device.status-reported",
  "deviceCode": "RMM-VAL-ST046-ENT-01",
  "occurredAt": "2026-08-07T10:15:30.125Z",
  "sentAt": "2026-08-07T10:15:30.420Z",
  "payload": {}
}
```

| Campo | Regla |
| --- | --- |
| `schemaVersion` | Versión entera del esquema del mensaje. |
| `messageId` | Identificador único generado por el publicador. |
| `correlationId` | `messageId` de la petición u orden que origina la respuesta. |
| `type` | Nombre estable del tipo de mensaje. |
| `deviceCode` | Máquina que publica, recibe o protagoniza el mensaje. |
| `occurredAt` | Momento observado por el origen. |
| `sentAt` | Momento de publicación; puede diferir tras una reconexión. |
| `payload` | Datos específicos del mensaje. |

El backend asigna su propia fecha de recepción. `occurredAt` ayuda al diagnóstico, pero no sustituye
el reloj autoritativo del servidor.

## Jerarquía de topics

### Publicados por una máquina

```text
rmm/v1/devices/{deviceCode}/presence
rmm/v1/devices/{deviceCode}/status
rmm/v1/devices/{deviceCode}/telemetry
rmm/v1/devices/{deviceCode}/events/{eventType}
rmm/v1/devices/{deviceCode}/requests/validations
rmm/v1/devices/{deviceCode}/acks
```

### Publicados por el backend

```text
rmm/v1/devices/{deviceCode}/commands
rmm/v1/devices/{deviceCode}/responses
rmm/v1/devices/{deviceCode}/configuration
rmm/v1/configuration/qr-public-keys
```

No se crean topics con códigos de billete, usuarios, órdenes o incidencias. Esos identificadores
pertenecen al payload para simplificar las ACL y evitar su aparición en registros del broker.

## Calidad de servicio y retención

| Topic bajo `rmm/v1/` | QoS | Retenido | Publicador |
| --- | ---: | --- | --- |
| `devices/{deviceCode}/presence` | 1 | Sí | Máquina o LWT |
| `devices/{deviceCode}/status` | 1 | Sí | Máquina |
| `devices/{deviceCode}/telemetry` | 0 | No | Máquina |
| `devices/{deviceCode}/events/{eventType}` | 1 | No | Máquina |
| `devices/{deviceCode}/requests/validations` | 1 | No | Máquina |
| `devices/{deviceCode}/acks` | 1 | No | Máquina |
| `devices/{deviceCode}/commands` | 1 | No | Backend |
| `devices/{deviceCode}/responses` | 1 | No | Backend |
| `devices/{deviceCode}/configuration` | 1 | Sí | Backend |
| `configuration/qr-public-keys` | 1 | Sí | Backend |

QoS 1 significa entrega al menos una vez. Todo consumidor deduplica por `messageId` y toda mutación
usa además una referencia idempotente de negocio.

Los comandos no se retienen: una máquina que se conecta tarde no debe ejecutar una orden antigua.
Las órdenes pendientes se conservan en el backend y solo se vuelven a publicar si siguen vigentes.

## Presencia y último testamento

Cada máquina configura como Last Will and Testament un mensaje retenido en su topic `presence`:

```json
{
  "schemaVersion": 1,
  "state": "OFFLINE",
  "reason": "CONNECTION_LOST",
  "changedAt": "2026-08-07T10:15:30.125Z"
}
```

Después de conectarse publica `ONLINE` con `reason: CONNECTED`. Antes de una desconexión controlada
publica `OFFLINE` con `reason: SHUTDOWN`. El backend combina la presencia con la antigüedad del último
estado: un mensaje retenido no demuestra por sí solo que la máquina esté saludable.

## Estado de máquina

La máquina publica su estado al arrancar, cuando cambia y como latido periódico:

```json
{
  "schemaVersion": 1,
  "messageId": "88661b53-306e-4288-b005-ac59bb267966",
  "correlationId": null,
  "type": "device.status-reported",
  "deviceCode": "RMM-VAL-ST046-ENT-01",
  "occurredAt": "2026-08-07T10:16:00Z",
  "sentAt": "2026-08-07T10:16:00Z",
  "payload": {
    "operationalState": "AVAILABLE",
    "serviceMode": "REGULAR",
    "softwareVersion": "1.0.0",
    "uptimeSeconds": 1842,
    "lastSuccessfulOperationAt": "2026-08-07T10:15:42Z"
  }
}
```

Estados iniciales:

- `AVAILABLE`: preparada para operar;
- `BUSY`: procesando una operación;
- `DEGRADED`: operativa con una limitación conocida;
- `OUT_OF_SERVICE`: conectada, pero no acepta operaciones;
- `MAINTENANCE`: retirada temporalmente por mantenimiento.

`OFFLINE` se deriva de presencia y no es un `operationalState`. El backend valida que tipo y estación
coincidan con su inventario; el cliente no puede reasignarse mediante este mensaje.

## Telemetría

La telemetría representa mediciones prescindibles y de alta frecuencia:

```json
{
  "schemaVersion": 1,
  "messageId": "460dd2ce-817b-4f34-8257-d6211063c7c4",
  "correlationId": null,
  "type": "device.telemetry-reported",
  "deviceCode": "RMM-SALE-ST046-01",
  "occurredAt": "2026-08-07T10:17:00Z",
  "sentAt": "2026-08-07T10:17:00Z",
  "payload": {
    "cpuUsagePercent": 18.4,
    "memoryUsagePercent": 41.2,
    "temperatureCelsius": 38.6,
    "paperLevelPercent": 82
  }
}
```

Los campos no aplicables se omiten. Perder telemetría QoS 0 no altera operaciones ni billetes.

## Eventos operativos

Los eventos se publican en `events/{eventType}`. Los tipos iniciales son `lifecycle`, `operation`,
`diagnostic` y `security`.

```json
{
  "schemaVersion": 1,
  "messageId": "3a36bd79-8c29-4374-a573-837afee57fc7",
  "correlationId": null,
  "type": "device.lifecycle-event",
  "deviceCode": "RMM-SALE-ST046-01",
  "occurredAt": "2026-08-07T10:12:00Z",
  "sentAt": "2026-08-07T10:12:01Z",
  "payload": {
    "eventCode": "DEVICE_STARTED",
    "severity": "INFO",
    "details": { "softwareVersion": "1.0.0" }
  }
}
```

Los niveles son `INFO`, `WARNING` y `ERROR`. Los textos para operadores se generan a partir de
`eventCode`; los mensajes libres no son identificadores funcionales.

## Solicitud de validación

Una validadora solicita al backend la decisión de entrada o salida:

```json
{
  "schemaVersion": 1,
  "messageId": "4ca026f2-1a51-4e62-b03a-d01a58f1c687",
  "correlationId": null,
  "type": "ticket.validation-requested",
  "deviceCode": "RMM-VAL-ST046-ENT-01",
  "occurredAt": "2026-08-07T10:20:15.120Z",
  "sentAt": "2026-08-07T10:20:15.130Z",
  "payload": {
    "validationReference": "87c02a1a-f67f-46a1-8502-711ab9810689",
    "direction": "ENTRY",
    "stationCode": "ST046",
    "qrValue": "RMM:TICKET:1:<JWS>"
  }
}
```

`direction` admite `ENTRY` y `EXIT`. `validationReference` permanece estable al reintentar una misma
lectura. El backend responde en `devices/{deviceCode}/responses`:

```json
{
  "schemaVersion": 1,
  "messageId": "b1594278-4af5-4348-9bbf-e8bddeef9de3",
  "correlationId": "4ca026f2-1a51-4e62-b03a-d01a58f1c687",
  "type": "ticket.validation-decided",
  "deviceCode": "RMM-VAL-ST046-ENT-01",
  "occurredAt": "2026-08-07T10:20:15.180Z",
  "sentAt": "2026-08-07T10:20:15.185Z",
  "payload": {
    "validationReference": "87c02a1a-f67f-46a1-8502-711ab9810689",
    "decision": "ACCEPTED",
    "reasonCode": "VALID",
    "ticketCode": "RMM-TKT-01J4YQ5V82F7V9Z8JQ1W2X3Y4Z",
    "validAtStationCode": "ST046",
    "decidedAt": "2026-08-07T10:20:15.180Z"
  }
}
```

`decision` admite `ACCEPTED` y `REJECTED`. Entre los `reasonCode` iniciales están `VALID`,
`UNKNOWN_TICKET`, `INVALID_SIGNATURE`, `INACTIVE`, `EXPIRED`, `EXHAUSTED`, `BLOCKED`,
`ENTRY_ALREADY_OPEN`, `ENTRY_REQUIRED`, `INSUFFICIENT_BALANCE`, `WRONG_DEVICE` y
`SERVICE_UNAVAILABLE`.

La verificación local de la firma no concede acceso por sí sola. Sin respuesta autoritativa dentro
del plazo configurado, la máquina muestra un resultado no concluyente y no crea una validación
definitiva. El funcionamiento sin conexión requerirá un contrato independiente.

## Órdenes del backend

El backend publica todas las órdenes en `devices/{deviceCode}/commands`. Tipos iniciales:

- `ticket.issue-command`: emitir o presentar un billete;
- `device.configuration-refresh-command`: volver a cargar configuración;
- `device.status-request-command`: publicar inmediatamente el estado;
- `device.restart-command`: reinicio controlado sujeto a autorización administrativa.

Toda orden incluye `commandId`, estable en sus reintentos; `expiresAt`; el destinatario; y un payload
específico.

## Emisión de billetes

Una emisión autorizada se entrega exclusivamente a una máquina de venta:

```json
{
  "schemaVersion": 1,
  "messageId": "0d80f67f-dcfa-4a16-a502-0ca74508d096",
  "correlationId": null,
  "type": "ticket.issue-command",
  "deviceCode": "RMM-SALE-ST046-01",
  "occurredAt": "2026-08-07T10:30:00Z",
  "sentAt": "2026-08-07T10:30:00Z",
  "payload": {
    "commandId": "RMM-CMD-01J4YR0N48B8E6ZFV7N3AK3X82",
    "issuanceCode": "RMM-ISS-01J4YR0FZSE2MKDZXF9DW1M1AC",
    "expiresAt": "2026-08-07T10:32:00Z",
    "ticket": {
      "ticketCode": "RMM-TKT-01J4YQ5V82F7V9Z8JQ1W2X3Y4Z",
      "productType": "SINGLE_TRIP",
      "qrValue": "RMM:TICKET:1:<JWS>",
      "originStationCode": "ST046",
      "destinationStationCode": "ST002"
    }
  }
}
```

La máquina valida destinatario, expiración y duplicados antes de presentar o imprimir. No recalcula
el precio ni altera los derechos firmados.

## Confirmaciones de órdenes

La máquina confirma cada orden en `devices/{deviceCode}/acks`:

```json
{
  "schemaVersion": 1,
  "messageId": "68374bc0-a13f-4306-a357-af2e18f81ee5",
  "correlationId": "0d80f67f-dcfa-4a16-a502-0ca74508d096",
  "type": "ticket.issue-acknowledged",
  "deviceCode": "RMM-SALE-ST046-01",
  "occurredAt": "2026-08-07T10:30:02Z",
  "sentAt": "2026-08-07T10:30:02Z",
  "payload": {
    "commandId": "RMM-CMD-01J4YR0N48B8E6ZFV7N3AK3X82",
    "issuanceCode": "RMM-ISS-01J4YR0FZSE2MKDZXF9DW1M1AC",
    "status": "COMPLETED",
    "resultCode": "TICKET_PRESENTED",
    "completedAt": "2026-08-07T10:30:02Z"
  }
}
```

`status` admite `RECEIVED`, `PROCESSING`, `COMPLETED`, `FAILED` y `REJECTED`. Repetir un `commandId`
devuelve el último estado sin volver a emitir. El backend solo completa una emisión compensatoria
cuando recibe `COMPLETED`.

## Configuración de máquina

El backend publica una configuración retenida específica:

```json
{
  "schemaVersion": 1,
  "configurationVersion": 14,
  "deviceCode": "RMM-VAL-ST046-ENT-01",
  "stationCode": "ST046",
  "deviceType": "ENTRY_VALIDATOR",
  "heartbeatSeconds": 30,
  "requestTimeoutMilliseconds": 2500,
  "publishedAt": "2026-08-07T10:00:00Z"
}
```

La máquina solo aplica una configuración destinada a su identidad y con versión posterior. La
configuración no contiene secretos ni permite cambiar la identidad asociada a las credenciales.

## Claves públicas QR

El backend publica el conjunto retenido de claves aceptadas:

```json
{
  "schemaVersion": 1,
  "keySetVersion": 3,
  "algorithm": "EdDSA",
  "keys": [
    {
      "kid": "rmm-ed25519-2026-01",
      "publicKey": "<clave-publica-codificada>",
      "notBefore": "2026-01-01T00:00:00Z",
      "notAfter": "2027-01-31T23:59:59Z",
      "status": "ACTIVE"
    }
  ],
  "publishedAt": "2026-08-07T10:00:00Z"
}
```

Nunca incluye la clave privada. La rotación sigue el orden del [contrato de códigos
QR](contrato-codigos-qr.md): distribuir, confirmar disponibilidad, empezar a firmar y retirar.

## Autenticación y ACL

- El ciclo completo de identidad, aprovisionamiento y credenciales se define en el [contrato de
  identidad de las máquinas](identidad-maquinas.md).
- Cada máquina utiliza una identidad MQTT individual.
- En producción se requiere TLS y verificación del servidor y del cliente.
- Las credenciales se inyectan fuera del repositorio y se protegen mediante el sistema operativo.
- El backend posee una identidad de servicio independiente.
- Mosquitto deniega por defecto cualquier topic no autorizado.

ACL conceptual de una máquina `{deviceCode}`:

| Acción | Topics permitidos |
| --- | --- |
| Publicar | Su `presence`, `status`, `telemetry`, `events/+`, `requests/validations` y `acks` |
| Suscribirse | Sus `commands`, `responses`, `configuration` y las claves públicas QR |

La identidad autenticada debe coincidir con `{deviceCode}`. Una máquina no puede acceder al
namespace de otra. Solo el backend publica órdenes, respuestas y configuración y consume mensajes
de máquinas.

## Reconexión e idempotencia

- Cada cliente usa un `clientId` estable y único derivado de su código público, sin secretos.
- La máquina emplea sesión persistente cuando la biblioteca y el broker lo permitan.
- Aplica reconexión con espera exponencial y variación aleatoria.
- Conserva solo operaciones pendientes imprescindibles y cifra las que contengan un QR.
- Reenvía con el mismo `messageId`, `validationReference` o `commandId`.
- Descarta órdenes expiradas y confirma el rechazo.
- Al reconectar publica presencia, estado y versiones de configuración conocidas.

El backend mantiene una ventana de deduplicación superior al máximo periodo de reintento. Un mensaje
duplicado puede provocar la misma respuesta, pero nunca una segunda mutación.

## Orden y concurrencia

El dominio no depende de un orden global de MQTT:

- `configurationVersion` y `keySetVersion` resuelven actualizaciones fuera de orden;
- `occurredAt` no decide por sí solo la precedencia;
- las transiciones de billetes se serializan y validan en el backend;
- una transición incompatible recibe un resultado estable y auditable.

## Errores y observabilidad

Los rechazos de peticiones u órdenes se devuelven como respuesta o confirmación correlacionada, no
solo como evento genérico. Los logs registran identificadores de mensaje y negocio, pero ocultan QR,
credenciales y datos personales. Las métricas incluyen conexiones, latencia, reintentos, duplicados,
expiraciones y rechazos.

## Compatibilidad y pruebas futuras

- Los consumidores ignoran campos opcionales desconocidos.
- Un campo obligatorio no cambia de significado dentro de la misma versión.
- Un enum desconocido se rechaza de forma controlada o se trata como no compatible.
- Los payloads se validarán con esquemas JSON versionados antes de persistirse.
- Los ejemplos son ilustrativos y no contienen claves ni billetes reales.

La implementación se acompañará de pruebas de contrato entre Spring Boot y Qt, duplicados,
reordenación, desconexiones, expiración de órdenes y ACL.

## Relación con otros contratos

- La [arquitectura del ecosistema](arquitectura-ecosistema.md) limita Mosquitto al transporte.
- El [ciclo de vida de los billetes](ciclo-vida-billetes.md) define las transiciones solicitadas.
- El [contrato de códigos QR](contrato-codigos-qr.md) define el valor transportado y su firma.
- Los [contratos REST de RMM App](contratos-rest-rmm-app.md) cubren Android sin exponer MQTT.
