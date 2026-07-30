# Integración final de la aplicación web

## Objetivo

Este documento fija los contratos transversales de la aplicación web que no pertenecen a una única
sección. Complementa la documentación funcional de Panel General, Líneas, Estaciones, Trenes,
Cocheras, Máquinas, Logs, títulos de transporte y usuarios.

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

### Navegación contextual

| Origen | Destino | Parámetro |
| --- | --- | --- |
| Estaciones | Máquinas | `stationCode` |
| Estaciones | Logs | `stationCode` |
| Máquinas | Logs | `deviceCode` |
| Cocheras | Trenes | `depotCode` |

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

## Límites de esta fase

Esta fase valida la aplicación web existente, pero no añade:

- emisión compensatoria de billetes desde una máquina;
- simuladores externos de compra o validación en C++;
- integración efectiva mediante MQTT;
- aplicación Android de pasajeros;
- pruebas end-to-end con un navegador y backend reales.

Estas capacidades se incorporarán en fases posteriores sin modificar los contratos operativos aquí
documentados.
