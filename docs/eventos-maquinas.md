# Ciclo de eventos de las máquinas

El backend simula actividad operativa de las máquinas de venta y de los validadores de la red de
Macegocia. Cada evento se registra como un log, actualiza cuando corresponde el estado de su máquina
y conserva la estación en la que se encuentra.

Este mecanismo es independiente del motor ferroviario. Las líneas, estaciones, trenes y cocheras no
generan logs simulados.

## Flujo general

```text
generador automático                   futuro consumidor MQTT
         │                                      │
         └──────────────┬───────────────────────┘
                        ▼
                  DeviceEvent
                        │
                        ▼
           validación y resolución de máquina
                        │
                 misma transacción
               ┌────────┴────────┐
               ▼                 ▼
       estado de la máquina   operational_logs
```

La generación y el registro son responsabilidades separadas:

- `SimulatedDeviceEventGenerator` decide qué evento produce una máquina;
- `DeviceEventRegistrationService` localiza la máquina, aplica la transición y persiste el log;
- `DeviceEventSimulationService` selecciona las máquinas de cada ciclo;
- `DeviceEventSimulationScheduler` ejecuta automáticamente esos ciclos;
- `DeviceEventIngress` es el puerto compartido por la simulación y la integración MQTT.

No existe ningún endpoint HTTP para generar logs manualmente.

## Simulación automática

El scheduler espera un segundo y después ejecuta un ciclo con frecuencia fija de un segundo. En
cada ciclo:

1. consulta las máquinas activas;
2. consulta si el servicio ferroviario se encuentra abierto;
3. conecta todas las máquinas si el servicio está abierto o las desconecta cuando está cerrado;
4. durante el servicio, mezcla la lista para repartir la actividad ordinaria;
5. selecciona una máquina;
6. genera y registra un único evento operativo sin simular averías.

Un ciclo sin máquinas activas termina sin generar registros. Durante el horario de servicio se
produce, por tanto, un evento ordinario por segundo. Las conexiones o desconexiones necesarias para
sincronizar el estado de toda la red son excepcionales y adicionales: al abrir o cerrar pueden
registrarse varias en el mismo ciclo.

### Configuración

| Variable de entorno | Valor predeterminado | Descripción |
| --- | ---: | --- |
| `DEVICE_EVENT_SIMULATION_ENABLED` | `true` | Activa o desactiva el scheduler. |
| `DEVICE_EVENT_SIMULATION_INITIAL_DELAY_MS` | `1000` | Espera antes del primer ciclo. |
| `DEVICE_EVENT_SIMULATION_INTERVAL_MS` | `1000` | Frecuencia fija entre ciclos. |

Para desactivar la simulación durante una ejecución:

```powershell
$env:DEVICE_EVENT_SIMULATION_ENABLED = "false"
```

## Eventos por tipo de máquina

Una máquina de venta en estado `ONLINE` puede producir:

- `TICKET_PURCHASE_REQUESTED`;
- `TICKET_PURCHASE_COMPLETED`;
- `QR_TICKET_GENERATED`.

Un validador de entrada o salida en estado `ONLINE` puede producir:

- `VALIDATION_ACCEPTED`;
- `VALIDATION_REJECTED`;
- `VALIDATION_REQUESTED`.

Antes de generar actividad ordinaria se sincronizan todas las máquinas con el horario:

| Estado del servicio | Evento generado cuando sea necesario | Estado resultante |
| --- | --- | --- |
| Abierto | `DEVICE_ONLINE` | `ONLINE` |
| Cerrado | `DEVICE_OFFLINE` | `OFFLINE` |

El simulador automático no genera `DEVICE_ERROR`, `TICKET_PURCHASE_FAILED`,
`VALIDATION_FAILED` ni transiciones de mantenimiento. Estos valores permanecen en el contrato para
eventos reales que puedan recibirse en el futuro mediante MQTT.

## Transiciones de estado

La política centralizada aplica las siguientes reglas:

| Eventos | Estado resultante |
| --- | --- |
| Conexión, fin de mantenimiento y actividad correcta | `ONLINE` |
| Validación rechazada | `ONLINE` |
| `DEVICE_OFFLINE` | `OFFLINE` |
| Inicio de mantenimiento | `MAINTENANCE` |
| Error de dispositivo, compra o validación | `ERROR` |
| `DEVICE_STATUS_CHANGED` sin estado explícito | Conserva el estado anterior |

Una validación rechazada no implica que el validador esté averiado. Por eso la máquina continúa
conectada.

Cada evento actualiza `last_connection_at` si su fecha es posterior a la conexión ya registrada. La
transición y el `INSERT` en `operational_logs` comparten una transacción: si la persistencia falla,
el estado tampoco se confirma.

## Persistencia

El modelo `DeviceEventLog` usa la tabla `operational_logs`. Para los eventos simulados:

- `log_origin` contiene `DEVICE_SIMULATION`;
- `device_id` identifica obligatoriamente la máquina emisora;
- `station_id` se obtiene de esa máquina;
- `event_type`, `severity` y `message` describen el suceso;
- `created_at` conserva el instante del evento;
- `received_at` registra su recepción por el backend;
- `payload_json` guarda contexto adicional;
- `external_reference` queda vacío porque el simulador no necesita idempotencia externa.

La estación solo representa la ubicación. No se considera emisora del evento.

## Integración con MQTT

El backend dispone de un cliente MQTT conectado a Mosquitto. Los mensajes autenticados de venta y
validación se convierten en `DeviceEventMessage` y atraviesan el mismo puerto `DeviceEventIngress`
que protege la validación, la transición de estado y el registro de logs.

El contrato actual usa la versión `1.0`:

```json
{
  "schemaVersion": "1.0",
  "eventId": "17dfd715-a55f-4e5f-b319-e254cb8436f8",
  "deviceCode": "TVM-ST001-01",
  "type": "DEVICE_ONLINE",
  "severity": "INFO",
  "message": "Máquina conectada",
  "occurredAt": "2026-07-23T08:30:00Z",
  "payload": {
    "temperature": 37
  }
}
```

| Campo | Obligatorio | Regla |
| --- | --- | --- |
| `schemaVersion` | Sí | Debe ser `1.0`; máximo 20 caracteres. |
| `eventId` | Sí | Identificador global del evento; máximo 150 caracteres. |
| `deviceCode` | Sí | Debe corresponder a una máquina activa; máximo 50 caracteres. |
| `type` | Sí | Valor de `DeviceEventType`. |
| `severity` | Sí | `DEBUG`, `INFO`, `WARNING`, `ERROR` o `CRITICAL`. |
| `message` | Sí | Texto no vacío de hasta 500 caracteres. |
| `occurredAt` | Sí | Instante ISO 8601 con referencia UTC. |
| `payload` | No | Objeto JSON con datos adicionales. |

El emisor no decide el origen del log. El adaptador lo establece siempre como `MQTT`.

### Idempotencia

MQTT puede volver a entregar un mensaje. Antes de registrar un evento, el puerto busca la combinación
de origen y `eventId`:

- si no existe, registra el evento y devuelve `ACCEPTED`;
- si ya existe, no repite la transición ni la persistencia y devuelve `DUPLICATE`.

El índice único `uk_operational_logs_origin_external_reference` refuerza esta regla en MySQL. Los
eventos simulados pueden conservar `external_reference` como `NULL`.

La conexión, autenticación, recepción idempotente, publicación de órdenes y recuperación después de
una desconexión se describen en [Integración MQTT del backend](integracion-mqtt-backend.md). La
estructura de los payloads y los topics se mantiene en el [contrato MQTT](contrato-mqtt.md).

No existen endpoints HTTP para crear eventos operativos manualmente.

La consulta del estado actual y del historial ya está disponible mediante las secciones web de
Máquinas y Logs. Su funcionamiento y los endpoints de lectura se documentan en
[Secciones de Máquinas y Logs](maquinas-y-logs.md).

## Pruebas

La suite de `service/deviceevent` cubre:

- generación según tipo y estado;
- selección de una sola máquina por ciclo;
- frecuencia fija predeterminada de 1000 ms;
- ausencia de máquinas activas;
- asociación entre log, máquina y estación;
- cambios de estado y última conexión;
- rechazo de máquinas desconocidas;
- validación y conversión del contrato MQTT;
- versiones incompatibles;
- reintentos idempotentes.

