# Secciones de Máquinas y Logs

Las secciones de Máquinas y Logs permiten supervisar los dispositivos instalados en la red y
consultar los eventos que generan automáticamente. Ambas pantallas están relacionadas, pero tienen
responsabilidades distintas:

- **Máquinas** muestra el inventario y el estado operativo actual de cada dispositivo.
- **Logs** conserva el historial paginado de eventos y permite investigarlo mediante filtros.

La pantalla de Máquinas no genera eventos manualmente ni muestra paneles de logs incrustados. Los
eventos proceden exclusivamente del ciclo automático descrito en
[Ciclo de eventos de las máquinas](eventos-maquinas.md) y, en el futuro, de la integración MQTT.

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
- máquinas de billetes, validadores de entrada y validadores de salida.

Las tarjetas forman una lista compacta y desplegable. Su cabecera presenta nombre, código, tipo,
estado y estación. Al desplegar una máquina aparecen su ubicación, la última conexión y la acción
**Ver logs**. El tipo y el estado no se repiten dentro del detalle.

Los filtros se aplican localmente sobre la instantánea recibida y pueden combinarse:

- búsqueda por código, nombre o estación;
- tipo de dispositivo;
- estado operativo;
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
`lastConnectionAt` y la estación asociada. El backend puede incluir además el último evento para
otros consumidores, pero la pantalla de Máquinas no lo representa: la consulta histórica se
centraliza en Logs.

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
- origen;
- referencia externa, cuando existe.

Los indicadores superiores resumen la página actual y el total devuelto por el backend. Los
recuentos de avisos y errores corresponden a los elementos visibles, mientras que el total procede
de la consulta paginada.

### Filtros

Se pueden combinar los siguientes criterios:

| Filtro | Parámetro de API y URL | Descripción |
| --- | --- | --- |
| Severidad | `severity` | `DEBUG`, `INFO`, `WARNING`, `ERROR` o `CRITICAL`. |
| Origen | `origin` | `DEVICE_SIMULATION` o `MQTT`. |
| Evento | `eventType` | Tipo concreto de evento de la máquina. |
| Tipo de máquina | `deviceType` | `TICKET_MACHINE`, `ENTRY_VALIDATOR` o `EXIT_VALIDATOR`. |
| Máquina | `deviceCode` | Código estable del dispositivo. |
| Estación | `stationCode` | Código estable de la estación. |
| Desde | `occurredFrom` | Inicio inclusivo del intervalo temporal. |
| Hasta | `occurredTo` | Final inclusivo del intervalo temporal. |

La pantalla lee estos parámetros al abrirse. Los valores enumerados desconocidos y las fechas con
formato incorrecto se ignoran, evitando que un enlace mal formado rompa la consulta.

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
los controles Primera, Anterior, Siguiente y Última, acompañados de hasta cinco páginas cercanas.
La ventana se centra alrededor de la página actual cuando es posible y se ajusta al inicio o al final
del resultado. La página activa se identifica visualmente y mediante `aria-current="page"`.

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
      "origin": "DEVICE_SIMULATION",
      "eventType": "DEVICE_STATUS_CHANGED",
      "severity": "WARNING",
      "message": "La máquina ha cambiado de estado",
      "deviceId": 10,
      "deviceCode": "RMM-MB-ST001-001",
      "deviceName": "Máquina de billetes 1",
      "stationId": 1,
      "stationCode": "ST001",
      "stationName": "Los Molinos",
      "externalReference": null,
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
Simulador automático / futuro cliente MQTT
                    │
                    ▼
       registro y persistencia del evento
                    │
          ┌─────────┴─────────┐
          ▼                   ▼
 estado actual de máquina   operational_logs
          │                   │
          ▼                   ▼
/api/devices/operations    /api/logs
          │                   │
          ▼                   ▼
      Máquinas ──────────► Logs filtrados
              Ver logs
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

Las pruebas de la generación, registro y persistencia de eventos se describen en
[Ciclo de eventos de las máquinas](eventos-maquinas.md).
