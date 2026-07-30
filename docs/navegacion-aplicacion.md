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
| Vista general | Panel General | `/dashboard` | Presenta el resumen global de la red. |
| Vista general | Mapa de red | `/network-map` | Permite explorar líneas, estaciones y correspondencias. |
| Red ferroviaria | Líneas | `/lines` | Muestra el estado operativo de las líneas. |
| Red ferroviaria | Estaciones | `/stations` | Muestra estaciones, máquinas y próximas llegadas. |
| Material móvil | Trenes | `/trains` | Permite consultar la flota y su situación operativa. |
| Material móvil | Cocheras | `/depots` | Presenta la distribución y los movimientos de la flota. |
| Billetaje | Títulos de transporte | `/transport-titles` | Presenta los productos tarifarios y sus reglas de uso. |
| Equipamiento | Máquinas | `/devices` | Permite consultar las máquinas instaladas en las estaciones. |
| Supervisión | Logs | `/logs` | Permite filtrar y revisar los eventos emitidos por las máquinas. |

Los iconos del menú tienen una función visual y están ocultos para las tecnologías de asistencia. El
nombre textual de cada opción es el que identifica de forma accesible su destino.

## Comportamiento de las rutas

La ruta raíz, `/`, redirige a `/dashboard`. Las direcciones que no correspondan con una sección
registrada también redirigen al Panel General. Todas las rutas incluidas en `MainLayout` requieren
una sesión de operador válida; un acceso anónimo redirige a `/login`. Después de autenticarse se
abre siempre el Panel General, sin restaurar la sección utilizada en una sesión anterior.

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
- la vista seleccionada ocupa todo el ancho disponible.

## Actualización de la información

Las secciones operativas actualizan sus datos periódicamente sin controles manuales en la cabecera.
Los botones de reintento solo aparecen cuando una petición falla.

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
