# Acceso y cuentas de operador

## Objetivo

El Centro de Control Operativo es una aplicación privada. Sus cuentas representan al personal que
administra la plataforma y son independientes de los futuros usuarios viajeros de RMM App.

La administración de esas cuentas de pasajeros se describe por separado en
[`usuarios-rmm-app.md`](usuarios-rmm-app.md).

La autenticación utiliza una sesión HTTP mantenida por Spring Security. El navegador no almacena la
contraseña ni un token de acceso reutilizable.

## Aprovisionamiento inicial

Los scripts SQL crean `operator_accounts`, pero no insertan usuarios ni contraseñas. Cuando la tabla
está vacía, el backend puede crear el primer administrador con estas cinco variables:

| Variable | Contenido |
| --- | --- |
| `OPERATOR_USERNAME` | Nombre de usuario, con al menos tres caracteres. |
| `OPERATOR_EMAIL` | Correo electrónico del operador. |
| `OPERATOR_PASSWORD` | Contraseña local, con al menos doce caracteres. |
| `OPERATOR_FIRST_NAME` | Nombre visible. |
| `OPERATOR_LAST_NAME` | Apellidos visibles. |

Las cinco variables deben definirse conjuntamente antes de arrancar Spring Boot. Si ya existe alguna
cuenta, el aprovisionamiento no crea ni modifica registros.

Ejemplo para PowerShell:

```powershell
$env:OPERATOR_USERNAME = "administrador_local"
$env:OPERATOR_EMAIL = "administrador@example.local"
$env:OPERATOR_PASSWORD = "contraseña-local-de-12-caracteres"
$env:OPERATOR_FIRST_NAME = "Nombre"
$env:OPERATOR_LAST_NAME = "Apellidos"
```

El ejemplo no constituye una credencial predeterminada. Cada instalación debe elegir sus propios
valores y mantenerlos fuera del repositorio.

## Datos almacenados

`operator_accounts` contiene:

- usuario, correo, nombre y apellidos;
- hash BCrypt de la contraseña, con coste 12;
- rol `OPERATOR` o `ADMINISTRATOR`;
- estado `ACTIVE`, `DISABLED` o `LOCKED`;
- intentos fallidos, fin del bloqueo y último acceso;
- fechas de creación, modificación y cambio de contraseña.

La contraseña original no puede recuperarse de la base de datos y nunca forma parte de las
respuestas de la API.

## Inicio de sesión y bloqueo

La pantalla `/login` acepta el usuario o el correo electrónico junto con la contraseña. El flujo es:

1. Angular obtiene un token CSRF mediante `GET /api/auth/csrf`.
2. Envía las credenciales a `POST /api/auth/login` con ese token.
3. El backend verifica la cuenta y crea una sesión, regenerando su identificador.
4. El navegador recibe una cookie `HttpOnly` con política `SameSite=Lax`.
5. Angular abre siempre `/dashboard`.

Una contraseña incorrecta incrementa los intentos fallidos. Al alcanzar cinco intentos, la cuenta
queda bloqueada durante 15 minutos. El backend responde sin revelar si un usuario desconocido existe.
Las cuentas desactivadas no pueden iniciar sesión.

## Sesión y protección

`GET /api/health`, `GET /api/auth/csrf` y `POST /api/auth/login` son públicos. Los demás endpoints
`/api/**` requieren una sesión autenticada.

Angular protege todas las rutas contenidas en `MainLayout`. Al abrirlas sin sesión se redirige a
`/login`. Cada petición al backend incluye la cookie mediante `withCredentials`. Si la API devuelve
`401`, el frontend elimina el estado local y vuelve al login.

La duración por defecto de una sesión inactiva es de 30 minutos. Puede configurarse con
`OPERATOR_SESSION_TIMEOUT`. En producción bajo HTTPS debe establecerse
`SESSION_COOKIE_SECURE=true`.

## Cuenta y configuración

El menú del operador en la cabecera ofrece:

- **Mi cuenta** (`/account`): identidad, rol, estado y actividad de acceso;
- **Configuración** (`/settings`): parámetros operativos de solo lectura y preferencia local para
  reducir animaciones;
- **Cerrar sesión**: invalida la sesión y regresa a `/login`.

La preferencia de accesibilidad se guarda únicamente en el navegador. No modifica la cuenta del
operador ni se sincroniza con el backend.

## Contrato de autenticación

| Método | Ruta | Acceso | Resultado |
| --- | --- | --- | --- |
| `GET` | `/api/auth/csrf` | Público | Nombre de cabecera, parámetro y token CSRF. |
| `POST` | `/api/auth/login` | Público con CSRF | Cuenta segura del operador y nueva sesión. |
| `GET` | `/api/auth/me` | Autenticado | Datos de la cuenta de la sesión. |
| `POST` | `/api/auth/logout` | Autenticado con CSRF | Respuesta `204` e invalidación de sesión. |

Los endpoints operativos devuelven `401` cuando falta una sesión válida y `403` cuando una cuenta
autenticada no dispone de autorización suficiente.

## Límites actuales

Esta fase no incluye alta de operadores desde la interfaz, modificación de datos personales,
recuperación de contraseña ni cambio de credenciales. Estas operaciones requerirán contratos
específicos, autorización administrativa y trazabilidad antes de incorporarse.
