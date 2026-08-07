# Arquitectura del ecosistema RMM

## Objetivo

El ecosistema de la Red de Metro de Macegocia (RMM) simula la operación ferroviaria, la gestión de
viajeros y el uso de títulos de transporte desde distintos puntos de acceso. Este documento delimita
los componentes que lo forman, sus responsabilidades y la autoridad que cada uno ejerce sobre los
datos.

Los contratos concretos de billetes, códigos QR, API REST y mensajes MQTT se documentarán por
separado. Esta visión evita que una aplicación cliente reproduzca reglas que deben permanecer en el
backend o modifique información cuya propiedad corresponde a otro componente.

## Vista general

```text
                         Operadores
                             │
                             ▼
                    Aplicación web Angular
                             │ HTTPS / JSON
                             ▼
Pasajeros ──► RMM App ──► Backend Spring Boot ◄──► MySQL
                             ▲        │
                             │        │ MQTT
                             │        ▼
                    Broker Eclipse Mosquitto
                         ▲             ▲
                         │             │
                 Máquina de venta   Validadora
                       Qt               Qt
```

Spring Boot constituye el límite de confianza central. Las aplicaciones cliente solicitan
operaciones y presentan sus resultados, pero no deciden por sí mismas si una compra, emisión,
registro o validación es válida.

## Componentes y responsabilidades

### Backend Spring Boot

Es la autoridad funcional y de seguridad del ecosistema.

Responsabilidades:

- aplicar las reglas de títulos, billetes, compras, recargas y validaciones;
- autenticar operadores, pasajeros y dispositivos mediante mecanismos independientes;
- calcular trayectos y consultar la configuración de la red;
- generar, firmar y verificar los datos representados mediante códigos QR;
- autorizar entradas y salidas y construir el historial de desplazamientos;
- consumir y publicar mensajes MQTT sin confiar directamente en el contenido recibido;
- procesar cada operación de forma idempotente para tolerar reintentos y mensajes duplicados;
- registrar auditoría, eventos de máquinas e incidencias;
- ofrecer contratos versionados a la aplicación web, RMM App y las máquinas.

No es responsabilidad del backend dibujar interfaces, acceder directamente a cámaras o simular un
pago realizado por la interfaz.

### Base de datos MySQL

Es el almacenamiento persistente del backend y no se expone a ninguna aplicación cliente.

Responsabilidades:

- conservar la red, la configuración operativa y el estado ferroviario necesario;
- almacenar cuentas, títulos, billetes, movimientos y validaciones;
- mantener relaciones, restricciones e identificadores únicos;
- persistir auditoría, incidencias y eventos procesados;
- sostener la idempotencia mediante claves y referencias estables.

Solo el backend accede a MySQL. Las máquinas, la aplicación web y RMM App nunca reciben credenciales
de base de datos.

### Broker Eclipse Mosquitto

Es el canal de mensajería entre el backend y las máquinas, no una fuente de verdad del negocio.

Responsabilidades:

- autenticar conexiones MQTT y aplicar permisos por topic;
- transportar eventos, órdenes y confirmaciones;
- proporcionar niveles de calidad de servicio adecuados;
- conservar únicamente los mensajes que el contrato determine;
- permitir la reconexión sin convertir la entrega de un mensaje en una operación duplicada.

Mosquitto no valida billetes, no calcula precios y no modifica la base de datos. Esas decisiones
permanecen en el backend.

### Aplicación web del centro de control

Es la herramienta de supervisión y administración utilizada por operadores.

Responsabilidades:

- presentar la operación ferroviaria y el estado de la infraestructura;
- consultar máquinas, eventos, pasajeros, títulos e incidencias;
- ejecutar acciones administrativas autorizadas;
- solicitar emisiones compensatorias y mostrar su resultado;
- mantener navegación contextual entre entidades relacionadas;
- diferenciar claramente datos simulados, eventos reales y estados sin conexión.

La aplicación web no se comunica directamente con MySQL, Mosquitto ni las máquinas. Toda operación
pasa por la API del backend.

### RMM App para Android

Es la aplicación de autoservicio del pasajero.

Responsabilidades:

- registrar y autenticar al pasajero;
- consultar la red y planificar trayectos;
- mostrar el catálogo de títulos y solicitar compras o recargas simuladas;
- mantener una cartera visual de billetes asociados a la cuenta;
- mostrar los códigos QR emitidos por el backend;
- escanear un billete físico para solicitar su vinculación;
- consultar el saldo, la vigencia y el historial de desplazamientos;
- proteger localmente la sesión y minimizar los datos almacenados en el dispositivo.

RMM App no firma billetes, no altera saldos y no determina por sí sola la validez de un QR. Su canal
principal será la API HTTPS; no necesita conectarse directamente al broker MQTT.

### Máquina de venta Qt

Simula una máquina física de expedición situada en una estación.

Responsabilidades:

- identificarse ante el ecosistema como un dispositivo concreto;
- presentar el catálogo y recoger los parámetros de la compra;
- simular el proceso de pago sin almacenar datos bancarios reales;
- solicitar al backend la emisión o recarga correspondiente;
- presentar o imprimir de forma simulada el billete y su QR;
- recibir y ejecutar órdenes de emisión compensatoria dirigidas a ella;
- publicar eventos de disponibilidad, emisión y error técnico;
- conservar temporalmente operaciones pendientes cuando se pierda la conexión.

La máquina no calcula de manera autoritativa el precio ni crea billetes válidos sin confirmación del
backend.

### Máquina validadora Qt

Simula el lector instalado en un torniquete de entrada o salida.

Responsabilidades:

- identificarse mediante su código, estación y función configurada;
- capturar o recibir el código QR de un billete;
- solicitar al backend la validación de entrada o salida;
- mostrar de forma inequívoca la aceptación o el motivo del rechazo;
- publicar eventos operativos y de conectividad;
- impedir envíos repetidos involuntarios mientras procesa una lectura;
- aplicar el comportamiento sin conexión que establezca el contrato futuro.

La validadora no descuenta viajes, modifica saldo ni decide definitivamente la validez del billete.

### Contenedores e infraestructura local

La infraestructura reproducible agrupa los servicios compartidos del entorno de desarrollo.

Responsabilidades:

- iniciar MySQL, Spring Boot y Mosquitto con configuración versionada;
- proporcionar redes, volúmenes y comprobaciones de salud;
- inyectar secretos mediante variables o archivos locales ignorados por Git;
- ofrecer puertos y nombres de servicio estables;
- separar configuración de desarrollo, pruebas y producción.

Las aplicaciones Android y Qt se ejecutan inicialmente fuera de los contenedores para facilitar su
interacción gráfica y depuración.

## Propiedad de los datos

| Información | Autoridad | Consumidores principales |
| --- | --- | --- |
| Red, estaciones y trayectos | Backend y MySQL | Web, Android y máquinas |
| Productos, precios y reglas | Backend y MySQL | Web, Android y máquina de venta |
| Billetes, saldo y vigencia | Backend y MySQL | Android, venta y validadora |
| Firma y verificación del QR | Backend | Android y máquinas |
| Sesión de operador | Backend | Aplicación web |
| Sesión de pasajero | Backend | RMM App |
| Identidad de máquina | Backend | Venta, validadora y web |
| Transporte de telemetría | Mosquitto | Backend y máquinas |
| Eventos y auditoría | Backend y MySQL | Aplicación web |
| Preferencias visuales | Cada cliente | El propio cliente |

## Canales de comunicación

| Origen | Destino | Canal | Finalidad |
| --- | --- | --- | --- |
| Aplicación web | Backend | HTTPS y JSON | Supervisión y administración |
| RMM App | Backend | HTTPS y JSON | Cuenta, red, compras y cartera |
| Máquina de venta | Backend | HTTPS y MQTT | Operaciones síncronas, órdenes y eventos |
| Validadora | Backend | HTTPS y MQTT | Validaciones, conectividad y eventos |
| Backend | MySQL | JDBC | Persistencia transaccional |
| Backend | Mosquitto | MQTT | Órdenes, recepción de eventos y estado |

Los clientes no deben crear canales alternativos directos entre sí. Una emisión iniciada por el
centro de control, por ejemplo, se autoriza en el backend y se entrega a la máquina a través del
canal establecido, manteniendo una única trazabilidad.

## Principios transversales

- **Autoridad central:** las reglas de negocio residen en el backend.
- **Mínimo privilegio:** cada usuario y máquina accede únicamente a sus operaciones y topics.
- **Sin secretos versionados:** contraseñas, claves privadas y tokens quedan fuera de Git.
- **Identificadores estables:** los intercambios usan códigos públicos y referencias únicas.
- **Idempotencia:** un reintento no puede generar dos compras, emisiones o validaciones.
- **Trazabilidad:** las operaciones relevantes conservan actor, dispositivo, fecha y resultado.
- **Contratos versionados:** REST, MQTT y QR evolucionan de manera compatible y documentada.
- **Responsabilidad limitada del cliente:** las interfaces validan la entrada para mejorar la
  experiencia, pero el backend vuelve a validar todas las operaciones.
- **Desarrollo reproducible:** el entorno local y los pipelines utilizan configuraciones declaradas.

## Alcance de las siguientes decisiones

Este documento no fija todavía:

- los estados y transiciones exactos de cada tipo de billete;
- el contenido binario o textual del QR y su algoritmo de firma;
- las rutas, cuerpos y respuestas concretas de la API REST;
- la jerarquía definitiva de topics y payloads MQTT;
- las reglas completas del funcionamiento sin conexión.

Cada aspecto se concretará en los siguientes documentos de arquitectura, respetando los límites y
responsabilidades establecidos aquí. El [ciclo de vida de los billetes RMM](ciclo-vida-billetes.md)
y el [contrato de códigos QR](contrato-codigos-qr.md) desarrollan las primeras decisiones. La
[API REST de RMM App](contratos-rest-rmm-app.md) delimita la comunicación del cliente Android con el
backend.
