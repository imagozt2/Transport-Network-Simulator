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

Las líneas se pueden seleccionar desde su trazado, sus etiquetas de extremo, cualquiera de sus
estaciones o el panel lateral. Al seleccionar una línea:

- su trazado y sus estaciones quedan resaltados;
- el resto de la red se atenúa;
- se abre su termómetro de estaciones ordenadas;
- se muestran las correspondencias disponibles en cada parada.

Pulsar nuevamente un tramo, una etiqueta o una estación de la misma línea elimina la selección.
También se puede deseleccionar pulsando una zona vacía del fondo del SVG. Los clics procedentes de
elementos de la red no se confunden con pulsaciones del fondo.

Las estaciones y los tramos se comportan como partes de una misma línea: al pasar el cursor se
resalta el conjunto completo y al pulsar se actualiza la selección y el acordeón lateral. La estación
no conserva un estado independiente ni queda visualmente pulsada.

Cuando una estación pertenece a varias líneas, el mapa utiliza como contexto la línea resaltada desde
la que se alcanzó la estación. Así se puede seleccionar desde un transbordo una línea distinta de la
que aparece primero en la respuesta de la API.

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
- el resaltado conjunto de tramos, etiquetas y estaciones;
- la selección, sustitución y deselección desde estaciones;
- la resolución contextual de estaciones de transbordo;
- la deselección desde el fondo vacío;
- la protección frente a falsas deselecciones por propagación de eventos.
