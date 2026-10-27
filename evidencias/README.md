# Evidencias de entrega — AgroSierra, Unidad 2, versión A

Estudiante: Richard Ismael Logacho Rivera.

## Repositorios

| Repositorio | URL | Versión entregada (`develop`) |
| --- | --- | --- |
| AgroCampo (KMP, Android) | https://github.com/07Ismael02/AgroCampo-Logacho_ExaU2 | `dbaf20b05606ff77003a765e894f47d1ba43c49d` |
| AgroAPI (Spring Boot) | https://github.com/07Ismael02/AgroAPI-Logacho_ExaU2 | `fa6b809eac90288581eb642ac0ce4f50868483b6` |

Las funcionalidades llegaron a `develop` mediante PR con merge commit (AgroCampo PR #1 a #5; AgroAPI cinco PR). `main` no recibió funcionalidades directamente.

## Archivos

| Archivo | Contenido |
| --- | --- |
| `gradle-pruebas-y-compilacion.log` | Ejecución real de `:shared:testAndroidHostTest` y `:androidApp:assembleDebug` con `--rerun-tasks` sobre `develop` `dbaf20b`. |
| `resultados-pruebas-agrocampo.txt` | Resumen por clase de los informes JUnit de esa ejecución. |
| `api-verificacion-real.txt` | Login real de `tecnico1`, `GET /usuarios/me`, `GET /lotes` y 401 sin token contra AgroAPI local (H2, Java 21). El JWT se omitió. |
| `agroapi-surefire-existente.txt` | Informes Surefire de AgroAPI generados antes de esta sesión (14:47); no se reejecutaron aquí. |
| `git-grafo-*.txt`, `git-commits-*.txt`, `ramas-*.txt` | Grafo, commits con autor y fecha, y ramas de ambos repositorios. |

## Pruebas en `commonTest`

`SincronizadorLaboresTest`, `LaboresContratoTest`, `LotesContratoTest` y `AutenticacionTest` están en `commonTest` y usan `MockEngine`. Las que usan base de datos obtienen el controlador SQLite en memoria mediante `expect`/`actual`; el `actual` (JDBC) está en `androidHostTest`, por lo que se ejecutan con `:shared:testAndroidHostTest`. El módulo solo declara destino Android: no se ha validado iOS.

## Limitaciones reales

- Pruebas manuales en emulador Pixel_8 con capturas reales en `capturas/`. No hay captura de un 403 dentro de la app (la interfaz oculta la acción); el 403 se evidencia por HTTP. El botón «Salir» no cerró la sesión al pulsarlo por adb; pendiente de revisión.
- AgroAPI no ofrece idempotencia: un POST sin respuesta queda como envío incierto y requiere revisión manual (ver README principal).
- No se implementó ningún cambio sorpresa (SC-A/B/C/D); no fue asignado.

## Entregables adicionales

| Archivo | Contenido |
| --- | --- |
| `Evidencias_Examen_U2_AgroSierra_Logacho.pdf` | Documento formal de evidencias (fuente: el `.html` del mismo nombre). |
| `capturas/01..07_*.png` | Capturas reales del emulador: login, lotes, labores, modo avión, pendiente, reconexión y vista de admin. |
| `AgroAPI_Logacho.postman_collection.json` | Colección Postman v2.1 con las nueve rutas y ejemplos 400, 401 y 403. |
| `api-coleccion-ejecucion.txt` | Ejecución real de esas peticiones con curl. |

Cambio sorpresa (SC): pendiente; el docente aún no comunicó la letra.
