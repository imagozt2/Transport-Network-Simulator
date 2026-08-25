# Guía final de ejecución y validación del ecosistema RMM

## Propósito

La Red de Metro de Macegocia (RMM) es un ecosistema de simulación formado por un centro de control,
una aplicación Android para pasajeros y dos máquinas Qt. Todos los clientes comparten el mismo
backend, pero cada uno dispone de responsabilidades, credenciales y canales de comunicación
distintos.

Esta guía es el punto de entrada para preparar, arrancar, comprobar y detener el ecosistema completo.
Los contratos y reglas de negocio detallados se mantienen en los documentos especializados
enlazados al final.

## Componentes y direcciones locales

| Componente | Tecnología | Dirección o puerto | Responsabilidad principal |
| --- | --- | --- | --- |
| Centro de control | Angular | `http://localhost:4200` | Supervisión y administración. |
| Backend | Java y Spring Boot | `http://127.0.0.1:8080` | Reglas de negocio, seguridad e integración. |
| Base de datos | MySQL | `127.0.0.1:3307` desde Windows | Persistencia de red, cuentas, billetes y operaciones. |
| RMM App | Kotlin y Jetpack Compose | Emulador Android | Red, compras, cartera e historial del pasajero. |
| Máquina de venta | C++ y Qt | Aplicación de escritorio | Compra, recarga y emisión de billetes. |
| Máquina validadora | C++ y Qt | Aplicación de escritorio | Lectura de QR y validaciones de entrada o salida. |
| Broker | Eclipse Mosquitto | `127.0.0.1:1883` | Mensajería MQTT autenticada. |

```text
                         Centro de control Angular
                                   | REST
                                   v
RMM App Android ------ REST ----> Backend Spring Boot ----> MySQL
                                   |
                                   | MQTT
                                   v
                           Eclipse Mosquitto
                              ^         ^
                              |         |
                       Venta Qt     Validadora Qt
```

El backend es el límite de confianza. Los clientes nunca acceden directamente a MySQL, no alteran
saldos y no deciden por sí solos si un billete o una validación son válidos.

## Requisitos

Para ejecutar todos los componentes se necesita:

- Docker Desktop con Docker Compose;
- PowerShell;
- Node.js 20 o posterior y npm;
- Android Studio, JDK 17 o posterior, Android SDK 36 y un emulador configurado;
- Qt 6.9 o posterior, Qt MQTT de la misma versión, CMake, Ninja y un compilador compatible;
- cámara disponible si se quieren probar las lecturas reales de QR de las máquinas Qt;
- puertos `4200`, `8080`, `1883` y `3307` libres.

La instalación local de referencia para Qt usa Qt 6.11.1 y MinGW 13.1 de 64 bits. La integración
continua compila con Qt 6.9.2 y MSVC 2022, lo que verifica la compatibilidad con ambas configuraciones.

## Preparación inicial

### 1. Configuración privada de Docker

Desde la raíz del repositorio, crea el archivo local de entorno:

```powershell
Copy-Item .env.example .env
```

Sustituye todos los marcadores de `.env`. Las variables `OPERATOR_*` aprovisionan el primer operador
solo cuando la tabla `operator_accounts` está vacía. Una modificación posterior no cambia una cuenta
ya existente.

### 2. Identidades MQTT

```powershell
Copy-Item infrastructure/mosquitto/mqtt-users.example `
  infrastructure/mosquitto/mqtt-users.local
```

Asigna una contraseña diferente a cada identidad. La contraseña de `rmm-backend` debe coincidir con
`MQTT_BACKEND_PASSWORD` en `.env`. Después genera el archivo cifrado y las ACL:

```powershell
.\infrastructure\mosquitto\scripts\initialize-security.ps1
```

Las identidades de las máquinas deben coincidir exactamente con códigos existentes en el inventario
de la base de datos. Cada proceso Qt utiliza su propia identidad y contraseña.

### 3. Direcciones de las aplicaciones cliente

```powershell
Copy-Item config/local-services.properties.example config/local-services.properties
```

El emulador Android debe usar `10.0.2.2` para alcanzar el `localhost` de Windows. Las aplicaciones Qt
usan `127.0.0.1`. Si se cambia este archivo, vuelve a sincronizar Android y a configurar CMake.

Los archivos `.env`, `mqtt-users.local`, `local-services.properties`, certificados y claves privadas
están excluidos de Git y nunca deben incorporarse al repositorio.

## Arranque completo

### 1. Infraestructura y backend

Con Docker Desktop iniciado, ejecuta desde la raíz:

```powershell
docker compose up -d --build
docker compose ps
Invoke-RestMethod http://127.0.0.1:8080/api/health
```

Docker inicia MySQL, Mosquitto y el backend. En el primer arranque, MySQL carga automáticamente el
esquema y los datos de `database/`. Estos scripts solo vuelven a ejecutarse al crear un volumen vacío.

Todos los servicios deben aparecer como iniciados y la consulta de salud debe responder
correctamente antes de abrir los clientes.

### 2. Centro de control web

En otra terminal:

```powershell
Set-Location frontend
npm install
npm start
```

Abre `http://localhost:4200` e inicia sesión con el operador configurado mediante `OPERATOR_*`. Una
autenticación correcta abre el Panel General.

### 3. RMM App

1. Abre la carpeta `android` en Android Studio.
2. Espera a que termine la sincronización de Gradle.
3. Inicia el emulador y ejecuta el módulo `app`.
4. Crea una cuenta de pasajero con un correo nuevo.
5. Inicia sesión con esa cuenta.

No existen credenciales predeterminadas para pasajeros. En local, el backend puede activar
automáticamente las cuentas nuevas mediante `RMM_APP_AUTO_VERIFY_REGISTRATION=true`.

### 4. Máquina de venta y máquina validadora

1. Abre `qt/CMakeLists.txt` en Qt Creator.
2. Selecciona el kit que incluya Qt MQTT.
3. Configura el proyecto con `BUILD_TESTING` activado.
4. Ejecuta `rmm-ticket-vending-machine`.
5. Ejecuta `rmm-ticket-validator` con una identidad y un modo coherentes con el inventario.

Para probar una entrada y una salida de extremo a extremo se necesitan dos procesos validadores con
identidades diferentes: uno en modo entrada y otro en modo salida.

## Validación funcional recomendada

Realiza las comprobaciones en este orden para conservar trazabilidad entre clientes:

1. **Centro de control:** inicia sesión y comprueba Panel General, red, trenes, máquinas y logs.
2. **RMM App:** registra un pasajero, inicia sesión, consulta la red y calcula un trayecto.
3. **Compra móvil:** compra un título y comprueba que aparece en la cartera con su QR.
4. **Máquina de venta:** completa una compra física y una recarga mediante QR.
5. **Validación:** usa el mismo billete para una entrada y una salida compatibles.
6. **Historial:** confirma en RMM App que el desplazamiento contiene estaciones, duración e importe.
7. **Trazabilidad:** comprueba en la web los eventos MQTT, la emisión, las validaciones y el trayecto.
8. **Emisión administrativa digital:** entrega un billete a la cartera de un pasajero.
9. **Emisión administrativa física:** envía una orden a una máquina MQTT conectada y comprueba su
   resultado; una máquina simulada no monitorizada debe registrar la operación sin generar un
   soporte físico real.
10. **Conectividad:** desconecta temporalmente una máquina, genera una operación y confirma que se
    reintenta sin duplicarla después de la reconexión.

Una comprobación se considera satisfactoria cuando el estado visible, la respuesta del backend, los
logs y los registros relacionados identifican la misma cuenta, máquina, billete y operación.

## Pruebas automatizadas locales

Ejecuta los comandos desde la raíz salvo que se indique lo contrario.

### Base de datos

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\database\tests\database-source-encoding-tests.ps1
```

### Backend

```powershell
Set-Location backend
.\mvnw.cmd test
.\mvnw.cmd clean package -DskipTests
Set-Location ..
```

### Frontend

```powershell
Set-Location frontend
npm ci
npm test -- --watch=false
npm run build -- --configuration production
npm run test:e2e
Set-Location ..
```

Las pruebas E2E requieren sus dependencias de Playwright y levantan los procesos definidos por su
configuración.

### Android

```powershell
Set-Location android
.\gradlew.bat testDebugUnitTest assembleDebug
Set-Location ..
```

Con un emulador iniciado:

```powershell
Set-Location android
.\gradlew.bat connectedDebugAndroidTest
Set-Location ..
```

### Qt

El siguiente ejemplo corresponde al entorno local de referencia:

```powershell
& D:\Qt\Tools\CMake_64\bin\cmake.exe `
  -S qt `
  -B qt/build `
  -G Ninja `
  -DCMAKE_PREFIX_PATH=D:\Qt\6.11.1\mingw_64 `
  -DCMAKE_CXX_COMPILER=D:\Qt\Tools\mingw1310_64\bin\g++.exe `
  -DBUILD_TESTING=ON

& D:\Qt\Tools\CMake_64\bin\cmake.exe --build qt/build
& D:\Qt\Tools\CMake_64\bin\ctest.exe --test-dir qt/build --output-on-failure
```

### MQTT

```powershell
.\infrastructure\mosquitto\tests\mqtt-integration-tests.ps1
```

Esta prueba valida autenticación, ACL, publicación, suscripción y reconexión con credenciales
temporales.

### Ecosistema en contenedores aislados

Con Docker Desktop iniciado:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\infrastructure\tests\ecosystem-container-tests.ps1
```

La prueba construye un entorno temporal, valida el esquema y la codificación de MySQL, comprueba la
conexión MQTT del backend, autentica un operador real y consulta los recursos operativos protegidos.
Sus nombres, puertos, credenciales y volúmenes son independientes del entorno habitual y se eliminan
al terminar.

## Integración continua

El workflow `.github/workflows/ci.yml` ejecuta siete controles:

1. codificación de los scripts de base de datos;
2. compilación del backend;
3. compilación y acceso real al frontend;
4. compilación, pruebas unitarias y pruebas en emulador de RMM App;
5. compilación y pruebas de las aplicaciones Qt;
6. integración MQTT;
7. integración del ecosistema en contenedores.

Una fase está lista para integrarse cuando todos los controles aplicables están en verde. Los
artefactos del workflow incluyen el APK, el informe de Android y los ejecutables Qt.

## Parada y conservación de datos

Detén Angular y las aplicaciones abiertas con `Ctrl+C` o cerrando sus ventanas. Para retirar los
contenedores conservando la base de datos y los mensajes persistentes:

```powershell
docker compose down
```

La siguiente orden elimina también los volúmenes y no debe usarse como parada habitual:

```powershell
docker compose down --volumes
```

Úsala únicamente para reconstruir deliberadamente MySQL y Mosquitto desde cero, después de exportar
cualquier dato que deba conservarse.

## Diagnóstico rápido

| Síntoma | Comprobación |
| --- | --- |
| Docker no responde | Inicia Docker Desktop y ejecuta `docker version`. |
| El backend no está saludable | Revisa `docker compose logs --since 5m backend mysql`. |
| El frontend no abre | Confirma que `npm start` sigue activo y que el puerto `4200` está libre. |
| El operador no puede entrar | Comprueba si la cuenta ya existía antes de cambiar `OPERATOR_*`. |
| Android no alcanza la API | Usa `10.0.2.2`, no `localhost`, desde el emulador. |
| Una cuenta nueva no puede entrar | Comprueba `RMM_APP_AUTO_VERIFY_REGISTRATION` y reconstruye backend. |
| Qt no encuentra `Qt6Mqtt` | Instala Qt MQTT para la misma versión y el mismo kit de Qt. |
| Una máquina recibe `Not authorized` | Comprueba identidad, contraseña, inventario y ACL MQTT. |
| No aparecen cambios SQL | Los scripts no se reaplican sobre un volumen MySQL existente. |
| Una cámara no abre | Cierra otras aplicaciones que la utilicen y revisa permisos del sistema. |

Para observar la integración en tiempo real:

```powershell
docker compose logs -f backend mosquitto
```

## Seguridad y límites de la demostración

- Los operadores usan sesiones web y protección CSRF.
- Los pasajeros usan tokens asociados a dispositivos móviles.
- Las máquinas tienen identidades MQTT individuales y ACL con denegación por defecto.
- Los QR se firman con Ed25519, incluyen versión de clave y admiten rotación.
- Las operaciones usan referencias idempotentes para tolerar duplicados y reintentos.
- Los pagos se simulan y no existe integración con una pasarela bancaria.
- El MQTT local sin TLS solo debe exponerse en el propio equipo.
- Los dispositivos Qt son simuladores y no representan hardware homologado.
- La ciudad, la red y sus datos son ficticios.

## Mapa de documentación

### Arquitectura e infraestructura

- [Arquitectura y responsabilidades](arquitectura-ecosistema.md)
- [Infraestructura local](infraestructura-local.md)
- [Ejecución de las aplicaciones cliente](ejecucion-aplicaciones-cliente.md)
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
