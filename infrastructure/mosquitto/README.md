# Broker MQTT local

Eclipse Mosquitto proporciona el transporte MQTT entre el backend y las futuras máquinas Qt. El
broker no contiene reglas de negocio ni es una fuente de verdad del ecosistema.

## Inicio y parada

Antes del primer inicio, crea las identidades locales y sustituye todas las contraseñas de ejemplo:

```powershell
Copy-Item infrastructure/mosquitto/mqtt-users.example `
  infrastructure/mosquitto/mqtt-users.local
```

La contraseña de `rmm-backend` debe coincidir con `MQTT_BACKEND_PASSWORD` en el `.env` de la raíz.
Después genera el archivo de contraseñas cifradas y las ACL:

```powershell
.\infrastructure\mosquitto\scripts\initialize-security.ps1
```

`mqtt-users.local`, `runtime/password_file` y `runtime/acl_file` no se versionan. Desde la raíz del
repositorio, inicia el broker o el entorno completo:

```powershell
docker compose up -d mosquitto
docker compose ps mosquitto
docker compose logs -f mosquitto
```

Para detener el servicio conservando sus mensajes persistidos:

```powershell
docker compose stop mosquitto
```

Mosquitto recibe una parada controlada y dispone de hasta 30 segundos para guardar el estado antes
de que Docker fuerce su finalización.

Para retirar todo el entorno definido por Docker Compose sin borrar sus volúmenes:

```powershell
docker compose down
```

Este último comando también retira los contenedores del backend y MySQL si estaban iniciados.

El broker escucha en `127.0.0.1:1883`, de acuerdo con
[`config/local-services.properties.example`](../../config/local-services.properties.example).

## Comprobación manual

Con el contenedor iniciado, una máquina solo puede publicar y suscribirse a sus topics autorizados.
Por ejemplo, usando una identidad validadora configurada localmente:

```powershell
docker compose exec mosquitto mosquitto_sub -h 127.0.0.1 `
  -u RMM-VAL-ST046-ENT-01 -P "<contraseña-local>" `
  -t rmm/v1/devices/RMM-VAL-ST046-ENT-01/status
```

En la segunda, publica un mensaje:

```powershell
docker compose exec mosquitto mosquitto_pub -h 127.0.0.1 `
  -u RMM-VAL-ST046-ENT-01 -P "<contraseña-local>" `
  -t rmm/v1/devices/RMM-VAL-ST046-ENT-01/status -m '{"state":"AVAILABLE"}'
```

La primera terminal debe mostrar el mensaje. Un intento con esa identidad sobre el topic de otra
máquina debe ser rechazado por el broker.

## Pruebas de integración

La comprobación automatizada levanta un broker aislado y valida tres comportamientos del transporte:

- una máquina publica su estado y el backend lo recibe mediante una suscripción autorizada;
- una máquina no puede publicar en el espacio de topics perteneciente a otra identidad;
- una sesión persistente recupera tras reconectarse un mensaje QoS 1 recibido mientras estaba
  desconectada.

Con Docker Desktop iniciado, se puede ejecutar desde la raíz del repositorio:

```powershell
.\infrastructure\mosquitto\tests\mqtt-integration-tests.ps1
```

El script crea credenciales exclusivamente temporales, no publica ningún puerto y elimina el
contenedor y los archivos generados incluso si una aserción falla. El pipeline ejecuta el mismo
escenario en cada pull request dirigida a `main` o `develop/ecosystem`.

## Usuarios y permisos

- `rmm-backend` consume presencia, estado, telemetría, eventos, validaciones y confirmaciones de
  todas las máquinas; publica respuestas, órdenes y configuración.
- Cada identidad `RMM-SALE-*` publica únicamente en sus topics de presencia, estado, telemetría,
  eventos y confirmaciones; consume sus órdenes, respuestas y configuración.
- Cada identidad `RMM-VAL-*` dispone de los mismos permisos y además puede publicar solicitudes de
  validación.
- Todas las máquinas pueden leer el conjunto global de claves públicas QR.
- Ninguna máquina puede leer o escribir los topics de otra identidad.

## Persistencia y seguridad

Los datos se conservan en `mosquitto.db` dentro del volumen Docker `rmm-local_mosquitto-data`. El
broker guarda periódicamente:

- sesiones persistentes y sus suscripciones;
- mensajes retenidos;
- mensajes QoS pendientes para clientes desconectados;
- estado interno necesario para recuperarse después de un reinicio.

El guardado se realiza cada 30 segundos y durante una parada controlada. Las sesiones que no vuelven
a conectarse durante 14 días caducan para evitar acumulaciones indefinidas. Cada cliente desconectado
puede conservar como máximo 1000 mensajes o 10 MiB en cola.

`docker compose restart mosquitto`, `stop`, `down` y la recreación del contenedor conservan el
volumen. `docker compose down --volumes` sí elimina definitivamente la persistencia y debe utilizarse
solo cuando se quiera reiniciar deliberadamente el entorno local.

## Logs del broker

Mosquitto escribe en la salida estándar los errores, advertencias, avisos, conexiones, suscripciones
y cancelaciones de suscripción. Los logs incluyen timestamps con zona horaria, pero no registran los
payloads ni las contraseñas de los clientes.

```powershell
docker compose logs mosquitto
docker compose logs --since 10m -f mosquitto
```

Docker utiliza su controlador `local` y conserva como máximo cinco archivos de 10 MB. La rotación
evita que el broker consuma espacio en disco indefinidamente; estos logs pertenecen a diagnóstico y
no sustituyen los eventos operativos persistidos por el backend.

## Listener TLS

`config/mosquitto-tls.conf` define un modo seguro alternativo en el puerto `8883` con estas
propiedades:

- TLS 1.2 como versión mínima, manteniendo la negociación de TLS 1.3 cuando esté disponible;
- certificado de servidor para el broker;
- validación obligatoria del certificado de cada cliente;
- acceso anónimo y autenticación por contraseña deshabilitados;
- identidad MQTT obtenida del `CN` del certificado para aplicar las ACL existentes.

El material criptográfico se suministra fuera de Git con esta estructura:

```text
infrastructure/mosquitto/runtime/certificates/
├── ca.crt
├── broker.crt
├── broker.key
└── clients/
    ├── rmm-backend.crt
    ├── rmm-backend.key
    ├── RMM-SALE-ST046-01.crt
    ├── RMM-SALE-ST046-01.key
    └── RMM-VAL-ST046-ENT-01.*
```

El certificado del broker debe admitir autenticación de servidor y contener los nombres usados para
conectarse, como `mosquitto` dentro de Docker y `localhost` o `127.0.0.1` desde el equipo. Cada
certificado cliente debe admitir autenticación de cliente, utilizar como `CN` exactamente su usuario
MQTT e incluir además la SAN URI definida en el
[contrato de identidad](../../docs/identidad-maquinas.md).

Antes de iniciar el modo seguro, comprueba que los archivos mínimos existen y no están versionados:

```powershell
.\infrastructure\mosquitto\scripts\validate-tls-material.ps1
Copy-Item .env.tls.example .env.tls
```

Después activa las sustituciones TLS sobre la configuración local habitual:

```powershell
docker compose --env-file .env --env-file .env.tls up -d --build
docker compose --env-file .env --env-file .env.tls ps
```

El broker queda disponible en `mqtts://127.0.0.1:8883`. El backend recibe las rutas de su CA,
certificado y clave dentro del contenedor. Las aplicaciones Qt deberán recibir sus propios archivos,
nunca los del backend ni los de otra máquina.

`.env.tls`, los certificados y todas las claves privadas permanecen ignorados por Git. La CA y las
credenciales deben proceder del mecanismo de aprovisionamiento del entorno; este repositorio no
genera una autoridad certificadora ni claves privadas de producción.

El acceso anónimo está deshabilitado y el puerto solo se publica en la interfaz local del equipo.
Las contraseñas cifradas permiten probar el aislamiento por dispositivo, pero no sustituyen la
identidad mediante certificados prevista para entornos desplegados. TLS y el aprovisionamiento
descritos en [`docs/identidad-maquinas.md`](../../docs/identidad-maquinas.md) se añadirán en fases
posteriores.
