# Sección de Títulos de transporte

La sección de Títulos de transporte presenta el catálogo de productos tarifarios de la Red de Metro
de Macegocia. Su finalidad es ofrecer al operador una vista única de las reglas económicas y de uso
que posteriormente emplearán las máquinas de venta, la aplicación Android y los procesos de
validación.

La ruta de la pantalla es `/transport-titles` y se encuentra en el grupo **Billetaje** del menú
lateral.

## Productos disponibles

El catálogo inicial se carga mediante `database/data/03_ticket_products.sql` y contiene cuatro
productos:

| Tipo | Producto | Regla económica | Condiciones |
| --- | --- | --- | --- |
| `SINGLE_TRIP` | Billete sencillo | 0,50 € + 0,05 € por estación | Requiere origen y destino. |
| `MULTI_TRIP` | Billete multiviaje | 1,00 € por viaje | Permite comprar entre 2 y 30 viajes. |
| `TIME_PASS` | Abono temporal | 2,00 € por día | Permite seleccionar entre 2 y 30 días. |
| `SMART_BALANCE` | Saldo inteligente | 0,25 € + 0,05 € por estación | Admite recargas entre 1,00 € y 100,00 €. |

Los códigos y el campo `product_type` son identificadores estables. Los nombres y descripciones son
contenido presentacional y no deben utilizarse para decidir la lógica de un producto.

### Billete sencillo

Representa un viaje concreto entre dos estaciones. El precio final se calculará a partir del precio
base y del número de estaciones del recorrido. Los campos
`requiresOriginDestination`, `basePrice` y `pricePerStation` describen esta regla.

### Billete multiviaje

Mantiene un saldo expresado en viajes. Cada entrada válida consume un viaje. Los límites permitidos
se exponen mediante `minTrips` y `maxTrips`, y `usesTripBalance` identifica el tipo de saldo.

### Abono temporal

Permite utilizar la red durante un intervalo de días. `minDays` y `maxDays` acotan la duración
seleccionable, mientras que `usesDayValidity` indica que el título se rige por fechas de validez.

### Saldo inteligente

Mantiene un saldo monetario del que se descuenta el coste de cada trayecto. Los importes admitidos
para una recarga se indican mediante `minRechargeAmount` y `maxRechargeAmount`.
`usesMoneyBalance` distingue este producto de los saldos de viajes y días.

## Pantalla web

La cabecera muestra:

- número total de productos;
- productos activos;
- productos inactivos;
- productos recargables.

Las tarjetas muestran el código, nombre, tipo, estado, descripción, tarifa, condiciones y capacidad
de recarga de cada producto. Los importes se formatean en euros con la configuración regional
española.

La pantalla permite filtrar localmente por:

- texto presente en el código, nombre o descripción;
- tipo de producto;
- estado activo o inactivo.

El catálogo contiene muy pocos elementos, por lo que el frontend realiza estos filtros sobre la
respuesta ya cargada. La API también admite filtros para otros consumidores.

## API de consulta

### Listado

```http
GET /api/transport-titles
```

Parámetros opcionales:

| Parámetro | Tipo | Descripción |
| --- | --- | --- |
| `search` | texto | Busca en código, nombre y descripción sin distinguir mayúsculas. |
| `type` | enumerado | Filtra por uno de los cuatro valores de `TicketProductType`. |
| `active` | booleano | Selecciona productos activos o inactivos. |
| `rechargeable` | booleano | Selecciona productos según permitan recarga. |

Los parámetros pueden combinarse. `titles` contiene únicamente los resultados filtrados, mientras
que `summary` mantiene los contadores del catálogo completo y añade `filteredTitles` para indicar
cuántos elementos coinciden.

Ejemplo abreviado:

```json
{
  "currency": "EUR",
  "summary": {
    "totalTitles": 4,
    "filteredTitles": 1,
    "activeTitles": 4,
    "inactiveTitles": 0,
    "byType": {
      "SINGLE_TRIP": 1,
      "MULTI_TRIP": 1,
      "TIME_PASS": 1,
      "SMART_BALANCE": 1
    }
  },
  "titles": [
    {
      "id": 1,
      "code": "SINGLE_TRIP",
      "name": "Billete sencillo",
      "type": "SINGLE_TRIP",
      "basePrice": 0.50,
      "pricePerStation": 0.05,
      "requiresOriginDestination": true,
      "rechargeable": true,
      "active": true
    }
  ]
}
```

### Consulta individual

```http
GET /api/transport-titles/{titleId}
GET /api/transport-titles/code/{code}
```

La consulta por código no distingue mayúsculas y minúsculas. Ambos endpoints responden con `404 Not
Found` cuando el producto no existe. El servicio de consulta rechaza los códigos vacíos antes de
acceder al repositorio.

## Responsabilidades por capa

| Capa | Responsabilidad |
| --- | --- |
| MySQL | Persiste las tarifas, límites, capacidades y estado del catálogo. |
| Spring Boot | Consulta, filtra y transforma `TicketProduct` en respuestas de solo lectura. |
| Angular | Presenta indicadores, filtros y tarjetas sin reproducir reglas tarifarias en el cliente. |

La API devuelve simultáneamente los importes y las capacidades booleanas porque cada consumidor
necesita saber tanto cuánto cuesta el producto como qué datos requiere para utilizarlo.

## Alcance actual

La sección implementada es de consulta. Todavía no modifica productos ni emite billetes.

La emisión administrativa gratuita prevista para incidencias deberá implementarse como una
operación transaccional independiente. Tendrá que validar:

- que el producto esté activo;
- que la máquina seleccionada sea una máquina de venta activa;
- los datos específicos requeridos por cada tipo de producto;
- la creación coherente del billete, su QR y el registro de compra con importe cero;
- la trazabilidad del motivo de la reemisión.

Separar esta operación evita que una consulta del catálogo pueda producir efectos sobre billetes o
compras.

## Pruebas

La cobertura automatizada comprueba:

- filtros combinados y contadores del catálogo;
- consultas individuales y recursos inexistentes;
- serialización del contrato REST;
- URL y método utilizados por el servicio Angular;
- indicadores, tarifas, límites y estados representados;
- filtros de la pantalla y recuperación ante errores de red.
