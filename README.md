# Transport Network Simulator

Ecosistema de simulación de la red de metro de **Macegocia**, una ciudad ficticia. Reúne un centro
de control web, una aplicación Android para pasajeros, dos máquinas Qt, una API central, MySQL y
mensajería MQTT autenticada.

El repositorio reconstruye y amplía un prototipo anterior mediante una arquitectura controlada,
contratos versionados, pruebas automatizadas, ramas de trabajo, pull requests y un pipeline de
integración continua.

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
- una sección de máquinas con indicadores por estado, tipo y conectividad MQTT, tarjetas compactas,
  filtros y acceso contextual a sus logs;
- una pantalla global de logs con filtros combinables, trazabilidad de eventos reales, simulados y
  administrativos, referencias de billetes, navegación directa entre páginas y creación contextual
  de incidencias;
- una sección de títulos de transporte con el catálogo tarifario, sus reglas de uso y filtros por
  producto y estado;
- emisión administrativa gratuita hacia carteras digitales o máquinas de venta, vinculada al
  operador y registrada en los logs operativos;
- autenticación de operadores mediante sesiones protegidas, rutas privadas y bloqueo temporal ante
  intentos fallidos;
- pantallas personales de cuenta y configuración accesibles desde la cabecera;
- una sección administrativa para consultar, filtrar y gestionar las cuentas de pasajeros de
  RMM App;
- un ciclo administrativo para crear, bloquear, reactivar y eliminar cuentas de pasajeros;
- una sección de incidencias con filtros, detalle, comentarios, cambios de estado y trazabilidad del
  operador responsable;
- interfaz disponible en español e inglés, con preferencias persistentes de zona horaria y tema por
  operador, además de idioma y reducción de movimiento locales;
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
- compilación automática del backend, el frontend, RMM App y las aplicaciones Qt mediante GitHub
  Actions.

El ecosistema funcional incluye la compra desde RMM App y la máquina de venta Qt, la cartera de
billetes, la vinculación de soportes físicos, la validación de entrada y salida mediante MQTT y el
historial de desplazamientos. Los pagos y los dispositivos son simulados y no representan sistemas
de producción.

La aceptación final automatizada recorre una compra y una recarga, una entrada válida, el rechazo de
una segunda entrada sobre el mismo billete y la salida que cierra el desplazamiento. También contrasta
el saldo, los logs MQTT y los registros persistidos. Las pruebas de interfaz cubren los temas claro y
oscuro, las preferencias de idioma, los errores administrativos recuperables y el restablecimiento
de las máquinas Qt después de cada resultado.

La [guía final de ejecución y validación](docs/guia-ecosistema-rmm.md) reúne la preparación, el
arranque de todos los componentes, las comprobaciones funcionales y automatizadas, la parada segura
y el diagnóstico del ecosistema.

El [estado final y las limitaciones](docs/estado-final-y-limitaciones.md) delimitan qué funciones
están implementadas, qué partes son simuladas y qué integraciones quedan fuera del alcance del
proyecto.

## Tecnologías

| Capa | Tecnologías principales |
| --- | --- |
| Frontend | Angular 21, TypeScript, RxJS, Zone.js y SVG |
| Backend | Java 21, Spring Boot 4, Spring Web MVC y Spring Data JPA |
| Base de datos | MySQL 8 y scripts SQL versionados |
| Aplicación Android | Kotlin, Jetpack Compose y Gradle |
| Máquinas simuladas | C++20, Qt 6, Qt Widgets, Qt MQTT y CMake |
| Pruebas | JUnit, Mockito, MockMvc, Vitest y Qt Test |
| Integración continua | GitHub Actions |

## Estructura del repositorio

```text
Transport-Network-Simulator/
├── .github/workflows/   # Pipeline de integración continua
├── android/             # Aplicación para pasajeros desarrollada con Kotlin y Compose
├── backend/             # API REST desarrollada con Spring Boot
├── config/              # Direcciones compartidas de los servicios locales
├── database/            # Esquema, datos iniciales y verificaciones de MySQL
├── docs/                # Documentación funcional y contratos de API
├── frontend/            # Aplicación web desarrollada con Angular
├── infrastructure/      # Broker MQTT y recursos de infraestructura local
└── qt/                  # Máquinas simuladas desarrolladas con Qt y C++
```

La [configuración local de servicios](config/README.md) centraliza las direcciones utilizadas por
RMM App y las aplicaciones Qt sin almacenar credenciales.

## Requisitos

Para ejecutar el proyecto localmente se necesita:

- Java 21;
- Node.js 20 o posterior;
- npm;
- MySQL 8;
- cliente de MySQL disponible desde la terminal para cargar los scripts;
- Docker Desktop con Docker Compose para ejecutar la infraestructura local coordinada.

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

### Aplicaciones cliente

RMM App y las máquinas Qt disponen de una guía específica con la configuración compartida, la
ejecución desde los IDE, los comandos de compilación y la ubicación de los artefactos:

- [Ejecución de las aplicaciones cliente](docs/ejecucion-aplicaciones-cliente.md)
- [Arquitectura de RMM App](docs/arquitectura-rmm-app.md)
- [Cartera de billetes de RMM App](docs/cartera-rmm-app.md)

### Entorno Docker

MySQL, Mosquitto y el backend pueden ejecutarse como un entorno coordinado. Antes del primer inicio,
crea la configuración local y sustituye todos sus marcadores:

```powershell
Copy-Item .env.example .env
Copy-Item infrastructure/mosquitto/mqtt-users.example `
  infrastructure/mosquitto/mqtt-users.local
.\infrastructure\mosquitto\scripts\initialize-security.ps1
docker compose up -d --build
docker compose ps
```

El backend queda disponible en `127.0.0.1:8080`, Mosquitto en `127.0.0.1:1883` y MySQL en
`127.0.0.1:3307`, evitando interferir con una instalación local de MySQL en el puerto `3306`.

Los scripts de `database/` se ejecutan automáticamente solo cuando el volumen de MySQL está vacío.
La configuración y prueba manual del broker se describen en la
[guía de Mosquitto](infrastructure/mosquitto/README.md).

Cuando exista material criptográfico aprovisionado, el archivo `.env.tls.example` permite sustituir
el listener local por MQTT sobre TLS con certificados de cliente obligatorios.

La preparación completa, la topología, las comprobaciones de salud, el ciclo de vida de los datos y
el diagnóstico se recogen en la [guía de infraestructura local](docs/infraestructura-local.md).

## Endpoints disponibles

| Método | Ruta | Descripción |
| --- | --- | --- |
| `GET` | `/api/health` | Comprueba el estado del backend y de MySQL. |
| `GET` | `/api/auth/csrf` | Entrega el token CSRF necesario para operaciones de autenticación. |
| `POST` | `/api/auth/login` | Autentica al operador y crea su sesión. |
| `GET` | `/api/auth/me` | Devuelve la cuenta asociada a la sesión actual. |
| `POST` | `/api/auth/logout` | Invalida la sesión del operador. |
| `POST` | `/api/rmm-app/v1/auth/register` | Registra una cuenta de pasajero pendiente de verificación. |
| `POST` | `/api/rmm-app/v1/auth/sessions` | Autentica al pasajero y registra su dispositivo móvil. |
| `POST` | `/api/rmm-app/v1/auth/session-refreshes` | Rota los tokens de una sesión móvil. |
| `GET` | `/api/rmm-app/v1/me` | Devuelve la cuenta del pasajero autenticado. |
| `GET` | `/api/rmm-app/v1/me/sessions` | Lista las sesiones móviles activas de la cuenta. |
| `GET` | `/api/rmm-app/v1/me/devices` | Lista los dispositivos Android registrados. |
| `GET` | `/api/rmm-app/v1/tickets` | Lista y filtra los billetes del pasajero autenticado. |
| `GET` | `/api/rmm-app/v1/tickets/{ticketCode}/qr` | Entrega bajo demanda el QR digital sin permitir caché. |
| `GET` | `/api/rmm-app/v1/tickets/{ticketCode}/history` | Devuelve el historial paginado de un billete propio. |
| `GET` | `/api/rmm-app/v1/journeys/history` | Devuelve los desplazamientos paginados del pasajero autenticado. |
| `POST` | `/api/rmm-app/v1/ticket-links` | Vincula un soporte físico mediante QR y código privado. |
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

El workflow de GitHub Actions compila el backend, el frontend, RMM App y las aplicaciones Qt en cada
pull request dirigida a `main` o `develop/ecosystem` y en cada actualización de esas ramas. También
ejecuta las pruebas de Android, Qt y MQTT, conserva temporalmente sus artefactos y valida en
contenedores aislados la integración del backend con MySQL y Mosquitto.

La validación integral de contenedores puede reproducirse localmente con Docker Desktop iniciado:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\infrastructure\tests\ecosystem-container-tests.ps1
```

## Documentación

- [Estado final y limitaciones del proyecto](docs/estado-final-y-limitaciones.md)
- [Guía final de ejecución y validación del ecosistema RMM](docs/guia-ecosistema-rmm.md)
- [Arquitectura, componentes y responsabilidades del ecosistema RMM](docs/arquitectura-ecosistema.md)
- [Infraestructura local del ecosistema RMM](docs/infraestructura-local.md)
- [Ejecución de RMM App y las aplicaciones Qt](docs/ejecucion-aplicaciones-cliente.md)
- [Máquina de venta de billetes Qt](docs/maquina-venta.md)
- [Máquina validadora de billetes Qt](docs/maquina-validadora.md)
- [Ciclo de vida de los billetes RMM](docs/ciclo-vida-billetes.md)
- [Dominio de billetes y reglas de los productos](docs/dominio-billetes.md)
- [Emisión administrativa de billetes](docs/emision-administrativa-billetes.md)
- [Seguridad, contrato y firma de los códigos QR](docs/contrato-codigos-qr.md)
- [Contratos REST para RMM App](docs/contratos-rest-rmm-app.md)
- [Autenticación, sesiones y dispositivos de RMM App](docs/autenticacion-rmm-app.md)
- [Cartera, QR e historial de billetes en RMM App](docs/cartera-rmm-app.md)
- [Compra de billetes desde RMM App](docs/compra-billetes-rmm-app.md)
- [Consulta de la red desde RMM App](docs/consulta-red-rmm-app.md)
- [Historial de desplazamientos en RMM App](docs/historial-desplazamientos-rmm-app.md)
- [Topics y mensajes MQTT del ecosistema RMM](docs/contrato-mqtt.md)
- [Integración MQTT del backend](docs/integracion-mqtt-backend.md)
- [Identidad y autenticación de las máquinas RMM](docs/identidad-maquinas.md)
- [Flujos online y sin conexión del ecosistema RMM](docs/flujos-conectividad.md)
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

## Galería de imágenes

<p align="center">
  <img src="https://i.postimg.cc/63bxqSs9/Captura-de-pantalla-2026-08-31-122238.png" alt="Inicio de sesión" width="100%">
</p>

<p align="center">
  <img src="https://i.postimg.cc/06BT46Gr/Captura-de-pantalla-2026-08-31-111748.png" alt="Panel general" width="100%">
</p>

<p align="center">
  <img src="https://i.postimg.cc/jWFp1W6J/Captura-de-pantalla-2026-08-31-113204.png" alt="Mapa de red" width="100%">
</p>

<p align="center">
  <img src="https://i.postimg.cc/9Rw6HW1m/Captura-de-pantalla-2026-08-31-113311.png" alt="Panel de líneas" width="100%">
</p>

<p align="center">
  <img src="https://i.postimg.cc/gwTC7wyX/Captura-de-pantalla-2026-08-31-113334.png" alt="Panel de estaciones" width="100%">
</p>

<p align="center">
  <img src="https://i.postimg.cc/KKH6WK73/Captura-de-pantalla-2026-08-31-113412.png" alt="Panel de trenes" width="100%">
</p>

<p align="center">
  <img src="https://i.postimg.cc/FdkMX9GR/Captura-de-pantalla-2026-08-31-113442.png" alt="Panel de cocheras" width="100%">
</p>

<p align="center">
  <img src="https://i.postimg.cc/ZBvGzTj9/Captura-de-pantalla-2026-08-31-113510.png" alt="Panel de máquinas" width="100%">
</p>

<p align="center">
  <img src="https://i.postimg.cc/bDS7fzLZ/Captura-de-pantalla-2026-08-31-113535.png" alt="Panel de títulos de transporte" width="100%">
</p>

<p align="center">
  <img src="https://i.postimg.cc/WFqBc2Sq/Captura-de-pantalla-2026-08-31-113552.png" alt="Panel de usuarios" width="100%">
</p>

<p align="center">
  <img src="https://i.postimg.cc/bDS7fzLn/Captura-de-pantalla-2026-08-31-113617.png" alt="Panel de incidencias" width="100%">
</p>

<p align="center">
  <img src="https://i.postimg.cc/cgtpGsTR/Captura-de-pantalla-2026-08-31-113644.png" alt="Panel de logs" width="100%">
</p>

<p align="center">
  <img src="https://i.postimg.cc/ph5wM2kQ/Captura-de-pantalla-2026-08-31-113921.png" alt="Pantalla de inicio de máquina de billetes" width="100%">
</p>

<p align="center">
  <img src="https://i.postimg.cc/XGBR6Vxk/Captura-de-pantalla-2026-08-31-113946.png" alt="Menú de títulos en la máquina de billetes" width="100%">
</p>

<p align="center">
  <img src="https://i.postimg.cc/mz1vWbVw/Captura-de-pantalla-2026-08-31-114236.png" alt="Máquina validadora en estado de espera" width="100%">
</p>

<p align="center">
  <img src="https://i.postimg.cc/CBn3pFJJ/Captura-de-pantalla-2026-08-31-114537.png" alt="Máquina validadora en estado de validación" width="100%">
</p>

<p align="center">
  <img src="https://i.postimg.cc/mz1vWbVm/Captura-de-pantalla-2026-08-31-121118.png" alt="Capturas de la app Android 1" width="100%">
</p>

<p align="center">
  <img src="https://i.postimg.cc/dkZbcqnW/Captura-de-pantalla-2026-08-31-121208.png" alt="Capturas de la app Android 2" width="100%">
</p>