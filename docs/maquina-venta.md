# Máquina de venta de billetes

La máquina de venta es una aplicación táctil desarrollada con C++20, Qt Widgets y Qt MQTT. Simula
la consulta, configuración, compra y entrega de títulos físicos de RMM, además de atender emisiones
compensatorias ordenadas desde el centro de control.

## Responsabilidades

La aplicación permite:

- seleccionar español o inglés y conservar la preferencia local;
- consultar el catálogo vigente de títulos y tarifas;
- cargar las estaciones y calcular trayectos para billetes sencillos;
- configurar origen y destino, viajes, días o saldo según el producto;
- simular el pago y solicitar al backend la emisión autoritativa;
- presentar el QR firmado y el código de vinculación del billete físico;
- leer mediante cámara el QR de un billete físico y recargar sus derechos;
- publicar eventos del proceso mediante MQTT;
- recibir y entregar emisiones compensatorias sin cobrar al viajero;
- recuperarse de interrupciones temporales conservando la identidad de cada solicitud.

La máquina no calcula por sí sola precios definitivos, derechos de uso ni firmas. Estas decisiones
pertenecen al backend para evitar resultados diferentes entre los canales de venta.

## Interfaz táctil

La cabecera mantiene visible la identidad de RMM y presenta las banderas de España y Reino Unido
como únicos controles de idioma. La información técnica de conexión se sitúa en el pie para no
interrumpir el recorrido del viajero.

El catálogo distribuye los cuatro títulos en una cuadrícula de dos columnas y dos filas. Al elegir
un producto, la compra continúa dentro del área principal como una sucesión de pantallas, sin abrir
diálogos intermedios:

```text
Inicio → Catálogo → Configuración → Revisión y pago → Espera de emisión
```

Los selectores de estaciones permiten escribir parte del nombre o del código y filtran las
coincidencias sin distinguir mayúsculas. También ofrecen una acción para intercambiar origen y
destino. Los viajes y días se eligen mediante botones táctiles `−` y `+`; el valor, el mínimo y el
máximo permanecen visibles y no dependen de los pequeños controles nativos del sistema operativo.

La identidad inventariada determina la ubicación de la máquina. Por ejemplo,
`RMM-TM-ST046-01` pertenece a `ST046`, por lo que esa estación aparece inicialmente como origen de
un billete sencillo. Es una selección predeterminada, no un valor obligatorio: el usuario puede
cambiarla. Si regresa desde el pago a la configuración, prevalecen los datos que ya había elegido.

## Flujo de compra

1. La pantalla principal permite iniciar una compra y la cabecera permite cambiar el idioma.
2. La máquina consulta títulos en `GET /api/public/v1/ticket-products` y los muestra en una
   cuadrícula de dos por dos.
3. Para un billete sencillo consulta las estaciones y solicita el trayecto a
   `GET /api/public/v1/journeys`.
4. El usuario configura el producto mediante controles táctiles:
   - origen predeterminado por la identidad de la máquina y destino para `SINGLE_TRIP`;
   - cantidad de viajes dentro de los límites para `MULTI_TRIP`;
   - número de días dentro de los límites para `TIME_PASS`;
   - importe de recarga para `SMART_BALANCE`.
5. Tras confirmar el pago simulado, publica `ticket.purchase-requested` mediante MQTT.
6. El backend valida el producto y el pago, emite el soporte físico y responde con
   `ticket.issue-command`.
7. La máquina comprueba la correlación, la caducidad y el contenido de la orden antes de abrir una
   ventana nueva con el QR, el identificador del billete y su código de vinculación.
8. El viajero puede pulsar **Finalizar** o esperar la cuenta atrás de 30 segundos. En ambos casos se
   publica una sola vez `TICKET_PURCHASE_COMPLETED`, se cierra la ventana y se regresa al inicio.

Si el PNG recibido no puede interpretarse como un QR, la aplicación no queda bloqueada en la
pantalla de espera: registra `TICKET_PURCHASE_FAILED` con `INVALID_QR_IMAGE`, informa de que el
billete llegó a emitirse y solicita al viajero que pida asistencia.

La misma referencia de compra y el mismo mensaje se conservan durante los reintentos. La
idempotencia del backend impide que una reconexión emita dos billetes para una sola compra.

## Flujo de recarga

La pantalla inicial ofrece **Recargar billete** como operación independiente de una compra. El
flujo se desarrolla dentro del área principal:

```text
Inicio → Lectura del QR → Opciones compatibles → Revisión y pago → Resultado
```

1. La máquina abre la cámara y muestra un marco de encuadre para centrar el QR. El usuario también
   puede cancelar y regresar al inicio sin crear ninguna operación.
2. Al detectar un código, la máquina detiene la lectura y consulta su elegibilidad mediante
   `POST /api/public/v1/ticket-recharges/lookup`.
3. El backend verifica la firma, el soporte, el estado del billete y las reglas del producto. La
   respuesta contiene únicamente las opciones que todavía son válidas para ese billete.
4. El usuario configura la recarga y la máquina solicita un precio autoritativo en
   `POST /api/public/v1/ticket-recharges/quotes`.
5. La pantalla de revisión muestra el producto, la configuración elegida, el resultado previsto y
   el importe. Confirmar simula el pago; volver atrás conserva la selección.
6. La máquina publica `ticket.recharge-requested` en
   `rmm/v1/devices/{deviceCode}/requests/recharges`, utilizando una referencia UUID estable.
7. El backend vuelve a verificar el billete y el importe, bloquea el billete durante la escritura,
   aplica la recarga, persiste la compra y su operación histórica en una única transacción.
8. La respuesta `ticket.recharge-completed` actualiza la pantalla con el código de recarga, el
   estado del billete y sus derechos resultantes. La interfaz permite finalizar y volver al inicio.

La configuración depende del producto:

| Producto | Selección de la recarga | Resultado mostrado |
| --- | --- | --- |
| `SINGLE_TRIP` | Nuevo origen y destino distintos | Estaciones e importe del nuevo trayecto |
| `MULTI_TRIP` | Viajes disponibles sin superar el máximo total | Viajes resultantes |
| `TIME_PASS` | Días comprendidos entre los límites del producto | Nueva vigencia |
| `SMART_BALANCE` | Importe dentro del mínimo y máximo admitidos | Saldo resultante |

La máquina nunca confía en un precio calculado localmente. Si el importe pagado no coincide con la
cotización vigente, el backend rechaza la petición sin modificar el billete. Una referencia ya
completada devuelve el resultado existente si la petición es idéntica y se rechaza si sus datos han
cambiado; de este modo, un reintento no suma dos veces viajes, días o saldo.

El valor firmado del QR se utiliza únicamente en las peticiones que necesitan verificar el billete.
No se copia en logs ni eventos operativos. La máquina publica, según avance el flujo,
`QR_TICKET_SCANNED`, `TICKET_RECHARGE_REQUESTED`, `TICKET_RECHARGE_COMPLETED` o
`TICKET_RECHARGE_FAILED`; sus detalles se limitan al código del billete, producto, soporte, importe,
moneda, referencia y resultado.

## Emisiones compensatorias

El centro de control puede ordenar la entrega gratuita de un billete cuando una venta anterior no
pudo materializarse. La orden se recibe en:

```text
rmm/v1/devices/{deviceCode}/commands
```

La máquina valida que la orden no haya caducado y que contenga un billete y un QR completos. Después
registra localmente el `commandId`, responde con `RECEIVED` y muestra un diálogo diferenciado. La
emisión solo queda completada en el backend cuando el operador de la máquina confirma la entrega y
se publica el ACK `COMPLETED` con `TICKET_PRESENTED`.

Los estados de las órdenes compensatorias se conservan mediante `QSettings`. Si el backend repite
una orden ya entregada, la aplicación confirma de nuevo su último estado sin volver a presentar el
billete.

## Eventos y conectividad MQTT

La máquina publica solicitudes, eventos y confirmaciones con QoS 1. Los topics y esquemas completos
se encuentran en el [contrato MQTT](contrato-mqtt.md).

Ante una desconexión:

- la interfaz informa de que los servicios se están reconectando;
- los intentos se espacian progresivamente entre 1 y 30 segundos;
- una publicación se reintenta antes de reconstruir la conexión;
- los eventos y ACK pendientes permanecen en una cola durante la ejecución;
- al reconectar se recupera la suscripción a órdenes y se vacía la cola;
- una solicitud de compra dispone de 45 segundos antes de finalizar con `MQTT_TIMEOUT`.
- una solicitud de recarga conserva su referencia durante los reintentos y aplica el mismo límite
  temporal que las demás operaciones pagadas.

La aplicación inicia la conexión al arrancar, aunque todavía no exista una compra, para poder
recibir órdenes compensatorias en todo momento.

## Configuración local

Las direcciones HTTP y MQTT se generan al configurar CMake a partir de
`config/local-services.properties`, que no se versiona. Si no existe, se utiliza
`config/local-services.properties.example`.

La identidad y la contraseña se proporcionan como variables de entorno:

```powershell
$env:RMM_TICKET_MACHINE_DEVICE_CODE = "RMM-TM-ST046-01"
$env:RMM_TICKET_MACHINE_MQTT_PASSWORD = "contraseña-local-de-la-maquina"
```

`RMM_TICKET_MACHINE_DEVICE_CODE` se usa como identidad, usuario MQTT y segmento de topic. Si se
omite, la identidad local predeterminada es `RMM-TM-ST046-01`. La contraseña no tiene valor
predeterminado: sin ella la máquina permite consultar el catálogo, pero rechaza una solicitud de
emisión e indica que falta la credencial MQTT.

El código debe cumplir `RMM-TM-STnnn-nn` y existir como `TICKET_MACHINE` activa en el inventario.
La aplicación rechaza identidades de validadores y los prefijos históricos antes de conectarse.

Las credenciales reales no deben escribirse en CMake, `local-services.properties`, el código ni la
documentación. La preparación de identidades está detallada en
[Identidad y autenticación de las máquinas](identidad-maquinas.md).

## Compilación, ejecución y pruebas

Desde Qt Creator se abre `qt/CMakeLists.txt` y se ejecuta el objetivo
`rmm-ticket-vending-machine` con el kit Qt 6.11.1 MinGW 64-bit. El procedimiento completo mediante
PowerShell está en la [guía de aplicaciones cliente](ejecucion-aplicaciones-cliente.md).

La cobertura automatizada valida las configuraciones de los cuatro productos, la lectura de
emisiones normales y compensatorias, el rechazo de órdenes caducadas o inválidas y la estructura de
eventos y ACK. También cubre la lectura y elegibilidad del QR, las opciones y precios de recarga, el
pago simulado, la persistencia, la idempotencia y el contrato MQTT. El escenario regular recorre el
contrato completo desde la solicitud pagada hasta `QR_TICKET_GENERATED` y
`TICKET_PURCHASE_COMPLETED`:

```powershell
& D:\Qt\Tools\CMake_64\bin\ctest.exe `
  --test-dir qt/build `
  --output-on-failure
```

La prueba correspondiente aparece como `rmm-ticket-machine-purchase-issuance-events`.

## Límites de la simulación

- El pago se aprueba de forma simulada y no integra una pasarela financiera.
- La lectura de QR depende de una cámara accesible para Qt; no se almacena ninguna captura.
- El QR se representa en pantalla; no existe una impresora física.
- La cola de eventos desconectados se conserva durante la ejecución, no tras cerrar el proceso.
- El backend y Mosquitto deben estar disponibles para completar una compra o una orden administrativa.

Estas limitaciones mantienen el foco en el dominio, la comunicación y la trazabilidad sin simular
hardware o proveedores externos que todavía no forman parte del ecosistema.
