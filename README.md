# Transport Network Simulator

Simulador del centro de control de la red de metro de **Macegocia**, una ciudad ficticia. El proyecto
permite consultar el estado operativo de la infraestructura y explorar visualmente sus líneas y
estaciones desde una aplicación web.

El repositorio reconstruye de forma progresiva un prototipo anterior, aplicando una arquitectura más
controlada, pruebas automatizadas, ramas de trabajo, pull requests y un pipeline de integración
continua.

## Estado actual

La aplicación incluye actualmente:

- un Panel General con indicadores de estaciones, líneas, trenes, dispositivos y cocheras;
- un mapa SVG interactivo con las seis líneas y las 50 estaciones de Macegocia;
- una sección operativa de líneas con frecuencias, termómetros y trenes en movimiento;
- una sección de estaciones con estados, dispositivos y próximas llegadas en formato `mm:ss`;
- una sección de trenes con filtros, clasificación de la flota y situación operativa en tiempo real;
- una sección de cocheras con ocupación, distribución de flota y movimientos de entrada y salida;
- recorridos ordenados y correspondencias entre líneas;
- calendarios, franjas horarias, frecuencias y tiempos de recorrido configurables;
- flota regular, de reserva e histórica diferenciada;
- un motor determinista de turnos, posiciones y movimientos de cocheras;
- una API REST conectada a MySQL;
- datos iniciales reproducibles para la red, operaciones y productos de transporte;
- pruebas unitarias del backend y del frontend;
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

El archivo [`backend/.env.example`](backend/.env.example) sirve como referencia. Los archivos `.env`
están ignorados por Git y Spring Boot no los carga automáticamente.

Ejemplo para PowerShell:

```powershell
$env:DB_USERNAME = "usuario_local"
$env:DB_PASSWORD = "contraseña_local"
$env:DB_URL = "jdbc:mysql://localhost:3306/transport_simulator_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
```

No deben añadirse usuarios, contraseñas ni archivos `.env` al repositorio.

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

La aplicación estará disponible en `http://localhost:4200`. La ruta inicial redirige al Panel General.

## Endpoints disponibles

| Método | Ruta | Descripción |
| --- | --- | --- |
| `GET` | `/api/health` | Comprueba el estado del backend y de MySQL. |
| `GET` | `/api/dashboard/summary` | Devuelve el resumen agregado del Panel General. |
| `GET` | `/api/network-map` | Devuelve las líneas activas y sus estaciones ordenadas. |
| `GET` | `/api/lines/operations` | Devuelve el estado operativo, recorridos y trenes de cada línea. |
| `GET` | `/api/stations/operations` | Devuelve el estado, líneas, dispositivos y próximas llegadas de cada estación. |
| `GET` | `/api/trains/operations` | Devuelve la flota, su clasificación y la ubicación operativa de cada tren. |
| `GET` | `/api/depots/operations` | Devuelve capacidad, ocupación, distribución y movimientos de las cocheras. |

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

- [Panel General y endpoint de resumen](docs/panel-general.md)
- [Mapa de red y endpoint del mapa](docs/mapa-red.md)
- [Sección de Líneas y endpoint operativo](docs/lineas.md)
- [Sección de Estaciones y próximas llegadas](docs/estaciones.md)
- [Sección de Trenes y situación operativa](docs/trenes.md)
- [Sección de Cocheras y movimientos de flota](docs/cocheras.md)
- [Modelo de operación ferroviaria](docs/modelo-operacion-ferroviaria.md)
- [Motor de simulación ferroviaria](docs/motor-simulacion-ferroviaria.md)
- [Inicialización y estructura de la base de datos](database/README.md)

## Desarrollo por ramas

El desarrollo se realiza en ramas temáticas y se integra en `main` mediante pull requests. Los cambios
deben agruparse en commits pequeños y coherentes, y superar la compilación automática antes de ser
integrados.
