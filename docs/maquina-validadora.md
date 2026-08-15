# Máquina validadora de billetes

La máquina validadora es una aplicación de escritorio desarrollada con C++20, Qt Widgets y Qt
MQTT. Simula el lector de los torniquetes de RMM y puede configurarse como validadora de entrada o
de salida. El backend conserva la autoridad sobre la firma del QR, el estado del billete, los
trayectos y cualquier consumo asociado.

## Responsabilidades

La aplicación permite:

- funcionar como torniquete de entrada o salida mediante configuración externa;
- leer automáticamente mediante cámara el QR de un billete físico o digital;
- publicar solicitudes de validación autenticadas mediante MQTT;
- presentar de forma accesible la aceptación o el motivo del rechazo;
- mostrar viajes, saldo o vigencia restantes cuando corresponda;
- representar la apertura del paso durante cinco segundos únicamente después de una respuesta
  aceptada;
- diferenciar acústicamente una aceptación de un rechazo;
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
aplicación obtiene el código y el nombre de la estación a partir de esa identidad canónica y los
muestra en la cabecera y en el panel de contexto. Si se proporcionan también estación o nombre
mediante variables opcionales, deben coincidir con el inventario. La estación de la solicitud debe
ser la asignada al dispositivo en la base de datos.

## Interfaz de kiosco y cámara

La validadora funciona en una sola pantalla y no ofrece botones, selectores de modo ni campos para
pegar manualmente el QR. Su cabecera identifica el dispositivo, el tipo de validadora, el código y
el nombre de la estación. El modo y la ubicación no pueden cambiarse desde la interfaz porque
forman parte del aprovisionamiento del puesto.

El lector utiliza la cámara predeterminada del sistema. El área de vídeo incorpora un marco que
ayuda a centrar el QR y solo admite contenidos con el formato de billete RMM. La cámara permanece
activa mientras la máquina está preparada, se detiene al detectar un código y no vuelve a aceptar
otra lectura hasta terminar el ciclo anterior. Si no existe cámara o falta el permiso del sistema,
la propia zona del lector informa del problema.

## Flujo de validación

1. El viajero sitúa el QR dentro del objetivo mostrado sobre la imagen de la cámara.
2. La aplicación genera una referencia de validación y un identificador de mensaje UUID.
3. Publica `ticket.validation-requested` con el modo, la estación y el QR.
4. El backend autentica la máquina y comprueba el contexto del torniquete.
5. Se verifican la firma, vigencia y revocación de la credencial QR.
6. El backend aplica las reglas del producto y registra la validación y el trayecto en una única
   transacción.
7. La respuesta `ticket.validation-decided` se publica en el topic privado del dispositivo.
8. La aplicación comprueba identidad y correlación antes de abrir el paso o mostrar el rechazo.
9. Al terminar el intervalo visual, limpia el resultado anterior y restablece automáticamente la
   cámara para la siguiente persona.

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

La pantalla representa cuatro estados estables:

| Estado | Representación | Cámara | Paso |
| --- | --- | --- | --- |
| Espera | Azul, esperando un billete | Activa | Cerrado |
| Comprobación | Ámbar, consultando el centro de control | Detenida | Cerrado |
| Aceptación | Verde y símbolo de confirmación | Detenida durante 5 segundos | Abierto |
| Rechazo | Rojo, símbolo de rechazo y motivo | Detenida durante 3 segundos | Cerrado |

Una aceptación emite un pitido. Un rechazo o una imposibilidad de validar emite tres pitidos. Las
señales acústicas complementan los textos, iconos y colores; no son el único medio de comunicar el
resultado. En un rechazo, la interfaz traduce los códigos técnicos a indicaciones claras:

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
torniquete cerrado. Al perder MQTT se detiene la cámara y se cancela cualquier restablecimiento
pendiente. Cuando la conexión vuelve, la pantalla regresa a espera y reactiva automáticamente el
lector. La solicitud pendiente se conserva durante las reconexiones de la ejecución actual, pero no
después de cerrar el proceso.

## Configuración local

Las direcciones del broker se generan desde `config/local-services.properties`. El modo, la
identidad y la contraseña se proporcionan mediante variables de entorno:

```powershell
$env:RMM_VALIDATOR_MODE = "ENTRY"
$env:RMM_VALIDATOR_DEVICE_CODE = "RMM-EN-ST038-01"
$env:RMM_VALIDATOR_MQTT_PASSWORD = "contraseña-local-de-la-maquina"
```

`RMM_VALIDATOR_STATION_CODE` y `RMM_VALIDATOR_STATION_NAME` son comprobaciones opcionales de
consistencia, no fuentes alternativas de ubicación. Para otra instancia de salida deben utilizarse
`EXIT` y una identidad `RMM-EX-*`. La contraseña debe coincidir con la generada para esa identidad
mediante la infraestructura de Mosquitto. No existe valor predeterminado y nunca debe almacenarse
en Git, CMake o la documentación.

## Compilación, ejecución y pruebas

En Qt Creator se abre `qt/CMakeLists.txt`, se selecciona el kit Qt 6.11.1 MinGW 64-bit y se ejecuta
el objetivo `rmm-ticket-validator`. El backend y Mosquitto deben estar activos para completar una
validación real.

Las pruebas automatizadas cubren solicitudes de entrada, respuestas de salida, rechazos,
correlación, referencias duplicadas y el ciclo visual del kiosco:

```powershell
& D:\Qt\Tools\CMake_64\bin\ctest.exe `
  --test-dir qt/build `
  --output-on-failure
```

Las pruebas Qt relevantes son:

- `rmm-validator-entries-exits-rejections-duplicates`, para el protocolo y las decisiones;
- `rmm-validator-camera-states-sounds-timers`, para activación de cámara, estados, uno o tres
  pitidos y tiempos de tres o cinco segundos;
- `rmm-qt-mqtt-identity-authentication`, para modo, identidad y estación inventariada.

Las reglas y la idempotencia del backend se comprueban también mediante JUnit.

## Límites de la simulación

- La lectura usa la cámara predeterminada del equipo; no se integra todavía con un escáner industrial
  ni ofrece entrada manual en el modo kiosco.
- La apertura del torniquete es una representación visual y no controla hardware.
- La solicitud pendiente no se conserva después de cerrar la aplicación.
- La operación online necesita Mosquitto, el backend y MySQL disponibles.

Estos límites permiten probar el flujo distribuido, la seguridad y la consistencia del dominio sin
depender todavía de periféricos específicos.
