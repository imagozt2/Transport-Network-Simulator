# Administración de usuarios de RMM App

## Objetivo

La sección **Usuarios** permite al personal del Centro de Control consultar y gestionar las cuentas
de pasajeros que, en el futuro, se registrarán mediante la aplicación Android RMM App.

Esta fase construye exclusivamente la administración desde la aplicación web. Todavía no implementa
el registro, inicio de sesión, recuperación de contraseña ni perfil personal de la aplicación
Android. Por ese motivo, una instalación nueva muestra el listado vacío y los scripts no crean
pasajeros ficticios.

## Separación entre identidades

El proyecto mantiene dos conceptos independientes:

| Cuenta | Tabla | Finalidad |
| --- | --- | --- |
| Operador | `operator_accounts` | Acceder y trabajar en el Centro de Control Operativo. |
| Pasajero | `passenger_accounts` | Utilizar los futuros servicios de RMM App. |

Una cuenta de pasajero nunca concede acceso al centro de control. Un operador tampoco se convierte
automáticamente en usuario viajero.

## Modelo de las cuentas

`passenger_accounts` almacena:

- un identificador interno y un UUID público;
- nombre, apellidos y correo electrónico;
- hash de contraseña, nunca la contraseña original;
- estado administrativo;
- fecha de verificación del correo;
- intentos fallidos y bloqueo temporal de seguridad;
- último acceso y cambio de contraseña;
- fechas de creación y modificación.

La API administrativa utiliza exclusivamente `public_id`. El identificador interno, el hash, los
intentos fallidos y los demás datos internos de seguridad no se incluyen en las respuestas.

## Estado, verificación y bloqueo temporal

Son conceptos diferentes:

- **estado administrativo**: decisión persistente tomada por un administrador;
- **verificación del correo**: confirma la titularidad del correo del pasajero;
- **bloqueo temporal**: medida automática ante futuros intentos de acceso fallidos.

Los estados administrativos son:

| Estado | Significado |
| --- | --- |
| `ACTIVE` | La cuenta está habilitada. |
| `BLOCKED` | El acceso se ha bloqueado administrativamente. |
| `DISABLED` | La cuenta se ha desactivado. |

Transiciones permitidas:

```text
ACTIVE   -> BLOCKED | DISABLED
BLOCKED  -> ACTIVE  | DISABLED
DISABLED -> ACTIVE
```

No se permite volver a asignar el mismo estado. Una cuenta desactivada debe activarse antes de poder
bloquearse. Bloquear o desactivar exige indicar un motivo administrativo.

## Auditoría

Cada modificación se guarda en `passenger_account_status_changes` dentro de la misma transacción que
actualiza la cuenta. El registro contiene:

- pasajero afectado;
- operador administrador responsable;
- estado anterior;
- estado nuevo;
- motivo normalizado;
- fecha del cambio.

Si no puede guardarse la auditoría, tampoco se confirma el cambio de estado.

## Consultas administrativas

### Listado

```http
GET /api/admin/passenger-users
```

Parámetros disponibles:

| Parámetro | Valor | Predeterminado |
| --- | --- | --- |
| `page` | Página basada en cero. | `0` |
| `size` | De 1 a 100 elementos. | `20` |
| `search` | Nombre, apellidos, nombre completo, correo o UUID. | Sin búsqueda |
| `status` | `ACTIVE`, `BLOCKED` o `DISABLED`. | Todos |
| `emailVerified` | `true` o `false`. | Ambos |
| `sortBy` | `registeredAt`, `name`, `email`, `status` o `lastLoginAt`. | `registeredAt` |
| `direction` | `ASC` o `DESC`. | `DESC` |

La respuesta contiene un resumen global, la página solicitada y sus metadatos. El resumen no cambia
al aplicar filtros, de modo que los indicadores siempre representan el conjunto completo.

### Detalle

```http
GET /api/admin/passenger-users/{publicId}
```

Devuelve la identidad segura, estado, verificación, registro, modificación y último acceso. Un UUID
desconocido produce `404`.

### Cambio de estado

```http
PATCH /api/admin/passenger-users/{publicId}/status
Content-Type: application/json
X-XSRF-TOKEN: ...

{
  "status": "BLOCKED",
  "reason": "Motivo administrativo"
}
```

La operación requiere protección CSRF y rol `ADMINISTRATOR`. Las transiciones redundantes o no
permitidas producen `409`; omitir un motivo obligatorio produce `400`.

## Permisos

| Operación | `OPERATOR` | `ADMINISTRATOR` |
| --- | --- | --- |
| Consultar resumen y listado | Sí | Sí |
| Consultar el detalle | Sí | Sí |
| Activar una cuenta | No | Sí |
| Bloquear una cuenta | No | Sí |
| Desactivar una cuenta | No | Sí |

El backend aplica el permiso en Spring Security y vuelve a comprobar el rol dentro del servicio. El
frontend oculta las acciones a los operadores normales, pero esta ocultación no sustituye la
autorización del servidor.

## Sección web

La ruta protegida `/users`, situada en el grupo **Administración**, ofrece:

- indicadores de cuentas totales, activas, bloqueadas, desactivadas y sin verificar;
- búsqueda y filtros combinables;
- ordenación y selección del tamaño de página;
- tabla con identidad, estado, verificación y actividad;
- panel lateral de detalle;
- confirmación y motivo para los cambios sensibles;
- estados de carga, error, reintento y ausencia de resultados.

Después de modificar una cuenta, la pantalla actualiza el detalle, la fila, los indicadores y la
página actual para conservar la coherencia con los filtros.

## Privacidad y seguridad

- No se insertan pasajeros ni credenciales de ejemplo.
- Las contraseñas no se guardan ni se devuelven en texto plano.
- El UUID público evita utilizar el identificador interno en las URLs.
- Las búsquedas y ordenaciones se limitan a una lista de campos permitidos.
- Las mutaciones requieren sesión administrativa y token CSRF.
- Todo cambio de estado conserva trazabilidad.

El registro, la verificación, las sesiones, la recuperación y el aislamiento respecto al centro de
control se describen en [Autenticación de RMM App](autenticacion-rmm-app.md). La administración de
esta pantalla no comparte credenciales ni sesiones con esos flujos móviles.
