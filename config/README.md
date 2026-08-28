# Configuración local de servicios

Este directorio centraliza las direcciones no sensibles que necesitan las aplicaciones cliente para
trabajar en el entorno local.

`local-services.properties.example` contiene valores reproducibles y se utiliza automáticamente
cuando no existe una configuración personal. Para sobrescribirlos:

```powershell
Copy-Item config/local-services.properties.example config/local-services.properties
```

Después se puede editar `config/local-services.properties`. Este archivo está ignorado por Git.

## Propiedades

| Propiedad | Consumidor | Uso |
| --- | --- | --- |
| `RMM_ANDROID_ENVIRONMENT` | RMM App | Entorno `local`, `staging` o `production` mostrado por la configuración interna. |
| `RMM_API_ANDROID_BASE_URL` | RMM App | API accesible desde el emulador Android. |
| `RMM_API_DESKTOP_BASE_URL` | Aplicaciones Qt | Origen HTTP local del backend. |
| `RMM_WEB_BASE_URL` | Documentación y entorno web | Origen local del centro de control. |
| `RMM_MQTT_HOST` | Aplicaciones Qt | Host del broker Mosquitto. |
| `RMM_MQTT_PORT` | Aplicaciones Qt | Puerto del broker Mosquitto. |
| `RMM_MQTT_TLS` | Aplicaciones Qt | Uso de TLS en la conexión MQTT. |

`10.0.2.2` es la dirección mediante la que el emulador Android alcanza el `localhost` del equipo
anfitrión. Las aplicaciones Qt se ejecutan directamente en Windows y utilizan `127.0.0.1`.

La URL de RMM App debe incluir el prefijo versionado `/api/rmm-app/v1`. Los builds de depuración
admiten HTTP para trabajar con el emulador; los builds que no sean de depuración exigen HTTPS.

## Límites de seguridad

Este archivo no admite:

- usuarios o contraseñas de MySQL;
- cuentas de operadores o pasajeros;
- tokens de sesión;
- contraseñas MQTT;
- claves privadas, certificados de cliente o códigos de aprovisionamiento.

Los secretos continuarán suministrándose mediante variables de entorno, almacenes seguros o ficheros
locales específicos que no estén versionados.

