# Panel General

El Panel General es la pantalla inicial del centro de control de la Red de Metro de Macegocia.
Resume el estado actual de la red utilizando las mismas consultas operativas que alimentan las
secciones de Líneas, Estaciones, Trenes, Cocheras y Máquinas.

## Acceso y actualización

Con el backend y el frontend en ejecución, el panel está disponible en:

```text
http://localhost:4200/dashboard
```

Al abrir la pantalla se muestra un estado de carga hasta completar todas las consultas. Si alguna
falla, aparece un mensaje con una acción para reintentar. Después de la carga inicial, los datos se
actualizan automáticamente cada cinco segundos. El mecanismo compartido de actualización impide que
se inicie un nuevo ciclo mientras el anterior continúa en curso.

El panel no genera datos simulados ni completa cifras en el navegador. Su función es presentar de
forma agregada el estado calculado por el backend.

## Indicadores principales

La cabecera contiene siete tarjetas. Cada una muestra exclusivamente su título y su valor:

| Indicador | Fuente operativa |
| --- | --- |
| Líneas de la red | Número de líneas devuelto por la consulta operativa de líneas. |
| Número de estaciones | Número total de estaciones de la consulta operativa de estaciones. |
| Flota total | Unidades activas registradas en el resumen operativo de trenes. |
| Trenes en servicio | Unidades que el motor ferroviario mantiene actualmente en circulación. |
| Cantidad de máquinas | Máquinas incluidas en el inventario operativo. |
| Cocheras | Número de cocheras del resumen operativo. |
| Ocupación de las cocheras | Porcentaje de plazas ocupadas en el instante consultado. |

## Paneles operativos

### Estado de trenes

Muestra la distribución actual de la flota por situación física u operativa:

- `IN_SERVICE`: en servicio;
- `DEPOT`: en cochera;
- `MAINTENANCE`: en mantenimiento;
- `STOPPED`: detenido;
- `OUT_OF_SERVICE`: fuera de servicio.

Los roles `REGULAR_SERVICE`, `RESERVE` e `HISTORIC` no son estados y no se suman de nuevo en este
panel. Esta separación evita duplicar unidades.

### Estado de máquinas

Presenta los recuentos por estado (`ONLINE`, `OFFLINE`, `MAINTENANCE` y `ERROR`) y por tipo
(`TICKET_MACHINE`, `ENTRY_VALIDATOR` y `EXIT_VALIDATOR`). Los valores proceden del mismo estado
operativo usado por la sección de Máquinas, incluida la actualización derivada de sus eventos.

### Ocupación de las cocheras

Muestra:

- porcentaje global de ocupación;
- plazas ocupadas, libres y capacidad total;
- ocupación actual de cada cochera.

La ocupación representa trenes físicamente presentes según la simulación, no la cantidad de unidades
asignadas administrativamente a cada cochera.

### Líneas de la red

Por cada línea se muestra:

- código y color identificativo;
- nombre;
- número actual de trenes en servicio;
- si el servicio está abierto o cerrado en el instante evaluado.

## Consultas utilizadas

El frontend agrega en paralelo estas consultas:

```http
GET /api/lines/operations
GET /api/stations/operations
GET /api/trains/operations
GET /api/depots/operations
GET /api/devices/operations
```

Cada endpoint conserva su propio contrato, documentado en la guía de su sección. `DashboardService`
extrae únicamente los resúmenes y elementos necesarios para construir la vista.

El endpoint histórico `GET /api/dashboard/summary` permanece disponible en el backend por
compatibilidad, pero el Panel General ya no lo consume porque su información persistida no representa
el estado ferroviario simulado en tiempo real.

## Componentes relacionados

- `DashboardService`: ejecuta y combina las consultas operativas.
- `Dashboard`: controla la carga inicial, la actualización periódica y la presentación.
- `PeriodicRefresh`: evita solapamientos entre actualizaciones y libera el temporizador al abandonar
  la pantalla.

## Verificación

Las pruebas comprueban:

- la composición del resumen a partir de las cinco respuestas operativas;
- la presencia y el orden de los siete indicadores;
- la ausencia de textos secundarios en las tarjetas de cabecera;
- la representación de trenes, máquinas, cocheras y líneas;
- el estado de error y la acción de reintento.
