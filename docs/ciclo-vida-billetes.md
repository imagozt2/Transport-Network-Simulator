# Ciclo de vida de los billetes RMM

## Objetivo

Este documento define cómo nace, se activa, utiliza, recarga, bloquea y termina un billete dentro del
ecosistema RMM. Las reglas son independientes de su representación física o digital: ambos soportes
referencian un billete cuya autoridad reside en el backend.

El documento fija el comportamiento funcional esperado. El núcleo implementado se describe en
[Dominio de billetes](dominio-billetes.md), donde se relacionan estas reglas con las entidades,
servicios y tablas actuales.

## Conceptos separados

Una operación de transporte participa en tres ciclos relacionados, pero distintos:

1. **Compra o emisión:** describe si la solicitud económica o compensatoria se completa.
2. **Billete:** conserva el producto, los derechos disponibles y su estado de uso.
3. **Trayecto:** se abre con una entrada aceptada y se cierra con una salida aceptada.

Un fallo de pago, por ejemplo, pertenece a la compra y no debe crear un billete activo. Una salida
pendiente pertenece al trayecto y no convierte por sí misma el billete en caducado.

## Identidad y soporte

Cada billete dispone de:

- un código público estable y no reutilizable;
- un producto tarifario y su configuración aplicada en el momento de la operación;
- un estado funcional;
- un código QR verificable y versionado;
- los saldos, validez o estaciones que correspondan al producto;
- referencias de emisión, recarga, uso y titularidad cuando procedan.

### Billete digital

- Se crea asociado al pasajero autenticado que realiza la compra.
- Aparece en la cartera de RMM App después de confirmarse la emisión.
- RMM App presenta el QR entregado por el backend, pero no puede generarlo ni alterarlo.

### Billete físico

- Se emite desde una máquina de venta y puede existir inicialmente sin pasajero asociado.
- Puede utilizarse sin registrarlo en RMM App.
- Su propietario puede vincularlo posteriormente escaneando el QR y demostrando que conserva un
  billete válido.
- La vinculación no crea un billete nuevo ni duplica sus derechos; solo asocia el registro existente
  a una cuenta.
- Un billete no puede estar vinculado simultáneamente a dos pasajeros.

El cambio de soporte o la vinculación no modifica saldo, viajes, vigencia ni historial.

## Ciclo de compra y emisión

### Estados de la solicitud

| Estado | Significado |
| --- | --- |
| `REQUESTED` | El backend ha recibido una petición con una referencia idempotente. |
| `PENDING_PAYMENT` | La configuración es válida y espera el resultado del pago simulado. |
| `PROCESSING` | El pago fue aceptado o no es necesario y se está emitiendo el billete. |
| `COMPLETED` | El billete fue creado y la respuesta quedó registrada. |
| `FAILED` | La solicitud no pudo completarse y conserva el motivo. |
| `CANCELLED` | Se canceló antes de crear o modificar el billete. |

```text
REQUESTED ──► PENDING_PAYMENT ──► PROCESSING ──► COMPLETED
    │                 │                │
    ├─────────────────┴────────────────┴──────► FAILED
    └────────────────────────────────────────► CANCELLED
```

Las recargas siguen el mismo ciclo, pero `COMPLETED` modifica de forma atómica un billete existente
en lugar de crear otro.

### Emisión compensatoria

La emisión solicitada por un operador utiliza `REQUESTED`, `PROCESSING`, `COMPLETED`, `FAILED` y
`CANCELLED`, pero cumple además estas reglas:

- el importe cobrado siempre es cero;
- exige operador, motivo y máquina de destino;
- la máquina confirma que ha presentado o impreso el billete;
- el backend conserva la relación entre solicitud, operador, máquina y billete;
- repetir la misma referencia devuelve el resultado existente y no emite un segundo billete.

Si la máquina no confirma la orden dentro del plazo configurado, la solicitud no se marca como
completada. Podrá permanecer pendiente o fallar de forma recuperable según el contrato MQTT.

## Estados del billete

| Estado | Permite validar | Permite recargar | Descripción |
| --- | --- | --- | --- |
| `ACTIVE` | Sí, si cumple las reglas del producto | Según el producto | Billete disponible para su uso. |
| `EXHAUSTED` | No | Sí, si es recargable | No conserva viajes, trayecto o saldo utilizable. |
| `EXPIRED` | No | Según el producto | Su periodo de validez ha terminado. |
| `BLOCKED` | No | No mientras continúe bloqueado | Bloqueo preventivo o administrativo. |
| `CANCELLED` | No | No | Billete anulado definitivamente. |

La emisión correcta crea el billete directamente como `ACTIVE`. Los estados de preparación o fallo
pertenecen a la solicitud de emisión, evitando billetes incompletos en la cartera.

```text
                         ┌──────────────┐
                 ┌──────►│   BLOCKED    │──────┐
                 │       └──────────────┘      │ desbloqueo
                 │ bloqueo                     ▼
emisión ──► ACTIVE ───────────────► EXHAUSTED ──► ACTIVE
             │ uso o saldo agotado        recarga
             │
             ├── fin de vigencia ───────► EXPIRED
             └── anulación ─────────────► CANCELLED
```

`EXPIRED` puede determinarse al consultar o validar el billete a partir de `valid_until`; no requiere
obligatoriamente un proceso que actualice todos los registros en el instante de caducidad.

`CANCELLED` es terminal. `BLOCKED` es reversible y conserva el saldo y la historia existentes.

## Ciclo del trayecto

### Estados

| Estado | Significado |
| --- | --- |
| `OPEN` | Existe una entrada aceptada y todavía no hay salida. |
| `CLOSED` | La salida fue aceptada y el trayecto terminó normalmente. |
| `FORCED_CLOSED` | El backend regularizó un trayecto incompleto sin inventar una salida. |
| `CANCELLED` | La apertura se anuló sin constituir un trayecto cobrable. |

```text
entrada aceptada ──► OPEN ──► CLOSED
                       ├────► FORCED_CLOSED
                       └────► CANCELLED
```

Reglas comunes:

- un billete solo puede tener un trayecto `OPEN` simultáneamente;
- una nueva entrada fuerza el cierre anómalo de la entrada anterior sin salida;
- los trayectos que superan el límite temporal también se cierran periódicamente;
- una salida exige un trayecto abierto del mismo billete;
- entrada y salida conservan estación, máquina, instante y referencia idempotente;
- las validaciones rechazadas se registran, pero no abren ni cierran trayectos;
- un reintento con la misma referencia devuelve el resultado original;
- una operación no puede aplicar dos veces un descuento de saldo o viajes.

## Reglas por producto

### `SINGLE_TRIP` — Billete sencillo

Configuración necesaria:

- estación de origen;
- estación de destino distinta;
- número de estaciones del recorrido;
- precio calculado y fijado al emitir o recargar.

Ciclo de uso:

1. La entrada solo se acepta en el origen configurado y abre el trayecto.
2. El billete queda reservado para ese trayecto y no admite otra entrada.
3. La salida esperada se realiza en el destino configurado.
4. Una salida aceptada cierra el trayecto y cambia el billete a `EXHAUSTED`.
5. Si continúa siendo recargable, una recarga configura un nuevo origen y destino y lo devuelve a
   `ACTIVE`.

Una salida en una estación distinta se rechazará o se resolverá mediante una regla de regularización
documentada antes de implementar la validación.

### `MULTI_TRIP` — Billete multiviaje

Configuración necesaria:

- viajes comprados dentro del intervalo permitido;
- viajes restantes inicialmente iguales a los comprados.

Ciclo de uso:

1. La entrada exige al menos un viaje disponible.
2. La entrada aceptada consume exactamente un viaje y abre el trayecto de forma atómica.
3. La salida cierra el trayecto sin consumir otro viaje.
4. Cuando el contador llega a cero, el billete pasa a `EXHAUSTED` y no admite nuevas entradas.
5. Una recarga añade viajes dentro de los límites permitidos y devuelve el billete a `ACTIVE`.

Consumir el viaje en la entrada evita que un trayecto sin salida conserve indebidamente el derecho
utilizado.

### `TIME_PASS` — Abono temporal

Configuración necesaria:

- número de días dentro del intervalo permitido;
- `valid_from` fijado al completarse la emisión o recarga;
- `valid_until` calculado por el backend en la zona horaria del servicio.

Ciclo de uso:

1. La entrada se acepta cuando el instante actual pertenece al intervalo de validez.
2. No se reduce saldo ni número de viajes.
3. Entrada y salida crean el trayecto con las mismas restricciones de simultaneidad.
4. Tras `valid_until`, las nuevas entradas se rechazan y el billete se considera `EXPIRED`.
5. Una salida de un trayecto abierto antes de caducar puede completarse para no dejarlo bloqueado.
6. La adquisición de un nuevo periodo devuelve el billete a `ACTIVE` con nuevas fechas.

### `SMART_BALANCE` — Saldo inteligente

Configuración necesaria:

- saldo monetario no negativo;
- recargas dentro de los importes permitidos;
- moneda fijada por el sistema tarifario.

Ciclo de uso:

1. La entrada exige saldo suficiente para la tarifa mínima y abre el trayecto.
2. No se descuenta todavía un importe definitivo porque se desconoce el destino.
3. La salida calcula el recorrido real y su tarifa.
4. El cierre descuenta el importe una sola vez y registra saldo anterior y posterior.
5. Si el saldo restante no permite otra entrada, el billete pasa a `EXHAUSTED`.
6. Una recarga suma saldo de forma atómica y lo devuelve a `ACTIVE`.

Los trayectos sin salida quedan como `FORCED_CLOSED`, sin destino ni cargo inventados. Si el saldo
no cubre el coste calculado en una salida real, la transacción se rechaza y conserva el trayecto
abierto hasta que pueda regularizarse.

## Validaciones aceptadas y rechazadas

Toda lectura produce un resultado auditable.

Una validación aceptada conserva, como mínimo:

- billete y trayecto;
- tipo `ENTRY` o `EXIT`;
- estación y máquina;
- fecha y hora del backend;
- saldo o viajes antes y después cuando proceda;
- importe aplicado;
- referencia externa idempotente.

Una validación rechazada no altera el billete y registra un motivo estable, por ejemplo:

- QR inválido o manipulado;
- billete desconocido, cancelado, bloqueado o caducado;
- billete agotado o saldo insuficiente;
- origen incorrecto;
- entrada ya abierta;
- salida sin entrada;
- máquina no autorizada para esa estación o función.

Los textos mostrados al usuario se traducen en cada cliente a partir del código de motivo; no se
utilizan como regla de negocio.

## Recargas

- Solo pueden aplicarse a productos marcados como recargables.
- El backend valida mínimos, máximos y compatibilidad con el producto.
- Cada recarga dispone de una referencia idempotente y un registro económico independiente.
- La modificación del saldo y la finalización de la recarga forman una única transacción.
- Una recarga no elimina usos, validaciones ni trayectos anteriores.
- Un billete `BLOCKED` o `CANCELLED` no puede recargarse.
- No se sustituye el QR salvo que la política de seguridad exija renovar su versión o revocar el
  anterior.

## Bloqueo, revocación y anulación

- `BLOCKED` detiene temporalmente compras asociadas, entradas, salidas no justificadas y recargas.
- El desbloqueo restaura el estado que corresponda a sus derechos actuales, nunca saldo consumido.
- La revocación del QR invalida esa credencial aunque el registro del billete se conserve para
  auditoría.
- `CANCELLED` exige una acción autorizada y un motivo; no elimina físicamente el registro.
- Un billete cancelado no vuelve a activarse. Una compensación posterior crea o recarga otro derecho
  mediante una operación trazable.

## Concurrencia e idempotencia

El backend debe proteger cada modificación del billete frente a solicitudes simultáneas:

- dos entradas concurrentes no pueden abrir dos trayectos;
- dos salidas no pueden cobrar dos veces;
- dos recargas con la misma referencia no pueden sumar dos veces;
- una confirmación MQTT repetida no puede duplicar la emisión;
- la respuesta repetida debe reflejar el resultado ya persistido.

Las aplicaciones cliente pueden bloquear temporalmente su interfaz para reducir duplicados, pero la
garantía definitiva corresponde al backend y a la transacción de base de datos.

## Responsabilidad temporal

- El backend determina el instante oficial de compras, emisiones, recargas y validaciones.
- Las fechas se persisten de forma inequívoca y se presentan en la zona horaria configurada.
- La hora enviada por una máquina se conserva como dato de diagnóstico, pero no sustituye a la hora
  autoritativa del backend.
- Los cálculos de vigencia utilizan una única política documentada para cambios de día y horario.

## Aspectos pendientes de otros contratos

Este ciclo no define por sí mismo:

- la estructura y firma criptográfica del QR, especificadas en el [contrato de códigos QR de
  RMM](contrato-codigos-qr.md);
- las rutas y representaciones de la API REST, definidas para Android en los [contratos de RMM
  App](contratos-rest-rmm-app.md);
- los topics, payloads y niveles de servicio MQTT, definidos en el [contrato de mensajería con las
  máquinas](contrato-mqtt.md);
- el almacenamiento local permitido durante una desconexión, acotado en los [flujos online y sin
  conexión](flujos-conectividad.md);
- las reglas tarifarias de regularización de trayectos incompletos.

Esos contratos deben respetar los estados, transiciones y autoridades definidos aquí.
