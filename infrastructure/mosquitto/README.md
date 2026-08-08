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

El acceso anónimo está deshabilitado y el puerto solo se publica en la interfaz local del equipo.
Las contraseñas cifradas permiten probar el aislamiento por dispositivo, pero no sustituyen la
identidad mediante certificados prevista para entornos desplegados. TLS y el aprovisionamiento
descritos en [`docs/identidad-maquinas.md`](../../docs/identidad-maquinas.md) se añadirán en fases
posteriores.
