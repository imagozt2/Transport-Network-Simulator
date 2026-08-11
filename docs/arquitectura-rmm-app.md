# Arquitectura de RMM App

## Objetivo y alcance

RMM App es el cliente Android para pasajeros de la Red de Metro de Macegocia. La aplicación presenta
los datos y operaciones de la cuenta, pero el backend Spring Boot continúa siendo la autoridad sobre
usuarios, sesiones, billetes, precios, trayectos y códigos QR.

Esta guía describe la base Android implementada actualmente: identidad visual, navegación principal,
configuración de entornos, cliente HTTP, registro, inicio y cierre de sesión, almacenamiento seguro,
consulta de la red, planificación de trayectos, compra simulada de billetes y pruebas.

## Tecnologías y requisitos

| Elemento | Elección |
| --- | --- |
| Lenguaje | Kotlin |
| Interfaz | Jetpack Compose y Material 3 |
| Navegación | Navigation Compose |
| HTTP | Retrofit y OkHttp |
| JSON | Gson |
| Seguridad local | Android Keystore, AES-256-GCM y almacenamiento privado |
| Compatibilidad | Android 8.0, API 26, o posterior |
| Compilación | Gradle Wrapper y Android Gradle Plugin |
| Pruebas | JUnit sobre la JVM |

La aplicación se compila con SDK 36 y Java 17. La integración continua utiliza Java 21 para ejecutar
Gradle, compilar el APK de depuración y lanzar los mismos tests unitarios disponibles localmente.

## Vista general

```text
┌──────────────────────────────── RMM App ────────────────────────────────┐
│                                                                         │
│  MainActivity                                                           │
│       │                                                                 │
│       ▼                                                                 │
│  Decisión raíz ───── sin sesión ─────► Registro / inicio de sesión      │
│       │                                      │                          │
│       │ con sesión                           ▼                          │
│       ▼                              Repositorio de autenticación       │
│  Navegación principal                       │                          │
│  Inicio · Billetes · Trayectos · Cuenta     ├────► Cliente REST         │
│                                             │          │               │
│                                             ▼          ▼               │
│                                      Almacén seguro   Backend           │
│                                      Android Keystore Spring Boot       │
└─────────────────────────────────────────────────────────────────────────┘
```

RMM App solo se comunica con la API REST versionada. No accede a MySQL, no utiliza credenciales de
operador y no se conecta al broker MQTT.

## Organización del código

El código de producción se encuentra bajo `android/app/src/main/java/com/rmm/app`:

```text
com.rmm.app/
├── MainActivity.kt                 # Composición raíz y puerta de sesión
├── core/
│   ├── auth/                       # Contratos y flujos de autenticación
│   ├── environment/                # Entorno y URL versionada de la API
│   ├── journeys/                   # Historial local y favoritos de trayectos
│   ├── network/                    # Retrofit, OkHttp y resultados tipados
│   ├── networkcatalog/             # Catálogo público y planificador REST
│   ├── session/                    # Modelo y persistencia cifrada de sesión
│   ├── ticketcatalog/              # Productos y reglas tarifarias públicas
│   └── ticketpurchase/             # Contrato y repositorio de compra
├── navigation/                     # Destinos raíz y navegación inferior
└── ui/
    ├── component/                  # Componentes visuales compartidos
    ├── screen/                     # Pantallas organizadas por función
    └── theme/                      # Colores, tipografía, formas y tema RMM
```

Las dependencias siguen una dirección sencilla: la interfaz utiliza los repositorios y modelos de
`core`; `core` no depende de pantallas ni de la navegación. Los contratos HTTP tampoco contienen
reglas de presentación.

## Composición raíz y navegación

`MainActivity` aplica `RMMAppTheme` y crea `RMMApp`. La función `resolveRootDestination` separa los
dos árboles principales:

- `AUTHENTICATION` muestra el registro y el inicio de sesión cuando no existe una sesión local;
- `APPLICATION` abre la navegación principal cuando se ha recuperado una sesión.

La barra inferior contiene cuatro destinos estables:

| Ruta | Pantalla |
| --- | --- |
| `home` | Inicio |
| `tickets` | Billetes |
| `journeys` | Trayectos |
| `account` | Cuenta |

Navigation Compose conserva el estado de cada destino, evita duplicarlo en la pila y vuelve al inicio
del grafo al cambiar de sección. La pantalla Cuenta recibe la sesión activa desde la raíz; al cerrar
sesión comunica el resultado hacia arriba y la aplicación sustituye todo el contenido privado por la
autenticación.

Actualmente la puerta raíz comprueba la presencia de la sesión persistida. El modelo ya permite
consultar la vigencia del token de acceso y del token de renovación con un margen de seguridad de
30 segundos, pero la renovación automática se incorporará en una fase posterior.

## Configuración de entornos

La compilación lee dos propiedades de la configuración compartida:

| Propiedad | Función |
| --- | --- |
| `RMM_ANDROID_ENVIRONMENT` | Selecciona `local`, `staging` o `production`. |
| `RMM_API_ANDROID_BASE_URL` | Define la raíz versionada de la API móvil. |

La URL debe terminar en `/api/rmm-app/v1/`. Los builds de depuración admiten HTTP para comunicarse
desde el emulador mediante `10.0.2.2`; el resto de builds exige HTTPS. Los valores se trasladan a
`BuildConfig` y `RMMApiConfiguration` vuelve a validarlos en ejecución.

La configuración se toma primero de `config/local-services.properties`, ignorado por Git, y utiliza
`config/local-services.properties.example` como alternativa reproducible. Estos archivos contienen
direcciones, nunca contraseñas ni tokens.

## Cliente HTTP y tratamiento de errores

`RMMApiClientFactory` configura una única base Retrofit con:

- tiempos máximos diferenciados para conexión, lectura, escritura y llamada completa;
- reintento de problemas de conexión permitido por OkHttp;
- conversión JSON mediante Gson;
- cabeceras públicas con cliente, versión y `User-Agent`;
- ausencia de interceptores de logging que puedan revelar credenciales.

`RMMApiCallExecutor` transforma las llamadas en `ApiResult` y evita propagar excepciones técnicas a
la interfaz. Los fallos se clasifican como:

- respuesta HTTP, conservando `ApiProblem`, código, petición y posible `Retry-After`;
- timeout, host inaccesible, conexión u otro problema de red;
- respuesta vacía o inválida;
- error de serialización;
- fallo inesperado.

Los códigos estables de `ApiProblem` son la referencia adecuada para decisiones futuras. Los textos
del backend son informativos y no deben convertirse en reglas de negocio del cliente.

## Registro e inicio de sesión

`PassengerAuthenticationApi` declara los endpoints públicos bajo `auth/`. El repositorio coordina la
entrada de la interfaz, la llamada HTTP, la identidad de instalación y el almacenamiento seguro.

### Registro

El formulario solicita nombre, apellidos, correo, contraseña, confirmación y aceptación de términos.
Antes del envío comprueba el formato básico y las mismas condiciones públicas de contraseña que el
backend. La API vuelve a validar todos los datos.

La implementación actual envía el idioma `es-ES` y la versión de términos `2026-01`. Un registro
correcto no crea una sesión: muestra que debe verificarse el correo y devuelve al formulario de
acceso.

### Identidad de instalación

`PassengerInstallation` genera un UUID durante el primer inicio de sesión y lo conserva en
preferencias privadas. Este identificador no es una credencial; permite al backend reconocer la
instalación y asociar o revocar sus sesiones. El nombre enviado se construye a partir del fabricante
y modelo del dispositivo y se limita a 100 caracteres.

### Inicio de sesión

El inicio de sesión envía correo, contraseña e identidad del dispositivo. Cuando la respuesta es
correcta, el repositorio convierte los tokens y el perfil público en `PassengerSession`, cifra la
sesión y solo entonces permite acceder a la navegación privada. La contraseña nunca se persiste.

## Almacenamiento seguro de sesión

`SecurePassengerSessionStore` conserva un único documento de sesión cifrado que contiene:

- tokens de acceso y renovación;
- fechas de caducidad;
- UUID de instalación;
- perfil público necesario para la interfaz.

La clave AES de 256 bits se genera dentro de Android Keystore y no se exporta. Cada escritura utiliza
AES-GCM, un vector aleatorio y datos asociados versionados, proporcionando confidencialidad e
integridad. El documento también contiene una versión de esquema para poder rechazar formatos
incompatibles.

El manifiesto establece `android:allowBackup="false"`, por lo que las credenciales no forman parte de
las copias de seguridad de la aplicación. Si el contenido no puede descifrarse, está manipulado, usa
otra versión o la clave ha quedado invalidada, se elimina la sesión ilegible y se restablece la clave.

No deben añadirse tokens a logs, excepciones visibles, URLs, telemetría ni archivos de configuración.

## Cuenta y cierre de sesión

La pantalla Cuenta presenta el nombre, correo, estado, idioma e identificador público contenidos en
la sesión. No muestra tokens ni identificadores internos de base de datos.

El cierre requiere confirmación y sigue este orden:

1. solicita al backend la revocación mediante `DELETE auth/sessions/current` y Bearer;
2. elimina siempre la sesión cifrada local, aunque falle la red o el token remoto ya no sea válido;
3. vuelve a la pantalla de autenticación.

Si el almacenamiento local no puede eliminarse, la aplicación conserva la pantalla privada y muestra
el error para evitar aparentar un cierre que dejaría credenciales recuperables tras reiniciar.

## Interfaz y estado

El tema RMM desactiva por defecto los colores dinámicos para conservar la identidad visual azul en
todos los dispositivos. Los componentes compartidos incluyen la marca y la cabecera, mientras que
las pantallas funcionales se separan por paquete.

Los formularios conservan sus valores durante cambios de configuración con `rememberSaveable`,
utilizan corrutinas ligadas a la composición para las llamadas y bloquean nuevos envíos mientras una
operación está en curso. El desplazamiento vertical y `imePadding` permiten trabajar con pantallas
compactas y con el teclado abierto.

Cuando las funciones de billetes y trayectos incorporen más estados y casos de uso, deberán introducir
modelos de estado o `ViewModel` específicos en lugar de concentrar esa lógica en los composables.

La sección Trayectos ya integra el catálogo de líneas y estaciones, el mapa, la búsqueda y el cálculo
remoto de recorridos. Su funcionamiento y persistencia local se describen en
[Consulta de la red y planificación de trayectos](consulta-red-rmm-app.md).

La sección Billetes integra el catálogo de títulos, los cuatro configuradores, la confirmación con
pago simulado y la presentación del billete recién emitido. El flujo, la idempotencia y sus límites
se describen en [Compra de billetes desde RMM App](compra-billetes-rmm-app.md).

La cartera persistente, la obtención bajo demanda de credenciales QR, la vinculación de soportes
físicos y el historial se describen en [Cartera de billetes de RMM App](cartera-rmm-app.md).

## Pruebas y CI

Los tests JVM se encuentran en `android/app/src/test/java/com/rmm/app` y cubren actualmente:

- identidad y espacio de nombres de la aplicación;
- decisión raíz con y sin sesión;
- contrato y unicidad de las rutas principales;
- caducidad, margen de seguridad y renovación de la sesión;
- rechazo de identidades de instalación inválidas;
- conservación de problemas HTTP y clasificación de DNS y timeout;
- búsquedas de estaciones, representación de tramos y continuidad de transbordos;
- coherencia geométrica del mapa, trayectos recientes y favoritos;
- precios de los cuatro productos, solicitudes de compra y errores de emisión.
- filtros y paginación de la cartera, vinculación de soportes y lectura previa de QR físicos.

Se ejecutan junto con la compilación mediante:

```powershell
cd android
./gradlew.bat testDebugUnitTest assembleDebug
```

GitHub Actions utiliza las tareas equivalentes, publica el APK de depuración y conserva el informe de
tests como artefactos. Las pruebas no necesitan un backend, una base de datos ni un emulador.

## Reglas para ampliar la aplicación

- Añadir cada contrato REST a una interfaz pequeña y cohesionada.
- Mantener DTO de transporte separados de los modelos utilizados por la interfaz.
- Ejecutar las reglas autoritativas en el backend y repetir en Android solo validaciones de experiencia.
- Modelar todos los fallos mediante resultados tipados; no mostrar trazas ni excepciones técnicas.
- Centralizar la futura autorización Bearer y la renovación, evitando implementarlas pantalla a pantalla.
- Guardar solo los datos imprescindibles y utilizar el almacén seguro para cualquier credencial.
- Conservar rutas estables y pasar únicamente identificadores públicos entre destinos.
- Añadir tests deterministas para cada decisión de navegación, sesión y transformación de respuestas.

## Documentación relacionada

- [Arquitectura general del ecosistema](arquitectura-ecosistema.md)
- [Contratos REST de RMM App](contratos-rest-rmm-app.md)
- [Autenticación de pasajeros](autenticacion-rmm-app.md)
- [Consulta de la red y planificación de trayectos](consulta-red-rmm-app.md)
- [Compra de billetes desde RMM App](compra-billetes-rmm-app.md)
- [Cartera de billetes de RMM App](cartera-rmm-app.md)
- [Flujos online y sin conexión](flujos-conectividad.md)
- [Configuración y ejecución de aplicaciones cliente](ejecucion-aplicaciones-cliente.md)

