# Consulta de la red y planificación de trayectos en RMM App

## Objetivo

La sección **Trayectos** permite a un pasajero autenticado consultar la infraestructura pública de
RMM y calcular recorridos entre estaciones. Android presenta la información, pero el backend sigue
siendo la autoridad sobre la topología, el orden de las estaciones, los sentidos y la elección del
trayecto recomendado.

RMM App no reproduce el algoritmo ferroviario ni accede a MySQL. Todas las consultas se realizan
mediante la API REST versionada bajo `/api/rmm-app/v1/network`.

## Funciones disponibles

| Vista | Función |
| --- | --- |
| Mapa | Representa las seis líneas y sus estaciones con la geometría visual de RMM. |
| Ruta | Selecciona origen y destino, calcula el recorrido y permite guardarlo. |
| Líneas | Muestra código, nombre, color, terminales y número de estaciones. |
| Estaciones | Busca estaciones por nombre o código y permite filtrar por línea. |

El catálogo solo incluye líneas y estaciones activas. Los colores y códigos proceden del backend y
se reutilizan en el mapa, las etiquetas y la representación del trayecto.

## Carga del catálogo

Al entrar en Trayectos, `PassengerNetworkRepository` solicita los recursos que forman el catálogo:

```http
GET /api/rmm-app/v1/network/lines
GET /api/rmm-app/v1/network/stations
Authorization: Bearer <access-token>
```

La interfaz distingue carga, error de conectividad, respuesta HTTP inválida y contenido correcto.
La opción **Reintentar** vuelve a solicitar el catálogo sin abandonar la sección. Los modelos móviles
contienen únicamente códigos públicos: no exponen identificadores internos, inventario técnico ni
información administrativa.

## Mapa de red

`NetworkMapView` combina el catálogo recibido con `NetworkMapGeometry`, que contiene exclusivamente
las coordenadas de dibujo. La geometría no decide qué estaciones pertenecen a una línea ni cuál es
su estado; esos datos proceden del backend.

El mapa permite desplazar y ampliar el lienzo. Las etiquetas aparecen al alcanzar una escala útil y
el control **Restablecer** recupera el encuadre inicial. Las estaciones de transbordo comparten el
mismo punto geométrico para que las líneas coincidan visualmente.

## Búsqueda de estaciones

El buscador funciona localmente sobre el catálogo ya descargado:

- ignora mayúsculas, espacios exteriores y tildes;
- acepta nombres completos, fragmentos y códigos como `ST016`;
- combina el texto con un filtro opcional de línea;
- identifica visualmente la estación seleccionada.

El mismo componente se reutiliza para explorar estaciones y para seleccionar el origen o destino.

## Cálculo del trayecto

La vista Ruta exige dos estaciones diferentes. El usuario puede intercambiar origen y destino antes
de calcular:

```http
GET /api/rmm-app/v1/network/journeys?origin=ST046&destination=ST002
Authorization: Bearer <access-token>
```

El backend aplica el algoritmo compartido con el centro de control. La respuesta incluye origen,
destino, duración estimada en segundos, estaciones, transbordos y los tramos ordenados. Cada tramo
conserva su línea, el terminal real que define el sentido, las estaciones recorridas, el número de
paradas y su duración.

Android redondea hacia arriba únicamente la presentación en minutos. No modifica la duración
recibida ni vuelve a elegir otra ruta.

## Representación de tramos y transbordos

Cada tramo se dibuja como un termómetro vertical con el color canónico de su línea. Las estaciones
inicial y final tienen mayor jerarquía y las intermedias conservan su orden. La cabecera muestra la
línea, la dirección hacia el terminal real, la duración y el número de paradas.

Cuando hay un cambio de línea, la última estación del tramo anterior debe coincidir con la primera
del siguiente. RMM App inserta un bloque que identifica la estación de transbordo y las líneas que se
abandonan y se toman. Una respuesta con tramos desconectados no se considera representable.

## Trayectos recientes y favoritos

Un cálculo correcto se añade automáticamente a los recientes. La pareja ordenada `origen > destino`
identifica el trayecto, por lo que repetir una consulta la mueve al principio sin duplicarla.

| Colección | Límite | Actualización |
| --- | ---: | --- |
| Recientes | 10 | Automática tras un cálculo correcto. |
| Favoritos | 20 | Manual mediante **Guardar como favorito**. |

Ambas colecciones se guardan en preferencias privadas y separadas por el identificador público del
pasajero. Solo contienen códigos, nombres y fecha local de guardado; nunca tokens o credenciales.

Seleccionar un elemento guardado vuelve a consultar el backend. Así, un favorito no congela una
ruta antigua, sino que utiliza la topología y los criterios vigentes. Estas preferencias no se
sincronizan entre dispositivos ni sustituyen al historial ferroviario de billetes del backend.

## Conectividad y errores

La consulta de red y el cálculo requieren conexión. Si el cálculo falla, la selección permanece y
puede reintentarse. Las referencias recientes y favoritas siguen visibles localmente, aunque hace
falta conexión para volver a calcularlas.

## Pruebas

Las pruebas JVM verifican búsquedas normalizadas, filtros por línea, deduplicación y límites del
historial, orden y sentido de los tramos, continuidad de transbordos y consistencia del mapa:

```powershell
cd android
./gradlew.bat testDebugUnitTest assembleDebug
```

## Documentación relacionada

- [Arquitectura de RMM App](arquitectura-rmm-app.md)
- [Contratos REST de RMM App](contratos-rest-rmm-app.md)
- [Flujos online y sin conexión](flujos-conectividad.md)
- [Mapa de red del centro de control](mapa-red.md)
