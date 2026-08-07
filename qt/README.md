# Aplicaciones Qt de RMM

Este directorio contiene las aplicaciones de escritorio que simularán las máquinas físicas del
ecosistema RMM. Incluye las estructuras iniciales de la máquina de venta y la máquina validadora.

## Requisitos locales

- Qt 6.11.1 para MinGW 64 bits.
- Qt MQTT 6.11.1.
- CMake y Ninja incluidos con Qt.
- Compilador MinGW 13.1 de 64 bits.

No se versionan rutas locales, configuraciones de Qt Creator, credenciales ni directorios de
compilación.

Las direcciones de API y MQTT se leen desde
[`../config/local-services.properties.example`](../config/local-services.properties.example). Para
usar otros valores sin modificar Git, crea `../config/local-services.properties` siguiendo la
[guía compartida](../config/README.md).

## Abrir con Qt Creator

Abre `qt/CMakeLists.txt`, selecciona el kit de Qt 6.11.1 MinGW 64-bit y configura el proyecto. Los
ejecutables disponibles son:

- `rmm-ticket-vending-machine` para simular la compra y emisión de billetes;
- `rmm-ticket-validator` para simular las validaciones de entrada y salida.

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

La conexión MQTT, la lectura de QR y los flujos de compra, emisión y validación se incorporarán en
fases posteriores.
