# Integración MQTT del backend

## Objetivo

El backend de RMM mantiene la comunicación operativa con las máquinas de venta y validación a
través de Eclipse Mosquitto. Spring Boot autentica la identidad lógica de cada máquina, recibe sus
eventos y estados, publica órdenes dirigidas y conserva en MySQL la información necesaria para
recuperarse de desconexiones.

Mosquitto solo transporta mensajes y aplica las ACL. El backend continúa siendo la autoridad de las
reglas de negocio y MySQL la fuente de verdad. La estructura de cada mensaje, los niveles de QoS y
la jerarquía completa de topics se definen en el [contrato MQTT](contrato-mqtt.md).

## Componentes

```text
Máquina Qt
    │ publicación QoS 0/1
    ▼
Mosquitto
    │
    ▼
ControlCenterMqttClient
    │
    ├── AuthenticatedMqttMessageRouter
    │       ├── autenticación de la máquina
    │       ├── bandeja de entrada idempotente
    │       ├── estado y presencia
    │       └── ventas y validaciones
    │
    └── MqttDeviceCommandPublisher ──► órdenes dirigidas a máquinas
```

| Componente | Responsabilidad |
| --- | --- |
| `ControlCenterMqttClient` | Mantener la sesión MQTT, publicar y restaurar suscripciones. |
| `MqttMachineAuthenticationService` | Resolver la identidad desde el topic y contrastarla con el inventario. |
| `AuthenticatedMqttMessageRouter` | Validar el sobre, deduplicar y distribuir mensajes autenticados. |
| `MqttInboundIdempotencyService` | Gestionar la bandeja de entrada persistente por `messageId`. |
| `MqttDeviceStateReceiver` | Actualizar presencia y situación operativa de la máquina. |
| `MqttTicketOperationEventReceiver` | Convertir ventas y validaciones en eventos del dominio. |
| `MqttDeviceCommandService` | Validar y persistir una orden antes de solicitar su publicación. |
| `MqttDeviceCommandPublisher` | Construir el sobre MQTT y publicar la orden con QoS 1. |
| `MqttPendingCommandRecovery` | Reintentar órdenes vigentes después de una caída. |
| `MqttDeviceDisconnectionMonitor` | Marcar offline una máquina cuyo estado ha quedado obsoleto. |

## Configuración

La integración permanece desactivada si no se configura `RMM_MQTT_ENABLED=true`. Esto permite
ejecutar el backend sin broker durante tareas que no necesitan las máquinas externas.

| Variable | Valor predeterminado | Función |
| --- | --- | --- |
| `RMM_MQTT_ENABLED` | `false` | Activa el cliente, los reintentos y el monitor de máquinas. |
| `RMM_MQTT_HOST` | `localhost` | Host de Mosquitto. |
| `RMM_MQTT_PORT` | `1883` | Puerto MQTT. |
| `RMM_MQTT_TLS` | `false` | Utiliza `ssl://` y certificados de cliente. |
| `RMM_MQTT_CLIENT_ID` | `rmm-backend` | Identidad estable de la sesión del centro de control. |
| `RMM_MQTT_USERNAME` | Vacío | Usuario del backend en Mosquitto. |
| `RMM_MQTT_PASSWORD` | Vacío | Contraseña obtenida del entorno, nunca del repositorio. |
| `RMM_MQTT_CONNECTION_TIMEOUT_SECONDS` | `10` | Tiempo máximo para establecer la conexión. |
| `RMM_MQTT_KEEP_ALIVE_SECONDS` | `30` | Intervalo keep-alive de MQTT. |
| `RMM_MQTT_RECONNECT_INTERVAL_MS` | `5000` | Reintento cuando ni siquiera se completó la primera conexión. |
| `RMM_MQTT_COMMAND_RETRY_INTERVAL_MS` | `10000` | Revisión periódica de órdenes recuperables. |
| `RMM_MQTT_COMMAND_MAX_ATTEMPTS` | `10` | Límite de publicaciones de una misma orden. |
| `RMM_MQTT_COMMAND_BATCH_SIZE` | `100` | Máximo de órdenes recuperadas en un ciclo. |
| `RMM_MQTT_DEVICE_MONITOR_INTERVAL_MS` | `10000` | Frecuencia del detector de desconexiones. |
| `RMM_MQTT_DEVICE_STALE_AFTER` | `90s` | Antigüedad a partir de la que una máquina se considera offline. |

Cuando se habilita TLS también son obligatorias `RMM_MQTT_CA_CERTIFICATE`,
`RMM_MQTT_CLIENT_CERTIFICATE` y `RMM_MQTT_CLIENT_PRIVATE_KEY`. Las rutas deben apuntar a archivos
legibles por el proceso del backend.

## Conexión y suscripciones

El cliente utiliza una sesión persistente (`cleanSession=false`), QoS acorde con cada topic y
reconexión automática de Eclipse Paho. Un supervisor adicional vuelve a intentar la conexión cuando
el broker no estaba disponible durante el arranque, caso que no queda cubierto por una reconexión
posterior a una sesión establecida.

El backend se suscribe a:

```text
rmm/v1/devices/+/presence
rmm/v1/devices/+/status
rmm/v1/devices/+/telemetry
rmm/v1/devices/+/events/+
rmm/v1/devices/+/requests/validations
rmm/v1/devices/+/acks
```

Después de cada conexión se restauran todas las suscripciones registradas. La conexión expone los
estados `DISABLED`, `DISCONNECTED`, `CONNECTING`, `CONNECTED` y `STOPPED` para diagnóstico interno.

## Autenticación de mensajes

La autorización del broker y la autenticación de dominio son capas complementarias:

1. Mosquitto autentica la conexión y limita los topics mediante ACL.
2. El router obtiene el código de máquina del topic.
3. El backend localiza una identidad activa en `device_mqtt_identities`.
4. Comprueba que `deviceCode`, `mqttClientId`, tipo y estación coinciden con el inventario.
5. Solo entonces entrega el mensaje a los consumidores del dominio.

El contenido del payload no puede cambiar la identidad ni reasignar una máquina. Los detalles sobre
aprovisionamiento y revocación están en [Identidad y autenticación de las máquinas](identidad-maquinas.md).

## Recepción e idempotencia

Los mensajes operativos deben incluir un `messageId` UUID. Antes de procesarlos, el backend crea o
recupera su registro en `mqtt_inbound_messages`:

- un identificador nuevo pasa a `PROCESSING` y se entrega una vez al dominio;
- un duplicado ya terminado incrementa `duplicate_count` y no repite efectos;
- un intento previo fallido puede recuperarse de forma controlada;
- reutilizar el mismo `messageId` con otro topic, máquina o payload se rechaza;
- un mensaje válido termina en `PROCESSED`; uno inválido, en `REJECTED`; y un fallo técnico, en
  `FAILED`.

El topic de presencia no utiliza la bandeja idempotente porque representa el último estado retenido
y aplica su propia ordenación temporal. Actualmente los consumidores especializados procesan:

- presencia y estado periódico de la máquina;
- eventos operativos de compra;
- solicitudes y resultados de validación.

Las suscripciones de telemetría y confirmaciones ya forman parte del router y del contrato, aunque
sus efectos de dominio se ampliarán con las aplicaciones Qt.

## Estado y desconexiones

Un mensaje de presencia `ONLINE` o un estado periódico válido demuestra actividad reciente. El
backend actualiza `mqtt_presence`, `operational_state`, versión, modo de servicio, uptime y fechas de
último mensaje. La situación visible se calcula así:

- `OFFLINE` cuando llega la presencia offline o caduca el último mensaje;
- `MAINTENANCE` para el estado operativo de mantenimiento;
- `ERROR` para una máquina fuera de servicio;
- `ONLINE` para el resto de estados conectados.

El monitor revisa únicamente máquinas gestionadas por MQTT que estén online. Si no recibe presencia
ni estado durante `RMM_MQTT_DEVICE_STALE_AFTER`, las marca offline. Un nuevo estado válido vuelve a
ponerlas online sin intervención manual.

Las aplicaciones Qt publican presencia retenida al conectarse y un latido periódico. También
configuran un Last Will retenido con estado `OFFLINE`, de modo que Mosquitto pueda comunicar una
desconexión no controlada. El backend valida la fecha declarada en el mensaje, pero registra la
presencia con su propia hora de recepción para que un reloj desajustado o un Last Will antiguo no
falseen la conectividad actual.

## Publicación y recuperación de órdenes

Las órdenes no se publican directamente desde una petición de negocio:

1. se valida la máquina, su tipo y su identidad activa;
2. se genera un `commandId` estable y un `messageId` UUID;
3. se guarda la orden `PENDING` en `device_mqtt_commands`;
4. después del commit se publica en `rmm/v1/devices/{deviceCode}/commands` con QoS 1;
5. el intento queda como `PUBLISHED`, `PUBLISH_FAILED` o `EXPIRED`.

Los tipos iniciales son `TICKET_ISSUE`, `CONFIGURATION_REFRESH`, `STATUS_REQUEST` y `RESTART`. Una
orden de emisión solo puede dirigirse a una máquina de venta.

Tras reconectar y también de forma periódica, el recuperador busca órdenes `PENDING` o
`PUBLISH_FAILED` que:

- todavía no hayan caducado;
- no hayan alcanzado el máximo de intentos;
- entren en el lote configurado.

Cada reintento conserva `commandId`, `messageId` y `expiresAt`; únicamente cambia `sentAt`. De esta
forma la máquina puede deduplicar la orden y el backend no convierte una reconexión en una nueva
operación. Mientras MQTT está desconectado no se consumen intentos adicionales.

## Persistencia

| Tabla | Información conservada |
| --- | --- |
| `device_mqtt_identities` | Identidad, modo de autenticación, vigencia y revocación. |
| `mqtt_inbound_messages` | Huella, estado de procesamiento, duplicados y errores de entrada. |
| `device_mqtt_commands` | Orden, payload, vigencia, estado e intentos de publicación. |
| `devices` | Presencia, estado operativo, versión, uptime y últimas comunicaciones. |
| `operational_logs` | Eventos de venta, validación y actividad de máquina aceptados. |

Los payloads no se escriben en los logs técnicos del cliente MQTT y ninguna contraseña o clave
privada se almacena en estas tablas.

## Pruebas

Las pruebas unitarias MQTT cubren:

- creación del topic y del sobre de una orden;
- transición a `PUBLISHED` y conservación recuperable de un fallo;
- recepción única ante una redelivery QoS 1;
- recuperación de órdenes tras reconectar;
- ausencia de reintentos mientras el broker está desconectado;
- conservación de caracteres UTF-8 en estados recibidos por MQTT;
- actualización de presencia con la identidad autenticada y la hora de recepción del backend;
- correspondencia entre código, tipo, estación e identidad MQTT del inventario cargado.

Para ejecutarlas:

```powershell
Set-Location backend
.\mvnw.cmd test "-Dtest=*Mqtt*Tests"
```

Las pruebas reales del broker, ACL y reconexión se describen en la
[infraestructura local](infraestructura-local.md) y se ejecutan mediante
`infrastructure/mosquitto/tests/mqtt-integration-tests.ps1`.
