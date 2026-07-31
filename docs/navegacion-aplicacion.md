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

Angular aplica la clase `active` exclusivamente al enlace cuya ruta coincide exactamente con la URL
actual. De esta manera, el menú indica siempre la sección que está abierta.

Las navegaciones contextuales pueden incluir parámetros de consulta. Por ejemplo, una tarjeta de
Máquinas puede abrir `/logs` con el filtro de esa máquina. Estos parámetros modifican el contenido
inicial de la pantalla de destino, pero no crean una sección distinta en el menú.

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

## Incorporación de nuevas secciones

Para añadir una sección navegable se deben realizar conjuntamente estos cambios:

1. registrar su ruta hija dentro de `MainLayout` en `app.routes.ts`;
2. añadir una única opción al grupo funcional adecuado en `sidebar.ts`;
3. asignar una etiqueta descriptiva y un icono exclusivamente decorativo;
4. comprobar el estado activo y el cierre del menú móvil;
5. actualizar las pruebas de rutas, orden y enlaces del sidebar;
6. actualizar esta tabla de navegación.

No deben añadirse al menú pantallas que todavía no dispongan de ruta y funcionalidad. Las acciones
contextuales relacionadas con una entidad deben enlazar a la sección existente mediante filtros en
la URL, en lugar de duplicar opciones de navegación.
