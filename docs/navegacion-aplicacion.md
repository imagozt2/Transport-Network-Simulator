# Navegación de la aplicación web

## Objetivo

La aplicación web representa el centro de control de la Red de Metro de Macegocia. Su estructura
principal mantiene accesibles las secciones operativas desde un menú lateral común y reserva la
cabecera superior para la identidad general de la plataforma y la sesión del operador.

Todas las vistas funcionales se renderizan dentro de `MainLayout`, compuesto por:

- una cabecera superior;
- un menú lateral de navegación;
- el área central en la que Angular carga la ruta seleccionada.

La cabecera muestra la identidad y el rol del operador autenticado. Su menú permite abrir
`/account`, abrir `/settings` o cerrar la sesión. Estas pantallas personales no forman parte del
menú lateral porque no representan áreas operativas de la red.

## Organización del menú lateral

Las opciones se agrupan por su función dentro del centro de control:

| Grupo | Opción | Ruta | Finalidad |
| --- | --- | --- | --- |
| Operación de red | Panel general | `/dashboard` | Presenta el resumen global de la red. |
| Operación de red | Mapa de red | `/network-map` | Permite explorar líneas, estaciones y correspondencias. |
| Operación de red | Líneas | `/lines` | Muestra el estado operativo de las líneas. |
| Operación de red | Estaciones | `/stations` | Muestra estaciones, máquinas y próximas llegadas. |
| Flota y equipamiento | Trenes | `/trains` | Permite consultar la flota y su situación operativa. |
| Flota y equipamiento | Cocheras | `/depots` | Presenta la distribución y los movimientos de la flota. |
| Flota y equipamiento | Máquinas | `/devices` | Permite consultar las máquinas instaladas en las estaciones. |
| Gestión y supervisión | Títulos de transporte | `/transport-titles` | Presenta los productos tarifarios y sus reglas de uso. |
| Gestión y supervisión | Usuarios | `/users` | Consulta y gestiona las cuentas de pasajeros de RMM App. |
| Gestión y supervisión | Incidencias | `/incidents` | Registra, consulta y actualiza incidencias operativas. |
| Gestión y supervisión | Logs | `/logs` | Permite filtrar y revisar los eventos emitidos por las máquinas. |

Los iconos del menú tienen una función visual y están ocultos para las tecnologías de asistencia. El
nombre textual de cada opción es el que identifica de forma accesible su destino.

## Identidad y componentes compartidos

La identidad visual utiliza una `M` blanca sobre azul celeste tanto en el favicon como en las marcas
del inicio de sesión y del menú lateral. Esta representación identifica RMM sin introducir símbolos
distintos entre la pestaña del navegador y la aplicación.

Los enlaces que conectan una entidad con otra sección reutilizan la clase global `context-link`. Se
presentan con fondo negro y texto blanco para no confundirse con los colores de las líneas. Este
componente se utiliza, entre otros casos, para abrir las máquinas o los logs de una estación, los
logs de una máquina y los trenes de una cochera. El estilo compartido no sustituye los parámetros de
la URL que inicializan el filtro de la pantalla de destino.

## Comportamiento de las rutas

La ruta raíz, `/`, redirige a `/dashboard`. Las direcciones que no correspondan con una sección
registrada también redirigen al Panel general. Todas las rutas incluidas en `MainLayout` requieren
una sesión de operador válida; un acceso anónimo redirige a `/login`. Después de autenticarse se
abre siempre el Panel general, sin restaurar la sección utilizada en una sesión anterior.

`ActiveSectionService` centraliza la detección de la sección abierta. El servicio observa las
navegaciones completadas por Angular, obtiene el primer segmento de la ruta primaria y lo compara
con el destino de cada opción del menú. El sidebar no interpreta la URL por su cuenta.

La comparación ignora los parámetros de consulta, los fragmentos y los demás datos contextuales.
Por tanto, `/stations`, `/stations?lineCode=L3` y
`/stations?lineCode=L3&stationCode=ST001` mantienen seleccionada la misma opción **Estaciones**. Al
cambiar a `/trains?lineCode=L3`, Estaciones deja de estar activa y se selecciona **Trenes**.

El único enlace activo recibe la clase `active` y el atributo accesible `aria-current="page"`. Así,
el estado visible del menú y el estado anunciado por las tecnologías de asistencia representan la
misma sección.

## Rutas y navegación contextual

`APPLICATION_ROUTES`, ubicado en `core/navigation/application-routes.ts`, es el catálogo común de
destinos. El menú lateral y los botones contextuales utilizan sus valores en lugar de repetir rutas
literales. De este modo, ambos tipos de navegación permanecen alineados si cambia una dirección.

Las acciones contextuales añaden únicamente los parámetros admitidos por la pantalla receptora:

| Origen | Destino | Contexto conservado |
| --- | --- | --- |
| Panel general | Líneas, Trenes, Cocheras o Máquinas | Sección operativa elegida. |
| Líneas | Trenes | `lineCode` o `trainCode`. |
| Líneas | Cocheras | `lineCode`. |
| Líneas | Estaciones | `lineCode` o `stationCode`. |
| Estaciones | Trenes | `lineCode` y estado `IN_SERVICE`. |
| Estaciones | Máquinas o Logs | `stationCode`. |
| Cocheras | Trenes | `depotCode`. |
| Máquinas | Logs | `deviceCode`. |
| Logs | Incidencias | Datos de la máquina y del evento. |

Estos parámetros inicializan los filtros visibles y modifican el contenido de destino, pero no
crean subsecciones en el menú. La navegación directa y la contextual comparten siempre la misma
ruta canónica.

## Escritorio y dispositivos móviles

En pantallas de escritorio el menú permanece fijado en el lateral izquierdo y el contenido reserva
el espacio necesario para no quedar oculto debajo de él.

En ventanas de hasta 900 píxeles:

- el menú permanece cerrado inicialmente;
- el botón de la cabecera permite abrirlo;
- aparece un fondo superpuesto sobre el resto de la aplicación;
- seleccionar una opción o pulsar el fondo cierra el menú;
- pulsar `Escape` cierra el menú;
- el botón comunica mediante `aria-expanded` si la navegación está abierta;
- la vista seleccionada ocupa todo el ancho disponible.

Un enlace visible al recibir el foco permite saltar el menú y acceder directamente a
`#main-content`. El botón adaptable está relacionado con la navegación mediante `aria-controls`.

## Actualización de la información

Las secciones operativas actualizan sus datos periódicamente sin controles manuales en la cabecera.
Los botones de reintento solo aparecen cuando una petición falla.

Las actualizaciones se suspenden mientras la pestaña está oculta. Al recuperar la visibilidad se
solicita una instantánea nueva y se reanuda el intervalo correspondiente.

Logs conserva el botón `Actualizar`, porque se trata de una consulta filtrada y paginada que el
operador puede volver a ejecutar de forma explícita.

## Idioma y preferencias

La pantalla `/settings` permite elegir español o inglés, ajustar la densidad visual y reducir las
animaciones. Estas preferencias se guardan en el navegador y se restauran en visitas posteriores;
no modifican la cuenta del operador ni se envían al backend. Los textos públicos, operativos y
administrativos utilizan el mismo servicio de internacionalización.

## Incorporación de nuevas secciones

Para añadir una sección navegable se deben realizar conjuntamente estos cambios:

1. registrar su ruta hija dentro de `MainLayout` en `app.routes.ts`;
2. declarar su dirección canónica en `APPLICATION_ROUTES`;
3. añadir una única opción al grupo funcional adecuado en `sidebar.ts` utilizando ese catálogo;
4. asignar una etiqueta descriptiva y un icono exclusivamente decorativo;
5. reutilizar la ruta canónica en los enlaces contextuales que apunten a la nueva sección;
6. comprobar el estado activo con acceso directo y con parámetros de consulta;
7. comprobar el cierre del menú móvil y el valor de `aria-current`;
8. actualizar las pruebas de rutas, orden y enlaces del sidebar;
9. actualizar esta tabla de navegación.

No deben añadirse al menú pantallas que todavía no dispongan de ruta y funcionalidad. Las acciones
contextuales relacionadas con una entidad deben enlazar a la sección existente mediante filtros en
la URL, en lugar de duplicar opciones de navegación.
