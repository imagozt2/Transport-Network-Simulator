# Contrato y firma de los códigos QR de RMM

## Objetivo

Este documento define el formato, la verificación y la renovación de los códigos QR que
representan billetes físicos y digitales de RMM. El contrato debe producir el mismo resultado en
Spring Boot, RMM App y las máquinas Qt.

El QR acredita una referencia emitida por RMM, pero no sustituye la consulta del estado actual del
billete. Saldo, viajes restantes, bloqueos, trayectos abiertos y reglas tarifarias continúan bajo la
autoridad del backend.

## Decisiones principales

- Las nuevas emisiones utilizan un **token opaco v2** aleatorio de 192 bits.
- El backend persiste la huella SHA-256 y resuelve el estado autoritativo de la credencial.
- Los JWS v1 firmados con Ed25519 continúan admitiéndose para no invalidar billetes existentes.
- El QR no incluye información personal, saldos ni otros datos funcionales variables.
- La validación online del backend es la decisión definitiva.
- Una captura o copia del QR no permite duplicar el consumo de un derecho.

## Representación externa

Las nuevas emisiones utilizan esta estructura:

```text
RMM:TICKET:2:<TOKEN_BASE64URL>
```

| Segmento | Descripción |
| --- | --- |
| `RMM` | Identifica el ecosistema emisor. |
| `TICKET` | Distingue un billete de otros QR futuros. |
| `2` | Versión compacta del envoltorio exterior. |
| `<TOKEN_BASE64URL>` | 24 bytes aleatorios representados mediante 32 caracteres Base64url. |

La versión exterior permite rechazar formatos desconocidos antes de consultar la credencial. El
token no contiene campos interpretables: su posesión permite presentar el billete, mientras que su
huella identifica la credencial persistida y todos sus derechos se consultan en el backend.

El valor completo debe tratarse como sensible. No se escribirá íntegramente en logs, mensajes de
error, analítica ni URLs.

## Compatibilidad con JWS v1

Los QR emitidos antes de la versión compacta mantienen la estructura
`RMM:TICKET:1:<JWS_COMPACTO>` y continúan verificándose mediante Ed25519. Los apartados siguientes
describen exclusivamente ese formato heredado y permiten completar una transición sin invalidar
soportes ya entregados.

### JWS compacto

Un JWS compacto contiene tres partes separadas por puntos:

```text
BASE64URL(cabecera).BASE64URL(payload).BASE64URL(firma)
```

La codificación Base64url se realiza sin relleno `=`. Los productores generan JSON UTF-8 sin campos
adicionales no definidos. Los consumidores verifican la firma antes de utilizar cualquier valor del
payload.

### Cabecera protegida

Ejemplo legible previo a Base64url:

```json
{
  "alg": "EdDSA",
  "kid": "rmm-ticket-2026-01",
  "typ": "RMM-TICKET"
}
```

| Campo | Obligatorio | Regla |
| --- | --- | --- |
| `alg` | Sí | Debe ser exactamente `EdDSA` en la versión 1. |
| `kid` | Sí | Selecciona una clave pública RMM conocida y vigente. |
| `typ` | Sí | Debe ser exactamente `RMM-TICKET`. |

No se acepta `alg: none`, algoritmos simétricos ni un algoritmo seleccionado libremente por el
cliente. La lista permitida se configura en el verificador, no se deduce del token.

### Payload firmado

Ejemplo:

```json
{
  "ver": 1,
  "iss": "rmm-ticketing",
  "aud": "rmm-validator",
  "jti": "b8915833-4199-4d21-9b5e-270b587d30aa",
  "ticket": "RMM-TKT-01J8YQ7V4F6V2X0K8M3P9N5C2A",
  "medium": "DIGITAL",
  "iat": 1786032000
}
```

| Campo | Tipo | Obligatorio | Descripción |
| --- | --- | --- | --- |
| `ver` | entero | Sí | Versión del payload; inicialmente `1`. |
| `iss` | texto | Sí | Emisor; debe ser `rmm-ticketing`. |
| `aud` | texto | Sí | Audiencia; debe ser `rmm-validator`. |
| `jti` | UUID | Sí | Identificador único de la credencial QR. |
| `ticket` | texto | Sí | Código público no secuencial del billete. |
| `medium` | enumeración | Sí | `PHYSICAL` o `DIGITAL`. |
| `iat` | entero | Sí | Instante de emisión de la credencial en Unix epoch UTC. |
| `exp` | entero | No | Caducidad técnica de la credencial, no del producto. |

`exp` se utilizará cuando una credencial deba renovarse periódicamente. Su ausencia no convierte el
billete en permanente: el backend sigue comprobando vigencia, estado y derechos del producto.

## Información que no contiene el QR

El payload no incorpora:

- nombre, correo, identificador o datos del pasajero;
- saldo monetario o viajes restantes;
- fechas funcionales del abono;
- origen, destino o precio del trayecto;
- estado actual del billete;
- trayectos o validaciones anteriores;
- contraseña, PIN, token de sesión o clave criptográfica;
- decisión anticipada de aceptación o rechazo.

Estos datos cambian o son privados. Incluirlos produciría decisiones obsoletas, filtraría información
y aumentaría el tamaño del símbolo.

## Creación de una credencial QR v2

Solo el backend puede emitir una credencial:

1. Confirma que la compra, recarga o emisión compensatoria puede completarse.
2. Crea o actualiza el billete dentro de una transacción.
3. Genera 24 bytes mediante `SecureRandom`, sin reutilizarlos entre credenciales.
4. Los representa como 32 caracteres Base64url sin relleno.
5. Calcula la huella SHA-256 del valor exterior completo.
6. Persiste la huella, el valor presentable, su estado y la relación con el billete.
7. Devuelve `RMM:TICKET:2:...` al cliente autorizado.

La respuesta de una petición idempotente ya completada devuelve la misma emisión o su estado
persistido; nunca crea otro billete por repetir el mensaje.

## Verificación

La verificación se divide en capas y se detiene ante el primer error:

### 1. Formato

- el tamaño está dentro del límite admitido;
- el prefijo y la versión exterior son conocidos;
- el JWS contiene exactamente tres segmentos;
- cabecera y payload son JSON UTF-8 válidos;
- no hay campos duplicados ni tipos incompatibles.

### 2. Resolución de la credencial

Para v2:

- el token contiene exactamente 24 bytes aleatorios;
- su huella SHA-256 corresponde a una credencial persistida;
- la credencial está asociada de forma coherente al billete y al soporte.

Para el formato v1 heredado se mantienen la firma y los claims:

- `alg`, `typ`, `iss`, `aud` y `ver` coinciden con valores permitidos;
- `kid` identifica una clave pública confiable;
- la firma Ed25519 es válida;
- `jti` y `ticket` tienen el formato esperado;
- `iat` no está injustificadamente en el futuro;
- `exp`, cuando existe, no ha vencido.

### 3. Credencial persistida

- `jti` existe y pertenece al billete indicado;
- la credencial no está revocada ni sustituida;
- el soporte físico o digital es coherente con la emisión;
- el billete existe y la referencia no ha sido manipulada.

### 4. Regla de transporte

- el billete se encuentra activo y no bloqueado;
- conserva saldo, viajes o vigencia suficiente;
- no existe otro trayecto abierto incompatible;
- la estación y función de la validadora son correctas;
- la referencia de validación no fue procesada anteriormente.

Una firma válida solo demuestra que RMM emitió la credencial. No garantiza por sí sola que el billete
pueda utilizarse en ese momento.

## Resultado de validación

El backend devuelve un resultado estructurado con:

- referencia idempotente de la validación;
- decisión `ACCEPTED` o `REJECTED`;
- código estable del motivo;
- tipo `ENTRY` o `EXIT`;
- instante autoritativo;
- cambios de viaje o saldo cuando correspondan;
- datos mínimos que la máquina necesita presentar.

La validadora traduce el código de resultado, pero no reemplaza la decisión del backend.

## Credenciales físicas y digitales

### Físicas

- El QR puede mantenerse estable mientras la credencial continúe activa.
- Una reimpresión administrativa debe decidir expresamente si conserva o sustituye la credencial.
- Revocar una credencial impide utilizar copias o fotografías anteriores.
- La vinculación a RMM App requiere, además del QR, un código de vinculación de un solo uso impreso o
  entregado separadamente por la máquina.

El código de vinculación no forma parte del QR y se almacena en el backend mediante una derivación no
reversible. Esto reduce el riesgo de que una fotografía del QR permita apropiarse del billete.

### Digitales

- RMM App solicita el QR únicamente para billetes pertenecientes a la sesión autenticada.
- La aplicación evita persistir el valor en logs, copias de seguridad o almacenamiento compartido.
- El backend puede emitir una credencial nueva y revocar la anterior ante pérdida de sesión,
  reinstalación o una política de rotación.
- Una captura sigue siendo una copia de la misma credencial y no crea derechos adicionales.

## Revocación y sustitución

Cada credencial mantiene uno de estos estados:

| Estado | Significado |
| --- | --- |
| `ACTIVE` | Puede presentarse y continuar con la validación funcional. |
| `REVOKED` | Se invalidó antes de su caducidad y no puede recuperarse. |
| `SUPERSEDED` | Fue reemplazada por otra credencial del mismo billete. |
| `EXPIRED` | Alcanzó su `exp` técnico, cuando exista. |

La revocación del QR no elimina el billete ni su historial. Una sustitución crea un nuevo token
opaco v2 y conserva la trazabilidad entre credenciales. Los billetes heredados mantienen su `jti`
y su JWS v1 mientras sigan vigentes.

## Gestión y rotación de claves heredadas

Los QR v2 no contienen datos firmados ni necesitan claves de firma: su seguridad procede de un token
aleatorio de 192 bits y de la consulta online al backend. Las claves descritas en esta sección se
mantienen exclusivamente para verificar QR v1 ya emitidos.

### Clave privada

- Se genera fuera del repositorio.
- Se proporciona al backend mediante un secreto montado o un gestor de claves.
- Nunca se envía a Android, Qt, MySQL, Mosquitto ni al frontend web.
- No se muestra en logs ni variables incorporadas a imágenes de contenedor.
- Producción y desarrollo utilizan claves diferentes.

### Claves públicas

- Se identifican mediante `kid`.
- Pueden distribuirse a las validadoras mediante una configuración firmada o un endpoint HTTPS
  autenticado.
- Se almacenan localmente con fecha de actualización y estado.
- No se eliminan mientras existan credenciales vigentes firmadas con ellas.

### Rotación

1. Se genera un nuevo par y se publica primero la clave pública.
2. Las máquinas confirman que conocen el nuevo `kid`.
3. El backend comienza a firmar con la nueva clave privada.
4. La clave anterior permanece disponible solo para verificar.
5. Al finalizar el periodo de convivencia se retira o revoca de forma controlada.

Una clave comprometida se revoca inmediatamente. Las credenciales afectadas se sustituyen o exigen
validación online según la respuesta de seguridad acordada.

## Implementación en el backend

La implementación se encuentra en `com.transport.simulator.ticketing.qr` y separa expresamente la
construcción del contenido, la firma, la verificación y la protección frente a reintentos:

| Componente | Responsabilidad |
| --- | --- |
| `TicketQrTokenIssuer` | Genera el token opaco aleatorio y su huella SHA-256 para los QR v2. |
| `TicketQrPayloadFactory` | Construye los claims de los QR v1 heredados. |
| `TicketQrPayloadCodec` | Serializa y deserializa el payload JSON UTF-8 de v1. |
| `TicketQrSigner` | Firma con Ed25519 los valores v1 heredados. |
| `TicketQrKeyRing` | Conserva las claves públicas necesarias para verificar v1. |
| `TicketQrVerifier` | Resuelve los tokens v2 contra la base de datos y verifica completamente los v1. |
| `TicketQrUseGuard` | Registra referencias idempotentes y detecta reutilizaciones incompatibles. |

La firma y la verificación utilizan directamente los proveedores criptográficos de Java 21. No se
emplean secretos simétricos ni se permite que el algoritmo indicado por el propio token seleccione
libremente el verificador.

### Configuración criptográfica

El repositorio no contiene claves reales ni valores predeterminados. El entorno de ejecución debe
proporcionar:

| Variable | Contenido |
| --- | --- |
| `TICKET_QR_SIGNING_KEY_ID` | Identificador versionado de la clave activa, por ejemplo `rmm-ticket-2026-01`. |
| `TICKET_QR_SIGNING_PRIVATE_KEY` | Clave privada Ed25519 en PKCS#8, PEM o Base64 DER. |
| `TICKET_QR_SIGNING_PUBLIC_KEY` | Clave pública correspondiente en X.509, PEM o Base64 DER. |
| `TICKET_QR_RETIRED_PUBLIC_KEYS` | Claves públicas anteriores que todavía pueden verificar credenciales. |
| `TICKET_QR_ALLOWED_CLOCK_SKEW_SECONDS` | Tolerancia temporal; por defecto, 60 segundos. |
| `TICKET_QR_MAXIMUM_LENGTH` | Longitud máxima aceptada; por defecto, 4096 caracteres. |

Las claves retiradas se expresan como entradas separadas por punto y coma:

```text
rmm-ticket-2025-01=BASE64_X509;rmm-ticket-2025-02=BASE64_X509
```

La emisión normal de QR v2 no carga material privado. Estas variables solo son necesarias mientras
el entorno deba crear o verificar credenciales v1 heredadas.

### Datos persistidos

`ticket_qr_credentials` conserva la autoridad sobre cada credencial:

- `credential_id` corresponde al claim `jti`;
- `signing_key_id` corresponde al `kid` protegido en v1 y contiene el marcador `opaque-v2` en v2;
- `token_fingerprint` contiene SHA-256 del valor exterior completo;
- `credential_status` controla activación, revocación, sustitución y caducidad;
- `issued_at` y `expires_at` son autoritativos en v2 y deben coincidir con `iat` y `exp` en v1;
- `ticket_id` y `support_id` vinculan el QR con su billete y soporte reales.

No se persiste la clave privada. El valor QR se conserva para poder volver a mostrarlo al propietario
del billete, pero las búsquedas y comparaciones utilizan exclusivamente su huella.

`ticket_qr_use_claims` protege el procesamiento de validaciones. Cada petición aporta una
`validation_reference` única y se registra junto con una huella canónica de credencial, operación,
máquina y estación. El valor QR completo tampoco se almacena en esta tabla.

### Secuencia de verificación

`TicketQrVerifier` identifica primero la versión. Para v2 aplica las comprobaciones en este orden:

1. limita la longitud y reconoce el prefijo y la versión exterior;
2. exige un token Base64url canónico de 32 caracteres y 24 bytes;
3. calcula su huella y localiza la credencial;
4. contrasta versión, esquema, billete, soporte y huella;
5. rechaza credenciales revocadas, sustituidas o caducadas.

Para v1 conserva adicionalmente la validación completa de cabecera, firma Ed25519, claims y fechas.

Los fallos se clasifican mediante `TicketQrVerificationFailure`:

| Grupo | Resultados principales |
| --- | --- |
| Formato | `MALFORMED_QR`, `UNSUPPORTED_VERSION`, `INVALID_HEADER`, `INVALID_PAYLOAD` |
| Criptografía | `UNTRUSTED_KEY`, `INVALID_SIGNATURE`, `VERIFICATION_NOT_CONFIGURED` |
| Tiempo | `NOT_YET_VALID`, `EXPIRED` |
| Persistencia | `CREDENTIAL_NOT_FOUND`, `CREDENTIAL_INCONSISTENT` |
| Ciclo de vida | `CREDENTIAL_REVOKED`, `CREDENTIAL_SUPERSEDED` |

Estos códigos son internos y estables. Las APIs futuras podrán traducirlos a decisiones aptas para
las máquinas sin revelar si un código concreto pertenece a un usuario o billete existente.

### Rotación operativa de v1

El `kid` funciona como versión de la clave. Para una rotación ordinaria:

1. se genera el nuevo par Ed25519 fuera del repositorio;
2. se distribuye la nueva clave pública a los verificadores;
3. se cambia `TICKET_QR_SIGNING_KEY_ID` y el par activo;
4. se incorpora la clave pública anterior a `TICKET_QR_RETIRED_PUBLIC_KEYS`;
5. se reinicia de forma controlada el servicio para cargar el nuevo anillo;
6. se conserva la clave retirada mientras queden credenciales vigentes firmadas con ella.

Una clave retirada verifica, pero nunca vuelve a firmar. Ante una filtración debe eliminarse del
anillo de confianza y deben revocarse o sustituirse las credenciales afectadas.

### Idempotencia y duplicación

Un QR físico puede permanecer estable durante varios trayectos, por lo que escanearlo no lo consume
automáticamente. La protección se aplica a cada operación de validación:

- la primera referencia crea una reserva `RECEIVED`;
- repetir la referencia con los mismos datos produce `IDEMPOTENT_RETRY`;
- cambiar credencial, tipo, máquina o estación con la misma referencia produce un rechazo;
- al finalizar la decisión, la reserva pasa a `COMPLETED`;
- un bloqueo pesimista serializa las operaciones concurrentes sobre la credencial.

De esta manera, los reintentos causados por pérdida de red no descuentan dos veces saldo o viajes,
pero una misma credencial puede realizar posteriormente otra entrada o salida legítima con una
referencia nueva.

### Cobertura automatizada

`TicketQrSecurityTests` cubre el formato opaco v2 y genera además un par Ed25519 efímero para la
compatibilidad v1. La cobertura incluye:

- emisión y verificación de tokens compactos v2;
- rechazo de tokens v2 desconocidos o revocados;
- firma y verificación completas de v1;
- alteración del payload;
- caducidad técnica;
- revocación persistida;
- reintentos idempotentes;
- reutilización de una referencia con datos distintos.

Las claves generadas para las pruebas solo existen en memoria y nunca se escriben en el repositorio.

## Funcionamiento sin conexión

La versión actual requiere una decisión online del backend. Un QR v2 no revela datos autorizables y
solo puede resolverse allí. En v1, la verificación local de la firma puede rechazar rápidamente una
manipulación, pero tampoco autoriza por sí sola el acceso porque la máquina desconoce bloqueos,
consumos o trayectos recientes.

Una futura política de aceptación offline deberá definir por separado:

- duración máxima de desconexión;
- copia firmada y acotada de datos autorizables;
- protección frente a doble uso entre estaciones;
- cola idempotente de validaciones;
- reconciliación y resolución de conflictos al reconectar;
- productos que nunca admiten validación offline.

Hasta aprobar ese contrato, una validadora sin backend mostrará indisponibilidad temporal y emitirá
el evento técnico correspondiente. Los demás comportamientos durante una interrupción se detallan
en los [flujos online y sin conexión](flujos-conectividad.md).

## Protección frente a amenazas

| Amenaza | Medida principal |
| --- | --- |
| Alteración o fabricación de QR v2 | Token aleatorio de 192 bits resuelto contra una credencial persistida. |
| Alteración o fabricación de QR v1 | Firma Ed25519 verificada antes de interpretar datos. |
| Copia o captura del QR | Estado central, trayecto único e idempotencia. |
| Apropiación de un billete físico | Código de vinculación separado y de un solo uso. |
| Enumeración de billetes | Códigos públicos aleatorios y respuestas no reveladoras. |
| Reutilización de una validación | Referencia externa única y resultado persistido. |
| Clave v1 antigua o comprometida | `kid`, rotación, revocación y sustitución. |
| Filtración en observabilidad | Registro de huellas, nunca del token completo. |

## Registro seguro

Los logs pueden conservar:

- `jti` o una huella truncada cuando sea necesario correlacionar;
- código público del billete según los permisos del operador;
- `kid`, versión y resultado de la verificación;
- máquina, estación, instante y código de error.

No deben conservar el token QR completo —sea opaco v2 o JWS v1—, la clave privada, el código de
vinculación ni datos personales.

## Límites y evolución

- Un consumidor que no conoce `ver` rechaza el QR de forma controlada.
- Añadir campos opcionales no cambia el significado de campos existentes.
- Cambiar algoritmo, semántica o campos obligatorios exige una nueva versión.
- Las versiones aceptadas se configuran explícitamente y disponen de una fecha de retirada.
- Los ejemplos de este documento son ilustrativos y nunca deben utilizarse como credenciales reales.

El [contrato REST de RMM App](contratos-rest-rmm-app.md) y el [contrato MQTT de las máquinas
Qt](contrato-mqtt.md) transportan este valor sin reinterpretarlo y aplican los límites de exposición
definidos aquí.
