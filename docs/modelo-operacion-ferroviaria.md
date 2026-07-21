# Modelo de operación ferroviaria

Este documento describe la configuración persistente sobre la que se construye la simulación del
servicio ferroviario de Macegocia. El modelo define horarios, frecuencias, recorridos, cocheras y
clasificación de la flota. Su transformación en turnos, posiciones y movimientos está descrita en
[Motor de simulación ferroviaria](motor-simulacion-ferroviaria.md).

## Principios del modelo

- La topología de una línea tiene una única fuente de verdad: `line_stations`.
- Los horarios comunes no se repiten para cada línea.
- El estado físico de un tren no se confunde con su función dentro de la flota.
- La operación se resuelve a partir de la fecha y hora actual, no del arranque del backend.

```text
service_calendars ──< service_periods ──< line_service_levels >── transport_lines
                                                                      │
                                                                      ├──< line_stations
                                                                      └──< line_depots >── depots

train_models ──< trains >── transport_lines
                    └── depots
```

## Calendarios de servicio

`service_calendars` contiene el tipo de jornada, las horas de apertura y cierre, su periodo de
vigencia y su estado activo.

| Valor | Aplicación actual |
| --- | --- |
| `WEEKDAY` | De lunes a viernes. |
| `SATURDAY` | Sábados. |
| `SUNDAY_HOLIDAY` | Domingos y, en el futuro, festivos configurados expresamente. |

Los datos iniciales definen:

| Calendario | Inicio | Finalización |
| --- | ---: | ---: |
| Laborable | 05:00 | 00:30 del día siguiente |
| Sábado | 06:00 | 01:00 del día siguiente |
| Domingo/festivo | 06:30 | 00:30 del día siguiente |

La aplicación automática de `SUNDAY_HOLIDAY` está limitada actualmente a los domingos. Las fechas
festivas concretas requerirán un futuro calendario de excepciones.

### Fecha de explotación

La fecha civil y la fecha de explotación no siempre coinciden. Si el servicio del viernes termina a
las 00:30, el sábado a las 00:15 todavía pertenece a la jornada ferroviaria del viernes.

`ServiceConfigurationService` comprueba primero si la hora pertenece a la continuación nocturna del
día anterior. Solo cuando no es así intenta iniciar la jornada actual. La hora final es exclusiva: a
las 00:30, un servicio que termina a las 00:30 ya está cerrado.

## Franjas y frecuencias

`service_periods` divide cada calendario en etapas ordenadas:

- `SERVICE_START`: incorporación progresiva;
- `OFF_PEAK`: hora valle;
- `PEAK`: hora punta;
- `REGULAR`: servicio estable;
- `SERVICE_END`: retirada progresiva.

`line_service_levels` relaciona una línea con una franja y establece su intervalo de paso mediante
`headway_seconds`. Los segundos conservan la precisión necesaria para calcular llegadas en formato
`mm:ss`.

Las franjas son compartidas. Cada línea almacena únicamente su nivel de servicio, evitando repetir
nombres y límites horarios. Los datos iniciales aplican estos ajustes a la frecuencia base:

| Línea | Ajuste |
| --- | ---: |
| L1 | 0 segundos |
| L2 | +15 segundos |
| L3 | +45 segundos |
| L4 | +15 segundos |
| L5 | +30 segundos |
| L6 | +60 segundos |

Un intervalo menor representa mayor frecuencia. El motor utiliza la duración del recorrido y este
intervalo para determinar la flota necesaria.

## Recorridos y tiempos

`line_stations` conserva la secuencia canónica de estaciones y añade:

| Campo | Significado |
| --- | --- |
| `travel_seconds_to_next` | Duración hasta la siguiente estación; es `NULL` en la terminal final. |
| `dwell_seconds` | Tiempo de permanencia antes de continuar. |

Los datos iniciales asignan 30 segundos en estaciones normales, 45 en correspondencias y 90 en
terminales. Las duraciones iniciales de los tramos proceden de
`station_connections.estimated_minutes` y se almacenan en segundos.

No existe otra tabla que vuelva a enumerar el recorrido. El mapa, los termómetros y el simulador
utilizan la misma secuencia.

## Líneas y cocheras

`line_depots` indica qué cocheras pueden abastecer y recibir trenes de una línea. Contiene prioridad de
expedición, permisos de salida y recepción, y estado activo.

Cada línea dispone inicialmente de dos cocheras. Una línea operativa debe tener como mínimo una
relación activa para salida y otra para recepción; ambas funciones pueden recaer en la misma cochera.

## Clasificación de la flota

`trains` separa dos conceptos:

- `status`: situación física u operativa actual;
- `fleet_role`: función estable dentro de la flota.

Los estados admitidos son `IN_SERVICE`, `DEPOT`, `MAINTENANCE`, `STOPPED` y `OUT_OF_SERVICE`.

| Función | Unidades iniciales | Comportamiento previsto |
| --- | ---: | --- |
| `REGULAR_SERVICE` | 230 trenes serie 9000 | Candidatos para el servicio ordinario. |
| `RESERVE` | 5 trenes serie 7000 | No circulan sin una incidencia simulada. |
| `HISTORIC` | 7 trenes clásicos | Se conservan en inventario y no prestan servicio. |

Al cargar los datos, las 242 unidades tienen estado `DEPOT` porque están físicamente en una cochera.
La reserva y el carácter histórico no son ubicaciones ni estados de circulación.

Los trenes regulares disponen además de `dispatch_order`. Es único dentro de cada cochera y permite
seleccionar unidades de forma estable. La reserva y la flota histórica no tienen orden de salida
ordinario.

## Modelo del backend

Las entidades principales son `ServiceCalendar`, `ServicePeriod`, `LineServiceLevel`, `LineDepot`,
`LineStation` y `Train`.

`ServiceConfigurationService` ofrece dos operaciones internas:

```java
findCurrentForLine(String lineCode)
findForLineAt(String lineCode, ZonedDateTime requestedDateTime)
```

La primera utiliza el reloj de la aplicación. La segunda permite resolver un instante concreto. Si
existe servicio, devuelve un `ResolvedLineServiceConfiguration` con la fecha de explotación,
calendario, franja, frecuencia, recorrido, tiempos y cocheras. Fuera del horario devuelve
`Optional.empty()`.

Una línea desconocida o una configuración incoherente provoca un error explícito en lugar de generar
un estado ferroviario incompleto.

## Zona horaria y reloj

La operación utiliza `Europe/Madrid` de forma predeterminada y puede modificarse con:

```text
SERVICE_TIME_ZONE
```

El backend inyecta un `Clock` compartido. El código operativo no debe llamar directamente a
`LocalDateTime.now()`, porque impediría reproducir una jornada concreta en las pruebas.

## Validaciones

El esquema, el backend y `database/verification/verify_database.sql` comprueban:

- fechas de vigencia válidas;
- frecuencias y tiempos positivos;
- una única configuración aplicable por instante;
- recorridos completos con al menos dos paradas;
- cocheras habilitadas para salida y recepción;
- funciones de flota conocidas;
- órdenes de salida exclusivos de la flota regular;
- ausencia de unidades no regulares en servicio.

Las consultas de verificación destinadas a buscar inconsistencias deben devolver cero filas.

## Cómo modificar la configuración

Los datos mantenidos están en `database/data/04_service_configuration.sql`. Para modificarlos:

1. utilizar códigos estables en los registros semilla;
2. cubrir el horario sin huecos ni solapamientos;
3. mantener una frecuencia por cada combinación activa de línea y franja;
4. asegurar que cada parada no terminal tenga duración hasta la siguiente;
5. ejecutar `database/verification/verify_database.sql`;
6. ejecutar las pruebas del backend.

Los scripts de esquema están destinados a instalaciones nuevas. Una base creada con el modelo
anterior debe respaldarse y reconstruirse antes de validar esta fase.

## Relación con el motor

El motor reconstruye cualquier hora a partir de este modelo, incorpora y retira trenes, limita el
servicio ordinario a la serie 9000 y calcula dirección, tramo, progreso y próxima llegada. Las
posiciones continúan siendo datos calculados: no se persisten ni requieren que Spring Boot haya estado
ejecutándose desde el inicio de la jornada.
