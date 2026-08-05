# Integración final de la aplicación web

## Objetivo

Este documento fija los contratos transversales de la aplicación web que no pertenecen a una única
sección. Complementa la documentación funcional de Panel General, Mapa de red, Líneas, Estaciones,
Trenes, Cocheras, Máquinas, Logs, títulos de transporte, usuarios e incidencias.

## Arquitectura de la comunicación

```text
Angular :4200
    │ sesión HTTP + JSON
    ▼
Spring Boot :8080
    │ JPA
    ▼
MySQL :3306
```

El navegador envía la cookie de sesión en las peticiones al backend. Las operaciones que modifican
estado requieren además el token CSRF obtenido mediante `/api/auth/csrf`. Las credenciales de MySQL
y del aprovisionamiento inicial del operador se suministran mediante variables de entorno y no
forman parte del código del frontend.

## Contratos de integración comprobados

### Operación

- Panel General, Líneas, Estaciones, Trenes y Cocheras representan una instantánea compatible del
  motor ferroviario.
- Los recuentos del Panel General proceden de los mismos repositorios y estados que las secciones
  detalladas.
- Las posiciones, sentidos y próximas llegadas conservan la misma línea y tren en todas las vistas.
- Máquinas y Logs comparten códigos estables de dispositivo y estación.

### Administración

- Solo un operador autenticado puede acceder al layout principal.
- La gestión del estado de pasajeros exige el rol `ADMINISTRATOR`.
- Cada cambio de estado registra el valor anterior, el nuevo, el motivo y el operador responsable.
- Los DTO administrativos no exponen hashes, contraseñas ni campos internos de seguridad.
- La creación y eliminación de pasajeros están restringidas a administradores y mantienen la
  separación entre cuentas de operadores y cuentas de RMM App.
- Las incidencias conservan una cronología auditable de estados y comentarios asociada al operador.
- Cada emisión compensatoria queda vinculada al producto, la máquina, el billete resultante y el
  operador solicitante, y genera un evento operativo correlacionable mediante su código.

### Planificación de trayectos

- El mapa solicita al backend el recorrido entre una estación de origen y otra de destino.
- El cálculo pondera tiempo y número de estaciones, penaliza los transbordos y evita abandonar una
  línea para regresar a ella sin una ventaja real.
- Cada tramo conserva la línea, el sentido hacia su terminal y las estaciones ordenadas; la estación
  de transbordo cierra el tramo anterior y abre el siguiente.
- El frontend utiliza ese mismo contrato para el itinerario lateral y para resaltar el recorrido en
  el mapa SVG.

### Navegación contextual

| Origen | Destino | Parámetro |
| --- | --- | --- |
| Estaciones | Máquinas | `stationCode` |
| Estaciones | Logs | `stationCode` |
| Máquinas | Logs | `deviceCode` |
| Cocheras | Trenes | `depotCode` |
| Panel General | Líneas | `lineCode` |
| Panel General | Estaciones | `stationCode` |
| Panel General | Trenes | `status` |
| Líneas | Estaciones | `lineCode` |
| Líneas | Trenes | `lineCode` o `trainCode` |
| Estaciones | Trenes | `stationCode` |

El código se conserva en la URL, en el estado interno y en el selector visible. Los valores textuales
se recortan y los parámetros vacíos no activan filtros. Logs descarta enumeraciones y fechas con un
formato no admitido.

## Sesión y rutas

`operatorAuthGuard` protege el layout completo. Un visitante anónimo es enviado a `/login`; un
operador autenticado que intenta volver al login es enviado al Panel General. Después de una
autenticación correcta se abre siempre `/dashboard`.

El cierre de sesión elimina el estado local incluso cuando falla la petición remota y devuelve al
usuario a `/login`. Cuenta y Configuración se abren desde la cabecera y no duplican entradas en el
menú operativo.

Las preferencias de idioma, densidad visual y reducción de movimiento son locales al navegador y
no forman parte de la sesión. La interfaz pública, operativa y administrativa está disponible en
español e inglés; cambiar el idioma actualiza la vista sin volver a autenticar al operador.

## Actualización, rendimiento y consistencia visual

Las consultas periódicas no se solapan y se suspenden mientras la pestaña permanece oculta. Al
recuperar la visibilidad se solicita una instantánea nueva antes de reiniciar el intervalo.

Las colecciones se renderizan con identificadores estables para conservar los nodos entre
instantáneas. Los indicadores repetidos utilizan un componente compartido con detección de cambios
`OnPush`; los colores, estados y formatos temporales se resuelven mediante utilidades comunes.

## Accesibilidad y adaptación

- enlace de salto al contenido principal;
- navegación principal identificada y botón móvil relacionado mediante `aria-controls`;
- exposición del estado del menú con `aria-expanded`;
- cierre del menú mediante selección, fondo superpuesto o tecla `Escape`;
- cabeceras de tabla asociadas como columnas;
- diálogos y estados de carga o error con roles explícitos;
- diseños de una sola columna en los puntos de ruptura más estrechos;
- soporte para la preferencia local de reducción de movimiento.

## Estrategia de pruebas

La verificación se distribuye en varios niveles:

1. pruebas unitarias de servicios, utilidades y componentes;
2. pruebas de integración de los servicios operativos y administrativos del backend;
3. coherencia transversal entre secciones ferroviarias y Panel General;
4. pruebas de rutas, guards, sesión y navegación contextual;
5. pruebas de filtros inicializados desde la URL;
6. pruebas de temporizadores, peticiones solapadas y visibilidad de la pestaña;
7. pruebas de semántica accesible y comportamiento del menú adaptable.

Comandos:

```powershell
# Backend
Set-Location backend
.\mvnw.cmd test

# Frontend
Set-Location ../frontend
npm test -- --watch=false
npm run build -- --configuration production
```

Las pruebas que arrancan el contexto completo del backend necesitan una instancia de MySQL
configurada. Las pruebas unitarias e integradas que usan dobles de repositorio no dependen de ella.

## Límites actuales

La aplicación web y sus contratos HTTP están integrados, pero todavía quedan fuera del repositorio
los simuladores externos de compra y validación en C++, la comunicación MQTT efectiva, la aplicación
Android de pasajeros y las pruebas end-to-end con navegador y backend reales.
