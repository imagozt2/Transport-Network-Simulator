# Identidad y autenticación de las máquinas RMM

## Objetivo

Este documento define cómo se identifica, aprovisiona y autentica cada máquina Qt del ecosistema
RMM. Su finalidad es impedir la suplantación de dispositivos, limitar el acceso de cada instalación
y permitir renovar o revocar credenciales sin compartir contraseñas entre máquinas.

La identidad de una máquina es distinta de las cuentas de operadores y pasajeros. Ninguna de sus
credenciales permite iniciar sesión en el centro de control o en RMM App.

## Principios

- Cada instalación dispone de una identidad y unas credenciales propias.
- La clave privada se genera y permanece en la máquina.
- Las credenciales nunca se incluyen en Git, imágenes de contenedor ni instaladores públicos.
- El backend mantiene el inventario y decide qué función, estación y permisos tiene cada máquina.
- Mosquitto autentica la conexión, pero no decide reglas de negocio.
- La autorización aplica denegación por defecto y mínimo privilegio.
- Una reinstalación, sustitución o traslado requiere una operación explícita y auditable.
- La identidad declarada en un payload nunca sustituye a la identidad autenticada del transporte.

## Componentes de la identidad

### Código lógico de máquina

`deviceCode` identifica de forma estable un puesto funcional registrado en el inventario, por
ejemplo:

```text
RMM-TM-ST046-01
RMM-EN-ST046-01
RMM-EX-ST046-01
```

El código permite reconocer:

- una máquina de venta (`TM`);
- una validadora de entrada (`EN`);
- una validadora de salida (`EX`);
- la estación asignada;
- el ordinal dentro de esa función.

El código es público y no constituye un secreto. No concede permisos por sí mismo.

La nomenclatura canónica es:

| Tipo del inventario | Prefijo | Formato | Aplicación autorizada |
| --- | --- | --- | --- |
| `TICKET_MACHINE` | `RMM-TM-` | `RMM-TM-STnnn-nn` | Máquina de venta Qt |
| `ENTRY_VALIDATOR` | `RMM-EN-` | `RMM-EN-STnnn-nn` | Validadora Qt en modo `ENTRY` |
| `EXIT_VALIDATOR` | `RMM-EX-` | `RMM-EX-STnnn-nn` | Validadora Qt en modo `EXIT` |

`STnnn` debe ser el código de la estación registrada y el último bloque representa el ordinal de
la máquina en esa estación. Los prefijos históricos `RMM-SALE-*`, `RMM-VAL-*`, `RMM-ENTRY-*` y
`RMM-EXIT-*` no forman parte del contrato y deben rechazarse.

### Correspondencia obligatoria

Para una instalación, el mismo `deviceCode` debe aparecer en todos estos puntos:

- `devices.code` y `device_mqtt_identities.mqtt_client_id` en MySQL;
- usuario y `clientId` enviados por la aplicación Qt;
- usuario incluido en `mqtt-users.local` durante el desarrollo local;
- segmento `{deviceCode}` de los topics MQTT;
- `deviceCode` declarado en cada mensaje.

Mosquitto comprueba las credenciales y limita los topics. El backend vuelve a contrastar el código,
el tipo de máquina, la estación y el estado de la identidad con el inventario. Coincidir solo en el
prefijo no permite suplantar otra máquina.

### Inventario como fuente de verdad

`devices` es la fuente de verdad para el puesto físico, el tipo y la estación de una máquina.
`device_mqtt_identities` autoriza una instancia de ese puesto, pero no puede reasignarlo. Para cada
registro activo se exige simultáneamente:

- el prefijo `RMM-TM`, `RMM-EN` o `RMM-EX` coincide con `devices.device_type`;
- el bloque `STnnn` coincide con `stations.code` a través de `devices.station_id`;
- `device_mqtt_identities.device_id` apunta a esa misma máquina;
- `device_mqtt_identities.mqtt_client_id` coincide exactamente con `devices.code`;
- la identidad está activa y dentro de su periodo de validez.

Los nombres (`El Espigón`, por ejemplo) son información visible codificada en UTF-8; no participan
en la autenticación. Las aplicaciones Qt derivan la estación desde el código inventariado y solo
aceptan un nombre configurado si coincide con el nombre canónico. Trasladar una máquina requiere
actualizar el inventario y aprovisionar la identidad apropiada, no editar únicamente variables
locales.

Los datos iniciales crean una identidad MQTT por cada máquina inventariada. Las pruebas del
ecosistema consultan MySQL después de cargar los datos y fallan si falta una identidad o si código,
tipo, estación y `mqttClientId` dejan de ser coherentes.

### Instancia instalada

`deviceInstanceId` es un UUID generado al aprovisionar una instalación concreta. Distingue la
máquina actual de instalaciones anteriores que hayan usado el mismo `deviceCode`.

Una reinstalación completa o sustitución física crea un nuevo `deviceInstanceId` y revoca las
credenciales de la instancia anterior. Una actualización ordinaria de software conserva la
instancia.

### Credencial

Cada instancia tiene uno o más registros de credencial para permitir rotaciones sin interrupción:

| Dato | Finalidad |
| --- | --- |
| `credentialId` | Identificador interno y auditable de la credencial. |
| `certificateSerial` | Número de serie único del certificado X.509. |
| `fingerprintSha256` | Huella usada para comprobaciones y auditoría. |
| `issuedAt` | Inicio del periodo de gestión de la credencial. |
| `notBefore` / `notAfter` | Ventana criptográfica de validez. |
| `status` | `PENDING`, `ACTIVE`, `ROTATING`, `EXPIRED` o `REVOKED`. |
| `revocationReason` | Motivo estable cuando se revoca. |

No se persiste la clave privada ni una copia exportable de ella en el backend.

## Autoridad y certificados

RMM utiliza una autoridad certificadora interna exclusiva para identidades de máquina. Esta CA es
independiente de las claves Ed25519 empleadas para firmar los códigos QR.

La política inicial utiliza:

- certificados X.509 de cliente;
- claves ECDSA P-256 y firma SHA-256;
- TLS 1.3 o TLS 1.2 cuando algún componente no admita todavía TLS 1.3;
- periodos de validez breves y renovación automática antes de caducar;
- números de serie impredecibles y únicos.

La clave privada de la CA se inyecta como secreto y no se guarda en el repositorio, MySQL,
Mosquitto ni las aplicaciones Qt. En un despliegue real conviene aislarla en un servicio de PKI o
almacén de claves; el backend solicita o coordina la emisión, pero no expone la clave.

## Contenido del certificado

La identidad autorizable se toma de una extensión SAN URI:

```text
urn:rmm:device:{deviceCode}:{deviceInstanceId}
```

Ejemplo:

```text
urn:rmm:device:RMM-EN-ST046-01:4fb7510f-dfc0-437f-a785-e7314854f170
```

El certificado contiene además:

- `Extended Key Usage: Client Authentication`;
- emisor y número de serie;
- fechas de validez;
- clave pública;
- identificadores de política necesarios para reconocer una credencial RMM.

El nombre común del sujeto es informativo. La autorización nunca depende únicamente de `CN`, del
nombre del equipo, de una dirección IP o del `clientId` enviado a MQTT.

## Estados de una máquina

El registro administrativo de una máquina mantiene un estado de identidad separado de su estado
operativo:

| Estado | Significado |
| --- | --- |
| `PENDING_PROVISIONING` | Existe en el inventario, pero aún no tiene una instancia activa. |
| `ACTIVE` | Puede autenticarse y operar con los permisos asignados. |
| `SUSPENDED` | Conserva su identidad, pero se rechazan temporalmente sus conexiones. |
| `RETIRED` | Fue retirada definitivamente y no puede volver a activarse. |

`ONLINE`, `AVAILABLE`, `DEGRADED` y otros estados descritos en el contrato MQTT no sustituyen estos
estados administrativos.

## Aprovisionamiento inicial

En el entorno local actual, las identidades se crean a partir del inventario SQL y se autentican
contra contraseñas individuales de Mosquitto. El flujo con certificados descrito más adelante es el
modelo previsto para entornos seguros; no implica guardar certificados o claves privadas en Git.

### 1. Alta administrativa

Un operador autorizado registra previamente:

- `deviceCode`;
- tipo de máquina;
- estación y función;
- descripción o ubicación física;
- estado `PENDING_PROVISIONING`.

El backend impide duplicar códigos o asignar una función incompatible con la estación.

### 2. Creación de un código de aprovisionamiento

El operador genera una credencial de arranque que:

- es aleatoria y de alta entropía;
- caduca en pocos minutos;
- sirve una sola vez;
- está vinculada a un único `deviceCode`;
- se almacena únicamente mediante un hash en el backend;
- se entrega a la persona que instala la máquina por un canal separado.

Este código no es una contraseña MQTT y deja de ser válido tras el primer uso, aunque el proceso
termine correctamente.

### 3. Generación local de claves

La aplicación Qt genera localmente el par de claves y una solicitud de firma (CSR). La clave privada
no forma parte de la CSR ni se envía al backend.

### 4. Solicitud de inscripción

La máquina se conecta mediante HTTPS verificando el certificado del servidor y presenta:

- código de aprovisionamiento;
- `deviceCode` esperado;
- CSR;
- versión de la aplicación y metadatos técnicos mínimos.

El endpoint de inscripción es independiente de las APIs de pasajeros y operadores, aplica límites de
frecuencia y no acepta cookies de sesión como sustituto del código.

### 5. Emisión y activación

El backend consume el código de forma atómica, crea `deviceInstanceId`, valida la CSR y entrega:

- certificado de cliente;
- cadena pública de confianza;
- dirección y puerto TLS del broker;
- identificador de cliente MQTT esperado;
- fecha de renovación recomendada.

La máquina instala la credencial, realiza una conexión de prueba y confirma la activación. Solo
entonces el inventario pasa a `ACTIVE`. Un fallo deja un resultado auditable y exige generar un nuevo
código de aprovisionamiento; el anterior no se reactiva.

## Autenticación MQTT

La conexión con Mosquitto utiliza autenticación TLS mutua:

1. La máquina valida que el certificado del broker pertenece a la CA de servidores esperada.
2. Mosquitto solicita el certificado de cliente.
3. Verifica cadena, fechas, propósito y revocación.
4. Extrae la identidad RMM de la SAN URI mediante una integración controlada.
5. Aplica las ACL correspondientes a ese `deviceCode`.
6. El backend contrasta que instancia y credencial continúan activas antes de aceptar sus mensajes.

No se utiliza una contraseña compartida como alternativa silenciosa. En desarrollo local puede
existir una CA de desarrollo distinta, pero mantiene certificados individuales y nunca se promueve a
otros entornos.

### Identificador de cliente

En la implementación actual, el `clientId` es exactamente el `deviceCode`:

```text
RMM-TM-ST046-01
```

De este modo coincide con `device_mqtt_identities.mqtt_client_id`, el usuario autenticado y el
segmento del topic. Es estable y único, pero no es una credencial. Una evolución basada en
certificados podrá incorporar `deviceInstanceId` al identificador de sesión cuando broker, backend
y clientes adopten conjuntamente ese contrato; no debe cambiarse de forma unilateral en una
aplicación Qt.

### Asociación con los topics

La identidad derivada del certificado debe coincidir con `{deviceCode}` en los topics definidos en
el [contrato MQTT](contrato-mqtt.md). También prevalece sobre cualquier `deviceCode` incluido en el
JSON.

Una máquina puede:

- publicar únicamente en sus topics permitidos;
- suscribirse únicamente a sus órdenes, respuestas y configuración;
- leer el conjunto global de claves públicas QR;
- no acceder a topics de otra máquina ni a topics administrativos del backend.

## Autenticación HTTPS de las máquinas

Cuando una función futura necesite HTTPS después del aprovisionamiento, reutilizará el certificado
de cliente mediante mTLS, pero no los endpoints ni tokens de RMM App.

El backend obtiene `deviceCode` y `deviceInstanceId` del certificado y aplica permisos equivalentes a
los de MQTT. Los valores enviados en rutas o cuerpos solo identifican el recurso solicitado; no
autentican al llamante.

No se contempla el inicio de sesión interactivo de una máquina con correo y contraseña.

## Almacenamiento local

La aplicación Qt debe:

- preferir el almacén seguro de certificados del sistema operativo y una clave no exportable;
- restringir el acceso a la cuenta del proceso que ejecuta la máquina;
- no mostrar ni registrar la clave privada;
- no copiar credenciales a archivos de configuración, logs o informes de diagnóstico;
- borrar de memoria el código de aprovisionamiento tras usarlo;
- verificar permisos y validez antes de conectarse.

Si durante el desarrollo una biblioteca exige PKCS#12, el archivo se mantiene fuera del repositorio,
cifrado, con permisos restrictivos y una contraseña suministrada por el entorno. Es una solución de
desarrollo, no el mecanismo preferido para producción.

## Renovación

La máquina inicia la renovación antes de `notAfter`, mientras conserva una credencial válida:

1. genera un nuevo par de claves y una CSR;
2. solicita renovación mediante mTLS;
3. el backend comprueba identidad, estado y ventana permitida;
4. emite un certificado nuevo para la misma instancia;
5. ambos certificados conviven durante un periodo breve;
6. la máquina prueba el nuevo certificado y confirma su instalación;
7. el anterior pasa a `REVOKED` o expira al cerrar la ventana.

La renovación no cambia `deviceCode` ni `deviceInstanceId`. Los fallos se reintentan con espera
exponencial y generan una alerta antes de la caducidad.

Una credencial ya caducada no puede autorrenovarse. Requiere un nuevo aprovisionamiento autorizado.

## Revocación y suspensión

Se revoca una credencial cuando:

- la máquina se pierde, sustituye o reinstala;
- se sospecha que la clave privada fue copiada;
- se cambia de forma incompatible la asignación física;
- un operador retira definitivamente el dispositivo;
- se detecta un uso anómalo confirmado.

La operación:

1. cambia inmediatamente el estado administrativo;
2. registra operador, motivo, instante y credencial afectada;
3. actualiza la lista o mecanismo de revocación consumido por Mosquitto y el backend;
4. desconecta la sesión MQTT existente;
5. invalida códigos de aprovisionamiento pendientes;
6. impide renovaciones y nuevas conexiones.

La revocación no elimina eventos históricos. El backend conserva qué instancia y certificado
participaron en cada operación.

`SUSPENDED` es reversible mediante una decisión administrativa. `RETIRED` es definitivo; para
recuperar el puesto se crea una nueva instancia o un nuevo registro según el caso.

## Sustitución y traslado

### Sustitución física

Se revoca la instancia anterior y se aprovisiona otra para el mismo `deviceCode`. El historial no se
reasigna ni se pierde.

### Reinstalación completa

Se trata como una sustitución porque no debe reutilizarse una clave privada exportada.

### Cambio de estación o función

No basta con modificar la configuración local. El operador actualiza el inventario y, para evitar
permisos residuales, revoca la instancia y realiza un nuevo aprovisionamiento con el código lógico
que corresponda a la nueva función.

## Backend y broker

El backend utiliza su propio certificado de servicio, nunca uno de máquina. Puede consumir los
topics autorizados de todos los dispositivos y publicar respuestas, órdenes y configuración.

La identidad del backend:

- se gestiona y rota por separado;
- no utiliza comodines de publicación innecesarios;
- no comparte clave con Mosquitto;
- no se incorpora a aplicaciones Qt ni Android.

Mosquitto utiliza además un certificado de servidor cuyo nombre debe coincidir con el host
configurado. El certificado del servidor no concede permisos para publicar como backend.

## Auditoría

Se registran como mínimo:

- alta, edición, suspensión y retirada de una máquina;
- creación, consumo y caducidad de aprovisionamientos, sin guardar el código en claro;
- emisión, activación, renovación y revocación de certificados;
- operador responsable y motivo de cada acción administrativa;
- huella y serie del certificado usado en conexiones y operaciones relevantes;
- rechazos por certificado, identidad, topic o estado incompatible.

Los logs nunca incluyen claves privadas, códigos completos de aprovisionamiento, contraseñas ni
contenido íntegro de QR.

## Respuesta ante compromiso

Ante una posible filtración:

1. se suspende la máquina y se desconecta su sesión;
2. se revoca la credencial afectada;
3. se examinan eventos por `deviceInstanceId`, serie y huella;
4. se bloquean operaciones pendientes no ejecutadas;
5. se aprovisiona una instancia limpia si procede;
6. se documenta la incidencia y su resolución.

Comprometer una máquina no permite firmar códigos QR, emitir certificados para otras máquinas,
consultar MySQL ni acceder a cuentas de usuarios.

## Configuración por entornos

Desarrollo, pruebas y producción usan autoridades y brokers distintos. Un certificado emitido para
un entorno no es válido en otro.

Las variables de entorno o secretos desplegados solo contienen rutas, alias o contraseñas necesarias
para abrir almacenes protegidos. El repositorio puede incluir certificados públicos de CA de
desarrollo, scripts y ejemplos, pero nunca claves privadas o credenciales funcionales.

## Validaciones y pruebas futuras

La implementación debe cubrir:

- aprovisionamiento correcto, código vencido, reutilizado o destinado a otra máquina;
- CSR inválida y algoritmos no admitidos;
- certificado caducado, revocado, de otra CA o sin propósito de cliente;
- discrepancias entre SAN, `clientId`, topic y payload;
- ACL entre máquinas, tipos y backend;
- rotación con convivencia y retirada de la credencial anterior;
- desconexión inmediata tras suspensión o revocación;
- recuperación segura tras una instalación incompleta.

## Relación con otros contratos

- La [arquitectura del ecosistema](arquitectura-ecosistema.md) asigna responsabilidades y fuentes de
  verdad.
- El [contrato MQTT](contrato-mqtt.md) define los topics accesibles después de autenticarse.
- El [contrato de códigos QR](contrato-codigos-qr.md) utiliza claves distintas y limita el alcance de
  una máquina comprometida.
- Los [contratos REST de RMM App](contratos-rest-rmm-app.md) mantienen separada la autenticación de
  pasajeros.
- Los [flujos online y sin conexión](flujos-conectividad.md) regulan la reconexión de cada instancia.
