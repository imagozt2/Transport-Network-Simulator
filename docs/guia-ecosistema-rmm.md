# Guía integral del ecosistema RMM

## Propósito

La Red de Metro de Macegocia (RMM) es un ecosistema de simulación formado por un centro de control,
una aplicación Android para pasajeros y dos máquinas Qt. Todos los clientes comparten el mismo
backend, pero cada uno dispone de responsabilidades, credenciales y canales de comunicación
distintos.

Esta guía ofrece una entrada común al proyecto. Los contratos y reglas detallados se mantienen en
los documentos especializados enlazados al final.

## Componentes

| Componente | Tecnología | Responsabilidad principal |
| --- | --- | --- |
| Centro de control | Angular | Supervisión ferroviaria, máquinas, logs, pasajeros e incidencias. |
| Backend | Java y Spring Boot | Reglas de negocio, autorización, idempotencia y coordinación. |
| Base de datos | MySQL | Fuente persistente de verdad para red, cuentas, billetes y operaciones. |
| RMM App | Kotlin y Jetpack Compose | Red, trayectos, compras, cartera e historial del pasajero. |
| Máquina de venta | C++ y Qt | Compra simulada, emisión y órdenes compensatorias. |
| Máquina validadora | C++ y Qt | Lectura de QR y validaciones de entrada y salida. |
| Broker | Eclipse Mosquitto | Transporte autenticado de mensajes MQTT. |

```text
                         Centro de control Angular
                                   │ REST
                                   ▼
RMM App Android ─── REST ───► Backend Spring Boot ───► MySQL
                                   │
                                   │ MQTT
                                   ▼
                           Eclipse Mosquitto
                              ▲         ▲
                              │         │
                       Venta Qt     Validadora Qt
```

El backend es el límite de confianza. Los clientes nunca acceden directamente a MySQL, no alteran
saldos y no deciden por sí solos si un billete o una validación son válidos.

## Flujos principales

### Compra desde RMM App

1. El pasajero inicia sesión y consulta el catálogo.
2. RMM App configura el producto y envía una compra con una clave de idempotencia.
3. El backend calcula el precio, registra la compra y emite el billete digital.
4. El QR firmado queda disponible en la cartera de su propietario.
5. Los reintentos de la misma solicitud no generan compras duplicadas.

### Compra desde una máquina de venta

1. La máquina consulta títulos y tarifas mediante REST.
2. El usuario configura el título y confirma un pago simulado.
3. La solicitud se envía al backend y sus eventos se publican por MQTT.
4. El backend emite el billete y la máquina presenta el QR resultante.
5. Si se pierde la conexión, la máquina conserva y reintenta las operaciones pendientes.

### Emisión compensatoria

1. Un operador solicita una emisión gratuita y justificada.
2. El backend persiste la solicitud y publica una orden dirigida a la máquina elegida.
3. La máquina procesa la orden de forma idempotente y confirma el resultado por MQTT.
4. El backend relaciona el billete, el operador, la máquina y el log correspondiente.

### Entrada, trayecto y salida

1. La validadora envía el QR, la estación, el sentido y una referencia única.
2. El backend verifica firma, vigencia, propiedad, saldo y reutilización.
3. Una entrada aceptada abre un trayecto y aplica el consumo correspondiente al producto.
4. La salida calcula estaciones, importe y duración y cierra el mismo trayecto.
5. La operación, el historial del pasajero y los logs conservan referencias coherentes.

## Seguridad y aislamiento

- Los operadores usan sesiones web y protección CSRF.
- Los pasajeros usan tokens asociados a dispositivos móviles.
- Cada cartera, compra y trayecto queda limitado a la cuenta autenticada.
- Las máquinas tienen identidades MQTT individuales y ACL restringidas por topic.
- Los QR se firman con Ed25519, incluyen versión de clave y admiten rotación.
- Los mensajes y órdenes usan referencias estables para soportar duplicados y reintentos.
- Las contraseñas, claves privadas y archivos `.env` no se versionan.

## Ejecución local

El entorno coordinado de backend, MySQL y Mosquitto se inicia desde la raíz:

```powershell
Copy-Item .env.example .env
Copy-Item infrastructure/mosquitto/mqtt-users.example `
  infrastructure/mosquitto/mqtt-users.local
.\infrastructure\mosquitto\scripts\initialize-security.ps1
docker compose up -d --build
docker compose ps
Invoke-RestMethod http://127.0.0.1:8080/api/health
```

Angular, Android y Qt se ejecutan fuera de Docker. Sus comandos, kits y direcciones compartidas se
describen en la [guía de aplicaciones cliente](ejecucion-aplicaciones-cliente.md).

Para detener la infraestructura sin borrar los datos:

```powershell
docker compose down
```

`docker compose down --volumes` elimina también los volúmenes de MySQL y Mosquitto y solo debe
usarse cuando se quiera reconstruir deliberadamente el entorno desde cero.

## Verificación

La integración continua valida:

- compilación del backend y del frontend;
- pruebas y APK de RMM App;
- compilación y pruebas de las aplicaciones Qt;
- autenticación, ACL, publicación, suscripción y reconexión MQTT;
- arranque conjunto del backend, MySQL y Mosquitto en contenedores aislados;
- salud del backend, carga del esquema y conexión MQTT autenticada.

La prueba de contenedores puede ejecutarse localmente con Docker Desktop iniciado:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\infrastructure\tests\ecosystem-container-tests.ps1
```

Utiliza nombres, puertos, credenciales y volúmenes temporales. No modifica la base de datos local ni
el entorno Docker habitual.

## Mapa de documentación

### Arquitectura e infraestructura

- [Arquitectura y responsabilidades](arquitectura-ecosistema.md)
- [Infraestructura local](infraestructura-local.md)
- [Flujos online y sin conexión](flujos-conectividad.md)
- [Identidad de las máquinas](identidad-maquinas.md)

### Contratos y backend

- [Contratos REST de RMM App](contratos-rest-rmm-app.md)
- [Contrato MQTT](contrato-mqtt.md)
- [Integración MQTT del backend](integracion-mqtt-backend.md)
- [Ciclo de vida de los billetes](ciclo-vida-billetes.md)
- [Dominio de billetes](dominio-billetes.md)
- [Seguridad de los códigos QR](contrato-codigos-qr.md)

### Aplicaciones cliente

- [Arquitectura de RMM App](arquitectura-rmm-app.md)
- [Consulta de la red](consulta-red-rmm-app.md)
- [Compra de billetes](compra-billetes-rmm-app.md)
- [Cartera de billetes](cartera-rmm-app.md)
- [Historial de desplazamientos](historial-desplazamientos-rmm-app.md)
- [Máquina de venta](maquina-venta.md)
- [Máquina validadora](maquina-validadora.md)

### Centro de control

- [Integración de la aplicación web](integracion-aplicacion-web.md)
- [Operación simulada](operacion-simulada.md)
- [Máquinas y logs](maquinas-y-logs.md)
- [Operadores](acceso-operadores.md)
- [Usuarios de RMM App](usuarios-rmm-app.md)

## Límites de la demostración

- Los pagos se simulan y no existe integración con una pasarela bancaria.
- Los servicios locales usan MQTT sin TLS; el repositorio permite habilitar TLS en entornos seguros.
- Los dispositivos Qt representan máquinas simuladas, no hardware homologado.
- La ciudad, la red y sus datos son ficticios.
