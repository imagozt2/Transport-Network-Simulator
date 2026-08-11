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
- publicar eventos del proceso mediante MQTT;
- recibir y entregar emisiones compensatorias sin cobrar al viajero;
- recuperarse de interrupciones temporales conservando la identidad de cada solicitud.

La máquina no calcula por sí sola precios definitivos, derechos de uso ni firmas. Estas decisiones
pertenecen al backend para evitar resultados diferentes entre los canales de venta.

## Flujo de compra

1. La pantalla principal permite elegir idioma e iniciar una compra.
2. La máquina consulta títulos en `GET /api/public/v1/ticket-products`.
3. Para un billete sencillo consulta las estaciones y solicita el trayecto a
   `GET /api/public/v1/journeys`.
4. El usuario configura el producto:
   - origen y destino para `SINGLE_TRIP`;
   - cantidad de viajes para `MULTI_TRIP`;
   - número de días para `TIME_PASS`;
   - importe de recarga para `SMART_BALANCE`.
5. Tras confirmar el pago simulado, publica `ticket.purchase-requested` mediante MQTT.
6. El backend valida el producto y el pago, emite el soporte físico y responde con
   `ticket.issue-command`.
7. La máquina comprueba la correlación, la caducidad y el contenido de la orden antes de mostrar el
   QR, el identificador del billete y su código de vinculación.
8. Al finalizar publica el evento `TICKET_PURCHASE_COMPLETED`.

La misma referencia de compra y el mismo mensaje se conservan durante los reintentos. La
idempotencia del backend impide que una reconexión emita dos billetes para una sola compra.

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

La aplicación inicia la conexión al arrancar, aunque todavía no exista una compra, para poder
recibir órdenes compensatorias en todo momento.

## Configuración local

Las direcciones HTTP y MQTT se generan al configurar CMake a partir de
`config/local-services.properties`, que no se versiona. Si no existe, se utiliza
`config/local-services.properties.example`.

La identidad y la contraseña se proporcionan como variables de entorno:

```powershell
$env:RMM_TICKET_MACHINE_DEVICE_CODE = "RMM-SALE-ST046-01"
$env:RMM_TICKET_MACHINE_MQTT_PASSWORD = "contraseña-local-de-la-maquina"
```

`RMM_TICKET_MACHINE_DEVICE_CODE` se usa como identidad, usuario MQTT y segmento de topic. Si se
omite, la identidad local predeterminada es `RMM-SALE-ST046-01`. La contraseña no tiene valor
predeterminado: sin ella la máquina permite consultar el catálogo, pero rechaza una solicitud de
emisión e indica que falta la credencial MQTT.

Las credenciales reales no deben escribirse en CMake, `local-services.properties`, el código ni la
documentación. La preparación de identidades está detallada en
[Identidad y autenticación de las máquinas](identidad-maquinas.md).

## Compilación, ejecución y pruebas

Desde Qt Creator se abre `qt/CMakeLists.txt` y se ejecuta el objetivo
`rmm-ticket-vending-machine` con el kit Qt 6.11.1 MinGW 64-bit. El procedimiento completo mediante
PowerShell está en la [guía de aplicaciones cliente](ejecucion-aplicaciones-cliente.md).

La cobertura automatizada valida las configuraciones de compra, la lectura de emisiones normales y
compensatorias, el rechazo de órdenes caducadas o inválidas y la estructura de eventos y ACK:

```powershell
& D:\Qt\Tools\CMake_64\bin\ctest.exe `
  --test-dir qt/build `
  --output-on-failure
```

La prueba correspondiente aparece como `rmm-ticket-machine-purchase-issuance-events`.

## Límites de la simulación

- El pago se aprueba de forma simulada y no integra una pasarela financiera.
- El QR se representa en pantalla; no existe una impresora física.
- La cola de eventos desconectados se conserva durante la ejecución, no tras cerrar el proceso.
- El backend y Mosquitto deben estar disponibles para completar una compra o una orden administrativa.

Estas limitaciones mantienen el foco en el dominio, la comunicación y la trazabilidad sin simular
hardware o proveedores externos que todavía no forman parte del ecosistema.
