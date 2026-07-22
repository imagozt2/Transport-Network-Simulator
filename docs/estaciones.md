# Sección de Estaciones

La sección de Estaciones muestra el servicio ferroviario y los dispositivos asociados a cada parada
del Metro de Macegocia. Todos los datos ferroviarios proceden de la misma instantánea del motor que
utiliza la sección de Líneas; el frontend no mantiene una simulación paralela ni genera llegadas
ficticias.

## Acceso y actualización

Con el backend y el frontend en ejecución, la pantalla está disponible en:

```text
http://localhost:4200/stations
```

El frontend obtiene una instantánea al abrir la página y vuelve a consultar el backend cada 15
segundos. La actualización automática se puede pausar y también existe una acción manual. Las
peticiones no se solapan.

Entre dos consultas, la cuenta atrás de cada llegada se actualiza localmente una vez por segundo.
Esto permite mostrar `mm:ss` sin realizar una petición HTTP cada segundo. Una respuesta nueva
sincroniza de nuevo los contadores. Si una actualización falla, se conservan los últimos datos
válidos; si falla la carga inicial, se ofrece una acción para reintentarla.

## Resumen y filtros

La cabecera muestra:

- estaciones totales y estaciones con alguna línea abierta;
- estaciones de transbordo;
- dispositivos activos registrados;
- dispositivos actualmente online.

La lista se puede filtrar por nombre o código, tipo de estación y estado operativo. Una estación se
considera de transbordo cuando pertenece a más de una línea activa en la infraestructura, aunque
alguna de ellas se encuentre temporalmente cerrada.

Cada tarjeta desplegable contiene:

- código, nombre, tipo y estado de la estación;
- disponibilidad de máquinas de venta y validadores;
- líneas que prestan servicio, terminales y trenes activos;
- posición de la estación dentro del termómetro de cada línea;
- próximas llegadas, sentido, terminal de destino y estaciones restantes.

`activeTrainCount` representa los trenes que circulan por las líneas que sirven la estación. No es
el número de trenes que están físicamente dentro de ella ni el número de llegadas mostradas.

## Estados operativos

Los estados se calculan en el backend con la siguiente prioridad:

| Estado | Condición |
| --- | --- |
| `CLOSED` | Ninguna línea de la estación tiene el servicio abierto. |
| `CRITICAL` | Existe al menos un dispositivo activo en estado `ERROR`. |
| `DEGRADED` | Existe algún dispositivo `OFFLINE` o en `MAINTENANCE`. |
| `NO_TRAINS` | Hay líneas abiertas, pero ninguna tiene trenes en servicio. |
| `NORMAL` | Hay servicio ferroviario y no se cumplen las condiciones anteriores. |

El estado de una estación no se calcula a partir de logs ni incidencias inventadas. Los dispositivos
proceden de la base de datos y el servicio ferroviario procede de `RailwaySimulationStateService`.

## Colores y termómetros

Las etiquetas y termómetros utilizan la configuración canónica compartida con el Mapa de red y la
sección de Líneas. La selección se realiza mediante el código estable `L1` a `L6`, por lo que valores
textuales como `Roja` o `Amarilla` no se interpretan directamente como CSS.

El marcador del termómetro se coloca mediante `stationOrder` y el número total de paradas observado
para la línea:

```text
posición = (orden - 1) / (número de estaciones - 1) × 100
```

Las etiquetas calculan automáticamente un color de texto con contraste suficiente. Por ejemplo, la
L3 utiliza texto oscuro sobre su fondo amarillo.

## Cálculo de próximas llegadas

El backend calcula cada llegada desde la posición simulada del tren en `evaluatedAt`:

1. toma la próxima estación y `secondsUntilNextStation`;
2. recorre las paradas en el sentido actual;
3. suma el tiempo de permanencia y el tiempo de viaje de cada tramo intermedio;
4. invierte el sentido cuando alcanza una terminal;
5. se detiene al encontrar la estación consultada.

Por tanto, una estación situada detrás del tren no se descarta: la estimación incluye el recorrido
hasta la terminal, el cambio de sentido y el regreso. Los 20 segundos de parada configurados se
suman en cada estación intermedia. Un tren que ya está detenido en la estación devuelve cero
segundos y `atStation: true`.

Para mantener una respuesta manejable se devuelven como máximo las dos próximas llegadas por línea
y sentido. Después se ordenan todas cronológicamente. Cada estimación conserva segundos enteros; el
backend no redondea a minutos.

## Formato `mm:ss`

El frontend transforma los segundos restantes sin perder precisión:

| Segundos | Presentación |
| ---: | --- |
| `45` | `0:45` |
| `81` | `1:21` |
| `725` | `12:05` |
| `0` con `atStation: true` | `En estación` |

El contador nunca presenta números negativos. Cuando llega a cero permanece en `0:00` hasta recibir
una nueva instantánea. El tiempo transcurrido se calcula desde la recepción de la respuesta, por lo
que no exige que el reloj del navegador esté perfectamente sincronizado con el servidor.

## Endpoint operativo

```http
GET /api/stations/operations
```

La URL local completa es `http://localhost:8080/api/stations/operations`. No requiere cuerpo ni
parámetros de consulta.

Ejemplo con PowerShell:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/stations/operations" -Method Get
```

Una respuesta correcta devuelve `200 OK`. Este ejemplo está abreviado:

```json
{
  "evaluatedAt": "2026-07-22T08:30:00+02:00",
  "phase": "OPERATING",
  "stationCount": 50,
  "activeStationCount": 50,
  "stations": [
    {
      "id": 2,
      "code": "STB",
      "name": "Estación B",
      "status": "NORMAL",
      "transferStation": false,
      "lineCount": 1,
      "activeLineCount": 1,
      "activeTrainCount": 4,
      "devices": {
        "total": 3,
        "ticketMachines": 1,
        "entryValidators": 1,
        "exitValidators": 1,
        "online": 3,
        "offline": 0,
        "maintenance": 0,
        "errors": 0
      },
      "lines": [
        {
          "id": 3,
          "code": "L3",
          "name": "Línea 3",
          "color": "Amarilla",
          "stationOrder": 2,
          "phase": "OPERATING",
          "serviceOpen": true,
          "activeTrainCount": 4,
          "firstTerminal": { "id": 1, "code": "STA", "name": "Estación A" },
          "lastTerminal": { "id": 3, "code": "STC", "name": "Estación C" }
        }
      ],
      "nextArrivals": [
        {
          "trainId": 90,
          "trainCode": "T-9001",
          "trainSeries": "9000",
          "lineId": 3,
          "lineCode": "L3",
          "lineName": "Línea 3",
          "lineColor": "Amarilla",
          "direction": "OUTBOUND",
          "destination": { "id": 3, "code": "STC", "name": "Estación C" },
          "stationsAway": 1,
          "secondsUntilArrival": 45,
          "estimatedArrivalAt": "2026-07-22T08:30:45+02:00",
          "atStation": false
        }
      ]
    }
  ]
}
```

## Contrato de respuesta

### Instantánea de red

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `evaluatedAt` | fecha y hora | Instante y zona horaria utilizados por el motor. |
| `phase` | texto | `CLOSED`, `STARTING`, `OPERATING` o `ENDING`. |
| `stationCount` | número | Estaciones activas incluidas en la respuesta. |
| `activeStationCount` | número | Estaciones con al menos una línea abierta. |
| `stations` | lista | Resúmenes operativos ordenados por nombre. |

### Estación

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `id`, `code`, `name` | varios | Identidad de la estación. |
| `status` | texto | Estado operativo calculado. |
| `transferStation` | booleano | Indica si pertenece a más de una línea. |
| `lineCount`, `activeLineCount` | número | Líneas totales y con servicio abierto. |
| `activeTrainCount` | número | Trenes activos en las líneas que la sirven. |
| `devices` | objeto | Recuento de dispositivos por tipo y estado. |
| `lines` | lista | Pertenencia, terminales y estado de cada línea. |
| `nextArrivals` | lista | Próximas llegadas ordenadas. |

### Llegada

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `trainId`, `trainCode`, `trainSeries` | varios | Identidad del tren regular. |
| `lineId`, `lineCode`, `lineName`, `lineColor` | varios | Línea que realiza el servicio. |
| `direction` | texto | `OUTBOUND` o `INBOUND` al llegar. |
| `destination` | objeto | Terminal hacia la que continúa el servicio. |
| `stationsAway` | número | Paradas que recorrerá antes de llegar. |
| `secondsUntilArrival` | número | Tiempo restante exacto en la instantánea. |
| `estimatedArrivalAt` | fecha y hora | Instante estimado de llegada. |
| `atStation` | booleano | Indica que el tren ya está detenido en la estación. |

## Componentes relacionados

- `StationOperationsController` expone el endpoint REST.
- `StationOperationsQueryService` agrupa estaciones, líneas, dispositivos y llegadas.
- `RailwaySimulationStateService` proporciona la instantánea ferroviaria coherente.
- `StationOperationsService` realiza la consulta desde Angular.
- `Stations` gestiona filtros, actualización y cuenta atrás.
- `line-visuals.ts` comparte colores y contraste con el resto de la red.

El origen de las posiciones y los tiempos se explica en
[Motor de simulación ferroviaria](motor-simulacion-ferroviaria.md). El detalle visual de los
recorridos se encuentra en [Sección de Líneas](lineas.md).

## Verificación

Las pruebas automatizadas cubren:

- serialización de `GET /api/stations/operations`;
- estados ferroviarios y de dispositivos;
- trenes detenidos y llegadas a la siguiente estación;
- paradas intermedias y cambios de sentido en terminales;
- precisión de segundos y hora estimada;
- URL utilizada por Angular;
- colores, termómetros, filtros y errores de carga;
- formato y descenso de la cuenta atrás `mm:ss`.

Para ejecutarlas:

```powershell
Set-Location backend
.\mvnw.cmd test

Set-Location ../frontend
npm test -- --watch=false
```
