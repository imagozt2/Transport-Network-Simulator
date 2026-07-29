# Sección de Trenes

La sección de Trenes permite consultar la flota completa del Metro de Macegocia y relacionar los
datos persistidos de cada unidad con su situación dentro de la simulación ferroviaria. La pantalla
incluye tanto los trenes que prestan servicio como los almacenados en cocheras, y diferencia la
flota regular, la reserva y el material histórico.

El frontend no calcula turnos ni asigna trenes a líneas. Toda la situación operativa procede de una
única instantánea generada por `RailwaySimulationStateService`.

## Acceso y actualización

Con el backend y el frontend en ejecución, la pantalla está disponible en:

```text
http://localhost:4200/trains
```

La primera consulta se realiza al abrir la sección. Después, el frontend solicita automáticamente
una instantánea nueva cada 15 segundos. Las peticiones no se solapan y la pantalla no expone
controles manuales de actualización.

La cuenta atrás hacia la próxima estación avanza localmente cada segundo y se sincroniza con cada
respuesta del backend. De esta forma se presenta precisión `mm:ss` sin realizar una petición HTTP
por segundo. Si falla una actualización se conservan los últimos datos válidos; si falla la carga
inicial se ofrece una acción para reintentarla.

## Resumen y filtros

La cabecera muestra:

- unidades activas registradas;
- trenes actualmente en servicio;
- trenes almacenados en cocheras;
- unidades de servicio regular;
- unidades de reserva;
- material histórico.

La lista admite filtros combinables por:

- código, fabricante, modelo o serie mediante búsqueda de texto;
- estado operativo;
- línea asignada;
- cochera base o cochera en la que se encuentra actualmente la unidad;
- serie;
- función dentro de la flota.

Los filtros se aplican en el navegador sobre la última instantánea recibida. El endpoint devuelve la
flota activa completa y actualmente no dispone de parámetros de consulta.

El filtro de cochera también se puede inicializar mediante `depotCode`:

```text
http://localhost:4200/trains?depotCode=DEP-CC-A
```

Esta navegación se utiliza desde el botón **Ver trenes** de cada cochera. El filtro incluye tanto
las unidades que tienen esa instalación como cochera base como las que se encuentran físicamente
en ella en la instantánea actual.

## Función de la flota y estado operativo

La función es una propiedad estable de la unidad y no debe confundirse con su estado actual:

| Función | Presentación | Uso |
| --- | --- | --- |
| `REGULAR_SERVICE` | Gris claro con texto negro | Flota principal destinada al servicio diario. Solo los trenes de la serie 9000 pueden circular en el servicio regular simulado. |
| `RESERVE` | Gris oscuro con texto blanco | Unidades disponibles para refuerzos o sustituciones. No se incorporan automáticamente al servicio regular actual. |
| `HISTORIC` | Negro con texto blanco | Material clásico preservado, sin asignación a la circulación regular. |

El estado describe la situación de la unidad en la instantánea:

| Estado | Significado |
| --- | --- |
| `IN_SERVICE` | El tren está cubriendo un turno y dispone de una posición sobre su línea. |
| `DEPOT` | El tren se encuentra en su cochera actual. |
| `MAINTENANCE` | La unidad está apartada para mantenimiento. |
| `STOPPED` | La unidad se encuentra detenida fuera del funcionamiento normal. |
| `OUT_OF_SERVICE` | La unidad no está disponible para circular. |

Una unidad puede pertenecer a la flota regular y estar en cocheras. Del mismo modo, que un tren esté
asignado estructuralmente a una línea no significa que esté circulando por ella en ese momento.

## Situación en tiempo real

Las tarjetas utilizan una cabecera compacta con la identidad, el estado, la función de flota y un
icono que representa la ubicación actual. Si el tren circula, se muestra el código y color de su
línea actual; si está guardado, se muestra una sigla negra de dos caracteres para su cochera.
El icono no representa necesariamente la línea estructuralmente asignada.

Al desplegar una tarjeta se presentan los datos técnicos, la línea asignada, la cochera base y la
situación operativa. Para un tren en servicio se muestran:

- número de turno;
- línea actual y terminal de destino;
- sentido `OUTBOUND` o `INBOUND`;
- estación actual cuando `positionState` es `AT_STATION`;
- estaciones anterior y siguiente cuando se encuentra entre paradas;
- porcentaje de progreso dentro del tramo;
- próxima estación y tiempo restante en formato `mm:ss`.

La barra de progreso representa únicamente el tramo actual. No es un termómetro de la línea
completa. Debajo aparecen dos bloques verticales diferenciados para la próxima estación y la llegada
en formato `mm:ss`. Cuando el tren está parado en una estación, la pantalla lo indica explícitamente
y mantiene la información de su próxima parada según el estado calculado por el motor.

Para un tren en estado `DEPOT`, `serviceLocation` es `null` y se muestra `currentDepot`. Estas dos
ubicaciones son excluyentes: una unidad en circulación no puede estar simultáneamente en cocheras.

## Endpoint operativo

```http
GET /api/trains/operations
```

La URL local completa es `http://localhost:8080/api/trains/operations`. No requiere cuerpo ni
parámetros de consulta.

Ejemplo con PowerShell:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/trains/operations" -Method Get
```

Una respuesta correcta devuelve `200 OK`. El siguiente ejemplo está abreviado; los recuentos de
servicio representan una instantánea ilustrativa y varían según la hora evaluada:

```json
{
  "evaluatedAt": "2026-07-22T08:30:00+02:00",
  "phase": "OPERATING",
  "summary": {
    "activeFleet": 242,
    "trainsInService": 14,
    "trainsInDepots": 228,
    "byStatus": { "IN_SERVICE": 14, "DEPOT": 228, "MAINTENANCE": 0, "STOPPED": 0, "OUT_OF_SERVICE": 0 },
    "byRole": { "REGULAR_SERVICE": 230, "RESERVE": 5, "HISTORIC": 7 },
    "bySeries": { "9000": 230, "7000": 5, "6000": 5, "3000 Histórica": 2 }
  },
  "trains": [
    {
      "id": 90,
      "code": "RMM-L1-9000-CCA-001",
      "manufacturer": "Alstom",
      "model": "Metropolis",
      "series": "9000",
      "carCount": 5,
      "passengerCapacity": 760,
      "maximumSpeedKmh": 100,
      "fleetRole": "REGULAR_SERVICE",
      "status": "IN_SERVICE",
      "dispatchOrder": 1,
      "assignedLine": { "id": 1, "code": "L1", "name": "Línea 1", "color": "Roja" },
      "homeDepot": {
        "id": 2,
        "code": "DEP-CC-A",
        "name": "Cochera de Cuatro Caminos - Sector A",
        "stationId": 43,
        "stationCode": "ST043",
        "stationName": "Cuatro Caminos"
      },
      "currentDepot": null,
      "serviceLocation": {
        "currentLine": { "id": 1, "code": "L1", "name": "Línea 1", "color": "Roja" },
        "dutyNumber": 1,
        "positionState": "BETWEEN_STATIONS",
        "direction": "OUTBOUND",
        "destination": { "id": 45, "code": "ST045", "name": "Los Molinos" },
        "currentStation": null,
        "previousStation": { "id": 43, "code": "ST043", "name": "Cuatro Caminos" },
        "nextStation": { "id": 45, "code": "ST045", "name": "Los Molinos" },
        "progressPercentage": 40,
        "secondsUntilNextStation": 65,
        "estimatedArrivalAt": "2026-07-22T08:31:05+02:00"
      }
    }
  ]
}
```

Los mapas `byStatus` y `byRole` contienen también las categorías cuyo recuento es cero.

## Contrato de respuesta

### Instantánea y resumen

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `evaluatedAt` | fecha y hora | Instante y zona horaria utilizados por el motor. |
| `phase` | texto | `CLOSED`, `STARTING`, `OPERATING` o `ENDING`. |
| `summary.activeFleet` | número | Unidades activas incluidas en la respuesta. |
| `summary.trainsInService` | número | Unidades con estado `IN_SERVICE`. |
| `summary.trainsInDepots` | número | Unidades con estado `DEPOT`. |
| `summary.byStatus` | mapa | Recuento de unidades por estado operativo. |
| `summary.byRole` | mapa | Recuento por función de flota. |
| `summary.bySeries` | mapa | Recuento por serie ferroviaria. |
| `trains` | lista | Unidades activas ordenadas por código. |

### Tren

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `id`, `code` | varios | Identidad estable de la unidad. |
| `manufacturer`, `model`, `series` | texto | Modelo ferroviario persistido. |
| `carCount` | número | Número de coches de la composición. |
| `passengerCapacity` | número | Capacidad máxima registrada. |
| `maximumSpeedKmh` | número | Velocidad máxima técnica en km/h. |
| `fleetRole` | texto | Función estable dentro de la flota. |
| `status` | texto | Estado operativo calculado. |
| `dispatchOrder` | número o `null` | Prioridad configurada para la salida de cocheras. |
| `assignedLine` | objeto | Línea estructuralmente asignada. |
| `homeDepot` | objeto | Cochera base configurada. |
| `currentDepot` | objeto o `null` | Cochera actual cuando el estado es `DEPOT`. |
| `serviceLocation` | objeto o `null` | Posición ferroviaria cuando el estado es `IN_SERVICE`. |

Los objetos de línea contienen `id`, `code`, `name` y `color`. Los de cochera incorporan además la
identidad de la estación asociada.

### Ubicación de servicio

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `currentLine` | objeto | Línea por la que circula realmente el tren. |
| `dutyNumber` | número | Turno diario cubierto por la unidad. |
| `positionState` | texto | `AT_STATION` o `BETWEEN_STATIONS`. |
| `direction` | texto | `OUTBOUND` o `INBOUND`. |
| `destination` | objeto | Terminal hacia la que circula. |
| `currentStation` | objeto o `null` | Estación ocupada cuando está parado. |
| `previousStation`, `nextStation` | objeto | Extremos del tramo y próxima parada. |
| `progressPercentage` | número | Progreso entero dentro del tramo, entre 0 y 100. |
| `secondsUntilNextStation` | número | Tiempo restante exacto en la instantánea. |
| `estimatedArrivalAt` | fecha y hora | Instante estimado de llegada. |

## Componentes relacionados

- `TrainOperationsController` expone el endpoint REST.
- `TrainOperationsQueryService` combina entidades persistidas y estado ferroviario simulado.
- `RailwaySimulationStateService` proporciona la instantánea coherente de red.
- `TrainOperationsService` realiza la consulta desde Angular.
- `Trains` gestiona actualización, filtros, navegación contextual, tarjetas y cuenta atrás.
- `line-visuals.ts` aplica los colores canónicos de línea.
- `depot-visuals.ts` centraliza las siglas de cochera compartidas con la sección de Cocheras.

La generación de turnos, posiciones y movimientos de cocheras se describe en
[Motor de simulación ferroviaria](motor-simulacion-ferroviaria.md). La representación completa de
los recorridos se documenta en [Sección de Líneas](lineas.md).

## Verificación

Las pruebas automatizadas cubren:

- resumen por estado, función y serie;
- trenes en servicio y almacenados en cocheras;
- siguiente estación, destino y cuenta atrás;
- URL y método utilizados por Angular;
- clasificación visual de flota regular, reserva e histórica;
- combinación y limpieza de filtros;
- inicialización del filtro de cochera desde la URL;
- inclusión de trenes asignados o presentes en la cochera seleccionada;
- error de carga y acción de reintento.

Para ejecutarlas:

```powershell
Set-Location backend
.\mvnw.cmd test

Set-Location ../frontend
npm test -- --watch=false
```
