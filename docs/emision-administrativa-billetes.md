# Emisión administrativa de billetes

La emisión administrativa permite que un operador entregue gratuitamente un billete cuando una
compra válida no haya terminado correctamente. No sustituye al proceso de venta habitual: exige un
motivo, conserva la identidad del operador y registra la operación completa en los logs del centro
de control.

La operación se inicia desde la sección **Títulos de transporte** de la aplicación web.

## Canales de entrega

El operador debe elegir uno de estos canales:

| Canal | Destino | Resultado |
| --- | --- | --- |
| `DIGITAL_WALLET` | Cuenta activa de un pasajero | Crea el billete y lo incorpora inmediatamente a su cartera digital. |
| `PHYSICAL_DEVICE` con MQTT | Máquina de venta conectada y monitorizada | Crea el billete y envía a la máquina una orden para presentarlo o imprimirlo. |
| `PHYSICAL_DEVICE` sin MQTT | Máquina de venta online no monitorizada | Simula la emisión y registra sus eventos, pero no crea un billete ni un QR. |

Una máquina monitorizada que no esté conectada no se sustituye por una simulación. La solicitud se
rechaza para evitar que el centro de control informe de una entrega física que la máquina no puede
confirmar.

## Parámetros según el producto

Todos los canales utilizan las mismas reglas del catálogo:

| Producto | Datos exigidos |
| --- | --- |
| `SINGLE_TRIP` | Estaciones distintas de origen y destino. |
| `MULTI_TRIP` | Número de viajes dentro de los límites del producto. |
| `TIME_PASS` | Número de días dentro de los límites del producto. |
| `SMART_BALANCE` | Importe inicial dentro de los límites configurados. |

La emisión siempre registra `chargedAmount = 0`. Los parámetros que no pertenecen al producto
seleccionado se rechazan en el backend.

## Flujo desde la aplicación web

1. El operador abre un producto activo y selecciona **Emitir billete**.
2. Elige cartera digital o máquina de venta.
3. Selecciona el pasajero o la máquina correspondiente.
4. Introduce los parámetros del producto y el motivo de la compensación.
5. La aplicación muestra el progreso y conserva el diálogo hasta obtener una respuesta.
6. El resultado indica la referencia, el canal, el destino y el código del billete cuando existe.

Cuando se genera un billete, la pantalla presenta una tarjeta con datos estables:

- código QR verificable;
- nombre y tipo del título;
- código público del billete;
- fecha de emisión;
- soporte físico o digital.

No se muestran en esa tarjeta viajes restantes, saldo, días disponibles ni otros valores que puedan
cambiar posteriormente mediante uso o recarga. En una emisión simulada se explica expresamente que
no existen billete ni QR.

## Estados

| Estado | Significado |
| --- | --- |
| `REQUESTED` | La solicitud y su auditoría inicial se han registrado. |
| `PROCESSING` | El billete físico existe y la orden MQTT espera confirmación de la máquina. |
| `COMPLETED` | La cartera recibió el billete, la máquina confirmó la entrega o la simulación terminó. |
| `FAILED` | La máquina rechazó la orden o no pudo presentar el billete. |
| `CANCELLED` | La solicitud fue cancelada antes de completarse. |

La entrega digital se completa dentro de la misma operación transaccional. Una emisión física MQTT
permanece en `PROCESSING` hasta recibir un acuse válido. Un acuse `COMPLETED` completa la emisión;
un acuse `FAILED` o `REJECTED` registra el fallo y su motivo.

## API REST

```http
POST /api/transport-titles/{titleId}/compensatory-issuances
Authorization: Bearer <sesión-operador>
Content-Type: application/json
```

Ejemplo de entrega digital:

```json
{
  "deliveryMethod": "DIGITAL_WALLET",
  "passengerPublicId": "8a89b70e-21d4-4ef8-a029-68b8e463f41b",
  "reason": "La compra se cobró pero no se entregó el billete",
  "trips": 10
}
```

Ejemplo de entrega física:

```json
{
  "deliveryMethod": "PHYSICAL_DEVICE",
  "deviceCode": "RMM-MB-ST001-001",
  "reason": "Fallo de impresión en la compra original",
  "originStationCode": "ST001",
  "destinationStationCode": "ST020"
}
```

La respuesta utiliza `201 Created` e incluye, entre otros campos:

- `code` y `status` de la emisión;
- `deliveryMethod` y `simulated`;
- producto, operador y destino;
- `ticketCode` y la imagen `qrPngBase64` cuando se creó un billete;
- fechas de solicitud y finalización.

El frontend antepone `data:image/png;base64,` a `qrPngBase64` para representar la imagen. El valor
firmado del QR sigue siendo generado exclusivamente por el backend.

## Entrega mediante MQTT

Para una máquina monitorizada, el backend publica una orden `TICKET_ISSUE` dirigida a su identidad.
La carga incluye la referencia compensatoria, el billete, el QR firmado, su imagen y los parámetros
necesarios para presentar el soporte. La máquina responde por el canal de acuses definido en el
[contrato MQTT](contrato-mqtt.md).

La persistencia de la emisión y del billete se realiza antes de publicar la orden. Los identificadores
de mensaje y de orden permiten procesar reintentos y respuestas duplicadas de forma idempotente.

## Auditoría y logs

El ciclo registra eventos administrativos para:

- solicitud de emisión;
- emisión completada, real o simulada;
- rechazo o fallo comunicado por la máquina.

Los logs relacionan, cuando corresponde, operador, producto, canal de entrega, máquina, estación,
pasajero, billete y referencia compensatoria. La emisión digital no inventa una máquina o estación;
la emisión simulada no inventa un código de billete.

## Seguridad

- El endpoint pertenece al área autenticada del centro de control.
- El backend vuelve a validar el producto, destino y parámetros aunque el frontend ya los haya
  comprobado.
- Las cuentas destinatarias deben estar activas.
- Solo se admiten dispositivos activos de tipo máquina de venta y con estado operativo online.
- El QR se firma en el backend y nunca se fabrica en Angular ni en la máquina.
- La operación conserva importe cero y el motivo introducido por el operador.

## Pruebas

La cobertura automatizada comprueba:

- entrega digital con billete y QR;
- emisión física simulada sin billete;
- orden física MQTT pendiente;
- confirmación y rechazo de la máquina;
- errores recuperables en el formulario;
- representación exclusiva de datos estables en la tarjeta resultante.

Documentos relacionados:

- [Catálogo de títulos de transporte](titulos-transporte.md)
- [Ciclo de vida de los billetes](ciclo-vida-billetes.md)
- [Contrato y seguridad de los códigos QR](contrato-codigos-qr.md)
- [Máquina de venta Qt](maquina-venta.md)
- [Máquinas y logs](maquinas-y-logs.md)
