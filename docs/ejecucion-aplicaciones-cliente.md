# Ejecución de las aplicaciones cliente

Esta guía describe cómo preparar, compilar y ejecutar RMM App y las dos máquinas Qt en el entorno
local. Las aplicaciones son todavía estructuras iniciales: permiten comprobar su identidad, su
configuración y su integración con el proceso de compilación, mientras que los flujos funcionales se
incorporarán en fases posteriores.

## Servicios compartidos

Las aplicaciones cliente consumen las direcciones definidas en
[`config/local-services.properties.example`](../config/local-services.properties.example). Para
personalizar el entorno sin modificar archivos versionados, crea una copia local desde la raíz del
repositorio:

```powershell
Copy-Item config/local-services.properties.example config/local-services.properties
```

El archivo `config/local-services.properties` está ignorado por Git. Después de cambiarlo es
necesario volver a sincronizar y compilar RMM App y volver a configurar el proyecto CMake de Qt.

Antes de probar conexiones reales deberán estar disponibles los servicios que necesite cada flujo:

- el backend en `http://localhost:8080`;
- el broker MQTT en `127.0.0.1:1883`;
- MySQL, cuando el backend requiera persistencia.

El emulador Android utiliza `10.0.2.2` para acceder al `localhost` del equipo anfitrión. No debe
sustituirse esa dirección por `localhost` dentro de RMM App.

## RMM App para Android

### Requisitos

- Android Studio y un emulador configurado;
- JDK 17 o posterior;
- Android SDK Platform 36 y Build Tools 36.0.0.

### Ejecución desde Android Studio

1. Abre la carpeta `android` como proyecto.
2. Espera a que termine la sincronización de Gradle.
3. Selecciona el módulo `app` y el emulador Android.
4. Ejecuta la configuración `app`.

Android Studio compilará, instalará y abrirá RMM App en el dispositivo seleccionado.

### Compilación y pruebas desde PowerShell

Desde la raíz del repositorio:

```powershell
Set-Location android
.\gradlew.bat testDebugUnitTest assembleDebug
```

El APK generado queda en `android/app/build/outputs/apk/debug/app-debug.apk`. Para instalarlo desde
la terminal con un emulador o dispositivo ya conectado:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Aplicaciones Qt

### Requisitos

- Qt 6.9 o posterior para escritorio;
- Qt MQTT de la misma versión que Qt;
- CMake, Ninja y un compilador compatible con el kit seleccionado.

El entorno local de referencia utiliza Qt 6.11.1, MinGW 13.1 de 64 bits, CMake y Ninja instalados
mediante Qt Maintenance Tool.

### Ejecución desde Qt Creator

1. Abre `qt/CMakeLists.txt`.
2. Selecciona el kit `Qt 6.11.1 MinGW 64-bit`.
3. Configura el proyecto con `BUILD_TESTING` activado.
4. Elige uno de los objetivos y pulsa **Ejecutar**:
   - `rmm-ticket-vending-machine` para la máquina de venta;
   - `rmm-ticket-validator` para la máquina validadora.

Cada aplicación puede ejecutarse de manera independiente. También pueden mantenerse ambas abiertas
para comprobar el futuro entorno de máquinas simuladas.

### Compilación y pruebas desde PowerShell

Las rutas exactas dependen de la instalación local de Qt. Desde la raíz del repositorio, un ejemplo
para el entorno de referencia es:

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

Los ejecutables se generan como `RMMTicketVendingMachine.exe` y `RMMTicketValidator.exe` dentro de
`qt/build`. Cuando se lanzan fuera de Qt Creator, las bibliotecas dinámicas de Qt deben estar
disponibles en `PATH` o desplegadas junto al ejecutable.

## Artefactos de integración continua

El pipeline ejecuta las pruebas y construye las tres aplicaciones cliente en cada pull request
dirigida a `main` o `develop/ecosystem`:

- `rmm-app-debug` contiene el APK de depuración de RMM App;
- `rmm-app-unit-test-report` contiene el informe de sus pruebas unitarias;
- `rmm-qt-applications` contiene los ejecutables de las dos máquinas Qt.

Los artefactos sirven para revisar el resultado de una compilación. Los ejecutables Qt todavía no
constituyen un paquete redistribuible autónomo con todas sus bibliotecas.

## Problemas habituales

- Si Gradle no encuentra el SDK, configura `ANDROID_HOME`, `ANDROID_SDK_ROOT` o
  `android/local.properties`.
- Si RMM App no alcanza el backend desde el emulador, comprueba que utiliza `10.0.2.2` y que el
  backend escucha en el puerto configurado.
- Si CMake no encuentra `Qt6Mqtt`, instala Qt MQTT para el mismo kit y versión de Qt.
- Si Qt Creator muestra un kit inválido, vuelve a seleccionar CMake, Ninja y el compilador que
  pertenecen a la instalación activa de Qt.
- Si se modifica la configuración compartida, regenera las aplicaciones para incorporar los nuevos
  valores.

