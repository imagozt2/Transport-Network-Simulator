# Contrato y firma de los códigos QR de RMM

## Objetivo

Este documento define el formato, la firma, la verificación y la renovación de los códigos QR que
representan billetes físicos y digitales de RMM. El contrato debe producir el mismo resultado en
Spring Boot, RMM App y las máquinas Qt sin compartir secretos entre aplicaciones.

El QR acredita una referencia emitida por RMM, pero no sustituye la consulta del estado actual del
billete. Saldo, viajes restantes, bloqueos, trayectos abiertos y reglas tarifarias continúan bajo la
autoridad del backend.

## Decisiones principales

- El contenido se firma mediante **JWS compacto**.
- El algoritmo de firma inicial es **Ed25519**, identificado como `EdDSA` en JWS.
- La clave privada solo existe en el backend o en su gestor de secretos.
- Android y Qt reciben únicamente claves públicas.
- Cada clave se identifica con un `kid` estable para permitir rotaciones.
- El contenido firmado es mínimo y no incluye información personal ni saldos.
- La validación online del backend es la decisión definitiva.
- Una captura o copia del QR no permite duplicar el consumo de un derecho.

## Representación externa

El texto codificado en el QR utiliza esta estructura:

```text
RMM:TICKET:1:<JWS_COMPACTO>
```

| Segmento | Descripción |
| --- | --- |
| `RMM` | Identifica el ecosistema emisor. |
| `TICKET` | Distingue un billete de otros QR futuros. |
| `1` | Versión del envoltorio exterior. |
| `<JWS_COMPACTO>` | Cabecera, payload y firma codificados con Base64url. |

La versión exterior permite rechazar formatos desconocidos antes de interpretar el JWS. También
queda incluida dentro del contenido firmado para impedir que alterar el prefijo cambie la semántica
del token.

El valor completo debe tratarse como sensible. No se escribirá íntegramente en logs, mensajes de
error, analítica ni URLs.

## JWS compacto

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

## Creación de una credencial QR

Solo el backend puede emitir una credencial:

1. Confirma que la compra, recarga o emisión compensatoria puede completarse.
2. Crea o actualiza el billete dentro de una transacción.
3. Genera un `jti` criptográficamente aleatorio y no reutilizable.
4. Selecciona la clave de firma activa y asigna su `kid`.
5. Construye la cabecera y el payload definidos por la versión.
6. Firma el JWS con Ed25519.
7. Persiste la huella de la credencial, su estado y la relación con el billete.
8. Devuelve el valor exterior `RMM:TICKET:1:...` al cliente autorizado.

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

### 2. Firma y claims

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

La revocación del QR no elimina el billete ni su historial. Una sustitución crea otro `jti`, firma un
nuevo JWS y conserva la trazabilidad entre credenciales.

## Gestión y rotación de claves

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

## Funcionamiento sin conexión

La versión inicial requiere una decisión online del backend. La verificación local de la firma puede
rechazar rápidamente un QR manipulado, pero no autoriza por sí sola el acceso porque la máquina
desconoce bloqueos, consumos o trayectos recientes.

Una futura política offline deberá definir por separado:

- duración máxima de desconexión;
- copia firmada y acotada de datos autorizables;
- protección frente a doble uso entre estaciones;
- cola idempotente de validaciones;
- reconciliación y resolución de conflictos al reconectar;
- productos que nunca admiten validación offline.

Hasta aprobar ese contrato, una validadora sin backend mostrará indisponibilidad temporal y emitirá
el evento técnico correspondiente.

## Protección frente a amenazas

| Amenaza | Medida principal |
| --- | --- |
| Alteración del payload | Firma Ed25519 verificada antes de interpretar datos. |
| Fabricación de billetes | Clave privada exclusiva del backend. |
| Copia o captura del QR | Estado central, trayecto único e idempotencia. |
| Apropiación de un billete físico | Código de vinculación separado y de un solo uso. |
| Enumeración de billetes | Códigos públicos aleatorios y respuestas no reveladoras. |
| Reutilización de una validación | Referencia externa única y resultado persistido. |
| Clave antigua o comprometida | `kid`, rotación, revocación y sustitución. |
| Filtración en observabilidad | Registro de huellas, nunca del token completo. |

## Registro seguro

Los logs pueden conservar:

- `jti` o una huella truncada cuando sea necesario correlacionar;
- código público del billete según los permisos del operador;
- `kid`, versión y resultado de la verificación;
- máquina, estación, instante y código de error.

No deben conservar el JWS completo, la clave privada, el código de vinculación ni datos personales.

## Límites y evolución

- Un consumidor que no conoce `ver` rechaza el QR de forma controlada.
- Añadir campos opcionales no cambia el significado de campos existentes.
- Cambiar algoritmo, semántica o campos obligatorios exige una nueva versión.
- Las versiones aceptadas se configuran explícitamente y disponen de una fecha de retirada.
- Los ejemplos de este documento son ilustrativos y nunca deben utilizarse como credenciales reales.

Los contratos REST y MQTT posteriores transportarán este valor sin reinterpretarlo y aplicarán los
límites de exposición definidos aquí.
