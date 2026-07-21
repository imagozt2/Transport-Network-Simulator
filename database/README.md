# Base de datos

Definición de MySQL 8 y datos iniciales de la red de transporte de Macegocia.

## Estructura

- `schema/01_create_database.sql`: crea la base de datos con codificación UTF-8.
- `schema/02_create_tables.sql`: crea el modelo relacional completo.
- `data/01_transport_network.sql`: estaciones, líneas, paradas ordenadas y conexiones.
- `data/02_operations.sql`: dispositivos, material rodante y cocheras.
- `data/03_ticket_products.sql`: catálogo inicial de productos de transporte.
- `data/04_service_configuration.sql`: calendarios, franjas, frecuencias, tiempos y cocheras por línea.
- `migrations/01_add_dispatch_terminal_to_line_depots.sql`: adapta una base existente para asignar
  a cada cochera su terminal de expedición.
- `migrations/02_set_station_dwell_to_20_seconds.sql`: normaliza a 20 segundos las paradas de una
  base existente.
- `verification/verify_database.sql`: recuentos esperados y comprobaciones de integridad.

## Orden de instalación

Los archivos deben ejecutarse en el orden anterior. Desde PowerShell se puede pasar cada archivo al
cliente de MySQL mediante la entrada estándar. Por ejemplo, después de definir `DB_USERNAME` y asignar
temporalmente `MYSQL_PWD`:

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

Los scripts de esquema están destinados a una base nueva y no son repetibles de forma intencionada.
Los scripts de datos utilizan claves naturales y pueden ejecutarse de nuevo para actualizar los
registros mantenidos sin duplicarlos.

La configuración ferroviaria se explica en
[`docs/modelo-operacion-ferroviaria.md`](../docs/modelo-operacion-ferroviaria.md).

Si la base se creó antes de incorporar los terminales de expedición, debe ejecutarse una sola vez la
migración y volver a cargar la configuración operativa:

```powershell
Get-Content database/migrations/01_add_dispatch_terminal_to_line_depots.sql -Raw | mysql --user=$env:DB_USERNAME
Get-Content database/data/04_service_configuration.sql -Raw | mysql --user=$env:DB_USERNAME
```

Para actualizar los tiempos de parada de una base ya inicializada:

```powershell
Get-Content database/migrations/02_set_station_dwell_to_20_seconds.sql -Raw | mysql --user=$env:DB_USERNAME
```

Este directorio no contiene credenciales.
