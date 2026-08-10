# RMM App

Aplicación Android para pasajeros de la Red de Metro de Macegocia.

## Requisitos locales

- Android Studio compatible con Android Gradle Plugin 9.0.1.
- JDK 17 o posterior; se recomienda utilizar el JDK incluido en Android Studio.
- Android SDK Platform 36 y Build Tools 36.0.0.

El SDK debe configurarse mediante `ANDROID_HOME`, `ANDROID_SDK_ROOT` o el archivo local no versionado
`local.properties`.

Las direcciones de los servicios se obtienen de
[`../config/local-services.properties.example`](../config/local-services.properties.example). Para
personalizarlas sin modificar archivos versionados, crea `../config/local-services.properties` como
se explica en la [guía de configuración](../config/README.md).

## Abrir el proyecto

Android Studio debe abrir directamente la carpeta `android`. Tras sincronizar Gradle puede ejecutar
el módulo `app` en el emulador configurado.

La guía conjunta de puesta en marcha, instalación del APK y servicios necesarios está disponible en
[`../docs/ejecucion-aplicaciones-cliente.md`](../docs/ejecucion-aplicaciones-cliente.md).

La estructura interna, los flujos de autenticación, la seguridad de la sesión y las reglas para
ampliar el cliente se describen en la
[`arquitectura de RMM App`](../docs/arquitectura-rmm-app.md).

La consulta del mapa, la búsqueda de estaciones, el planificador y los trayectos guardados se
documentan en la guía de
[`consulta de la red en RMM App`](../docs/consulta-red-rmm-app.md).

## Comprobación desde PowerShell

```powershell
cd android
./gradlew.bat testDebugUnitTest assembleDebug
```

El APK de depuración se genera en `app/build/outputs/apk/debug/app-debug.apk`.

La aplicación obtiene el entorno y la URL de la API durante la compilación. Los builds de depuración
permiten la URL HTTP del emulador; cualquier otro build exige HTTPS. El cliente común utiliza
Retrofit y OkHttp, añade únicamente cabeceras públicas y no registra cuerpos ni credenciales. Las
llamadas devuelven resultados tipados que distinguen respuestas HTTP, problemas de conectividad,
errores de serialización y respuestas inválidas sin mostrar directamente excepciones técnicas en la
interfaz.
