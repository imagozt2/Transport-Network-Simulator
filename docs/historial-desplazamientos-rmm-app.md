# Historial de desplazamientos de RMM App

## Objetivo

El historial permite que cada pasajero consulte los desplazamientos construidos a partir de las
validaciones de entrada y salida de sus billetes. Spring Boot calcula y conserva el resultado;
RMM App se limita a solicitarlo y presentarlo.

No debe confundirse con los trayectos recientes del planificador, el historial de operaciones de
un billete ni los logs técnicos de las máquinas.

## Construcción del desplazamiento

Una entrada aceptada crea un `TicketJourney` en estado `OPEN` con el billete, el pasajero, la
estación y el instante de apertura. Una salida aceptada recupera ese registro y añade:

- estación e instante de salida;
- número de estaciones recorridas según el planificador de la red;
- importe correspondiente al producto;
- duración entre entrada y salida;
- estado `CLOSED`.

La entrada, la salida, los consumos y las operaciones asociadas se confirman dentro de sus
transacciones. Un fallo no puede dejar aplicado solamente parte del cambio.

## Estaciones e importes

`TicketJourneySettlementService` centraliza el cálculo para impedir interpretaciones diferentes.

| Producto | Estaciones | Importe del desplazamiento |
| --- | --- | --- |
| `SINGLE_TRIP` | Recorrido real entre entrada y salida. | Precio del trayecto configurado. |
| `MULTI_TRIP` | Recorrido real. | Precio de un viaje; se consume en la entrada. |
| `TIME_PASS` | Recorrido real. | `0,00 EUR`, porque el derecho procede de la vigencia. |
| `SMART_BALANCE` | Recorrido real. | Base más precio por estación, descontado en la salida. |

Si entrada y salida coinciden, el recorrido se registra como una estación sin consultar el
planificador.

## Entradas sin salida y anomalías

El backend regulariza un trayecto sin salida sin inventar destino, distancia ni importe:

- una nueva entrada cierra primero el trayecto anterior como `FORCED_CLOSED`;
- el proceso periódico hace lo mismo cuando lleva abierto más de seis horas;
- el límite se configura con `app.ticketing.maximum-open-journey-duration`;
- la revisión usa `app.ticketing.open-journey-review-interval-ms`.

Una salida sin entrada se rechaza con `ENTRY_REQUIRED`. Los cierres forzosos conservan la fecha de
regularización y se presentan como incidencias en RMM App.

## API privada

```http
GET /api/rmm-app/v1/journeys/history?limit=20&cursor=RMM-JRN-...
Authorization: Bearer <access-token>
```

La consulta solamente devuelve trayectos cuyo `passenger_account_id` corresponde a la sesión. Un
cursor ajeno o inexistente se rechaza sin revelar datos de otra cuenta.

```json
{
  "items": [
    {
      "code": "RMM-JRN-...",
      "ticketCode": "RMM-TKT-...",
      "productName": "Saldo inteligente",
      "productType": "SMART_BALANCE",
      "origin": { "code": "ST001", "name": "Aeropuerto" },
      "destination": { "code": "ST010", "name": "Gueto Norte" },
      "status": "CLOSED",
      "stationCount": 7,
      "fareAmount": 0.60,
      "currency": "EUR",
      "openedAt": "2026-08-11T10:00:00",
      "endedAt": "2026-08-11T10:20:00",
      "durationSeconds": 1200,
      "anomalous": false
    }
  ],
  "nextCursor": null
}
```

La paginación se ordena por apertura descendente. El código del último trayecto funciona como
cursor y el cliente solo carga páginas anteriores cuando el pasajero lo solicita.

## Presentación en Android

La pestaña **Historial** de **Trayectos** muestra origen, destino, fecha, estado, estaciones,
duración, importe y título. Al seleccionar una tarjeta se abre el detalle con códigos de estación,
horas, billete, referencia y explicación del cierre anómalo. El detalle reutiliza los datos de la
página y no realiza una segunda petición.

## Verificación

- `TicketJourneyTests`: construcción, cierre y transiciones inválidas.
- `TicketJourneySettlementServiceTests`: estaciones e importes de los cuatro productos.
- `TicketJourneyAnomalyServiceTests`: cierres forzosos inmediatos y periódicos.
- `PassengerJourneyHistoryRepositoryTest`: autenticación y cursor Android.

## Documentos relacionados

- [Dominio de billetes](dominio-billetes.md)
- [Ciclo de vida de los billetes](ciclo-vida-billetes.md)
- [Contratos REST de RMM App](contratos-rest-rmm-app.md)
- [Arquitectura de RMM App](arquitectura-rmm-app.md)
