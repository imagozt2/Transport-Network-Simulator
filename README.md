# Transport-Network-Simulator

## Backend database configuration

The backend connects to MySQL using environment variables. Before starting it,
define the following variables in your shell:

- `DB_USERNAME`: MySQL username.
- `DB_PASSWORD`: MySQL password.
- `DB_URL`: optional JDBC URL. It defaults to
  `jdbc:mysql://localhost:3306/transport_simulator_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`.
- `FRONTEND_URL`: optional allowed CORS origin. It defaults to
  `http://localhost:4200`.

Use [`backend/.env.example`](backend/.env.example) as a reference. `.env` files
are ignored by Git and are not loaded automatically by Spring Boot.

For example, in PowerShell:

```powershell
$env:DB_USERNAME = "your_database_user"
$env:DB_PASSWORD = "your_database_password"
Set-Location backend
.\mvnw.cmd spring-boot:run
```

In another terminal, start the frontend:

```powershell
Set-Location frontend
npm start
```

Open `http://localhost:4200`. The initial screen is the **Panel General**, which
uses `GET /api/dashboard/summary` to show the current operational state stored
in MySQL.

The panel behaviour and the endpoint response contract are documented in
[`docs/panel-general.md`](docs/panel-general.md).

## Database initialization

The complete MySQL schema, initial Macegocia network and installation instructions are in
[`database/README.md`](database/README.md).
