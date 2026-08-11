# Cartera de billetes de RMM App

La cartera es la vista privada desde la que un pasajero consulta los billetes asociados a su cuenta,
presenta el QR de los soportes digitales y registra soportes físicos. El backend sigue siendo la
fuente de verdad para la titularidad, los derechos disponibles, la vigencia y el estado.

## Contenido de la cartera

RMM App obtiene los billetes mediante `GET /api/rmm-app/v1/tickets`. La consulta admite filtros por
estado y tipo de producto, utiliza cursores y carga como máximo veinte elementos por petición. Cada
tarjeta identifica el producto y el medio `DIGITAL` o `PHYSICAL`, y adapta sus indicadores:

| Producto | Información presentada |
| --- | --- |
| `SINGLE_TRIP` | Origen, destino y número de estaciones del trayecto. |
| `MULTI_TRIP` | Saldo de viajes restante. |
| `TIME_PASS` | Inicio y fin de vigencia con fecha y hora. |
| `SMART_BALANCE` | Saldo monetario y moneda. |

Los estados `ACTIVE`, `EXHAUSTED`, `EXPIRED`, `BLOCKED` y `CANCELLED` se muestran con una etiqueta y
una explicación. Android no deduce el estado a partir del saldo o de la fecha: representa el valor
calculado por el backend. Un billete digital solo ofrece la acción de mostrar su QR mientras está
activo.

## QR de un billete digital

El QR no viaja en el listado ni se conserva en el estado general de la pantalla. Al pulsar
**Mostrar QR**, RMM App solicita:

```http
GET /api/rmm-app/v1/tickets/{ticketCode}/qr
Authorization: Bearer <access-token>
```

El backend comprueba la titularidad y devuelve la credencial activa con `Cache-Control: no-store`.
La aplicación genera la imagen localmente, aumenta temporalmente el brillo y aplica
`FLAG_SECURE` para impedir capturas mientras el diálogo está abierto. Al cerrarlo se descarta el
valor, se restaura el brillo y se retira la protección si no estaba activa previamente.

RMM App no firma, renueva ni decide la validez criptográfica del código. Esa responsabilidad
pertenece al backend y a las máquinas validadoras.

## Registro de un billete físico

Un soporte físico puede utilizarse sin cuenta, pero su propietario puede incorporarlo a la cartera:

1. RMM App abre Google Code Scanner y limita la lectura al formato QR.
2. El cliente descarta valores que no empiecen por `RMM:TICKET:`, superen 4096 caracteres o incluyan
   caracteres de control.
3. El pasajero introduce el código privado impreso junto al QR.
4. Android envía ambas pruebas mediante `POST /api/rmm-app/v1/ticket-links` y una nueva
   `Idempotency-Key`.
5. El backend verifica firma, estado, código, caducidad y ausencia de otro propietario en una única
   transacción.
6. Tras recibir `201 Created`, la aplicación recarga la cartera desde el primer cursor.

El escáner se ejecuta mediante Google Play Services y no concede permiso de cámara directamente a
RMM App. El módulo `barcode_ui` se solicita desde el manifiesto para que esté disponible antes del
primer uso. El código privado se mantiene únicamente en el estado efímero del diálogo y se oculta
durante la escritura.

Los errores no indican si falló la firma o el código privado. `INVALID_LINK_CODE` agrupa ambos casos,
mientras que `TICKET_ALREADY_LINKED` y `TICKET_ALREADY_IN_WALLET` evitan apropiaciones y duplicados.
Repetir la misma solicitud con la misma clave devuelve el resultado anterior; reutilizarla con otros
datos se rechaza.

## Historial del billete

Cada tarjeta ofrece **Ver historial**. La consulta protegida es:

```http
GET /api/rmm-app/v1/tickets/{ticketCode}/history?limit=20&cursor=...
Authorization: Bearer <access-token>
```

El backend acepta el código y el cursor únicamente dentro del billete perteneciente al pasajero. La
respuesta ordena las operaciones de más reciente a más antigua y expone solo información destinada
al usuario:

- emisión, recarga y vinculación del soporte;
- entradas y salidas aceptadas;
- bloqueos, desbloqueos, cancelaciones y revocaciones de QR;
- estación e importe cuando sean aplicables;
- saldo, viajes o vigencia resultantes;
- fecha y hora de la operación.

Las referencias externas, identificadores internos y datos de dispositivos no se presentan en
Android. Las páginas anteriores se solicitan bajo demanda para evitar descargar un historial sin
límite.

## Conectividad y seguridad

La cartera necesita conexión para listar billetes, obtener el QR, vincular un soporte y consultar el
historial. No se presenta un QR antiguo cuando la petición falla. Todas las operaciones privadas usan
la sesión Bearer almacenada mediante Android Keystore y respetan el aislamiento entre pasajeros.

Las pruebas JVM de Android verifican filtros, cursores, cabeceras, respuestas HTTP y lectura previa
del QR. Las pruebas backend cubren firma inválida, propiedad, código privado e idempotencia de la
vinculación.

## Documentación relacionada

- [Compra de billetes desde RMM App](compra-billetes-rmm-app.md)
- [Seguridad de los códigos QR](contrato-codigos-qr.md)
- [Contratos REST de RMM App](contratos-rest-rmm-app.md)
- [Autenticación de RMM App](autenticacion-rmm-app.md)
- [Ciclo de vida de los billetes](ciclo-vida-billetes.md)
