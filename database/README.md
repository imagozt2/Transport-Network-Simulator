# Base de datos

Definición de MySQL 8 y datos iniciales de la red de transporte de Macegocia.

## Estructura

- `schema/01_create_database.sql`: crea la base de datos con codificación UTF-8.
- `schema/02_create_tables.sql`: crea el modelo relacional completo.
- `data/01_transport_network.sql`: estaciones, líneas, paradas ordenadas y conexiones.
- `data/02_operations.sql`: dispositivos, material rodante y cocheras.
- `data/03_ticket_products.sql`: catálogo inicial de productos de transporte.
- `data/04_service_configuration.sql`: calendarios, franjas, frecuencias, tiempos y cocheras por línea.
- `verification/verify_database.sql`: recuentos esperados y comprobaciones de integridad.

El esquema incluye `operator_accounts` para las cuentas del centro de control. Esta tabla almacena
únicamente el hash de la contraseña junto con la identidad, el rol, el estado y los datos de
seguridad de la cuenta. Los scripts SQL no crean operadores ni contienen credenciales iniciales.
El primer operador se aprovisionará desde el backend mediante variables de entorno.
El procedimiento y el modelo de seguridad se describen en
[`docs/acceso-operadores.md`](../docs/acceso-operadores.md).

`passenger_accounts` mantiene las cuentas de los futuros usuarios viajeros de RMM App. Estas
cuentas están separadas de los operadores e incluyen un UUID público, identidad, correo, hash de
contraseña, verificación, estado administrativo y datos de seguridad. La tabla
`passenger_account_status_changes` permitirá auditar qué operador cambia el estado de una cuenta.
Los scripts no insertan pasajeros ficticios.
El modelo administrativo se explica en
[`docs/usuarios-rmm-app.md`](../docs/usuarios-rmm-app.md).

`compensatory_ticket_issuances` registra las emisiones gratuitas solicitadas por un operador ante
una incidencia de compra. Conserva el producto y sus parámetros, la máquina de venta de destino,
el operador responsable, el estado de la solicitud y, cuando finaliza, el billete emitido. No se
incluyen emisiones iniciales: esta tabla contiene exclusivamente actividad administrativa real o
simulada de la aplicación. Los logs pueden asociarse a la emisión mediante
`operational_logs.compensatory_issuance_id`.

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

Este directorio no contiene credenciales ni contraseñas en texto plano.
