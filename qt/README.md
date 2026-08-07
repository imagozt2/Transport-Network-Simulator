# Aplicaciones Qt de RMM

Este directorio contiene las aplicaciones de escritorio que simularán las máquinas físicas del
ecosistema RMM. Por ahora incluye la estructura inicial de la máquina de venta.

## Requisitos locales

- Qt 6.11.1 para MinGW 64 bits.
- Qt MQTT 6.11.1.
- CMake y Ninja incluidos con Qt.
- Compilador MinGW 13.1 de 64 bits.

No se versionan rutas locales, configuraciones de Qt Creator, credenciales ni directorios de
compilación.

## Abrir con Qt Creator

Abre `qt/CMakeLists.txt`, selecciona el kit de Qt 6.11.1 MinGW 64-bit y configura el proyecto. El
ejecutable disponible es `rmm-ticket-vending-machine`.

## Compilar desde PowerShell

Desde la raíz del repositorio:

```powershell
& D:\Qt\Tools\CMake_64\bin\cmake.exe `
  -S qt `
  -B qt/build `
  -G Ninja `
  -DCMAKE_PREFIX_PATH=D:\Qt\6.11.1\mingw_64 `
  -DCMAKE_CXX_COMPILER=D:\Qt\Tools\mingw1310_64\bin\g++.exe

& D:\Qt\Tools\CMake_64\bin\cmake.exe --build qt/build
```

La conexión MQTT y los flujos de compra y emisión se incorporarán en fases posteriores.
