# Compra de billetes desde RMM App

## Objetivo y alcance

La sección **Billetes** permite a un pasajero autenticado consultar los títulos disponibles,
configurarlos y realizar una compra simulada. El backend calcula de nuevo el precio, emite el
billete digital y conserva la compra; Android recoge la selección y presenta el resultado.

El pago `SIMULATED` no mueve dinero real. Sirve para desarrollar y probar el ciclo completo sin
integrar todavía una pasarela de pago.

## Flujo de compra

```text
Catálogo → Configuración → Resumen → Confirmación → Emisión → Billete emitido
```

1. RMM App solicita `GET /api/rmm-app/v1/ticket-products` con la sesión del pasajero.
2. El pasajero elige un producto y completa únicamente sus parámetros compatibles.
3. La aplicación calcula un importe orientativo para preparar el resumen.
4. La confirmación indica expresamente que el pago es simulado y no produce un cargo real.
5. Android envía la compra con una clave idempotente y bloquea nuevos envíos mientras espera.
6. El backend valida producto, configuración y precio, emite el billete y persiste la compra.
7. La respuesta correcta sustituye el diálogo por la representación del billete emitido.

La sesión, los códigos de estación y todos los identificadores intercambiados por la interfaz son
públicos. Android nunca utiliza identificadores internos de MySQL.

## Configuración de los productos

| Producto | Selección del pasajero | Cálculo mostrado |
| --- | --- | --- |
| `SINGLE_TRIP` | Origen y destino distintos | Base más precio por estación del trayecto |
| `MULTI_TRIP` | Número de viajes dentro del intervalo permitido | Viajes por precio unitario |
| `TIME_PASS` | Número de días dentro del intervalo permitido | Días por precio diario |
| `SMART_BALANCE` | Saldo inicial dentro del intervalo permitido | Importe seleccionado |

Las restricciones y precios proceden del catálogo. Las comprobaciones de Android mejoran la
experiencia, pero no sustituyen la validación autoritativa del backend. En particular, el número de
estaciones del billete sencillo se obtiene del planificador compartido y el servidor vuelve a
calcularlo antes de emitir.

## Solicitud de compra

```http
POST /api/rmm-app/v1/purchases
Authorization: Bearer <access-token>
Idempotency-Key: <uuid-estable-durante-los-reintentos>
Content-Type: application/json
```

Ejemplo para un bono multiviaje:

```json
{
  "productCode": "MULTI_TRIP",
  "configuration": {
    "tripCount": 10
  },
  "paymentMethod": "SIMULATED"
}
```

La clave idempotente se genera al abrir la confirmación y se conserva si la misma operación se
reintenta. El backend devuelve la compra ya creada cuando recibe de nuevo esa clave para el mismo
pasajero. Si pertenece a otra cuenta, rechaza la solicitud. Una nueva configuración crea una clave
nueva.

## Emisión y presentación del billete

Una compra correcta devuelve `201 Created`, el código público de compra, el código del billete, el
importe definitivo y las fechas de la operación. RMM App muestra inmediatamente una tarjeta con:

- nombre y tipo del producto;
- configuración comprada;
- estado inicial activo;
- identificador público del billete;
- fecha de emisión, importe y referencia de compra.

Esta vista confirma la emisión actual. Al volver al catálogo se descarta su estado de presentación;
el billete continúa guardado en el backend y se recupera desde la cartera mediante la consulta de
billetes propios.

El valor QR no forma parte de la respuesta de compra. Se obtendrá mediante
`GET /api/rmm-app/v1/tickets/{ticketCode}/qr`, con autorización y `Cache-Control: no-store`, solo
cuando el pasajero abre el diálogo del QR en la cartera. Esta separación evita exponer la credencial
verificable en respuestas, notificaciones o estados que no la necesitan.

## Errores y reintentos

Mientras se emite, el diálogo no puede cerrarse y el botón muestra el progreso. Ante un fallo se
mantienen la configuración y la clave idempotente para permitir un reintento seguro.

Los errores HTTP, de conectividad, serialización y respuesta inválida pasan por `RMMApiCallExecutor`.
La interfaz muestra un mensaje estable y no revela excepciones técnicas. Si la emisión falla, el
backend no guarda una compra completada; si la respuesta se pierde después de completarse, el
reintento idempotente recupera la operación sin emitir otro billete.

## Organización Android

```text
core/ticketcatalog/       # Catálogo y reglas públicas de los productos
core/ticketpurchase/      # Contrato y repositorio de compra
ui/screen/tickets/        # Catálogo, configuradores, confirmación y billete emitido
```

`TicketPurchaseDraft` es un estado de interfaz previo a la compra. No es una entidad persistente ni
una fuente autoritativa de precio. `PassengerTicketPurchaseResponse` representa exclusivamente el
resultado REST confirmado por el backend.

## Pruebas

La cobertura automatizada comprueba:

- los cálculos de los cuatro productos;
- la configuración enviada por Android y el método `SIMULATED`;
- las cabeceras Bearer e `Idempotency-Key`;
- la reutilización segura de una compra idempotente;
- la ausencia de persistencia cuando falla la emisión;
- la conservación de los errores HTTP de emisión.

```powershell
Set-Location backend
.\mvnw.cmd -Dtest=PassengerTicketPurchaseServiceTests test

Set-Location ..\android
.\gradlew.bat testDebugUnitTest assembleDebug
```

## Documentación relacionada

- [Arquitectura de RMM App](arquitectura-rmm-app.md)
- [Dominio de billetes](dominio-billetes.md)
- [Ciclo de vida de los billetes](ciclo-vida-billetes.md)
- [Contratos REST de RMM App](contratos-rest-rmm-app.md)
- [Seguridad de los códigos QR](contrato-codigos-qr.md)
