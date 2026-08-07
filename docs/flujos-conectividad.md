# Flujos online y sin conexión del ecosistema RMM

## Objetivo

Este documento define cómo se comportan RMM App, las máquinas Qt, el backend y la aplicación web
cuando sus comunicaciones están disponibles, se interrumpen o se recuperan.

La primera versión prioriza la coherencia de billetes, saldo, viajes y trayectos. Trabajar sin
conexión no significa tomar decisiones de negocio con datos potencialmente obsoletos: las funciones
de consulta pueden degradarse, pero las mutaciones que necesitan al backend quedan pendientes o se
rechazan de forma segura.

## Principios

- Spring Boot y MySQL continúan siendo la autoridad del negocio.
- Un cliente nunca presenta como confirmado un resultado que el backend no haya confirmado.
- Toda operación reintentable conserva la misma referencia idempotente.
- La interfaz distingue datos actuales, datos almacenados y funciones no disponibles.
- La firma del QR permite comprobar integridad, pero no revela bloqueos o consumos recientes.
- Solo se almacenan localmente los datos mínimos y durante el tiempo necesario.
- La recuperación de conexión no puede duplicar compras, recargas, emisiones o validaciones.
- La hora local de un cliente no sustituye a la hora autoritativa del backend.

## Estados de conectividad

Cada aplicación deriva uno de estos estados sin modificar por ello el estado administrativo u
operativo de sus entidades:

| Estado | Significado |
| --- | --- |
| `ONLINE` | Los servicios necesarios responden y la última comprobación es reciente. |
| `DEGRADED` | Solo una parte de los servicios está disponible o las respuestas exceden el umbral. |
| `RECONNECTING` | El cliente perdió la comunicación y está intentando recuperarla. |
| `OFFLINE` | No existe una ruta utilizable hacia el servicio requerido. |

No se deduce `ONLINE` únicamente porque exista red local, conexión Wi-Fi o una sesión MQTT abierta.
La aplicación comprueba el servicio concreto que necesita.

## Matriz de disponibilidad inicial

| Función | Online | Sin conexión en la versión inicial |
| --- | --- | --- |
| Consultar mapa, líneas y estaciones | Datos actuales | Última copia válida con fecha |
| Calcular trayectos | Backend | Resultado almacenado o no disponible |
| Consultar productos y precios | Backend | Catálogo informativo almacenado |
| Registrar o iniciar sesión | Backend | No disponible |
| Consultar cartera e historial | Backend | Última copia, marcada como no actualizada |
| Mostrar un QR de RMM App | Backend | No disponible inicialmente |
| Comprar o recargar | Backend | No se ejecuta; puede conservarse un borrador |
| Vincular un billete físico | Backend | No disponible |
| Vender o emitir desde una máquina | Backend y máquina | No se inicia una emisión nueva |
| Validar entrada o salida | Decisión del backend | No se autoriza el paso |
| Consultar el centro de control | Backend | No disponible; no inventa datos actuales |
| Telemetría y eventos técnicos | MQTT | Cola local limitada y posterior reenvío |

Esta política es deliberadamente conservadora. Una futura validación offline parcial necesitará
listas de autorización firmadas, límites temporales y mecanismos contra doble uso antes de poder
aceptar pasajeros sin consultar al backend.

## Flujo online de RMM App

### Inicio y sincronización

1. La aplicación comprueba conectividad HTTPS.
2. Si dispone de refresh token, solicita una sesión nueva mediante el contrato REST.
3. Consulta en paralelo la cuenta, cartera y datos públicos necesarios.
4. Usa `ETag` para evitar descargar red y catálogo sin cambios.
5. Reemplaza las copias locales únicamente tras validar una respuesta completa.
6. Muestra la fecha de última sincronización cuando los datos puedan quedar obsoletos.

Un fallo parcial conserva la última copia válida y marca solo la sección afectada como degradada.

### Compra o recarga

1. El usuario configura la operación y solicita una cotización.
2. La aplicación genera una `Idempotency-Key` por intención del usuario.
3. Envía la compra o recarga una sola vez desde la interfaz.
4. Si recibe confirmación, actualiza la cartera desde el backend.
5. Si la respuesta se pierde, no supone éxito ni fracaso: consulta el recurso usando la referencia
   antes de reintentar.
6. Un reintento usa la misma clave y el mismo cuerpo.

Cambiar cualquier parámetro crea una intención nueva y otra clave.

### Presentación del QR

1. La aplicación solicita el QR del billete autenticado.
2. El backend comprueba propiedad y estado actual.
3. La respuesta usa `Cache-Control: no-store`.
4. El QR se mantiene únicamente durante la vista activa y se elimina al salir o cerrar sesión.
5. La validadora solicita igualmente una decisión online al backend.

La versión inicial no promete que un QR pueda abrirse por primera vez sin conexión.

## RMM App sin conexión

### Datos permitidos

Android puede conservar cifrados o en almacenamiento privado:

- estructura pública de la red;
- catálogo informativo y reglas de presentación;
- últimos datos de cuenta, cartera e historial necesarios para consulta;
- preferencias de idioma y accesibilidad;
- borradores de formularios que no contengan contraseñas, tokens ni QR.

Cada conjunto incluye `syncedAt`, versión y, cuando exista, `ETag`. Las pantallas muestran
“Información actualizada el …” y no expresiones como “estado actual”.

### Datos no disponibles

Sin conexión no se permite:

- crear cuentas, verificar correo o iniciar sesión;
- confirmar compras, pagos o recargas;
- vincular billetes físicos;
- renovar una credencial QR;
- modificar correo, contraseña o sesiones;
- afirmar saldo, viajes o vigencia actuales.

Cerrar sesión elimina tokens, QR en memoria y datos privados según la política de privacidad. La
aplicación puede conservar únicamente datos públicos y preferencias no sensibles.

### Operaciones iniciadas durante una interrupción

Un formulario puede guardarse como borrador, pero no como operación enviada. Al recuperar conexión,
la aplicación vuelve a solicitar precio y confirmación explícita si el importe o las condiciones
pudieron cambiar.

No se automatizan compras o recargas en segundo plano después de que el usuario crea que fallaron.

## Flujo online de una validadora Qt

1. La máquina se autentica con su certificado individual.
2. Publica presencia y estado y recibe configuración y claves públicas QR.
3. Al leer un QR comprueba formato, versión, algoritmo y firma para descartar manipulaciones obvias.
4. Genera `validationReference` y publica `ticket.validation-requested`.
5. Bloquea nuevas lecturas del mismo torno mientras espera la decisión.
6. El backend verifica máquina, estación, billete, estado y reglas tarifarias en una transacción.
7. Devuelve `ticket.validation-decided` correlacionado.
8. La máquina abre o mantiene cerrado el torno y muestra un resultado inequívoco.
9. Los reintentos conservan `validationReference` y reciben la misma decisión.

La apertura se produce únicamente tras `ACCEPTED`. Un timeout no equivale a aceptación ni a rechazo
tarifario.

## Validadora sin conexión

### Comportamiento inicial

Al perder MQTT o superar el tiempo máximo sin respuesta:

- termina la espera actual con `SERVICE_UNAVAILABLE`;
- no abre el torno;
- muestra “Validación temporalmente no disponible”, no “Billete inválido”;
- impide que la lectura cree una entrada, salida o descuento local;
- publica o encola un evento técnico sin incluir el QR completo;
- inicia reconexión con espera exponencial.

La verificación criptográfica local puede rechazar un QR mal formado o con firma inválida, pero un QR
correctamente firmado no se acepta offline porque podrían existir bloqueos, usos o trayectos
posteriores a su emisión.

### Lo que no se encola

Una lectura a la que no se concedió acceso no se reenvía después como si hubiera ocurrido una
validación. Puede conservarse una referencia diagnóstica anonimizada, pero no crea derechos ni
trayectos retroactivos.

### Evolución futura

Aceptar validaciones offline requeriría, como mínimo:

- una política firmada con productos y estaciones admitidos;
- una duración máxima de desconexión;
- un reloj protegido y tolerancias documentadas;
- listas acotadas de credenciales revocadas o autorizadas;
- protección frente a doble uso entre validadores;
- un diario local cifrado, inmutable e idempotente;
- reglas de reconciliación y regularización de conflictos;
- límites de riesgo y un mecanismo de desactivación remota.

Hasta implementar y probar todas esas piezas, la política permanece cerrada a la aceptación offline.

## Flujo online de una máquina de venta Qt

### Compra ordinaria

1. La máquina consulta catálogo y reglas actuales al backend.
2. El usuario configura el título y obtiene una cotización.
3. La máquina crea una referencia idempotente y solicita la compra.
4. El backend valida el pago simulado y crea el billete y su QR.
5. Envía una orden de emisión con `commandId` y `expiresAt`.
6. La máquina confirma `RECEIVED`, presenta o imprime y responde `COMPLETED`.
7. El backend conserva compra, billete, máquina y resultado.

### Emisión compensatoria

1. Un operador la autoriza desde la aplicación web.
2. El backend persiste la solicitud antes de publicar la orden MQTT.
3. La máquina verifica destinatario, expiración y duplicados.
4. Solo `COMPLETED` marca la emisión como finalizada.
5. Repetir `commandId` devuelve el resultado almacenado sin imprimir de nuevo.

## Máquina de venta sin conexión

La máquina no inicia compras, recargas ni emisiones nuevas porque no puede:

- confirmar precios y estado del producto;
- procesar el pago simulado de forma autoritativa;
- crear o firmar un QR válido;
- comprobar que una orden administrativa sigue vigente.

Mantiene visible el catálogo almacenado solo como información y deshabilita la confirmación con un
mensaje de indisponibilidad. No promete completar automáticamente un formulario cuando vuelva la
conexión.

### Interrupción durante una orden

Si una orden válida ya fue recibida antes de la desconexión:

- la máquina la persiste antes de confirmar `RECEIVED`;
- puede terminar la presentación si no ha expirado y no requiere otra decisión del backend;
- conserva cifrado el resultado y el `commandId`;
- al reconectar publica el último estado conocido;
- nunca repite una impresión ya marcada localmente como `COMPLETED`.

Si no puede determinar si llegó a presentar el billete, informa `FAILED` con un código de resultado
específico. El backend no crea una segunda emisión automáticamente; exige consulta o intervención
según el tipo de operación.

## Backend, broker y base de datos

### Broker no disponible

- Las APIs que no dependen de máquinas pueden continuar.
- Las validaciones y emisiones dirigidas a máquinas responden como temporalmente no disponibles o
  permanecen en estado pendiente con caducidad explícita.
- El backend no marca máquinas como averiadas: distingue `OFFLINE` o conectividad desconocida de un
  error técnico del dispositivo.
- Las órdenes se conservan en MySQL, no en memoria ni como mensajes MQTT retenidos.

### Base de datos no disponible

El backend no autoriza validaciones, compras, recargas o emisiones. Devuelve un error temporal y no
usa datos en memoria como fuente alternativa para mutaciones.

### Backend no disponible

Mosquitto puede mantener sesiones y mensajes según su configuración, pero no toma decisiones de
negocio. Las máquinas aplican su política sin conexión y RMM App muestra datos almacenados.

### Aplicación web

El centro de control no tiene modo offline administrativo. Si pierde el backend:

- conserva la estructura visual y comunica la interrupción;
- no muestra agregados almacenados como si fueran actuales;
- deshabilita acciones de usuarios, incidencias y emisiones;
- no se conecta directamente a MySQL, Mosquitto o las máquinas.

## Colas locales de las máquinas

### Qué puede encolarse

- confirmaciones de órdenes ya ejecutadas;
- eventos de ciclo de vida y conectividad;
- eventos técnicos y métricas agregadas;
- último estado operativo;
- referencias mínimas necesarias para reconciliar una operación ya iniciada.

No se encolan nuevas decisiones de compra ni validaciones aceptadas localmente.

### Registro de cola

Cada elemento contiene:

```json
{
  "queueId": "bd524cdc-1b79-4af5-ae34-6c06cf51b737",
  "messageId": "68374bc0-a13f-4306-a357-af2e18f81ee5",
  "businessReference": "RMM-CMD-01J4YR0N48B8E6ZFV7N3AK3X82",
  "messageType": "ticket.issue-acknowledged",
  "occurredAt": "2026-08-07T10:30:02Z",
  "queuedAt": "2026-08-07T10:30:02.050Z",
  "attempts": 0,
  "nextAttemptAt": "2026-08-07T10:30:03Z"
}
```

El payload se conserva cifrado por separado cuando contiene información sensible.

### Límites y prioridad

- Las confirmaciones de negocio tienen prioridad y no se eliminan por métricas.
- La telemetría puede compactarse o descartarse al alcanzar el límite.
- Los estados sucesivos pueden sustituirse conservando el más reciente.
- Los eventos técnicos tienen límites de tamaño, antigüedad y cantidad.
- Una cola llena cambia la máquina a `DEGRADED` y genera una alerta visible.

No se registran contraseñas, claves privadas, códigos de aprovisionamiento ni QR completos en logs.

## Reconexión y reconciliación

Al recuperar la comunicación, una máquina sigue este orden:

1. valida fecha, certificado y configuración TLS;
2. se autentica y publica `ONLINE`;
3. recibe configuración y claves públicas retenidas;
4. compara versiones antes de aplicarlas;
5. publica confirmaciones de negocio pendientes en orden local;
6. publica eventos y el último estado operativo;
7. elimina un elemento solo después de la confirmación técnica establecida;
8. reanuda nuevas operaciones cuando configuración y reloj son válidos.

El backend:

1. autentica la instancia y comprueba que sigue activa;
2. deduplica por `messageId` y referencia de negocio;
3. devuelve el resultado ya persistido a los duplicados;
4. rechaza órdenes o respuestas expiradas e incompatibles;
5. conserva hora del dispositivo y hora de recepción;
6. actualiza agregados solo desde datos reconciliados.

El orden de recepción no prevalece sobre las transiciones permitidas del dominio.

## Reintentos y resultados ambiguos

Los clientes aplican espera exponencial con variación aleatoria. No reintentan indefinidamente una
operación vencida ni crean una referencia nueva para evitar una respuesta previa.

Cuando se pierde una respuesta:

- RMM App consulta compra, recarga o billete por su referencia;
- una máquina consulta o vuelve a publicar usando el mismo identificador;
- el backend responde con el resultado persistido;
- la interfaz usa “Comprobando resultado” hasta resolver la ambigüedad.

`UNKNOWN` significa que todavía no puede determinarse el resultado, no que la operación fallara.

## Tiempo y caducidad

- El backend usa UTC como autoridad.
- Las máquinas sincronizan su reloj y publican su desviación estimada.
- Una desviación superior al umbral cambia la máquina a `DEGRADED` y bloquea operaciones sensibles.
- `expiresAt` se evalúa con tolerancias pequeñas y documentadas, nunca ampliadas por el cliente.
- Al reconectar, eventos antiguos no alteran retroactivamente un estado ya resuelto.

## Experiencia de usuario

Los mensajes distinguen:

- **billete rechazado**, cuando existe una decisión funcional;
- **servicio no disponible**, cuando falta conectividad o autoridad;
- **resultado pendiente**, cuando una operación pudo llegar al backend;
- **datos almacenados**, cuando la pantalla no está actualizada.

No se utiliza el color como único indicador. Los controles bloqueados mantienen una explicación
accesible y las aplicaciones no muestran errores técnicos, topics o identificadores internos al
usuario final.

## Seguridad y privacidad

- El almacenamiento local privado se cifra y se limita por usuario o identidad de máquina.
- Los tokens de Android se protegen mediante Android Keystore.
- Las claves de máquinas siguen el contrato de identidad y no son exportables cuando sea posible.
- Una cola se elimina de forma segura al retirar o reprovisionar una máquina.
- Los datos de otro pasajero nunca se mezclan con la sesión actual.
- La reconexión exige volver a autenticar; tener mensajes pendientes no concede acceso.

## Observabilidad

Se registran y miden:

- inicio, duración y recuperación de interrupciones;
- tamaño, antigüedad y descartes de colas;
- reintentos, duplicados y operaciones ambiguas;
- latencia de validaciones y emisiones;
- diferencias entre hora local y del servidor;
- máquinas y aplicaciones con versiones o configuraciones obsoletas.

Las métricas no incluyen QR completos, tokens, credenciales o datos personales innecesarios.

## Pruebas futuras

La implementación debe cubrir:

- pérdida de red antes, durante y después de cada operación;
- respuesta procesada por el backend pero perdida para el cliente;
- reinicio de una máquina con elementos pendientes;
- duplicados y mensajes fuera de orden;
- broker, backend y MySQL caídos de forma independiente;
- expiración de órdenes durante una desconexión;
- colas llenas, corruptas o con elementos sensibles;
- cambio de usuario de Android con datos almacenados;
- recuperación con credencial de máquina revocada;
- presentación accesible de estados degradados.

## Relación con otros contratos

- La [arquitectura del ecosistema](arquitectura-ecosistema.md) define autoridades y componentes.
- El [ciclo de vida de los billetes](ciclo-vida-billetes.md) limita las transiciones reconciliables.
- El [contrato QR](contrato-codigos-qr.md) explica por qué una firma válida no basta offline.
- Los [contratos REST de RMM App](contratos-rest-rmm-app.md) definen idempotencia y caché móvil.
- El [contrato MQTT](contrato-mqtt.md) define mensajes, QoS y correlación.
- La [identidad de las máquinas](identidad-maquinas.md) protege la reconexión y las colas por
  instancia.
