# Mapa de red

El Mapa de red representa las líneas y estaciones del Metro de Macegocia mediante un diagrama SVG.
Combina la información operativa almacenada en MySQL con una geometría visual mantenida por el
frontend.

## Acceso y funcionamiento

Con el backend y el frontend en ejecución, el mapa está disponible en:

```text
http://localhost:4200/network-map
```

Al abrir la pantalla, el frontend solicita la red completa al backend. Mientras espera muestra un
estado de carga. Si la petición falla, presenta un mensaje de error y un botón para reintentarla.

La pantalla contiene dos zonas:

- el diagrama de la red, con los trazados, estaciones, transbordos y etiquetas de línea;
- un panel lateral con un acordeón y el termómetro de paradas de cada línea.

## Interacciones

Las líneas se pueden activar desde su trazado en el SVG o desde el panel lateral. Al activar una
línea:

- su trazado y sus estaciones quedan resaltados;
- el resto de la red se atenúa;
- se abre su termómetro de estaciones ordenadas;
- se muestran las correspondencias disponibles en cada parada.

Activar nuevamente la misma línea elimina el filtro. En el SVG también se puede utilizar `Enter` o la
barra espaciadora para activar una línea mediante el teclado.

Las estaciones son elementos informativos. El cursor puede situarse sobre ellas para facilitar su
localización, pero no son botones, no reciben foco y pulsarlas no crea una selección ni desencadena
ninguna acción.

## Endpoint del mapa

```http
GET /api/network-map
```

La URL local completa es `http://localhost:8080/api/network-map`. No requiere cuerpo ni parámetros de
consulta.

Ejemplo con PowerShell:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/network-map" -Method Get
```

Una respuesta correcta devuelve `200 OK`. El siguiente ejemplo es ilustrativo:

```json
{
  "lines": [
    {
      "id": 1,
      "code": "L1",
      "name": "Línea 1",
      "color": "Roja",
      "stations": [
        {
          "id": 30,
          "code": "ST030",
          "name": "Plaza de la Mina",
          "stationOrder": 1
        },
        {
          "id": 23,
          "code": "ST023",
          "name": "Las Fuentes",
          "stationOrder": 2
        }
      ]
    }
  ]
}
```

Si no existen líneas activas, el endpoint mantiene el contrato y responde:

```json
{
  "lines": []
}
```

## Contrato de respuesta

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `lines` | lista | Líneas activas, ordenadas por código. |
| `lines[].id` | número | Identificador interno de la línea. |
| `lines[].code` | texto | Código estable de la línea, por ejemplo `L1`. |
| `lines[].name` | texto | Nombre visible de la línea. |
| `lines[].color` | texto | Nombre del color asociado a la línea. |
| `lines[].stations` | lista | Estaciones activas de la línea, ordenadas por recorrido. |
| `lines[].stations[].id` | número | Identificador interno de la estación. |
| `lines[].stations[].code` | texto | Código estable de estación, por ejemplo `ST030`. |
| `lines[].stations[].name` | texto | Nombre visible de la estación. |
| `lines[].stations[].stationOrder` | número | Posición de la estación dentro de la línea. |

Una línea activa puede aparecer con `stations` vacío. Las relaciones de línea desactivadas y las
estaciones inactivas no se incluyen en la respuesta.

## Datos visuales y códigos estables

El backend no almacena coordenadas de presentación. Su responsabilidad es devolver la estructura
lógica actual de la red. El archivo frontend `network-map.data.ts` contiene:

- el `viewBox` del SVG;
- las coordenadas y etiquetas de las 50 estaciones;
- los colores y trazados de las seis líneas;
- la posición de las etiquetas situadas en los extremos.

Ambas fuentes se relacionan mediante `stationCode`. No se utilizan IDs autoincrementales para unir la
respuesta con la geometría, porque pueden variar entre instalaciones de la base de datos. Los códigos
`ST001` a `ST050` son las claves estables compartidas por los scripts de datos, la API y el mapa.

Las estaciones de transbordo se calculan en el frontend comprobando en cuántas líneas aparece cada
código. La geometría solo define la posición; el nombre, el orden y las correspondencias proceden de
la respuesta del backend.

## Componentes relacionados

- Backend: `NetworkMapController` expone el endpoint y `NetworkMapQueryService` construye la respuesta
  mediante consultas de solo lectura.
- Persistencia: `LineStation` representa una parada dentro de una línea y `LineStationRepository`
  recupera las paradas activas en orden.
- Frontend: `NetworkMapService` realiza la petición y `NetworkMap` combina la respuesta con
  `network-map.data.ts`.
- Base de datos: las líneas, estaciones y paradas ordenadas se cargan siguiendo
  [`../database/README.md`](../database/README.md).

## Verificación

Las pruebas cubren:

- el contrato HTTP y la serialización JSON de `GET /api/network-map`;
- la agrupación y el orden de estaciones en el backend;
- la integridad de los 50 códigos visuales y los seis trazados;
- la unión entre API y geometría por código;
- el resaltado, la navegación con teclado y la desactivación de líneas;
- la ausencia de selección al pulsar una estación.
