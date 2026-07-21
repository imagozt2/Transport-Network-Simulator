# Panel General

El Panel General es la pantalla inicial del centro de control de la Red de Metro de Macegocia. Resume
el estado de la red, la flota, los dispositivos y las cocheras a partir de los datos persistidos en
MySQL.

## Acceso y funcionamiento

Con el backend y el frontend en ejecución, el panel está disponible en:

```text
http://localhost:4200/dashboard
```

Al abrir la pantalla, el frontend solicita un resumen agregado al backend. Mientras espera muestra un
estado de carga. Si la petición falla, presenta un mensaje de error y permite reintentarla. El botón
**Actualizar** vuelve a consultar el resumen sin recargar la aplicación.

El panel contiene:

- seis indicadores principales: estaciones, líneas, trenes, máquinas, cocheras y ocupación;
- el número de trenes agrupados por estado;
- los dispositivos agrupados por estado y por tipo;
- la ocupación total y el detalle de cada cochera;
- las líneas activas de la red y su color identificativo.

Los indicadores solo consideran registros activos. La ocupación de cocheras se redondea al entero más
cercano y se calcula como `trenes asignados / capacidad total * 100`. Cuando no existe capacidad, el
porcentaje es `0`.

## Endpoint de resumen

```http
GET /api/dashboard/summary
```

La URL local completa es `http://localhost:8080/api/dashboard/summary`. No requiere cuerpo ni
parámetros de consulta.

Ejemplo con PowerShell:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/dashboard/summary" -Method Get
```

Una respuesta correcta devuelve `200 OK` y un objeto con esta estructura. Los valores del ejemplo son
ilustrativos:

```json
{
  "network": {
    "activeStations": 50,
    "activeLines": 2
  },
  "fleet": {
    "activeTrains": 242,
    "byStatus": {
      "IN_SERVICE": 0,
      "DEPOT": 242,
      "MAINTENANCE": 0,
      "STOPPED": 0,
      "OUT_OF_SERVICE": 0
    }
  },
  "devices": {
    "activeDevices": 622,
    "byStatus": {
      "ONLINE": 0,
      "OFFLINE": 622,
      "MAINTENANCE": 0,
      "ERROR": 0
    },
    "byType": {
      "TICKET_MACHINE": 126,
      "ENTRY_VALIDATOR": 248,
      "EXIT_VALIDATOR": 248
    }
  },
  "depots": {
    "activeDepots": 2,
    "totalCapacity": 50,
    "assignedTrains": 47,
    "freeSlots": 3,
    "occupationPercentage": 94,
    "items": [
      {
        "id": 1,
        "code": "DEP-A",
        "name": "Cochera A",
        "capacity": 30,
        "assignedTrains": 29,
        "freeSlots": 1
      }
    ]
  },
  "lines": [
    {
      "id": 1,
      "code": "L1",
      "name": "Línea 1",
      "color": "Roja"
    }
  ]
}
```

`byStatus` representa exclusivamente la situación física u operativa. La pertenencia al servicio
regular, la reserva o la flota histórica se almacena mediante `fleetRole` en los trenes y no forma
parte de este resumen.

## Contrato de respuesta

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `network.activeStations` | número | Estaciones activas de la red. |
| `network.activeLines` | número | Líneas activas de la red. |
| `fleet.activeTrains` | número | Trenes activos registrados. |
| `fleet.byStatus` | objeto | Recuento de trenes para cada estado admitido. |
| `devices.activeDevices` | número | Dispositivos activos registrados. |
| `devices.byStatus` | objeto | Recuento de dispositivos para cada estado admitido. |
| `devices.byType` | objeto | Recuento de dispositivos para cada tipo admitido. |
| `depots.activeDepots` | número | Cocheras activas. |
| `depots.totalCapacity` | número | Suma de la capacidad de las cocheras activas. |
| `depots.assignedTrains` | número | Trenes asignados a esas cocheras. |
| `depots.freeSlots` | número | Plazas libres calculadas. |
| `depots.occupationPercentage` | número | Porcentaje de ocupación total redondeado. |
| `depots.items` | lista | Capacidad y ocupación de cada cochera activa. |
| `lines` | lista | Identidad y color de cada línea activa, ordenadas por código. |

### Valores enumerados

- Estados de tren: `IN_SERVICE`, `DEPOT`, `MAINTENANCE`, `STOPPED` y `OUT_OF_SERVICE`.
- Estados de dispositivo: `ONLINE`, `OFFLINE`, `MAINTENANCE` y `ERROR`.
- Tipos de dispositivo: `TICKET_MACHINE`, `ENTRY_VALIDATOR` y `EXIT_VALIDATOR`.

Los mapas `byStatus` y `byType` siempre incluyen todos sus valores enumerados. Cuando no hay registros
para uno de ellos, su recuento es `0`, lo que permite al frontend renderizar el panel sin completar
datos ausentes.

`RESERVE` y `HISTORIC` son valores de `fleetRole`, no de `TrainStatus`.

## Componentes relacionados

- Backend: `DashboardController` expone el endpoint y `DashboardQueryService` construye el resumen
  mediante consultas de solo lectura.
- Frontend: `DashboardService` realiza la petición y el componente `Dashboard` representa los datos.
- Base de datos: el endpoint consulta estaciones, líneas, trenes, dispositivos y cocheras; por ello, la
  base de datos debe estar inicializada siguiendo [`../database/README.md`](../database/README.md).
