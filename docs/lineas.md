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

La primera consulta se realiza al abrir la sección. Después, el frontend solicita una nueva
instantánea cada cinco segundos. La actualización automática puede pausarse y también existe un
botón para actualizar manualmente.

Las peticiones no se solapan. Si una actualización posterior falla, se conservan los últimos datos
válidos y se muestra un aviso. Si falla la carga inicial, la pantalla ofrece una acción para
reintentarla.

## Resumen operativo

La cabecera resume:

- líneas existentes y líneas con servicio abierto;
- trenes regulares de la serie 9000 actualmente en servicio;
- estaciones únicas de la red, sin contar dos veces las correspondencias;
- trenes en sentido de ida y vuelta;
- fase operativa y hora de la instantánea.

Cada línea se presenta mediante una tarjeta desplegable. Su cabecera indica terminales, número de
estaciones, trenes activos y estado del servicio. El contenido ampliado muestra:

- franja y frecuencia actuales;
- horario de apertura y cierre;
- duración estimada de un trayecto de extremo a extremo;
- distribución de trenes por sentido;
- termómetro completo de estaciones y correspondencias.

## Colores y termómetros

Las etiquetas, el trazado del termómetro y los nodos de estación utilizan los mismos colores
canónicos que el Mapa de red. La relación se basa en el código estable de línea (`L1` a `L6`) y no
en el nombre textual del color almacenado en la base de datos.

Las etiquetas calculan automáticamente un color de texto legible. Esto permite utilizar texto
oscuro en fondos claros, como la L3 amarilla, y texto blanco en las demás líneas. Una correspondencia
se muestra con el color de la línea a la que permite cambiar, no con el de la línea cuyo termómetro
se está consultando.

## Trenes sobre el recorrido

Los trenes activos se dibujan como puntos sobre el termómetro. El frontend no inventa su posición:
utiliza la estación anterior, la estación siguiente y `progressPercentage` recibidos del backend.

Para un tren entre estaciones se interpola su posición en el tramo:

```text
posición visual = estación anterior + (distancia hasta la siguiente × progreso / 100)
```

Cuando `positionState` es `AT_STATION`, el marcador se coloca exactamente en el nodo indicado por
`currentStationId`. Los sentidos `OUTBOUND` e `INBOUND` ocupan carriles laterales diferentes para
reducir el solapamiento. La transición entre dos consultas se anima durante el intervalo de
actualización, evitando saltos visuales bruscos.

Al pasar el cursor sobre un marcador se muestra el código del tren y su sentido. La misma
información se puede consultar enfocando el marcador con el teclado y está disponible mediante su
etiqueta accesible. Los marcadores son informativos y actualmente no desencadenan acciones.

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
| `stations` | lista | Estaciones activas en orden de recorrido. |
| `trains` | lista | Unidades en servicio en el instante evaluado. |

Una estación contiene `id`, `code`, `name` y `stationOrder`.

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

Fuera del horario de servicio, los campos dependientes del plan o de la franja pueden ser `null` y
`trains` se devuelve vacío. Las posiciones corresponden siempre a la misma instantánea que
`evaluatedAt`.

## Componentes relacionados

- `LineOperationsController` expone el endpoint REST.
- `LineOperationsQueryService` transforma el estado ferroviario en DTO y añade estaciones y datos
  persistidos de cada línea.
- `RailwaySimulationStateService` proporciona una instantánea coherente del motor.
- `LineOperationsService` realiza la consulta desde Angular.
- `Lines` calcula los totales de presentación y representa tarjetas, termómetros y trenes.
- `line-visuals.ts` contiene los colores canónicos y el cálculo de contraste.

La generación de turnos y posiciones se documenta en
[Motor de simulación ferroviaria](motor-simulacion-ferroviaria.md). La geometría y las interacciones
generales de la red se explican en [Mapa de red](mapa-red.md).

## Verificación

Las pruebas cubren:

- serialización y contrato HTTP de `GET /api/lines/operations`;
- URL y método utilizados por el servicio Angular;
- resumen de líneas, trenes, sentidos y estaciones únicas;
- colores, correspondencias y contenido del termómetro;
- posiciones entre estaciones y en una estación;
- límites del progreso, carriles por sentido e identificación accesible;
- estado de error y acción de reintento.
