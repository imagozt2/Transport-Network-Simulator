# Transport Network Simulator

Simulador del centro de control de la red de metro de **Macegocia**, una ciudad ficticia. El proyecto
permite consultar el estado operativo de la infraestructura y explorar visualmente sus líneas y
estaciones desde una aplicación web.

El repositorio reconstruye de forma progresiva un prototipo anterior, aplicando una arquitectura más
controlada, pruebas automatizadas, ramas de trabajo, pull requests y un pipeline de integración
continua.

## Estado actual

La aplicación incluye actualmente:

- un Panel General en vivo con indicadores y paneles operativos de la red;
- un mapa SVG interactivo con las seis líneas y las 50 estaciones de Macegocia;
- un planificador de trayectos que prioriza el tiempo y el número de estaciones sin introducir
  transbordos innecesarios, con representación del recorrido sobre el mapa;
- una sección operativa de líneas con frecuencias, termómetros y trenes en movimiento;
- una sección de estaciones con filtros, máquinas, circulación por sentido, próximas llegadas en
  formato `mm:ss` y navegación contextual;
- una sección de trenes con filtros combinables —incluida la cochera—, clasificación de la flota y
  situación operativa en tiempo real;
- una sección de cocheras con ocupación, distribución de flota, navegación contextual a sus trenes
  y ventanas de doce horas para movimientos de entrada y salida;
- una sección de máquinas con indicadores por estado y tipo, tarjetas compactas, filtros y acceso
  contextual a sus logs;
- una pantalla global de logs con filtros combinables —incluido el tipo de máquina— y navegación
  directa entre páginas;
- una sección de títulos de transporte con el catálogo tarifario, sus reglas de uso y filtros por
  producto y estado;
- emisión compensatoria gratuita de billetes desde máquinas de venta, vinculada al operador y
  registrada en los logs operativos;
- autenticación de operadores mediante sesiones protegidas, rutas privadas y bloqueo temporal ante
  intentos fallidos;
- pantallas personales de cuenta y configuración accesibles desde la cabecera;
- una sección administrativa para consultar, filtrar y gestionar las cuentas de pasajeros de la
  futura RMM App;
- un ciclo administrativo para crear, bloquear, reactivar y eliminar cuentas de pasajeros;
- una sección de incidencias con filtros, detalle, comentarios, cambios de estado y trazabilidad del
  operador responsable;
- interfaz disponible en español e inglés, con preferencias locales persistentes de idioma,
  reducción de movimiento y densidad visual;
- recorridos ordenados y correspondencias entre líneas;
- calendarios, franjas horarias, frecuencias y tiempos de recorrido configurables;
- flota regular, de reserva e histórica diferenciada;
- un motor determinista de turnos, posiciones y movimientos de cocheras;
- un ciclo automático de eventos y estados para las máquinas de venta y validación;
- una API REST conectada a MySQL;
- datos iniciales reproducibles para la red, operaciones y productos de transporte;
- pruebas unitarias del backend y del frontend;
- escenarios integrados de operación, administración, sesión y navegación contextual;
- controles de accesibilidad, diseño adaptable y suspensión de consultas en pestañas ocultas;
- compilación automática de ambas aplicaciones mediante GitHub Actions.

El proyecto continúa en desarrollo. En fases posteriores se incorporarán nuevas funciones al centro
de control y simuladores externos para validación y compra de billetes.

## Tecnologías

| Capa | Tecnologías principales |
| --- | --- |
| Frontend | Angular 21, TypeScript, RxJS, Zone.js y SVG |
| Backend | Java 21, Spring Boot 4, Spring Web MVC y Spring Data JPA |
| Base de datos | MySQL 8 y scripts SQL versionados |
| Pruebas | JUnit, Mockito, MockMvc y Vitest |
| Integración continua | GitHub Actions |

## Estructura del repositorio

```text
Transport-Network-Simulator/
├── .github/workflows/   # Pipeline de integración continua
├── backend/             # API REST desarrollada con Spring Boot
├── database/            # Esquema, datos iniciales y verificaciones de MySQL
├── docs/                # Documentación funcional y contratos de API
└── frontend/            # Aplicación web desarrollada con Angular
```

## Requisitos

Para ejecutar el proyecto localmente se necesita:

- Java 21;
- Node.js 20 o posterior;
- npm;
- MySQL 8;
- cliente de MySQL disponible desde la terminal para cargar los scripts.

## Inicialización de la base de datos

La definición se encuentra en `database/` y debe ejecutarse en este orden:

1. `database/schema/01_create_database.sql`
2. `database/schema/02_create_tables.sql`
3. `database/data/01_transport_network.sql`
4. `database/data/02_operations.sql`
5. `database/data/03_ticket_products.sql`
6. `database/data/04_service_configuration.sql`
7. `database/verification/verify_database.sql`

Ejemplo desde PowerShell, después de definir el usuario de MySQL y asignar temporalmente la contraseña
a `MYSQL_PWD`:

```powershell
Get-Content database/schema/01_create_database.sql -Raw | mysql --user=$env:DB_USERNAME
Get-Content database/schema/02_create_tables.sql -Raw | mysql --user=$env:DB_USERNAME
Get-Content database/data/01_transport_network.sql -Raw | mysql --user=$env:DB_USERNAME
Get-Content database/data/02_operations.sql -Raw | mysql --user=$env:DB_USERNAME
Get-Content database/data/03_ticket_products.sql -Raw | mysql --user=$env:DB_USERNAME
Get-Content database/data/04_service_configuration.sql -Raw | mysql --user=$env:DB_USERNAME
Get-Content database/verification/verify_database.sql -Raw | mysql --user=$env:DB_USERNAME
Remove-Item Env:MYSQL_PWD
```

La guía completa está disponible en [`database/README.md`](database/README.md).

## Configuración segura del backend

La conexión a MySQL utiliza variables de entorno:

| Variable | Obligatoria | Descripción |
| --- | --- | --- |
| `DB_USERNAME` | Sí | Usuario de MySQL. |
| `DB_PASSWORD` | Sí | Contraseña de MySQL. |
| `DB_URL` | No | URL JDBC; dispone de un valor local predeterminado. |
| `FRONTEND_URL` | No | Origen permitido por CORS; por defecto, `http://localhost:4200`. |
| `SERVICE_TIME_ZONE` | No | Zona horaria operativa; por defecto, `Europe/Madrid`. |
| `DEVICE_EVENT_SIMULATION_ENABLED` | No | Activa la simulación automática; por defecto, `true`. |
| `DEVICE_EVENT_SIMULATION_INITIAL_DELAY_MS` | No | Retraso inicial en milisegundos; por defecto, `1000`. |
| `DEVICE_EVENT_SIMULATION_INTERVAL_MS` | No | Frecuencia fija entre ciclos; por defecto, `1000`. |
| `OPERATOR_USERNAME` | Primera ejecución | Usuario del administrador inicial. |
| `OPERATOR_EMAIL` | Primera ejecución | Correo del administrador inicial. |
| `OPERATOR_PASSWORD` | Primera ejecución | Contraseña inicial, con un mínimo de 12 caracteres. |
| `OPERATOR_FIRST_NAME` | Primera ejecución | Nombre del administrador inicial. |
| `OPERATOR_LAST_NAME` | Primera ejecución | Apellidos del administrador inicial. |
| `SESSION_COOKIE_SECURE` | No | Exige HTTPS para la cookie; por defecto, `false` en local. |
| `OPERATOR_SESSION_TIMEOUT` | No | Duración de la sesión inactiva; por defecto, `30m`. |

El archivo [`backend/.env.example`](backend/.env.example) sirve como referencia. Los archivos `.env`
están ignorados por Git y Spring Boot no los carga automáticamente.

Ejemplo para PowerShell:

```powershell
$env:DB_USERNAME = "usuario_local"
$env:DB_PASSWORD = "contraseña_local"
$env:DB_URL = "jdbc:mysql://localhost:3306/transport_simulator_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"

$env:OPERATOR_USERNAME = "administrador_local"
$env:OPERATOR_EMAIL = "administrador@example.local"
$env:OPERATOR_PASSWORD = "contraseña-local-de-12-caracteres"
$env:OPERATOR_FIRST_NAME = "Nombre"
$env:OPERATOR_LAST_NAME = "Apellidos"
```

No deben añadirse usuarios, contraseñas ni archivos `.env` al repositorio.
Las cinco variables `OPERATOR_*` solo se utilizan para aprovisionar el primer administrador cuando
`operator_accounts` está vacía. No modifican una cuenta ya creada.

## Ejecución local

### Backend

En la misma terminal donde se definieron las variables de entorno:

```powershell
Set-Location backend
.\mvnw.cmd spring-boot:run
```

La API estará disponible en `http://localhost:8080`.

### Frontend

En otra terminal:

```powershell
Set-Location frontend
npm install
npm start
```

La aplicación estará disponible en `http://localhost:4200`. Las rutas operativas requieren iniciar
sesión y una autenticación correcta abre siempre el Panel General.

## Endpoints disponibles

| Método | Ruta | Descripción |
| --- | --- | --- |
| `GET` | `/api/health` | Comprueba el estado del backend y de MySQL. |
| `GET` | `/api/auth/csrf` | Entrega el token CSRF necesario para operaciones de autenticación. |
| `POST` | `/api/auth/login` | Autentica al operador y crea su sesión. |
| `GET` | `/api/auth/me` | Devuelve la cuenta asociada a la sesión actual. |
| `POST` | `/api/auth/logout` | Invalida la sesión del operador. |
| `GET` | `/api/dashboard/summary` | Devuelve el resumen persistido legado; el Panel General utiliza las consultas operativas. |
| `GET` | `/api/network-map` | Devuelve las líneas activas y sus estaciones ordenadas. |
| `GET` | `/api/network-map/journeys` | Calcula un trayecto entre dos estaciones y devuelve sus tramos ordenados. |
| `GET` | `/api/lines/operations` | Devuelve frecuencias, cocheras, próximas llegadas, recorridos y trenes de cada línea. |
| `GET` | `/api/stations/operations` | Devuelve el estado, líneas, dispositivos y próximas llegadas de cada estación. |
| `GET` | `/api/trains/operations` | Devuelve la flota, su clasificación y la ubicación operativa de cada tren. |
| `GET` | `/api/depots/operations` | Devuelve capacidad, ocupación, distribución y movimientos de las cocheras. |
| `GET` | `/api/devices/operations` | Devuelve el inventario y estado operativo de las máquinas. |
| `GET` | `/api/logs` | Devuelve los eventos de máquinas filtrados y paginados. |
| `GET` | `/api/transport-titles` | Devuelve el catálogo de títulos y admite filtros combinables. |
| `GET` | `/api/transport-titles/{titleId}` | Consulta un título por su identificador. |
| `GET` | `/api/transport-titles/code/{code}` | Consulta un título por su código estable. |
| `POST` | `/api/transport-titles/{titleId}/compensatory-issuances` | Emite gratuitamente un billete compensatorio y audita la operación. |
| `GET` | `/api/admin/passenger-users` | Devuelve el resumen y listado paginado de pasajeros. |
| `GET` | `/api/admin/passenger-users/{publicId}` | Consulta una cuenta mediante su UUID público. |
| `POST` | `/api/admin/passenger-users` | Crea una cuenta de pasajero. |
| `PATCH` | `/api/admin/passenger-users/{publicId}/status` | Cambia y audita el estado de una cuenta. |
| `DELETE` | `/api/admin/passenger-users/{publicId}` | Elimina una cuenta de pasajero cuando cumple las reglas administrativas. |
| `GET` | `/api/incidents` | Consulta y filtra las incidencias registradas. |
| `GET` | `/api/incidents/{code}` | Devuelve el detalle y la cronología de una incidencia. |
| `POST` | `/api/incidents` | Registra una incidencia nueva. |
| `PATCH` | `/api/incidents/{code}/status` | Cambia y audita el estado de una incidencia. |
| `POST` | `/api/incidents/{code}/comments` | Añade un comentario a la cronología de una incidencia. |

## Pruebas y compilación

Backend:

```powershell
Set-Location backend
.\mvnw.cmd test
.\mvnw.cmd clean package -DskipTests
```

Frontend:

```powershell
Set-Location frontend
npm test -- --watch=false
npm run build -- --configuration production
```

El workflow de GitHub Actions compila el backend y el frontend en cada pull request dirigida a `main`
y en cada actualización de esa rama.

## Documentación

- [Arquitectura, componentes y responsabilidades del ecosistema RMM](docs/arquitectura-ecosistema.md)
- [Ciclo de vida de los billetes RMM](docs/ciclo-vida-billetes.md)
- [Contrato y firma de los códigos QR](docs/contrato-codigos-qr.md)
- [Contratos REST para RMM App](docs/contratos-rest-rmm-app.md)
- [Integración final de la aplicación web](docs/integracion-aplicacion-web.md)
- [Acceso y cuentas de operador](docs/acceso-operadores.md)
- [Administración de usuarios de RMM App](docs/usuarios-rmm-app.md)
- [Navegación y estructura de la aplicación web](docs/navegacion-aplicacion.md)
- [Visión transversal de la operación simulada](docs/operacion-simulada.md)
- [Panel General y agregación de datos operativos](docs/panel-general.md)
- [Mapa de red y endpoint del mapa](docs/mapa-red.md)
- [Sección de Líneas y endpoint operativo](docs/lineas.md)
- [Sección de Estaciones y próximas llegadas](docs/estaciones.md)
- [Sección de Trenes y situación operativa](docs/trenes.md)
- [Sección de Cocheras y movimientos de flota](docs/cocheras.md)
- [Secciones de Máquinas y Logs](docs/maquinas-y-logs.md)
- [Sección de Títulos de transporte](docs/titulos-transporte.md)
- [Modelo de operación ferroviaria](docs/modelo-operacion-ferroviaria.md)
- [Motor de simulación ferroviaria](docs/motor-simulacion-ferroviaria.md)
- [Ciclo de eventos de las máquinas](docs/eventos-maquinas.md)
- [Inicialización y estructura de la base de datos](database/README.md)

## Desarrollo por ramas

El desarrollo se realiza en ramas temáticas y se integra en `main` mediante pull requests. Los cambios
deben agruparse en commits pequeños y coherentes, y superar la compilación automática antes de ser
integrados.
