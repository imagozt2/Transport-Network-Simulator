# Operación simulada

La operación simulada conecta la configuración persistida de la red con las pantallas del centro de
control. Su objetivo es reconstruir una jornada ferroviaria reproducible y presentar la misma
realidad operativa en Líneas, Estaciones, Trenes y Cocheras. Las Máquinas siguen un ciclo de eventos
independiente, coordinado únicamente con la apertura o cierre del servicio.

## Fuentes de verdad

El sistema evita mantener estados paralelos:

- MySQL conserva topología, tiempos, calendarios, frecuencias, cocheras, flota y máquinas;
- `RailwaySimulationStateService` calcula la instantánea ferroviaria de un instante;
- `DeviceEventSimulationScheduler` sincroniza y genera la actividad ordinaria de las máquinas;
- los servicios de consulta transforman esos datos en DTO, sin modificar el estado ferroviario;
- Angular representa las respuestas y solo calcula localmente cuentas atrás visuales.

```text
configuración MySQL
       │
       ▼
RailwaySimulationStateService
       │
       ├── Líneas
       ├── Estaciones
       ├── Trenes
       └── Cocheras

horario del servicio ──► simulación de máquinas ──► estado + logs
```

Las posiciones ferroviarias no se persisten periódicamente. Reiniciar Spring Boot no reinicia la
jornada: el estado vuelve a calcularse usando la fecha, la hora y la configuración vigentes.

## Coherencia entre secciones

Las cuatro consultas ferroviarias consumen `RailwaySimulationStateService.getCurrentState()`. En una
misma instantánea deben cumplirse estas correspondencias:

- el número de trenes de una línea coincide con las unidades `IN_SERVICE` que circulan por ella;
- la línea, el sentido, la estación anterior y la próxima estación de un tren coinciden en Líneas y
  Trenes;
- las próximas llegadas de Estaciones proceden de esas mismas posiciones y conservan segundos;
- los trenes con estado `DEPOT` coinciden con la ocupación mostrada en Cocheras;
- una unidad en servicio tiene ubicación ferroviaria y no tiene cochera actual;
- una unidad en cochera tiene cochera actual y no tiene ubicación ferroviaria.

`OperationalSectionsConsistencyTests` fija este contrato transversal. Las pruebas específicas de
cada servicio verifican además sus recuentos, estados y transformaciones.

## Jornada ferroviaria

El motor resuelve la fecha de explotación, incluso durante la continuación nocturna después de
medianoche. Después:

1. determina la fase y la franja aplicables;
2. calcula la duración de la vuelta y la flota objetivo;
3. genera turnos espaciados según la frecuencia;
4. asigna exclusivamente trenes regulares de la serie 9000;
5. calcula paradas, tramos, sentidos y próximas llegadas;
6. calcula entradas y salidas de las cocheras.

Los trenes permanecen 20 segundos en cada estación según `line_stations.dwell_seconds`. La frecuencia
representa el intervalo entre turnos sucesivos, no una estimación visual obtenida a partir de la
distancia del termómetro.

La descripción detallada está en
[Motor de simulación ferroviaria](motor-simulacion-ferroviaria.md).

## Actualización del frontend

Las secciones operativas consultan periódicamente sus endpoints:

| Sección | Intervalo |
| --- | ---: |
| Líneas | 5 segundos |
| Estaciones | 15 segundos |
| Trenes | 15 segundos |
| Cocheras | 15 segundos |
| Máquinas | 15 segundos |

`PeriodicRefresh` centraliza el temporizador, la pausa y reanudación, la cancelación al abandonar la
pantalla y el bloqueo de solicitudes solapadas. También detiene las consultas cuando la pestaña no
es visible y solicita una actualización inmediata al recuperarla. Un fallo durante una actualización
conserva la última respuesta válida y permite que el siguiente ciclo vuelva a intentarlo.

Estaciones y Trenes mantienen además una cuenta atrás local de un segundo. Esa cuenta atrás no
inventa posiciones nuevas: descuenta segundos desde la última instantánea recibida y se resincroniza
con la siguiente respuesta.

Los colores de línea, las etiquetas de estado y los formatos temporales también se resuelven mediante
utilidades compartidas para que una misma situación tenga la misma representación en todas las
pantallas.

Las listas de entidades usan sus identificadores persistentes como claves de renderizado. Angular
puede así conservar los nodos cuando cambia la instantánea. Los indicadores comunes de Líneas,
Estaciones, Trenes y Cocheras utilizan el componente compartido `SummaryCard`.

## Consultas operativas

Los servicios de lectura están diseñados para mantener un número acotado de consultas:

- las relaciones necesarias de paradas, trenes y cocheras se precargan mediante `EntityGraph`;
- Estaciones obtiene los recuentos de máquinas agrupados por estación, tipo y estado directamente
  desde MySQL, sin materializar el inventario completo;
- Cocheras consulta únicamente las estaciones referenciadas por movimientos y agrupa los movimientos
  en una sola pasada;
- Trenes indexa las paradas de cada recorrido por identificador para resolver ubicaciones sin
  búsquedas lineales repetidas.

No se aplica caché al estado ferroviario. Cada respuesta debe reflejar el reloj operativo actual y no
debe reutilizar una instantánea potencialmente obsoleta.

## Máquinas y logs

Cuando el servicio abre, todas las máquinas activas pasan a `ONLINE`; al cerrar pasan a `OFFLINE`.
Durante el servicio se genera un evento ordinario de compra o validación por segundo para una
máquina seleccionada en cada ciclo. El simulador no produce averías, fallos ni mantenimientos
artificiales.

Solo las máquinas generan logs simulados. Líneas, estaciones, trenes y cocheras no emiten registros
operativos. La transición de estado y la persistencia del evento comparten una transacción.

El contrato y las reglas completas están en
[Ciclo de eventos de las máquinas](eventos-maquinas.md).

## Límites actuales

La simulación no incluye:

- incidencias ferroviarias;
- sustituciones automáticas con trenes de reserva;
- circulación de la flota histórica;
- persistencia continua de posiciones;
- generación de logs ferroviarios;
- conexión efectiva con un broker MQTT.

La futura integración MQTT reutilizará el contrato de entrada y el registro de eventos ya preparados,
sin cambiar el funcionamiento determinista del motor ferroviario.

## Pruebas recomendadas

Para ejecutar las pruebas unitarias e integradas del backend sin la prueba de contexto que requiere
MySQL:

```powershell
Set-Location backend
.\mvnw.cmd test "-Dtest=!SpringbootApiApplicationTests"
```

Para validar la actualización periódica y la presentación compartida del frontend:

```powershell
Set-Location frontend
npm test -- --watch=false
```
