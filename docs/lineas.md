# Sección de Líneas

La sección de Líneas presenta una instantánea operativa de las seis líneas del Metro de Macegocia.
Reúne la configuración del servicio y el estado calculado por el motor ferroviario para mostrar
horarios, frecuencias, recorridos y trenes en circulación sin mantener una simulación duplicada en
el frontend.

## Acceso y actualización

Con el backend y el frontend en ejecución, la pantalla está disponible en:

```text
http://localhost:4200/lines
```

La primera consulta se realiza al abrir la sección. Después, el frontend solicita automáticamente
una nueva instantánea cada cinco segundos. La pantalla no incorpora controles manuales de
actualización: utiliza el mecanismo periódico compartido por las secciones operativas.

Las peticiones no se solapan. Si una actualización posterior falla, se conservan los últimos datos
válidos y se muestra un aviso. Si falla la carga inicial, la pantalla ofrece una acción para
reintentarla.

## Resumen operativo

La cabecera contiene cinco indicadores, ordenados de la siguiente forma:

1. líneas de la red;
2. estaciones únicas, sin contar dos veces las correspondencias;
3. trenes actualmente en servicio;
4. trenes en sentido de ida;
5. trenes en sentido de vuelta.

Cada indicador muestra únicamente su título y su valor principal.

Cada línea se presenta mediante una tarjeta desplegable. Su cabecera indica terminales, número de
estaciones, trenes activos y una única fase operativa. El contenido ampliado muestra:

- franja y frecuencia aproximada actuales;
- horario de apertura y cierre;
- duración estimada de un trayecto de extremo a extremo;
- distribución de trenes por sentido;
- cocheras que abastecen la línea, su terminal de salida y su distribución de flota;
- dos termómetros horizontales con los trenes en cada sentido;
- termómetro completo de estaciones y correspondencias.

El símbolo de ayuda situado junto a la franja explica la sucesión de periodos del servicio:
inicio progresivo, valle, punta, servicio regular y retirada progresiva. El prefijo `~` de la
frecuencia recuerda que el intervalo configurado es una referencia operativa, no una separación
visual exacta e invariable entre todos los trenes.

## Colores y termómetros

Las etiquetas, el trazado de los termómetros y los nodos de estación utilizan los mismos colores
canónicos que el Mapa de red. La relación se basa en el código estable de línea (`L1` a `L6`) y no
en el nombre textual del color almacenado en la base de datos.

Las etiquetas calculan automáticamente un color de texto legible. Esto permite utilizar texto
oscuro en fondos claros, como la L3 amarilla, y texto blanco en las demás líneas. En el termómetro
vertical, una correspondencia muestra las etiquetas de las otras líneas disponibles. En los
termómetros de circulación, su nodo tiene un contorno más grueso para distinguirlo de una parada
simple.

## Cocheras abastecedoras

La tarjeta de cocheras se construye con las asociaciones operativas de la línea, no con datos
calculados en el navegador. Por cada cochera muestra:

- nombre e indicador visual de cochera;
- terminal de la línea desde la que se despachan sus trenes;
- flota asignada;
- trenes de esa flota que están en servicio;
- trenes regulares disponibles en la cochera.

Si una línea no tiene cocheras operativas asociadas, la tarjeta muestra un estado vacío explícito.
Los recuentos corresponden a la misma instantánea `evaluatedAt` que las posiciones ferroviarias.

## Trenes sobre el recorrido

Los trenes activos se dibujan mediante marcadores circulares azules con la `M` blanca de la
identidad visual de la aplicación. Se distribuyen sobre dos termómetros horizontales, uno por
sentido. El frontend no inventa su posición:
utiliza la estación anterior, la estación siguiente y `progressPercentage` recibidos del backend.

Para un tren entre estaciones se interpola su posición en el tramo:

```text
posición visual = estación anterior + (distancia hasta la siguiente × progreso / 100)
```

Cuando `positionState` es `AT_STATION`, el marcador se coloca exactamente en el nodo indicado por
`currentStationId`. Los sentidos `OUTBOUND` e `INBOUND` ocupan termómetros separados para reducir
el solapamiento. La transición entre dos consultas se anima durante el intervalo de
actualización, evitando saltos visuales bruscos.

Al pasar el cursor sobre un marcador se muestra el código del tren y su sentido. La misma
información se puede consultar enfocando el marcador con el teclado y está disponible mediante su
etiqueta accesible. Los marcadores son informativos y actualmente no desencadenan acciones.

Cada nodo de estación también admite cursor y foco de teclado. Su etiqueta muestra la estación,
el terminal que define el sentido y el tiempo restante hasta el próximo tren en formato `mm:ss`.
La llegada se obtiene de `nextArrivals`; si no existe una estimación para esa estación y sentido,
se muestra `Sin próximas llegadas`.

## Endpoint operativo

```http
GET /api/lines/operations
```

La URL local completa es `http://localhost:8080/api/lines/operations`. No requiere cuerpo ni
parámetros de consulta.

Ejemplo con PowerShell:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/lines/operations" -Method Get
```

Una respuesta correcta devuelve `200 OK`. El siguiente ejemplo está abreviado:

```json
{
  "evaluatedAt": "2026-07-21T08:30:00+02:00",
  "phase": "OPERATING",
  "activeLineCount": 6,
  "lines": [
    {
      "id": 4,
      "code": "L4",
      "name": "Línea 4",
      "color": "Lila",
      "phase": "OPERATING",
      "serviceOpen": true,
      "serviceStartsAt": "2026-07-21T06:00:00+02:00",
      "serviceEndsAt": "2026-07-22T00:30:00+02:00",
      "currentPeriodCode": "PEAK_MORNING",
      "currentPeriodType": "PEAK",
      "headwaySeconds": 240,
      "estimatedOneWayDurationSeconds": 1200,
      "stationCount": 16,
      "firstTerminal": { "id": 1, "code": "ST001", "name": "Aeropuerto", "stationOrder": 1 },
      "lastTerminal": { "id": 31, "code": "ST031", "name": "Muralla Ibérica", "stationOrder": 16 },
      "activeTrainCount": 1,
      "depots": [
        {
          "id": 10,
          "code": "DEP-AIR-A",
          "name": "Cochera de Aeropuerto",
          "station": { "id": 1, "code": "ST001", "name": "Aeropuerto" },
          "dispatchTerminal": { "id": 31, "code": "ST031", "name": "Muralla Ibérica" },
          "dispatchPriority": 1,
          "dispatchEnabled": true,
          "receptionEnabled": true,
          "assignedTrainCount": 21,
          "trainsInService": 8,
          "availableTrainCount": 13
        }
      ],
      "nextArrivals": [
        {
          "stationId": 2,
          "stationCode": "ST002",
          "stationName": "HUB Industrial Norte",
          "trainId": 90,
          "trainCode": "T-9001",
          "trainSeries": "9000",
          "direction": "OUTBOUND",
          "destinationStationId": 31,
          "destinationStationCode": "ST031",
          "destinationStationName": "Muralla Ibérica",
          "stationsAway": 1,
          "secondsUntilArrival": 72,
          "estimatedArrivalAt": "2026-07-21T08:31:12+02:00",
          "atStation": false
        }
      ],
      "stations": [
        { "id": 1, "code": "ST001", "name": "Aeropuerto", "stationOrder": 1 }
      ],
      "trains": [
        {
          "id": 90,
          "code": "T-9001",
          "series": "9000",
          "dutyNumber": 1,
          "positionState": "BETWEEN_STATIONS",
          "direction": "OUTBOUND",
          "currentStationId": null,
          "currentStationCode": null,
          "previousStationId": 1,
          "previousStationCode": "ST001",
          "nextStationId": 2,
          "nextStationCode": "ST002",
          "progressPercentage": 40,
          "secondsUntilNextStation": 72,
          "estimatedArrivalAt": "2026-07-21T08:31:12+02:00"
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
| `phase` | texto | Fase global: `CLOSED`, `STARTING`, `OPERATING` o `ENDING`. |
| `activeLineCount` | número | Líneas con servicio abierto. |
| `lines` | lista | Resumen de todas las líneas calculadas. |

### Línea

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `id`, `code`, `name`, `color` | varios | Identidad y presentación de la línea. |
| `phase` | texto | Fase operativa particular de la línea. |
| `serviceOpen` | booleano | Indica si el servicio está abierto. |
| `serviceStartsAt`, `serviceEndsAt` | fecha y hora o `null` | Ventana del plan diario. |
| `currentPeriodCode` | texto o `null` | Código de la franja vigente. |
| `currentPeriodType` | texto o `null` | Tipo funcional de la franja. |
| `headwaySeconds` | número o `null` | Intervalo actual entre trenes. |
| `estimatedOneWayDurationSeconds` | número o `null` | Duración estimada de ida. |
| `stationCount` | número | Número de paradas activas. |
| `firstTerminal`, `lastTerminal` | objeto | Terminales del recorrido ordenado. |
| `activeTrainCount` | número | Trenes incluidos en `trains`. |
| `depots` | lista | Cocheras operativas que abastecen la línea. |
| `nextArrivals` | lista | Próxima llegada por estación y sentido cuando existe una estimación. |
| `stations` | lista | Estaciones activas en orden de recorrido. |
| `trains` | lista | Unidades en servicio en el instante evaluado. |

Una estación contiene `id`, `code`, `name` y `stationOrder`.

### Cochera de línea

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `id`, `code`, `name` | varios | Identidad de la cochera. |
| `station` | objeto | Estación en la que se encuentra la cochera. |
| `dispatchTerminal` | objeto | Terminal desde el que la cochera incorpora trenes al servicio. |
| `dispatchPriority` | número | Prioridad configurada para el despacho. |
| `dispatchEnabled` | booleano | Indica si puede despachar trenes. |
| `receptionEnabled` | booleano | Indica si puede recibir trenes retirados. |
| `assignedTrainCount` | número | Flota activa asignada a esa combinación de línea y cochera. |
| `trainsInService` | número | Unidades asignadas que circulan en la instantánea. |
| `availableTrainCount` | número | Unidades regulares de la serie 9000 disponibles en la cochera. |

Los objetos `station` y `dispatchTerminal` contienen `id`, `code` y `name`.

### Próxima llegada

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `stationId`, `stationCode`, `stationName` | varios | Estación para la que se calcula la llegada. |
| `trainId`, `trainCode`, `trainSeries` | varios | Tren que llegará primero. |
| `direction` | texto | Sentido `OUTBOUND` o `INBOUND`. |
| `destinationStationId`, `destinationStationCode`, `destinationStationName` | varios | Terminal que da nombre al sentido. |
| `stationsAway` | número | Número de estaciones que separan al tren de la parada consultada. |
| `secondsUntilArrival` | número | Cuenta atrás precisa utilizada para formar `mm:ss`. |
| `estimatedArrivalAt` | fecha y hora | Instante estimado de llegada. |
| `atStation` | booleano | Indica si el tren ya se encuentra detenido en la estación. |

El backend conserva solo la estimación más próxima para cada pareja estación-sentido. El frontend
vuelve a seleccionar el menor `secondsUntilArrival` de forma defensiva si recibe más de una.

### Tren

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `id`, `code`, `series` | varios | Identidad y serie de la unidad. |
| `dutyNumber` | número | Turno diario cubierto por el tren. |
| `positionState` | texto | `AT_STATION` o `BETWEEN_STATIONS`. |
| `direction` | texto | `OUTBOUND` o `INBOUND`. |
| `currentStationId`, `currentStationCode` | varios o `null` | Estación actual cuando el tren está detenido. |
| `previousStationId`, `previousStationCode` | varios | Inicio del tramo actual. |
| `nextStationId`, `nextStationCode` | varios | Próxima parada. |
| `progressPercentage` | número | Progreso entero dentro del tramo. |
| `secondsUntilNextStation` | número | Cuenta atrás sin redondear. |
| `estimatedArrivalAt` | fecha y hora | Llegada estimada a la próxima estación. |

Fuera del horario de servicio, los campos dependientes del plan o de la franja pueden ser `null`;
`trains` y `nextArrivals` se devuelven vacíos. Una línea sin asociaciones de cochera devuelve
`depots` vacío. Las posiciones, cocheras y llegadas corresponden siempre a la misma instantánea
`evaluatedAt`.

## Componentes relacionados

- `LineOperationsController` expone el endpoint REST.
- `LineOperationsQueryService` transforma el estado ferroviario en DTO y añade estaciones,
  cocheras y próximas llegadas.
- `RailwaySimulationStateService` proporciona una instantánea coherente del motor.
- `LineOperationsService` realiza la consulta desde Angular.
- `Lines` calcula los totales de presentación, selecciona la llegada más próxima y representa
  tarjetas, termómetros y trenes.
- `line-visuals.ts` contiene los colores canónicos y el cálculo de contraste.
- `temporal-formatters.ts` centraliza el formato `mm:ss` de las cuentas atrás.

La generación de turnos y posiciones se documenta en
[Motor de simulación ferroviaria](motor-simulacion-ferroviaria.md). La geometría y las interacciones
generales de la red se explican en [Mapa de red](mapa-red.md).

## Verificación

Las pruebas cubren:

- serialización y contrato HTTP de `GET /api/lines/operations`;
- URL y método utilizados por el servicio Angular;
- resumen de líneas, trenes, sentidos y estaciones únicas;
- frecuencia y franja vigentes;
- asociaciones de cochera, terminal de despacho y distribución de la flota;
- coherencia de las llegadas entre los resúmenes de líneas y estaciones;
- elección de la llegada más próxima y formato `mm:ss`;
- estados sin frecuencia, cocheras o llegadas;
- colores, correspondencias y contenido del termómetro;
- posiciones entre estaciones y en una estación;
- límites del progreso, termómetros por sentido e identificación accesible;
- estado de error y acción de reintento.
