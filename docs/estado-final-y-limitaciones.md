# Estado final y limitaciones del proyecto

## Propósito

Transport Network Simulator es un ecosistema funcional de simulación y portfolio para la Red de
Metro de Macegocia. Integra supervisión operativa, administración, venta, recarga y validación de
billetes sin depender del prototipo anterior. No pretende sustituir una plataforma ferroviaria,
tarifaria o de pagos de producción.

Este documento fija el alcance consolidado del repositorio. Las guías especializadas describen los
contratos y cada interfaz con mayor detalle; cuando una propuesta de evolución aparezca en ellas, no
debe interpretarse como una función ya disponible.

## Componentes terminados

| Componente | Estado funcional |
| --- | --- |
| Centro de control Angular | Supervisa la red simulada y administra operadores, pasajeros, incidencias, máquinas, logs y emisiones compensatorias. |
| Backend Spring Boot | Es la autoridad sobre autenticación, operación ferroviaria, billetes, QR, trayectos, dispositivos y mensajería. |
| MySQL | Conserva el inventario, la configuración, las cuentas y la trazabilidad transaccional mediante un esquema reproducible. |
| RMM App para Android | Permite registrarse, iniciar sesión, consultar la red, planificar trayectos, comprar títulos y utilizar la cartera. |
| Máquina de venta Qt | Simula compras y recargas, presenta los QR emitidos y procesa órdenes compensatorias. |
| Máquina validadora Qt | Lee QR y simula validaciones de entrada y salida con respuesta visual y acústica. |
| Mosquitto | Transporta mensajes autenticados entre el backend y las identidades Qt con ACL por dispositivo. |
| Integración continua | Compila todos los clientes y ejecuta pruebas de backend, frontend, Android, Qt, MQTT, base de datos y contenedores. |

## Flujos integrados disponibles

- compra digital desde RMM App y entrega en la cartera del pasajero;
- compra y recarga desde la máquina de venta;
- emisión administrativa digital o dirigida a una máquina física;
- vinculación de un soporte físico con una cuenta de pasajero;
- validaciones de entrada y salida y construcción del desplazamiento resultante;
- consulta de QR, saldos, viajes, vigencia, operaciones y desplazamientos;
- supervisión de conectividad MQTT, eventos de máquinas, incidencias y logs;
- recuperación de sesiones Android y operaciones Qt pendientes frente a interrupciones o reinicios;
- cálculo de trayectos sobre la red y simulación horaria de trenes, estaciones y cocheras.

La aceptación final automatizada verifica de forma conjunta compra, recarga, entrada, rechazo de una
entrada duplicada, salida, cierre del desplazamiento, consumo del título y trazabilidad MQTT. Las
suites de interfaz verifican además temas, preferencias, errores recuperables y restablecimiento de
los clientes, sin presentar esas comprobaciones como integración con hardware real.

## Naturaleza de la simulación

- La ciudad, la red, sus horarios, la flota y las máquinas son ficticios.
- La circulación ferroviaria se calcula de forma determinista a partir de la hora y la configuración;
  no recibe telemetría, enclavamientos ni posiciones de trenes reales.
- Los estados y eventos automáticos de máquinas pueden ser simulados por el backend. Los clientes Qt
  conectados mediante MQTT se distinguen de esas fuentes simuladas.
- Los pagos usan un método simulado. No existe conexión con bancos, adquirentes, terminales EMV ni
  gestión real de devoluciones.
- Las aplicaciones Qt representan una máquina y un torniquete, pero no controlan impresoras,
  barreras, sensores, lectores NFC ni otro hardware físico.

## Limitaciones funcionales deliberadas

### Conectividad y operación sin conexión

El backend sigue siendo la autoridad para compras, recargas, emisiones y validaciones. Android y Qt
pueden conservar sesión u operaciones pendientes y reintentarlas de forma idempotente, pero no
autorizan viajes ni alteran saldos sin conexión. No se ha implementado una lista de autorización
offline firmada ni una política local contra el doble uso de un QR.

### Infraestructura

El entorno de Docker Compose inicia MySQL, Mosquitto y el backend. El frontend, el emulador Android y
las interfaces Qt se ejecutan en el equipo anfitrión. El despliegue está pensado para desarrollo
local y validación automatizada, no para alta disponibilidad, replicación, balanceo, copias de
seguridad administradas o recuperación ante desastres.

El listener MQTT local funciona sin TLS de forma predeterminada. Existe configuración para habilitar
TLS y certificados de cliente, pero el repositorio no incorpora una PKI de producción ni automatiza
la custodia, renovación y revocación de certificados.

### Seguridad y servicios externos

- Las credenciales, claves de firma QR y certificados deben aprovisionarse fuera de Git.
- La configuración local no sustituye un gestor de secretos, KMS o HSM de producción.
- La verificación de correo y la recuperación de contraseña admiten SMTP, desactivado por defecto;
  requieren un proveedor y una configuración externos para entregar mensajes reales.
- La seguridad implementada reduce riesgos dentro del simulador, pero no constituye una auditoría,
  certificación ni garantía de cumplimiento normativo.

### Contratos y clientes

- El contrato REST implementado es el que consumen los clientes actuales. Las cotizaciones remotas,
  el listado histórico de compras y la generación automática de OpenAPI descritos como evolución en
  los contratos no forman parte del estado final.
- Los QR firmados se verifican contra el backend y sus claves configuradas. No se publican claves
  privadas ni QR completos en logs.
- RMM App se valida con la versión de Android y el emulador definidos por el proyecto. No existe una
  publicación en Google Play ni una matriz exhaustiva de fabricantes y dispositivos físicos.
- Las aplicaciones Qt se compilan con los kits documentados, pero no se distribuyen mediante un
  instalador firmado para Windows.

### Observabilidad y datos

Los logs operativos e incidencias permiten seguir los escenarios simulados. No se incluyen una
plataforma externa de métricas, alertas, trazas distribuidas, retención regulada o anonimización para
producción. Los datos iniciales son reproducibles y demostrativos; no representan pasajeros ni
operaciones reales.

## Criterio de finalización

El proyecto se considera funcionalmente terminado cuando:

1. los componentes pueden arrancarse siguiendo la guía del ecosistema;
2. los flujos integrados anteriores pueden ejecutarse con datos locales;
3. las siete comprobaciones del workflow finalizan correctamente;
4. el repositorio no contiene credenciales ni configuración privada;
5. código, esquema, contratos y documentación describen el mismo comportamiento.

Los criterios funcionales anteriores están respaldados por el escenario integrado en contenedores y
por las suites específicas de backend, frontend, Android y Qt. Las limitaciones deliberadas de este
documento no se consideran fallos pendientes.

Las mejoras posteriores deben corregir defectos o inconsistencias dentro de este alcance. Una
integración con pagos reales, hardware ferroviario, validación offline, infraestructura distribuida
o publicación comercial constituye un proyecto adicional, no una corrección pendiente.

## Documentación de referencia

- [Guía final de ejecución y validación](guia-ecosistema-rmm.md)
- [Arquitectura del ecosistema](arquitectura-ecosistema.md)
- [Flujos online y sin conexión](flujos-conectividad.md)
- [Contratos REST de RMM App](contratos-rest-rmm-app.md)
- [Contrato MQTT](contrato-mqtt.md)
- [Seguridad de los códigos QR](contrato-codigos-qr.md)

