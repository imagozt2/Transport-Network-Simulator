# Infraestructura local del ecosistema RMM

Esta guía describe el entorno reproducible que permite ejecutar conjuntamente MySQL, el backend
Spring Boot y Eclipse Mosquitto durante el desarrollo. Las aplicaciones web, Android y Qt se
ejecutan fuera de Docker para mantener un ciclo de desarrollo rápido.

## Topología y responsabilidades

```text
Frontend Angular ───────► Backend Spring Boot ───────► MySQL
 http://localhost:4200     http://localhost:8080       localhost:3307
                                │
Aplicaciones Qt ────────────────►│◄──────────────────► Mosquitto
                                                        localhost:1883
```

Los contenedores pertenecen a la red privada `rmm-internal`. Dentro de ella, el backend accede a
MySQL mediante `mysql:3306` y al broker mediante `mosquitto:1883`. MySQL y Mosquitto solo publican
sus puertos sobre `127.0.0.1`, por lo que no quedan expuestos a otros equipos de la red.

| Servicio | Imagen o construcción | Puerto del equipo | Persistencia |
| --- | --- | --- | --- |
| MySQL | `mysql:8.4.10-oraclelinux9` | `127.0.0.1:3307` | Volumen `mysql-data` |
| Backend | `backend/Dockerfile` | `127.0.0.1:8080` | MySQL |
| Mosquitto | `eclipse-mosquitto:2.0.22-openssl` | `127.0.0.1:1883` | Volumen `mosquitto-data` |

El puerto `3307` evita interferir con una instalación de MySQL que utilice localmente el `3306`.
Mosquitto transporta mensajes, pero el backend conserva las reglas de negocio y MySQL continúa
siendo la fuente de verdad.

## Requisitos

- Docker Desktop iniciado y Docker Compose disponible.
- PowerShell para ejecutar los scripts auxiliares.
- Puertos `8080`, `1883` y `3307` libres.

Desde la raíz del repositorio se puede comprobar el entorno con:

```powershell
docker version
docker compose version
docker compose --env-file .env.example config --quiet
```

## Preparación inicial

### Variables del entorno Docker

Copia la plantilla y sustituye todos sus marcadores:

```powershell
Copy-Item .env.example .env
```

`.env` contiene las credenciales locales de MySQL, el primer operador y la identidad MQTT del
backend. Docker Compose lo carga automáticamente desde la raíz. No debe versionarse ni se deben
reutilizar contraseñas entre servicios.

Las variables `OPERATOR_*` solo aprovisionan el primer administrador cuando `operator_accounts`
está vacía. Modificarlas posteriormente no cambia una cuenta existente.

### Identidades MQTT

Copia la plantilla y asigna una contraseña diferente, de al menos 12 caracteres, a cada identidad:

```powershell
Copy-Item infrastructure/mosquitto/mqtt-users.example `
  infrastructure/mosquitto/mqtt-users.local
```

La contraseña de `rmm-backend` debe coincidir con `MQTT_BACKEND_PASSWORD` en `.env`. Después genera
el archivo cifrado de contraseñas y las reglas de acceso:

```powershell
.\infrastructure\mosquitto\scripts\initialize-security.ps1
```

El script crea `runtime/password_file` y `runtime/acl_file`. Estos archivos y
`mqtt-users.local` están excluidos de Git.

Los usuarios de la plantilla corresponden a máquinas existentes en el inventario inicial:

| Aplicación | Identidad local | Variable de contraseña |
| --- | --- | --- |
| Backend | `rmm-backend` | `MQTT_BACKEND_PASSWORD` en `.env` |
| Máquina de venta | `RMM-TM-ST046-01` | `RMM_TICKET_MACHINE_MQTT_PASSWORD` |
| Validadora de entrada | `RMM-EN-ST046-01` | `RMM_VALIDATOR_MQTT_PASSWORD` |
| Validadora de salida | `RMM-EX-ST046-01` | `RMM_VALIDATOR_MQTT_PASSWORD` |

El nombre situado a la izquierda de `=` en `mqtt-users.local` debe coincidir exactamente con la
variable `RMM_TICKET_MACHINE_DEVICE_CODE` o `RMM_VALIDATOR_DEVICE_CODE` de la aplicación que se
arranque. Cada proceso Qt utiliza una identidad y una contraseña diferentes; las validadoras de
entrada y salida no comparten usuario aunque pertenezcan a la misma estación.

Si se añade otra máquina, primero debe existir en `devices` con el tipo y la estación correctos y
tener una identidad activa en `device_mqtt_identities`. Después se añade el mismo código a
`mqtt-users.local` y se vuelve a ejecutar `initialize-security.ps1` antes de reiniciar Mosquitto.

## Inicio y comprobación

Construye el backend e inicia los tres servicios:

```powershell
docker compose up -d --build
docker compose ps
```

MySQL y Mosquitto tienen comprobaciones de salud que condicionan el arranque del backend. Durante
el primer inicio, MySQL crea la base y carga en orden el esquema, la red, la flota, las máquinas,
los productos y la configuración ferroviaria desde `database/`.

Los scripts SQL solo se ejecutan cuando `mysql-data` está vacío. Cambiar posteriormente un script
no modifica automáticamente una base ya creada.

Comprueba la API y consulta los logs de los servicios:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/health
docker compose logs --since 5m mysql
docker compose logs --since 5m mosquitto
docker compose logs --since 5m backend
```

La prueba automatizada del broker valida publicación y suscripción, aislamiento entre máquinas y
recuperación de mensajes QoS 1 después de una reconexión:

```powershell
.\infrastructure\mosquitto\tests\mqtt-integration-tests.ps1
```

Esta prueba usa un contenedor y credenciales temporales; no altera el broker local.

## Acceso de las aplicaciones

Con la infraestructura iniciada:

- Angular utiliza `http://localhost:8080`;
- el emulador Android utiliza `http://10.0.2.2:8080` para alcanzar el anfitrión;
- las aplicaciones Qt utilizan `127.0.0.1:8080` y `127.0.0.1:1883`;
- ninguna aplicación cliente se conecta directamente a MySQL.

Las direcciones no sensibles se centralizan en `config/local-services.properties.example`. La
[guía de aplicaciones cliente](ejecucion-aplicaciones-cliente.md) explica su ejecución desde
Android Studio y Qt Creator.

## Operación habitual

Para seguir los logs durante el desarrollo:

```powershell
docker compose logs -f backend mosquitto
```

Los logs de Mosquitto rotan automáticamente y no incluyen payloads ni contraseñas. Los eventos de
negocio son responsabilidad del backend y de MySQL.

Para reiniciar un servicio o reconstruir el backend:

```powershell
docker compose restart mosquitto
docker compose up -d --build backend
```

Para detener los servicios conservando los contenedores:

```powershell
docker compose stop
```

Para retirar los contenedores y la red conservando los datos:

```powershell
docker compose down
```

Reiniciar, detener o recrear un contenedor no elimina los volúmenes.

## Reinicialización deliberada

Si se necesita probar un esquema completamente nuevo, primero se debe exportar cualquier dato que
deba conservarse. Después se puede retirar el entorno junto con sus volúmenes:

```powershell
docker compose down --volumes
docker compose up -d --build
```

Esta operación elimina `mysql-data` y `mosquitto-data`, no es recuperable desde Docker y provoca
que los scripts SQL vuelvan a ejecutarse en el siguiente inicio.

## MQTT con TLS mutuo

El modo local predeterminado usa autenticación por contraseña en `127.0.0.1:1883`. El modo seguro
dispone de un listener en `8883`, TLS 1.2 o superior y certificados de cliente obligatorios.

El material criptográfico debe aprovisionarse fuera de Git bajo
`infrastructure/mosquitto/runtime/certificates/`. Antes de iniciar este modo:

```powershell
.\infrastructure\mosquitto\scripts\validate-tls-material.ps1
Copy-Item .env.tls.example .env.tls
docker compose --env-file .env --env-file .env.tls up -d --build
```

Cada proceso recibe únicamente su certificado y clave. La estructura y los requisitos se describen
en la [guía de Mosquitto](../infrastructure/mosquitto/README.md) y en el
[contrato de identidad de máquinas](identidad-maquinas.md).

## Diagnóstico rápido

| Síntoma | Comprobación |
| --- | --- |
| Docker no responde | Iniciar Docker Desktop y ejecutar `docker version`. |
| Un puerto está ocupado | Revisar los puertos publicados con `docker compose ps`. |
| Mosquitto no arranca | Regenerar sus archivos de seguridad y consultar sus logs. |
| El backend espera indefinidamente | Comprobar la salud de MySQL y Mosquitto. |
| El backend no conecta con MySQL | Verificar `DB_USERNAME`, `DB_PASSWORD` y los logs. |
| Faltan cambios del esquema | Los scripts no se reaplican sobre un volumen existente. |
| Una máquina recibe `Not authorized` | Revisar su identidad y los topics permitidos por las ACL. |
| Android no alcanza la API | Desde el emulador se debe usar `10.0.2.2`, no `localhost`. |

## Seguridad

- No se versionan `.env`, credenciales MQTT, certificados ni claves privadas.
- Solo el backend accede a MySQL.
- Los clientes MQTT disponen de identidades y permisos individuales con denegación por defecto.
- El listener sin TLS está limitado al equipo local y no constituye una configuración productiva.
- Los contratos REST, MQTT y QR son la referencia para los intercambios entre componentes.

La [arquitectura del ecosistema](arquitectura-ecosistema.md) detalla la separación de
responsabilidades y el [contrato MQTT](contrato-mqtt.md) especifica los topics, QoS y mensajes.
