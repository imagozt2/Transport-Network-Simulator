# Broker MQTT local

Eclipse Mosquitto proporciona el transporte MQTT entre el backend y las futuras máquinas Qt. El
broker no contiene reglas de negocio ni es una fuente de verdad del ecosistema.

## Inicio y parada

Desde la raíz del repositorio:

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

Con el contenedor iniciado, abre dos terminales. En la primera, suscríbete a un topic de prueba:

```powershell
docker compose exec mosquitto mosquitto_sub -h 127.0.0.1 -t rmm/local/health
```

En la segunda, publica un mensaje:

```powershell
docker compose exec mosquitto mosquitto_pub -h 127.0.0.1 -t rmm/local/health -m connected
```

La primera terminal debe mostrar `connected`. Este topic solo sirve para comprobar el entorno y no
forma parte del contrato funcional `rmm/v1`.

## Persistencia y seguridad

Los datos se conservan en el volumen Docker `rmm-local_mosquitto-data`. `docker compose down` no lo
elimina; la opción `--volumes` sí lo hace y debe usarse únicamente cuando se quiera reiniciar
deliberadamente el estado local.

Esta configuración admite conexiones anónimas exclusivamente para facilitar el arranque inicial en
desarrollo y el puerto solo se publica en la interfaz local del equipo. No debe desplegarse en un
entorno compartido o de producción. Las identidades individuales, ACL y conexiones TLS descritas en
[`docs/identidad-maquinas.md`](../../docs/identidad-maquinas.md) se añadirán en fases posteriores.
