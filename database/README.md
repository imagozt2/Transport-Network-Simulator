# Database

MySQL 8 database definition and initial data for the Macegocia transport network.

## Structure

- `schema/01_create_database.sql`: creates the UTF-8 database.
- `schema/02_create_tables.sql`: creates the complete relational model.
- `data/01_transport_network.sql`: stations, lines, ordered stops and connections.
- `data/02_operations.sql`: devices, rolling stock, depots and service settings.
- `data/03_ticket_products.sql`: initial ticket catalogue.
- `verification/verify_database.sql`: expected counts and integrity checks.

## Installation order

Run the files in the order shown above. From PowerShell, pass each file to the MySQL client through
standard input. For example, after defining `DB_USERNAME` and temporarily defining `MYSQL_PWD`:

```powershell
Get-Content database/schema/01_create_database.sql -Raw | mysql --user=$env:DB_USERNAME
Get-Content database/schema/02_create_tables.sql -Raw | mysql --user=$env:DB_USERNAME
Get-Content database/data/01_transport_network.sql -Raw | mysql --user=$env:DB_USERNAME
Get-Content database/data/02_operations.sql -Raw | mysql --user=$env:DB_USERNAME
Get-Content database/data/03_ticket_products.sql -Raw | mysql --user=$env:DB_USERNAME
Get-Content database/verification/verify_database.sql -Raw | mysql --user=$env:DB_USERNAME
Remove-Item Env:MYSQL_PWD
```

Schema scripts target a new database and are intentionally not repeatable. Data scripts use natural
keys and can be executed again to update the maintained seed records without duplicating them.

No credentials are stored in this directory.
