# Aplicaciones Qt de RMM

Este directorio contiene las aplicaciones de escritorio que simulan las máquinas físicas del
ecosistema RMM: una máquina de venta y una máquina validadora funcionales.

## Requisitos locales

- Qt 6.9 o posterior para MinGW 64 bits (el entorno local de referencia utiliza Qt 6.11.1).
- Qt MQTT de la misma versión que Qt.
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

La guía conjunta de ejecución, servicios previos y artefactos está disponible en
[`../docs/ejecucion-aplicaciones-cliente.md`](../docs/ejecucion-aplicaciones-cliente.md).
El flujo funcional, la configuración y los contratos de la máquina de venta están documentados en
[`../docs/maquina-venta.md`](../docs/maquina-venta.md).
El funcionamiento de los torniquetes, sus modos y validaciones MQTT se describe en
[`../docs/maquina-validadora.md`](../docs/maquina-validadora.md).

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
& D:\Qt\Tools\CMake_64\bin\ctest.exe --test-dir qt/build --output-on-failure
```

Los ejecutables `RMMTicketVendingMachine.exe` y `RMMTicketValidator.exe` se generan dentro de
`qt/build`.

La máquina de venta integra el catálogo, la red y la consulta de billetes recargables mediante HTTP;
las compras, recargas, emisiones, eventos y órdenes compensatorias se completan mediante MQTT. La
validadora publica entradas y salidas, interpreta las decisiones del backend y se recupera de
desconexiones temporales.
