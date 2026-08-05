# Aplicación web del Centro de Control RMM

Frontend Angular del centro de control de la Red de Metro de Macegocia. La aplicación presenta una
vista unificada de la simulación ferroviaria, las máquinas, los logs, los títulos de transporte y
la administración de usuarios de RMM App.

## Requisitos

- Node.js 20 o posterior;
- npm 10 o una versión compatible con `package-lock.json`;
- backend disponible en `http://localhost:8080`.

## Puesta en marcha

Desde `frontend/`:

```powershell
npm install
npm start
```

La aplicación queda disponible en `http://localhost:4200`. Si PowerShell impide ejecutar
`npm.ps1`, se pueden usar los comandos equivalentes `npm.cmd install` y `npm.cmd start`.

Todas las secciones, excepto `/login`, requieren una sesión válida de operador. Después de iniciar
sesión se abre siempre `/dashboard`.

## Secciones

| Ruta | Contenido |
| --- | --- |
| `/dashboard` | Indicadores y resúmenes vivos de la red. |
| `/network-map` | Mapa SVG interactivo de líneas y estaciones. |
| `/lines` | Frecuencias, cocheras, recorridos y circulación por sentido. |
| `/stations` | Estado, máquinas, circulación y próximas llegadas. |
| `/trains` | Flota, filtros y ubicación operativa de cada tren. |
| `/depots` | Ocupación, distribución de flota y agenda de movimientos. |
| `/transport-titles` | Catálogo y reglas de los productos tarifarios. |
| `/users` | Consulta y gestión administrativa de pasajeros. |
| `/incidents` | Consulta, detalle y gestión del ciclo de incidencias. |
| `/devices` | Inventario y estado operativo de las máquinas. |
| `/logs` | Consulta filtrada y paginada de eventos de máquinas. |
| `/account` | Datos de la cuenta del operador autenticado. |
| `/settings` | Idioma y preferencias locales de presentación y accesibilidad. |

El mapa incluye un planificador de trayectos. El itinerario calculado por el backend se presenta por
tramos, muestra el sentido de cada línea y se representa sobre la red sin añadir transbordos
innecesarios.

## Navegación contextual

Algunas pantallas enlazan con otras conservando el contexto mediante parámetros de consulta:

```text
/stations -> /devices?stationCode=ST001
/stations -> /logs?stationCode=ST001
/devices  -> /logs?deviceCode=RMM-MB-ST001-001
/depots   -> /trains?depotCode=DEP-AIR-A
/lines    -> /trains?lineCode=L1
/stations -> /trains?stationCode=ST001
```

Las pantallas receptoras normalizan los códigos, inicializan el control visible y aplican el mismo
valor a los resultados o a la petición del backend. Un parámetro vacío equivale a no filtrar.

## Actualización y renderizado

`PeriodicRefresh` centraliza las actualizaciones periódicas:

- evita solicitudes solapadas;
- detiene los temporizadores al abandonar una sección;
- suspende las consultas cuando la pestaña está oculta;
- actualiza inmediatamente al recuperar la visibilidad.

Las listas utilizan identificadores estables en los bloques `@for`, de modo que Angular conserva los
nodos existentes cuando recibe una nueva instantánea. Las tarjetas de indicadores comunes de
Líneas, Estaciones, Trenes y Cocheras utilizan `SummaryCard`.

## Accesibilidad y diseño adaptable

- El documento y los controles están etiquetados en español.
- Existe un enlace para saltar directamente al contenido principal.
- El menú móvil comunica su estado mediante `aria-expanded` y puede cerrarse con `Escape`.
- Los iconos decorativos se ocultan a las tecnologías de asistencia.
- Las tablas identifican semánticamente sus cabeceras de columna.
- La preferencia «Reducir animaciones» se guarda únicamente en el navegador.
- El idioma y la densidad visual también se conservan localmente y no alteran la cuenta del operador.
- El sidebar pasa a modo superpuesto hasta 900 píxeles y las cuadrículas reducen progresivamente sus
  columnas en pantallas estrechas.

## Pruebas y compilación

```powershell
npm test -- --watch=false
npm run build -- --configuration production
```

La cobertura incluye servicios HTTP, sesión y guards, rutas, navegación contextual, filtros
inicializados desde la URL, refresco periódico, presentación operativa, accesibilidad y layout
adaptable.

## Organización principal

```text
src/app/
├── core/       # Modelos, servicios, guards, interceptor y utilidades
├── features/   # Pantallas funcionales y sus pruebas
├── layout/     # Cabecera, sidebar y layout protegido
└── shared/     # Componentes visuales reutilizables
```

La documentación transversal de la aplicación se encuentra en
[`../docs/integracion-aplicacion-web.md`](../docs/integracion-aplicacion-web.md).
