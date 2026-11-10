# Guitar Pro Offline

Afinador cromático para Android, diseñado para funcionar sin conexión.

## Funciones iniciales
- Afinación cromática mediante micrófono y algoritmo YIN.
- Instrumentos: guitarra, bajo, ukelele, violín y viola.
- Presets de afinación para guitarra, bajo y ukelele.
- Sin permisos de Internet, anuncios ni telemetría.
- Android 6.0 (API 23) o superior.

## Compilar
Requiere JDK 17, Android SDK API 35 y Gradle 8.9.

```bash
gradle assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Privacidad
El audio se procesa localmente en el dispositivo. La aplicación no solicita permiso de Internet.
