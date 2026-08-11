# Secciones de Máquinas y Logs

Las secciones de Máquinas y Logs permiten supervisar los dispositivos instalados en la red y
consultar los eventos que generan automáticamente. Ambas pantallas están relacionadas, pero tienen
responsabilidades distintas:

- **Máquinas** muestra el inventario, el estado operativo y la conectividad MQTT actual de cada
  dispositivo.
- **Logs** conserva el historial paginado de eventos simulados, reales y administrativos, y permite
  investigarlo mediante filtros.

La pantalla de Máquinas no genera eventos manualmente ni muestra paneles de logs incrustados. Los
eventos proceden del ciclo automático descrito en
[Ciclo de eventos de las máquinas](eventos-maquinas.md), de las máquinas reales conectadas mediante
MQTT y de operaciones administrativas trazables, como las emisiones compensatorias.

## Tipos de máquinas

El sistema distingue tres tipos:

| Valor | Descripción | Identificador visual |
| --- | --- | --- |
| `TICKET_MACHINE` | Máquina destinada a la compra de billetes. | `MB` |
| `ENTRY_VALIDATOR` | Validador situado en el acceso a la estación. | `VE` |
| `EXIT_VALIDATOR` | Validador utilizado al abandonar la estación. | `VS` |

Cada máquina pertenece a una estación, pero no a una línea. Esta relación evita duplicar
dispositivos en estaciones con correspondencia entre varias líneas.

Los estados operativos son:

| Estado | Significado |
| --- | --- |
| `ONLINE` | La máquina está conectada y disponible. |
| `OFFLINE` | No existe conexión operativa. |
| `MAINTENANCE` | El dispositivo se encuentra en mantenimiento. |
| `ERROR` | Se ha registrado un fallo que requiere atención. |

El estado operativo no debe confundirse con la conectividad MQTT. Una máquina puede estar
operativamente `ONLINE` sin estar gestionada por MQTT, o conservar su último estado conocido después
de una desconexión. La conectividad se expresa por separado:

| Conectividad | Significado |
| --- | --- |
| `CONNECTED` | La máquina gestionada por MQTT mantiene presencia activa. |
| `DISCONNECTED` | La máquina está gestionada por MQTT, pero no tiene presencia activa. |
| `NOT_MONITORED` | La máquina no está configurada para supervisión MQTT real. |

## Pantalla de Máquinas

La ruta del frontend es:

```text
/devices
```

La sección solicita una instantánea nueva cada 15 segundos. Las peticiones no se solapan y no
existen controles manuales de actualización en su cabecera.

Los indicadores generales muestran únicamente títulos y valores numéricos:

- total de máquinas;
- máquinas `ONLINE`, `OFFLINE`, en mantenimiento y con error;
- máquinas de billetes, validadores de entrada y validadores de salida;
- máquinas conectadas por MQTT.

Las tarjetas forman una lista compacta y desplegable. Su cabecera presenta nombre, código, tipo,
estado operativo, conectividad y estación. Al desplegar una máquina aparecen su ubicación, la última
conexión operativa, la última comunicación MQTT, la versión de software disponible, el origen y la
fecha del último evento, además de la acción **Ver logs**.

Los filtros se aplican localmente sobre la instantánea recibida y pueden combinarse:

- búsqueda por código, nombre o estación;
- tipo de dispositivo;
- estado operativo;
- conectividad MQTT;
- estación.

La sección de Estaciones puede abrir esta pantalla mediante
`/devices?stationCode=ST045`. En ese caso el filtro de estación se inicializa antes de presentar las
tarjetas, de modo que solo aparecen las máquinas de la parada seleccionada.

### Acceso contextual a Logs

Cada tarjeta contiene la acción **Ver logs**. La navegación utiliza el código estable de la máquina:

```text
/logs?deviceCode=RMM-MB-ST001-001
```

El código se utiliza en lugar del identificador numérico para que el enlace sea legible, estable
entre instalaciones de la base de datos y adecuado para compartir o guardar.

También se puede acceder desde una tarjeta de estación mediante
`/logs?stationCode=ST045`. La pantalla de Logs conserva ese contexto seleccionando la estación en su
filtro y enviando `stationCode` en la primera consulta.

## Endpoint operativo de Máquinas

```http
GET /api/devices/operations
```

Parámetros opcionales disponibles en el backend:

| Parámetro | Tipo | Descripción |
| --- | --- | --- |
| `search` | texto | Busca por código o nombre de máquina y estación. |
| `type` | enumeración | Limita la consulta a un tipo de dispositivo. |
| `status` | enumeración | Limita la consulta a un estado operativo. |
| `stationCode` | texto | Selecciona una estación mediante su código estable. |

Ejemplo:

```http
GET /api/devices/operations?type=ENTRY_VALIDATOR&status=ONLINE&stationCode=ST001
```

La respuesta contiene:

| Campo | Descripción |
| --- | --- |
| `evaluatedAt` | Fecha y hora de la instantánea. |
| `summary.totalDevices` | Número total de máquinas activas. |
| `summary.filteredDevices` | Número de máquinas incluidas tras aplicar filtros del backend. |
| `summary.byType` | Recuento completo agrupado por tipo. |
| `summary.byStatus` | Recuento completo agrupado por estado. |
| `devices` | Máquinas que cumplen los filtros. |

Cada elemento de `devices` incluye su identificador, código, nombre, tipo, estado,
`lastConnectionAt`, la estación asociada, el último evento y el bloque `connectivity`. Este bloque
distingue presencia MQTT y estado operativo, e incluye cuando están disponibles
`lastCommunicationAt`, `lastPresenceAt`, `lastStatusAt`, `serviceMode`, `softwareVersion` y
`uptimeSeconds`.

Los mensajes MQTT retrasados se guardan en el historial, pero no pueden sustituir el estado ni la
última comunicación más recientes. De este modo, la vista actual y la trazabilidad histórica se
mantienen coherentes aunque el broker entregue mensajes fuera de orden.

## Pantalla global de Logs

La ruta del frontend es:

```text
/logs
```

La pantalla muestra los eventos ordenados desde el más reciente en una tabla inspirada en el
proyecto original. Cada fila presenta:

- severidad;
- tipo de evento;
- fecha y hora de ocurrencia;
- mensaje;
- código y nombre de la máquina;
- estación;
- procedencia funcional y canal de entrada;
- datos del billete o de la emisión, cuando existen;
- referencia externa, cuando existe;
- acceso contextual para crear una incidencia.

Los indicadores superiores resumen la página actual y el total devuelto por el backend. Los
recuentos de avisos y errores corresponden a los elementos visibles, mientras que el total procede
de la consulta paginada.

### Filtros

Se pueden combinar los siguientes criterios:

| Filtro | Parámetro de API y URL | Descripción |
| --- | --- | --- |
| Severidad | `severity` | `DEBUG`, `INFO`, `WARNING`, `ERROR` o `CRITICAL`. |
| Canal | `origin` | `DEVICE_SIMULATION`, `MQTT` o `ADMINISTRATION`. |
| Evento | `eventType` | Tipo concreto de evento de la máquina. |
| Tipo de máquina | `deviceType` | `TICKET_MACHINE`, `ENTRY_VALIDATOR` o `EXIT_VALIDATOR`. |
| Máquina | `deviceCode` | Código estable del dispositivo. |
| Estación | `stationCode` | Código estable de la estación. |
| Desde | `occurredFrom` | Inicio inclusivo del intervalo temporal. |
| Hasta | `occurredTo` | Final inclusivo del intervalo temporal. |

La pantalla lee estos parámetros al abrirse. Los valores enumerados desconocidos y las fechas con
formato incorrecto se ignoran, evitando que un enlace mal formado rompa la consulta.

### Procedencia y operaciones de billetes

La tabla diferencia dos conceptos que no deben confundirse:

- **Procedencia** usa `REAL`, `SIMULATED` o `ADMINISTRATIVE` para indicar si el evento pertenece a
  un dispositivo real, a la simulación o a una operación de un operador.
- **Canal** conserva el origen técnico persistido: `MQTT`, `DEVICE_SIMULATION` o
  `ADMINISTRATION`.

Los eventos de venta, emisión y validación se identifican visualmente y pueden incluir, cuando están
disponibles, el tipo y el código del billete, el código de una emisión compensatoria o una referencia
externa. Esto permite seguir una operación desde la máquina física o simulada hasta el registro
almacenado por el centro de control.

### Creación contextual de incidencias

Cada fila ofrece la acción **Crear incidencia**. La navegación abre `/incidents` con el formulario
de alta preparado a partir del evento: conserva la máquina afectada y utiliza el tipo de evento, el
billete, la emisión compensatoria y la referencia externa para completar el título y la descripción.

La relación estructurada de la incidencia se establece actualmente con la máquina. Las referencias
de billete y emisión se conservan como contexto textual hasta que el modelo de incidencias disponga
de relaciones específicas para esos recursos.

Ejemplo de enlace filtrado:

```text
/logs?deviceType=TICKET_MACHINE&deviceCode=RMM-MB-ST001-001&occurredFrom=2026-07-23T08:00
```

Los filtros temporales utilizan el formato empleado por `datetime-local`:

```text
AAAA-MM-DDTHH:mm
```

### Paginación

La pantalla permite solicitar 25, 50 o 100 resultados. Tanto encima como debajo de la tabla aparecen
los controles Primera, Anterior, Siguiente y Última junto con una paginación segmentada:

- al inicio se muestran las tres primeras y las tres últimas páginas;
- en una posición intermedia se muestran las dos primeras, la página anterior, la actual, la
  siguiente y las dos últimas;
- los saltos entre segmentos se representan mediante puntos suspensivos.

Los puntos suspensivos son interactivos. Al pulsarlos se abre un selector en el que se puede
introducir cualquier número comprendido entre `1` y `totalPages`. Esto permite saltar directamente
a páginas alejadas sin recorrerlas de una en una. La página activa se identifica visualmente y
mediante `aria-current="page"`.

Todos los controles quedan deshabilitados mientras se resuelve una petición. Aplicar filtros,
limpiarlos o cambiar el tamaño de página reinicia la consulta en la primera página.

El backend limita cualquier tamaño superior a 100 para evitar consultas excesivas. Los resultados
se ordenan por `occurredAt` descendente y, cuando varios eventos comparten el mismo instante, por
`id` descendente. Este segundo criterio mantiene estable la paginación.

## Endpoint de Logs

```http
GET /api/logs
```

Parámetros:

| Parámetro | Obligatorio | Valor predeterminado |
| --- | --- | --- |
| `page` | No | `0` |
| `size` | No | `25` |
| `origin` | No | Sin filtro |
| `severity` | No | Sin filtro |
| `eventType` | No | Sin filtro |
| `deviceType` | No | Sin filtro |
| `deviceCode` | No | Sin filtro |
| `stationCode` | No | Sin filtro |
| `occurredFrom` | No | Sin límite inferior |
| `occurredTo` | No | Sin límite superior |

Ejemplo:

```http
GET /api/logs?page=0&size=25&deviceType=ENTRY_VALIDATOR&severity=WARNING
```

La respuesta paginada tiene esta estructura:

```json
{
  "logs": [
    {
      "id": 150,
      "origin": "MQTT",
      "source": "REAL",
      "eventType": "VALIDATION_ACCEPTED",
      "severity": "INFO",
      "message": "Entrada validada correctamente",
      "deviceId": 10,
      "deviceCode": "RMM-MB-ST001-001",
      "deviceName": "Máquina de billetes 1",
      "stationId": 1,
      "stationCode": "ST001",
      "stationName": "Los Molinos",
      "ticketCode": "RMM-TKT-000150",
      "ticketType": null,
      "compensatoryIssuanceCode": null,
      "externalReference": "evt-machine-150",
      "occurredAt": "2026-07-23T11:59:55",
      "receivedAt": "2026-07-23T11:59:55"
    }
  ],
  "currentPage": 0,
  "pageSize": 25,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true,
  "empty": false
}
```

Si `occurredFrom` es posterior a `occurredTo`, el backend responde con `400 Bad Request`. Los códigos
de máquina y estación se comparan sin distinguir mayúsculas y minúsculas.

## Flujo de datos

```text
Simulador automático / máquinas MQTT / operaciones administrativas
                              │
                              ▼
       registro y persistencia del evento
                    │
          ┌─────────┴─────────┐
          ▼                   ▼
 estado y conectividad      operational_logs
          │                   │
          ▼                   ▼
/api/devices/operations    /api/logs
          │                   │
          ▼                   ▼
      Máquinas ──────────► Logs filtrados ──────────► Incidencias
              Ver logs                    Crear incidencia
```

La separación permite consultar el estado actual sin cargar todo el historial y paginar los eventos
sin duplicar información en cada tarjeta de máquina.

## Archivos principales

Backend:

- `DeviceOperationsController` y `DeviceOperationsQueryService`;
- `OperationalLogsController` y `OperationalLogsQueryService`;
- `DeviceEventLogRepository`;
- DTO de `deviceoperation` y `operationallog`.

Frontend:

- `features/devices/`;
- `features/logs/`;
- `DeviceOperationsService`;
- `OperationalLogsService`;
- modelos `device-operation` y `operational-log`.

## Pruebas

La cobertura incluye:

- creación del enlace contextual desde cada tarjeta;
- existencia de la ruta `/logs`;
- inicialización de filtros desde parámetros de URL;
- rechazo de enumeraciones y fechas incorrectas;
- conversión de paginación y filtros a parámetros HTTP;
- combinación y limpieza del filtro por tipo de máquina;
- normalización de página y tamaño máximo en el backend;
- navegación directa mediante páginas cercanas;
- rechazo de intervalos temporales invertidos antes de consultar el repositorio;
- representación de resultados en la pantalla de Logs.
- separación visual entre estado operativo y conectividad MQTT;
- conservación de la última comunicación ante mensajes reales retrasados;
- distinción entre eventos reales, simulados y administrativos;
- representación de ventas, emisiones, validaciones y referencias de billete;
- navegación contextual desde un log hasta el alta de una incidencia.

Las pruebas de la generación, registro y persistencia de eventos se describen en
[Ciclo de eventos de las máquinas](eventos-maquinas.md).
