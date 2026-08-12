# Máquina validadora de billetes

La máquina validadora es una aplicación de escritorio desarrollada con C++20, Qt Widgets y Qt
MQTT. Simula el lector de los torniquetes de RMM y puede configurarse como validadora de entrada o
de salida. El backend conserva la autoridad sobre la firma del QR, el estado del billete, los
trayectos y cualquier consumo asociado.

## Responsabilidades

La aplicación permite:

- funcionar como torniquete de entrada o salida mediante configuración externa;
- leer el contenido de un QR físico o digital;
- publicar solicitudes de validación autenticadas mediante MQTT;
- presentar de forma accesible la aceptación o el motivo del rechazo;
- mostrar viajes, saldo o vigencia restantes cuando corresponda;
- representar la apertura del paso únicamente después de una respuesta aceptada;
- conservar una solicitud pendiente durante desconexiones temporales;
- correlacionar las respuestas y descartar las pertenecientes a otra lectura.

La validadora no interpreta criptográficamente el QR ni modifica localmente los billetes. Tampoco
calcula trayectos, tarifas o derechos de acceso. De este modo, todos los torniquetes aplican las
mismas reglas y MySQL conserva el estado autoritativo.

## Modos de funcionamiento

`RMM_VALIDATOR_MODE` admite dos valores:

| Modo | Tipo de dispositivo | Operación |
| --- | --- | --- |
| `ENTRY` | `ENTRY_VALIDATOR` | Abre un trayecto y consume un viaje cuando el producto lo requiere. |
| `EXIT` | `EXIT_VALIDATOR` | Cierra el trayecto, calcula su coste y descuenta saldo cuando corresponde. |

La identidad debe ser compatible con el modo: `RMM-EN-*` para entrada y `RMM-EX-*` para salida. La
identidad, la estación configurada y el registro del inventario deben representar la misma máquina.
La estación de la solicitud debe coincidir con la estación asignada al dispositivo en la base de
datos.

## Flujo de validación

1. El viajero presenta o introduce el QR en el lector simulado.
2. La aplicación genera una referencia de validación y un identificador de mensaje UUID.
3. Publica `ticket.validation-requested` con el modo, la estación y el QR.
4. El backend autentica la máquina y comprueba el contexto del torniquete.
5. Se verifican la firma, vigencia y revocación de la credencial QR.
6. El backend aplica las reglas del producto y registra la validación y el trayecto en una única
   transacción.
7. La respuesta `ticket.validation-decided` se publica en el topic privado del dispositivo.
8. La aplicación comprueba identidad y correlación antes de abrir el paso o mostrar el rechazo.

Una entrada aceptada queda asociada al trayecto abierto y una salida aceptada al trayecto cerrado.
Las validaciones rechazadas también se persisten para mantener trazabilidad operativa.

## Reglas de los títulos

- `SINGLE_TRIP`: solo permite entrar en el origen configurado y salir en el destino. Se agota al
  completar el trayecto.
- `MULTI_TRIP`: descuenta un viaje al aceptar la entrada. Aunque ese fuera el último viaje, permite
  completar la salida que permanece abierta.
- `TIME_PASS`: admite nuevas entradas únicamente dentro de su periodo de vigencia. Un trayecto que
  ya comenzó puede finalizar aunque la vigencia termine durante el recorrido.
- `SMART_BALANCE`: exige saldo suficiente para iniciar el viaje y descuenta en la salida la tarifa
  calculada según el recorrido real.

Los reintentos reutilizan la misma referencia. La idempotencia del backend devuelve la decisión ya
registrada y evita consumir dos veces viajes o saldo.

## Topics y mensajes MQTT

La solicitud se publica con QoS 1 en:

```text
rmm/v1/devices/{deviceCode}/requests/validations
```

La respuesta se recibe con QoS 1 en:

```text
rmm/v1/devices/{deviceCode}/responses
```

El payload de solicitud incluye `validationReference`, `direction`, `stationCode` y `qrValue`. La
respuesta contiene `decision`, `reasonCode`, la tarifa aplicada y, según el producto, el saldo, los
viajes o la vigencia restantes. Los sobres utilizan la versión 1 descrita en el
[contrato MQTT](contrato-mqtt.md).

## Resultados y rechazos

Una aceptación muestra el estado verde y autoriza visualmente el paso. En un rechazo, el
torniquete permanece cerrado y la interfaz traduce los códigos técnicos a indicaciones claras:

- QR desconocido, inválido o procesado con una referencia incompatible;
- billete inactivo, bloqueado, agotado o caducado;
- saldo insuficiente;
- entrada previa inexistente o trayecto ya abierto;
- estación o dispositivo incorrectos;
- indisponibilidad temporal del servicio.

La interfaz nunca abre el paso por una lectura local ni por una respuesta que no coincida con la
referencia pendiente.

## Desconexiones y reintentos

La validadora mantiene en memoria la solicitud pendiente y bloquea nuevas lecturas mientras intenta
completarla. La reconexión utiliza una espera exponencial de 1, 2, 4, 8, 16 y hasta 30 segundos. Una
solicitud puede publicarse hasta tres veces con el mismo contenido y referencia. Cada publicación
espera ocho segundos por la respuesta antes de reintentarse.

Si se agotan los intentos, la interfaz informa de que la validación no está disponible y mantiene el
torniquete cerrado. La solicitud pendiente se conserva durante las reconexiones de la ejecución
actual, pero no después de cerrar el proceso.

## Configuración local

Las direcciones del broker se generan desde `config/local-services.properties`. La identidad, la
estación, el modo y la contraseña se proporcionan mediante variables de entorno:

```powershell
$env:RMM_VALIDATOR_MODE = "ENTRY"
$env:RMM_VALIDATOR_STATION_CODE = "ST038"
$env:RMM_VALIDATOR_STATION_NAME = "Acueducto"
$env:RMM_VALIDATOR_DEVICE_CODE = "RMM-EN-ST038-01"
$env:RMM_VALIDATOR_MQTT_PASSWORD = "contraseña-local-de-la-maquina"
```

Para otra instancia de salida deben utilizarse `EXIT` y una identidad `RMM-EX-*`. La contraseña debe
coincidir con la generada para esa identidad mediante la infraestructura de Mosquitto. No existe
valor predeterminado y nunca debe almacenarse en Git, CMake o la documentación.

## Compilación, ejecución y pruebas

En Qt Creator se abre `qt/CMakeLists.txt`, se selecciona el kit Qt 6.11.1 MinGW 64-bit y se ejecuta
el objetivo `rmm-ticket-validator`. El backend y Mosquitto deben estar activos para completar una
validación real.

Las pruebas automatizadas cubren solicitudes de entrada, respuestas de salida, rechazos,
correlación y referencias duplicadas:

```powershell
& D:\Qt\Tools\CMake_64\bin\ctest.exe `
  --test-dir qt/build `
  --output-on-failure
```

La prueba Qt aparece como `rmm-validator-entries-exits-rejections-duplicates`. Las reglas y la
idempotencia del backend se comprueban también mediante JUnit.

## Límites de la simulación

- La lectura utiliza un diálogo de entrada porque no existe una cámara o escáner físico integrado.
- La apertura del torniquete es una representación visual y no controla hardware.
- La solicitud pendiente no se conserva después de cerrar la aplicación.
- La operación online necesita Mosquitto, el backend y MySQL disponibles.

Estos límites permiten probar el flujo distribuido, la seguridad y la consistencia del dominio sin
depender todavía de periféricos específicos.
