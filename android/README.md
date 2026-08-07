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

## Comprobación desde PowerShell

```powershell
cd android
./gradlew.bat testDebugUnitTest assembleDebug
```

La aplicación contiene por ahora únicamente la identidad visual mínima. Las capas de navegación,
datos, dominio y seguridad se incorporarán de forma progresiva.
