# Motor de simulación ferroviaria

El motor reconstruye el estado operativo de la red de Macegocia para una fecha y hora concretas. A
partir de calendarios, frecuencias, recorridos, cocheras y flota persistidos calcula los turnos del
día, los trenes que los cubren, su posición y los movimientos de entrada y salida de las cocheras.

La simulación es determinista y no depende del momento en que se inicia Spring Boot. Consultar dos
veces el mismo instante con la misma configuración produce el mismo resultado.

## Fuentes de datos

El motor no mantiene una segunda representación de la red. Utiliza:

- `service_calendars` para la jornada y su ventana de apertura;
- `service_periods` y `line_service_levels` para las franjas y frecuencias;
- `line_stations` para el orden de las paradas y los tiempos;
- `line_depots` para los permisos de expedición y recepción;
- `trains` y `train_models` para seleccionar unidades reales de la flota.

La estructura y las reglas de estas tablas están descritas en
[Modelo de operación ferroviaria](modelo-operacion-ferroviaria.md).

## Flujo de cálculo

```text
fecha y hora solicitadas
          │
          ▼
estado operativo y fecha de explotación
          │
          ▼
frecuencias y flota necesaria por franja
          │
          ▼
turnos y unidades 9000 asignadas
          │
          ├──► posiciones sobre los recorridos
          └──► salidas y entradas de cocheras
                         │
                         ▼
            estado ferroviario centralizado
```

Todos los pasos emplean la zona de `Clock` configurada mediante `SERVICE_TIME_ZONE`. El valor
predeterminado es `Europe/Madrid`.

## Jornada y estado operativo

`ServiceOperationStateService` resuelve si cada línea está abierta en el instante solicitado. Los
servicios que terminan después de medianoche conservan la fecha de explotación del día anterior.

La red y las líneas utilizan estas fases:

| Fase | Significado |
| --- | --- |
| `CLOSED` | No existe una configuración de servicio aplicable. |
| `STARTING` | Se están incorporando trenes al inicio de la jornada. |
| `OPERATING` | El servicio está funcionando con normalidad. |
| `ENDING` | Se están retirando trenes al final de la jornada. |

La hora final de un calendario es exclusiva. Por ejemplo, si el servicio termina a las 00:30, a las
00:30 ya se considera cerrado.

## Duración del recorrido y flota objetivo

La duración de una vuelta completa se obtiene en segundos:

```text
ida y vuelta = 2 × tiempos de tramo + 2 × paradas intermedias + paradas en ambos terminales
```

Para cada franja, la flota objetivo se calcula redondeando hacia arriba:

```text
trenes necesarios = techo(duración de ida y vuelta / intervalo de paso)
```

Un intervalo menor aumenta la frecuencia y, por tanto, puede aumentar la flota necesaria. Cuando
cambia la franja, el planificador compara el nuevo objetivo con los turnos existentes:

- si aumenta, crea turnos adicionales desde el inicio de la nueva franja;
- si disminuye, solicita la retirada de los turnos con número más alto;
- si no cambia, conserva la planificación existente.

## Turnos y salidas

`TrainDutyPlanningService` genera un plan diario por línea. Las salidas se forman por parejas desde
ambos terminales: los dos trenes comienzan simultáneamente en sentidos opuestos. El intervalo
operativo se obtiene dividiendo la vuelta completa entre la flota objetivo, evitando un hueco residual
cuando la duración no es múltiplo exacto de la frecuencia configurada. Si una franja necesita ampliar
la flota, las nuevas parejas se incorporan a mitad del primer intervalo para ocupar huecos.

Cada turno contiene:

- número estable dentro del plan diario;
- tren asignado y cochera de origen;
- sentido y estación terminal iniciales;
- franja y frecuencia con las que comienza;
- hora de inicio;
- hora de retirada solicitada;
- hora efectiva de regreso a cochera.

Los planes se regeneran a partir de los datos. No se almacenan como filas adicionales en MySQL.

## Selección de trenes

El servicio ordinario está limitado a unidades que cumplan simultáneamente estas condiciones:

- serie `9000`;
- función `REGULAR_SERVICE`;
- tren y modelo activos;
- línea asignada coincidente;
- cochera activa y habilitada para expedición y recepción.

La selección respeta `dispatch_order` y la estación terminal de la cochera. Las unidades de reserva
serie 7000 y los trenes históricos nunca sustituyen silenciosamente a un 9000. Si falta flota
elegible en un terminal, se lanza `ServiceConfigurationException`.

Una unidad puede cubrir otro turno únicamente después de completar su entrada anterior en cochera.

## Posición sobre el recorrido

Solo se calcula posición para los turnos activos en el instante evaluado. El movimiento forma un
ciclo compuesto por la ida, el cambio de sentido y la vuelta. El desplazamiento dentro del ciclo se
obtiene a partir de los segundos transcurridos desde el inicio del turno:

```text
posición en el ciclo = segundos transcurridos módulo duración de ida y vuelta
```

El resultado distingue:

| Estado | Información disponible |
| --- | --- |
| `AT_STATION` | Estación actual, siguiente estación, sentido y tiempo hasta la próxima llegada. |
| `BETWEEN_STATIONS` | Estación anterior, próxima estación, sentido, progreso y tiempo restante. |

`SimulatedTrainPosition` expresa la cuenta atrás en segundos y también proporciona
`estimatedArrivalAt`. Las interfaces deben formatear los segundos como `mm:ss`; el backend conserva
la precisión y no redondea a minutos.

El progreso de un tramo se mantiene entre 0 y 99 mientras el tren circula. La llegada se representa
como una parada en la estación siguiente, evitando mostrar simultáneamente un tren llegado y todavía
en tránsito.

## Cocheras

Cada turno produce dos `PlannedDepotMovement`:

| Movimiento | Momento |
| --- | --- |
| `EXIT` | Inicio planificado del turno. |
| `ENTRY` | Regreso efectivo a la cochera de origen. |

No existe actualmente un tiempo de enlace independiente entre una cochera y su estación, porque las
cocheras están asociadas directamente a terminales. Por ello, la salida coincide con el inicio del
turno.

Una solicitud de retirada no hace desaparecer el tren inmediatamente. El motor calcula el siguiente
regreso completo a su terminal de origen y mantiene el turno activo hasta ese momento. El modelo
distingue `requestedReleaseAt` de `plannedReleaseAt` para conservar ambas horas.

## Estado centralizado

Los módulos de Líneas, Estaciones, Trenes y Cocheras consumen
`RailwaySimulationStateService`, que es la fachada del motor:

```java
RailwaySimulationState getCurrentState()
RailwaySimulationState getStateAt(ZonedDateTime requestedDateTime)
```

La primera operación utiliza el reloj compartido. La segunda permite reproducir un instante en
pruebas o herramientas internas. Los servicios de consulta no coordinan manualmente
`ServiceOperationStateService` y `TrainDutyPlanningService`.

`RailwaySimulationState` reúne una instantánea coherente:

- fase de la red y número de líneas activas;
- estado operativo y plan de cada línea;
- todos los trenes activos, tanto en servicio como en cochera;
- posición, dirección y próxima llegada de las unidades en circulación;
- movimientos de cochera ordenados cronológicamente.

La fachada valida que un tren no tenga dos posiciones simultáneas, que una línea abierta tenga plan y
que solo los 9000 regulares aparezcan en servicio.

El motor no expone un endpoint REST que devuelva directamente su modelo interno. Las APIs de
Líneas, Estaciones, Trenes y Cocheras transforman la instantánea en DTO específicos:

| Endpoint | Vista derivada |
| --- | --- |
| `/api/lines/operations` | Frecuencia, recorrido y posiciones por línea. |
| `/api/stations/operations` | Estado de estación y próximas llegadas. |
| `/api/trains/operations` | Inventario y ubicación individual de cada tren. |
| `/api/depots/operations` | Ocupación, distribución y movimientos de cocheras. |

Las correspondencias entre estas respuestas están documentadas en
[Operación simulada](operacion-simulada.md).

## Persistencia y límites actuales

La simulación es calculada y de solo lectura:

- no actualiza periódicamente las columnas de posición de `trains`;
- no cambia el estado persistido de las unidades;
- no genera logs ferroviarios;
- no simula incidencias, mantenimiento ni sustituciones con la reserva;
- no contiene una API pública para consultar la instantánea interna completa.

Estas decisiones evitan que la simulación dependa de un proceso iniciado continuamente. La
persistencia de eventos reales procedentes de máquinas MQTT tendrá un ciclo de vida independiente.

## Pruebas

`ServiceOperationStateServiceTests` cubre horarios cerrados y jornadas que continúan después de
medianoche. `TrainDutyPlanningServiceTests` cubre frecuencias, flota objetivo, salidas escalonadas,
recorridos, cambios de sentido, progreso, llegadas y movimientos de cochera.
`ServiceDayIntegrationTests` reproduce momentos representativos de una jornada completa y
`OperationalSectionsConsistencyTests` comprueba que Líneas, Estaciones, Trenes y Cocheras describan
la misma instantánea.

Para ejecutar las pruebas unitarias del motor:

```powershell
Set-Location backend
.\mvnw.cmd test "-Dtest=ServiceOperationStateServiceTests,TrainDutyPlanningServiceTests,ServiceDayIntegrationTests,OperationalSectionsConsistencyTests"
```

La prueba de contexto completa necesita una instancia de MySQL y las variables `DB_USERNAME` y
`DB_PASSWORD`.
