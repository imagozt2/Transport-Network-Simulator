# Sección de Cocheras

La sección de Cocheras muestra la capacidad física de las instalaciones del Metro de Macegocia, su
ocupación en la instantánea ferroviaria y la distribución de la flota que tiene cada cochera como
base. También presenta las entradas y salidas generadas por el plan diario del motor de simulación.

La pantalla no modifica trenes ni crea movimientos manuales. Todos los datos operativos proceden de
`RailwaySimulationStateService`, la misma fuente utilizada por las secciones de Líneas, Estaciones y
Trenes.

## Acceso y actualización

Con el backend y el frontend en ejecución, la pantalla está disponible en:

```text
http://localhost:4200/depots
```

El frontend consulta el backend al abrir la sección y actualiza automáticamente la instantánea cada
15 segundos. Las peticiones no se solapan y la pantalla no expone controles manuales de
actualización.

Si una actualización falla, se conservan los últimos datos válidos. Si no se puede completar la
carga inicial, se muestra una acción para reintentarla.

## Capacidad, ocupación y flota asignada

Estos conceptos representan datos diferentes:

| Concepto | Significado |
| --- | --- |
| Capacidad | Número máximo de trenes que permite la configuración física de la cochera. Coincide con `trackCount × trainsPerTrack`. |
| Ocupación actual | Trenes cuyo estado simulado es `DEPOT` y cuya ubicación actual corresponde a la cochera. |
| Plazas disponibles | Diferencia entre capacidad y ocupación, con un mínimo de cero. |
| Flota asignada | Trenes activos cuya cochera base es la instalación, estén guardados o circulando. |
| Trenes en servicio | Parte de la flota asignada que tiene estado simulado `IN_SERVICE`. |

Por tanto, la flota asignada puede ser mayor que la ocupación. Cuando un tren abandona su cochera
para cubrir un turno, sigue perteneciendo a ella, pero deja de ocupar una plaza hasta que regresa.

La cochera base es actualmente también el lugar al que vuelve el tren. La simulación no transfiere
unidades entre cocheras distintas.

## Estados de ocupación

El backend calcula el estado exclusivamente a partir de la capacidad y la ocupación actual:

| Estado | Condición |
| --- | --- |
| `EMPTY` | No hay ningún tren presente. |
| `AVAILABLE` | Existe ocupación y permanece por debajo del 80 %. |
| `HIGH_OCCUPANCY` | La ocupación es al menos del 80 %, pero quedan plazas. |
| `FULL` | La ocupación coincide con la capacidad. |
| `OVER_CAPACITY` | La ocupación supera la capacidad configurada. Indica una incoherencia operativa. |

El porcentaje se redondea al entero más próximo. Una cochera sobreocupada devuelve cero plazas
disponibles, aunque el porcentaje pueda superar el 100 %.

## Presentación compacta y distribución operativa

Cada cochera se presenta mediante una tarjeta compacta. Su cabecera reúne la identidad, la estación,
la ocupación y el estado. Al desplegarla, la infraestructura y la distribución operativa comparten
una misma fila en pantallas amplias para reducir la altura ocupada.

El bloque de infraestructura muestra la estación, el número de vías, los trenes admitidos por vía y
la capacidad. El bloque de flota asignada agrupa las unidades por:

- función: servicio regular, reserva o histórica;
- serie ferroviaria;
- situación actual: en cochera o en servicio.

El botón **Ver trenes** abre `/trains` con el parámetro `depotCode` de la instalación. La sección de
Trenes recibe ese parámetro y activa automáticamente el filtro correspondiente.

La interfaz utiliza una escala neutral para las funciones de flota:

| Función | Presentación |
| --- | --- |
| `REGULAR_SERVICE` | Fondo gris claro y texto negro. |
| `RESERVE` | Fondo gris oscuro y texto blanco. |
| `HISTORIC` | Fondo negro y texto blanco. |

Los colores canónicos de `L1` a `L6` se reservan para movimientos asociados a una línea concreta.
Las cocheras se identifican mediante una sigla negra de dos caracteres, compartida con la sección
de Trenes.

## Entradas y salidas

Cada turno regular genera dos eventos:

1. `EXIT` en la hora de inicio planificada del turno;
2. `ENTRY` cuando el tren completa su servicio y puede regresar a la cochera base.

La entrada no se coloca simplemente en el instante en el que cambia una franja horaria. El motor
ajusta el final del turno para que el tren complete el recorrido necesario y regrese de forma
coherente.

Los movimientos tienen uno de estos estados:

| Estado | Significado |
| --- | --- |
| `SCHEDULED` | La hora programada es posterior a `evaluatedAt`. Incluye `secondsUntilMovement`. |
| `COMPLETED` | La hora programada ya se ha alcanzado. `secondsUntilMovement` es `null`. |

Una entrada o una salida se modela como un evento instantáneo, no como un intervalo. Por eso no
existe un estado intermedio `IN_PROGRESS`. La posición posterior del tren se refleja en su estado
simulado.

El endpoint devuelve todos los movimientos disponibles en la instantánea. El frontend construye dos
ventanas de doce horas alrededor de `evaluatedAt`:

- **Próximos movimientos**: eventos `SCHEDULED` comprendidos entre el instante evaluado y las doce
  horas posteriores, ordenados de menor a mayor fecha;
- **Movimientos recientes**: eventos `COMPLETED` comprendidos entre las doce horas anteriores y el
  instante evaluado, ordenados de mayor a menor fecha.

Los límites exactos de ambas ventanas están incluidos. Cada lista reserva visualmente espacio para
cinco movimientos y permite consultar el resto mediante desplazamiento vertical; los elementos
adicionales no se eliminan del resultado.

### Alcance temporal actual

Los movimientos son deterministas y se vuelven a calcular desde la configuración del día; no se
guardan como un historial de eventos en la base de datos. La instantánea solo contiene movimientos
de las líneas que tienen un plan de servicio abierto en el momento evaluado. Cuando la red está en
fase `CLOSED`, la lista puede estar vacía aunque existan operaciones del día anterior o del siguiente.
La persistencia de un historial operativo queda fuera del alcance de esta fase.

## Resumen y filtros

El resumen general incluye:

- número de cocheras activas;
- capacidad, ocupación y plazas libres de toda la red;
- porcentaje global de ocupación;
- flota asignada y trenes en servicio;
- próximas entradas y salidas comprendidas en la ventana futura de doce horas.

La búsqueda acepta nombre o código de cochera y nombre o código de su estación. También se puede
filtrar por estado de ocupación. Los filtros se aplican en el navegador sobre la última instantánea
recibida; el endpoint devuelve todas las cocheras activas.

## Endpoint operativo

```http
GET /api/depots/operations
```

La URL local completa es `http://localhost:8080/api/depots/operations`. No requiere cuerpo ni
parámetros de consulta.

Ejemplo con PowerShell:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/depots/operations" -Method Get
```

Una respuesta correcta devuelve `200 OK`. Este ejemplo está abreviado y sus recuentos representan
una instantánea ilustrativa:

```json
{
  "evaluatedAt": "2026-07-22T08:30:00+02:00",
  "phase": "OPERATING",
  "summary": {
    "depotCount": 12,
    "totalCapacity": 260,
    "occupiedSpaces": 180,
    "availableSpaces": 80,
    "occupancyPercentage": 69,
    "assignedFleet": 242,
    "trainsInService": 62,
    "movements": {
      "total": 124,
      "exits": 62,
      "entries": 62,
      "completed": 38,
      "scheduled": 86,
      "nextMovementAt": "2026-07-22T08:35:00+02:00"
    }
  },
  "depots": [
    {
      "id": 3,
      "code": "DEP-CC-A",
      "name": "Cochera de Cuatro Caminos - Sector A",
      "station": { "id": 43, "code": "ST043", "name": "Cuatro Caminos" },
      "capacity": 20,
      "trackCount": 4,
      "trainsPerTrack": 5,
      "occupiedSpaces": 12,
      "availableSpaces": 8,
      "occupancyPercentage": 60,
      "status": "AVAILABLE",
      "fleet": {
        "assignedTrainCount": 20,
        "assignedTrainsInService": 8,
        "byStatus": { "IN_SERVICE": 8, "DEPOT": 12 },
        "byRole": { "REGULAR_SERVICE": 19, "RESERVE": 1, "HISTORIC": 0 },
        "bySeries": { "9000": 19, "7000": 1 }
      },
      "movementsSummary": {
        "total": 16,
        "exits": 8,
        "entries": 8,
        "completed": 5,
        "scheduled": 11,
        "nextMovementAt": "2026-07-22T08:35:00+02:00"
      },
      "movements": [
        {
          "dutyNumber": 7,
          "type": "ENTRY",
          "status": "SCHEDULED",
          "scheduledAt": "2026-07-22T09:00:00+02:00",
          "secondsUntilMovement": 1800,
          "train": {
            "id": 90,
            "code": "RMM-L1-9000-CCA-001",
            "series": "9000",
            "fleetRole": "REGULAR_SERVICE"
          },
          "line": { "id": 1, "code": "L1", "name": "Línea 1", "color": "Roja" },
          "terminal": { "id": 45, "code": "ST045", "name": "Los Molinos" }
        }
      ]
    }
  ]
}
```

Los mapas `byStatus` y `byRole` contienen también las categorías con recuento cero.

## Contrato de respuesta

### Instantánea y resumen

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `evaluatedAt` | fecha y hora | Instante y zona horaria utilizados por el motor. |
| `phase` | texto | `CLOSED`, `STARTING`, `OPERATING` o `ENDING`. |
| `summary.depotCount` | número | Cocheras activas incluidas. |
| `summary.totalCapacity` | número | Suma de las capacidades. |
| `summary.occupiedSpaces` | número | Trenes físicamente presentes. |
| `summary.availableSpaces` | número | Plazas libres, sin valores negativos. |
| `summary.occupancyPercentage` | número | Porcentaje global redondeado. |
| `summary.assignedFleet` | número | Trenes activos con una cochera base incluida. |
| `summary.trainsInService` | número | Parte de esa flota que está circulando. |
| `summary.movements` | objeto | Agregado de entradas y salidas. |
| `depots` | lista | Cocheras activas ordenadas por código. |

### Cochera

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `id`, `code`, `name` | varios | Identidad de la instalación. |
| `station` | objeto | Estación física asociada. |
| `capacity` | número | Capacidad máxima configurada. |
| `trackCount` | número | Número de vías de estacionamiento. |
| `trainsPerTrack` | número | Unidades admitidas por vía. |
| `occupiedSpaces` | número | Trenes actualmente presentes. |
| `availableSpaces` | número | Plazas disponibles. |
| `occupancyPercentage` | número | Ocupación porcentual redondeada. |
| `status` | texto | Estado derivado de la ocupación. |
| `fleet` | objeto | Distribución de la flota cuya base es esta cochera. |
| `movementsSummary` | objeto | Recuento de movimientos de la instalación. |
| `movements` | lista | Entradas y salidas ordenadas cronológicamente. |

### Distribución de flota

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `assignedTrainCount` | número | Trenes activos con esta cochera base. |
| `assignedTrainsInService` | número | Trenes asignados actualmente en servicio. |
| `byStatus` | mapa | Recuento por estado simulado. |
| `byRole` | mapa | Recuento por función de flota. |
| `bySeries` | mapa | Recuento por serie ferroviaria. |

### Movimiento

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `dutyNumber` | número | Turno relacionado. |
| `type` | texto | `EXIT` o `ENTRY`. |
| `status` | texto | `SCHEDULED` o `COMPLETED`. |
| `scheduledAt` | fecha y hora | Instante programado del evento. |
| `secondsUntilMovement` | número o `null` | Cuenta atrás disponible solo para movimientos futuros. |
| `train` | objeto | Identidad, serie y función del tren. |
| `line` | objeto | Línea relacionada, incluido su color. |
| `terminal` | objeto | Terminal utilizada para incorporarse o regresar. |

El resumen de movimientos contiene `total`, `exits`, `entries`, `completed`, `scheduled` y
`nextMovementAt`. Este último es `null` cuando no quedan eventos pendientes.

## Componentes relacionados

- `DepotOperationsController` expone el endpoint REST.
- `DepotOperationsQueryService` calcula ocupación, distribución y presentación de movimientos.
- `RailwaySimulationStateService` proporciona trenes y movimientos de la instantánea actual.
- `TrainDutyPlanningService` genera las salidas y entradas de cada turno.
- `DepotOperationsService` realiza la petición desde Angular.
- `Depots` gestiona filtros, actualización, ventanas temporales, tarjetas desplegables y navegación
  contextual hacia Trenes.
- `depot-visuals.ts` centraliza las siglas utilizadas por Cocheras y Trenes.

La planificación de turnos y el cálculo horario se explican en
[Motor de simulación ferroviaria](motor-simulacion-ferroviaria.md). La ubicación individual de las
unidades está documentada en [Sección de Trenes](trenes.md).

## Verificación

Las pruebas automatizadas cubren:

- ocupación, disponibilidad, porcentaje y estado de la cochera;
- distribución por estado, función y serie;
- entradas y salidas completadas o programadas;
- segundos restantes, línea, terminal, turno y tren;
- serialización de `GET /api/depots/operations`;
- URL utilizada por el servicio Angular;
- representación de capacidad, distribución y movimientos;
- filtros combinados, limpieza y error de carga;
- navegación hacia Trenes con el código de cochera;
- límites inclusivos y orden de las ventanas de doce horas;
- conservación de más de cinco movimientos para su consulta mediante desplazamiento.

Para ejecutarlas:

```powershell
Set-Location backend
.\mvnw.cmd test

Set-Location ../frontend
npm test -- --watch=false
```
