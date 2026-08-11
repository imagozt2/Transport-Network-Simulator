# Dominio de billetes

## Objetivo

El núcleo de billetaje de RMM modela los productos tarifarios, los derechos adquiridos por el
pasajero, su soporte físico o digital, los trayectos realizados, las recargas y el historial de
operaciones. Spring Boot es la autoridad del dominio y MySQL conserva su estado.

Un producto, un billete y un soporte son conceptos distintos:

- `TicketProduct` define una tarifa reutilizable y sus límites.
- `Ticket` representa los derechos concretos adquiridos y su estado actual.
- `TicketSupport` determina cómo se presenta ese billete: tarjeta física o cartera digital.

Esta separación permite cambiar o vincular el soporte sin duplicar saldo, viajes o vigencia.

## Modelo persistente

| Tabla | Responsabilidad |
| --- | --- |
| `ticket_products` | Catálogo y reglas económicas de los cuatro productos. |
| `tickets` | Estado y derechos actuales de cada billete. |
| `ticket_supports` | Soportes físicos y digitales asociados al billete. |
| `purchases` | Emisiones y recargas con referencia idempotente e importe. |
| `ticket_journeys` | Trayectos abiertos y cerrados mediante entrada y salida. |
| `ticket_operations` | Historial inmutable de cambios relevantes del billete. |
| `ticket_validations` | Resultado técnico y funcional de cada validación. |

`tickets.lock_version` habilita el bloqueo optimista de JPA. Las operaciones que consumen o
recargan derechos recuperan además el billete con bloqueo de escritura para impedir consumos
duplicados ante solicitudes simultáneas.

## Productos

### Billete sencillo (`SINGLE_TRIP`)

- Requiere estaciones de origen y destino diferentes.
- Conserva el número de estaciones calculado por el planificador de trayectos.
- Su precio es `precio base + precio por estación × estaciones`.
- Solo permite entrar por el origen y salir por el destino configurados.
- Una salida correcta lo deja `EXHAUSTED`.
- Una recarga configura un nuevo recorrido y lo devuelve a `ACTIVE`.

### Bono multiviaje (`MULTI_TRIP`)

- Se emite con entre 2 y 30 viajes.
- Cada entrada aceptada consume un viaje; la salida no consume otro.
- Al consumir el último viaje pasa a `EXHAUSTED`.
- La recarga añade viajes sin superar el máximo del producto.

### Abono temporal (`TIME_PASS`)

- Se emite con una vigencia de entre 2 y 30 días.
- No consume viajes ni saldo al utilizarse.
- Solo admite entradas dentro de `valid_from` y `valid_until`.
- Al comprobarlo después de su vigencia pasa a `EXPIRED`.
- Una renovación amplía un abono todavía vigente o inicia un periodo nuevo si ya caducó.

### Saldo inteligente (`SMART_BALANCE`)

- Mantiene un saldo monetario en euros.
- La entrada exige al menos la tarifa mínima posible.
- La salida calcula la tarifa real con las estaciones recorridas y la descuenta.
- Pasa a `EXHAUSTED` cuando el saldo restante no permite otro trayecto mínimo.
- Las recargas admiten importes de 1,00 € a 100,00 € y reactivan el billete.

Los límites y precios proceden siempre de `TicketProduct`; no se duplican como constantes en los
clientes.

## Emisión y soportes

`TicketIssuanceService` crea el billete y su soporte dentro de una única transacción.

### Emisión física

Exige una máquina de venta activa, online y de tipo `TICKET_MACHINE`. El soporte conserva:

- un número de serie único;
- la máquina emisora;
- un código de vinculación almacenado mediante su huella;
- el plazo durante el cual puede vincularse con una cuenta.

### Emisión digital

Exige una cuenta de pasajero activa. El billete y el soporte digital quedan asociados a esa cuenta
desde el momento de la emisión.

En ambos casos se generan códigos públicos no secuenciales y se registra una operación `ISSUED`.
La firma y renovación de las credenciales QR se rigen por el
[contrato de códigos QR](contrato-codigos-qr.md).

## Estados

| Estado | Significado | Transiciones principales |
| --- | --- | --- |
| `ACTIVE` | Puede utilizarse si cumple las reglas del producto. | A agotado, caducado, bloqueado o cancelado. |
| `EXHAUSTED` | No conserva derechos suficientes para otra entrada. | A activo mediante una recarga válida. |
| `EXPIRED` | Ha terminado su periodo de validez. | A activo mediante renovación temporal. |
| `BLOCKED` | Suspensión administrativa temporal. | A su estado operativo cuando se desbloquee. |
| `CANCELLED` | Anulación definitiva. | Estado terminal. |

Las transiciones se ejecutan mediante métodos del propio `Ticket`. De esta forma, los servicios no
pueden modificar directamente contadores, saldo o vigencia saltándose las reglas del producto.

## Trayectos y consumo

Los servicios especializados `SingleTripTicketService`, `MultiTripTicketService`,
`TimePassTicketService` y `SmartBalanceTicketService` aplican las reglas de entrada, salida y
recarga de cada producto.

Una entrada aceptada abre un único `TicketJourney`. Si una nueva entrada revela que la anterior no
tuvo salida, el trayecto incompleto se regulariza como `FORCED_CLOSED` antes de abrir el siguiente.
La salida recupera el trayecto vigente, calcula la distancia y lo cierra con la estación, el importe
y la hora oficiales.

El momento del consumo depende del producto:

| Producto | Entrada | Salida |
| --- | --- | --- |
| Sencillo | Comprueba el origen. | Comprueba el destino y agota el billete. |
| Multiviaje | Descuenta un viaje. | Cierra el trayecto. |
| Temporal | Comprueba la vigencia. | Cierra el trayecto sin coste adicional. |
| Saldo inteligente | Comprueba la tarifa mínima. | Calcula y descuenta la tarifa real. |

Si cualquier paso falla, la transacción revierte conjuntamente el billete, el trayecto y su asiento
de historial.

## Recargas

`TicketRechargeService` centraliza la operación económica y delega la modificación del billete en
el servicio correspondiente a su producto.

Cada solicitud requiere:

- código del billete;
- parámetros compatibles exclusivamente con ese producto;
- origen de la compra (`RMM_APP`, `TICKET_MACHINE` o `CONTROL_CENTER`);
- método de pago;
- referencia externa idempotente;
- contexto de pasajero o máquina cuando corresponda.

Repetir una referencia ya completada devuelve la compra existente y no vuelve a sumar saldo,
viajes o días. La modificación del billete, la compra `COMPLETED` y el registro histórico se
confirman dentro de la misma transacción.

## Historial de operaciones

`ticket_operations` actúa como un libro de trazabilidad independiente de los logs técnicos de las
máquinas. Cada registro conserva:

- billete, tipo, origen e instante de la operación;
- estado anterior y resultante;
- saldo y viajes antes y después;
- vigencia anterior y posterior;
- importe y moneda;
- soporte, compra, trayecto, estación, máquina o pasajero relacionados cuando proceda;
- referencia externa utilizada para la idempotencia.

Actualmente se registran `ISSUED`, `RECHARGED`, `ENTRY_ACCEPTED` y `EXIT_ACCEPTED`. El modelo admite
también futuras operaciones de bloqueo, desbloqueo, cancelación, vinculación de soporte y
revocación de QR sin mezclar estos cambios con `DeviceEventLog`.

Los registros históricos no se actualizan para reflejar el estado actual: cada uno representa la
fotografía anterior y posterior de una operación concreta. El repositorio permite recuperar el
historial de un billete en orden cronológico inverso.

## Responsabilidades

| Componente | Responsabilidad |
| --- | --- |
| MySQL | Persistencia, relaciones, unicidad, límites estructurales e índices. |
| Entidades | Invariantes y transiciones del producto. |
| Servicios especializados | Reglas de entrada, salida y recarga. |
| Servicio de emisión | Creación coherente del billete y su soporte. |
| Servicio de recarga | Contexto económico, idempotencia y delegación por producto. |
| Registro de operaciones | Fotografía auditable de cada cambio confirmado. |
| Clientes | Recoger datos y presentar resultados sin reproducir reglas autoritativas. |

## Verificación

Las pruebas de `TicketLifecycleTests` cubren:

- cálculo, agotamiento y recarga del billete sencillo;
- consumo, agotamiento y límites del bono multiviaje;
- caducidad y renovación del abono temporal;
- cálculo, descuento, agotamiento y recarga del saldo inteligente;
- rechazo de operaciones incompatibles con el tipo de producto.

`database/verification/verify_database.sql` comprueba además la integridad de las relaciones del
historial y la coherencia entre cada operación y su billete.

## Documentos relacionados

- [Ciclo de vida de los billetes](ciclo-vida-billetes.md)
- [Catálogo de títulos de transporte](titulos-transporte.md)
- [Contrato de códigos QR](contrato-codigos-qr.md)
- [Contratos REST de RMM App](contratos-rest-rmm-app.md)
- [Historial de desplazamientos de RMM App](historial-desplazamientos-rmm-app.md)
- [Contrato MQTT](contrato-mqtt.md)
- [Flujos online y sin conexión](flujos-conectividad.md)
