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
- `verification/verify_encoding.sql`: comprueba los literales canónicos almacenados y posibles
  secuencias de texto mal decodificado.
- `tests/database-source-encoding-tests.ps1`: valida que todos los scripts SQL sean UTF-8 y que los
  nombres con caracteres especiales permanezcan intactos antes de cargar MySQL.

## Codificación de los datos

Todos los archivos SQL se versionan en UTF-8 y la conexión de carga utiliza `utf8mb4`. El esquema,
las tablas y las columnas de texto emplean una intercalación `utf8mb4`, por lo que nombres como
`Ramón y Cajal`, `Museo Marítimo` o `El Espigón` no deben transliterarse ni convertirse a una página
de códigos local.

La codificación se verifica en dos niveles:

1. `database-source-encoding-tests.ps1` rechaza bytes que no formen UTF-8 válido, indicadores de
   mojibake y la pérdida de literales canónicos en los archivos fuente.
2. `verify_encoding.sql` comprueba los valores una vez importados en MySQL. La prueba del ecosistema
   ejecuta esta consulta contra el contenedor real y exige un resultado de cero incidencias.

En PowerShell debe indicarse la codificación al leer los scripts para no depender de la versión ni
de la página de códigos de la consola:

```powershell
Get-Content database/data/01_transport_network.sql -Raw -Encoding utf8 |
    mysql --user=$env:DB_USERNAME transport_simulator_db
```

Las API y los mensajes MQTT transportan JSON UTF-8. El backend conserva los caracteres recibidos,
pero nunca utiliza el nombre visible de una estación o máquina como identidad técnica.

El esquema incluye `operator_accounts` para las cuentas del centro de control. Esta tabla almacena
únicamente el hash de la contraseña junto con la identidad, el rol, el estado y los datos de
seguridad de la cuenta. Los scripts SQL no crean operadores ni contienen credenciales iniciales.
El primer operador se aprovisionará desde el backend mediante variables de entorno.
El procedimiento y el modelo de seguridad se describen en
[`docs/acceso-operadores.md`](../docs/acceso-operadores.md).

`passenger_accounts` mantiene las cuentas de los usuarios viajeros de RMM App. Estas
cuentas están separadas de los operadores e incluyen un UUID público, identidad, correo, hash de
contraseña, verificación, estado administrativo y datos de seguridad. La tabla
`passenger_account_status_changes` permitirá auditar qué operador cambia el estado de una cuenta.
Los scripts no insertan pasajeros ficticios.
El modelo administrativo se explica en
[`docs/usuarios-rmm-app.md`](../docs/usuarios-rmm-app.md).

`compensatory_ticket_issuances` registra las emisiones gratuitas solicitadas por un operador ante
una incidencia de compra. Conserva el producto y sus parámetros, el canal de entrega, el operador
responsable, el estado de la solicitud y, cuando finaliza, el billete emitido. Una entrega
`PHYSICAL_DEVICE` referencia una máquina de venta; una entrega `DIGITAL_WALLET` referencia la cuenta
del pasajero destinatario. No se incluyen emisiones iniciales: esta tabla contiene exclusivamente
actividad administrativa real o simulada de la aplicación. Los logs pueden asociarse a la emisión
mediante `operational_logs.compensatory_issuance_id`.

El núcleo de ticketing separa el derecho de transporte de su representación. `tickets` conserva el
producto aplicado, su estado, saldo o vigencia, titularidad y datos de concurrencia. Un billete puede
representarse mediante uno o varios registros de `ticket_supports`, físicos o digitales, sin
duplicar sus derechos. Los soportes físicos admiten un número de serie y vinculación posterior a una
cuenta; los digitales nacen vinculados al pasajero correspondiente.

`ticket_journeys` es la fuente única de los trayectos realizados. Conserva las validaciones y
estaciones de entrada y salida, el coste, la duración derivada y el estado del recorrido. La
referencia opcional a `passenger_accounts` fija la titularidad histórica necesaria para RMM App sin
crear una segunda tabla que duplique los viajes. El índice por pasajero y fecha de cierre permite
consultar el historial de forma paginada.

`ticket_qr_credentials` mantiene las credenciales QR versionadas asociadas a cada soporte. Solo
persiste su identificador y huella, nunca el contenido íntegro del QR. Sus estados permiten revocar,
caducar o sustituir una credencial sin eliminar el billete ni su historial. `qr_token` conserva el
identificador interno empleado por el flujo de emisión; la vinculación con Android se representa
mediante la titularidad del billete y sus soportes, sin columnas de compatibilidad adicionales.

`incidents` constituye la base de la herramienta de ticketing del centro de control. Cada incidencia
incluye categoría, prioridad, estado, operador creador y responsable, fechas del ciclo de vida y
referencias opcionales a la línea, estación, tren, máquina o cochera afectada. La evolución queda
auditada en `incident_status_changes`, mientras que `incident_comments` conserva la conversación
operativa sin duplicarla en el registro principal. Los logs relacionados pueden enlazarse mediante
`operational_logs.incident_id`. No se cargan incidencias iniciales ni se simulan fallos.

## Orden de instalación

Los archivos deben ejecutarse en el orden anterior. Desde PowerShell se puede pasar cada archivo al
cliente de MySQL mediante la entrada estándar. Por ejemplo, después de definir `DB_USERNAME` y asignar
temporalmente `MYSQL_PWD`:

```powershell
Get-Content database/schema/01_create_database.sql -Raw -Encoding utf8 | mysql --user=$env:DB_USERNAME
Get-Content database/schema/02_create_tables.sql -Raw -Encoding utf8 | mysql --user=$env:DB_USERNAME
Get-Content database/data/01_transport_network.sql -Raw -Encoding utf8 | mysql --user=$env:DB_USERNAME
Get-Content database/data/02_operations.sql -Raw -Encoding utf8 | mysql --user=$env:DB_USERNAME
Get-Content database/data/03_ticket_products.sql -Raw -Encoding utf8 | mysql --user=$env:DB_USERNAME
Get-Content database/data/04_service_configuration.sql -Raw -Encoding utf8 | mysql --user=$env:DB_USERNAME
Get-Content database/verification/verify_database.sql -Raw -Encoding utf8 | mysql --user=$env:DB_USERNAME
Get-Content database/verification/verify_encoding.sql -Raw -Encoding utf8 | mysql --user=$env:DB_USERNAME
Remove-Item Env:MYSQL_PWD
```

Los scripts de esquema están destinados a una base nueva y no son repetibles de forma intencionada.
Los scripts de datos utilizan claves naturales y pueden ejecutarse de nuevo para actualizar los
registros mantenidos sin duplicarlos.

La configuración ferroviaria se explica en
[`docs/modelo-operacion-ferroviaria.md`](../docs/modelo-operacion-ferroviaria.md).

Este directorio no contiene credenciales ni contraseñas en texto plano.
