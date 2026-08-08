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

Los datos se conservan en el volumen Docker `rmm-local_mosquitto-data`. `docker compose down` no lo
elimina; la opción `--volumes` sí lo hace y debe usarse únicamente cuando se quiera reiniciar
deliberadamente el estado local.

El acceso anónimo está deshabilitado y el puerto solo se publica en la interfaz local del equipo.
Las contraseñas cifradas permiten probar el aislamiento por dispositivo, pero no sustituyen la
identidad mediante certificados prevista para entornos desplegados. TLS y el aprovisionamiento
descritos en [`docs/identidad-maquinas.md`](../../docs/identidad-maquinas.md) se añadirán en fases
posteriores.
