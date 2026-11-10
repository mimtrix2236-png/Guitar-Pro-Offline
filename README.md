# Guitar Pro Offline

Afinador musical para Android con procesamiento local y funcionamiento sin conexión.

## Funciones
- Afinador cromático con detección de tono YIN, visualización de cents y frecuencia.
- Presets: guitarra (estándar, Drop D, Eb, D, DADGAD, Open G, Open D y 7 cuerdas), bajo de 4/5/6 cuerdas y Drop D, ukelele estándar/barítono, violín y viola.
- Generador local de tonos de referencia.
- Metrónomo audible de 40 a 240 BPM.
- Calibración A4 de 410 a 480 Hz y tolerancia configurable.
- Notación latina Do-Re-Mi o anglosajona.
- Preferencias guardadas localmente.
- Sin anuncios, rastreadores, cuentas ni permiso INTERNET.

## Privacidad
El permiso RECORD_AUDIO solo se utiliza mientras el afinador está activo. El audio se procesa en memoria y no se guarda ni se transmite.

## Compilar
Requisitos: JDK 17, Android SDK API 35 y Gradle 8.9.

```bash
gradle testDebugUnitTest
gradle assembleDebug
```

El APK de depuración se genera en `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions ejecuta pruebas y publica el APK como artefacto en cada push a `main`.

## Licencia
MIT. Consulta `LICENSE`.
