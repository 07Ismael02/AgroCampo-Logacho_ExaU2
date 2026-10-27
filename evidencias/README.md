# Evidencias de entrega — AgroSierra, Unidad 2, versión A

Estudiante: Richard Ismael Logacho Rivera.

Documento principal: `Evidencias_Examen_U2_AgroSierra_Logacho.pdf`, de 13 páginas. Se genera a partir del `.html` del mismo nombre.

## Repositorios y etiquetas

| Repositorio | URL | `develop` actual | `v1.0-unidad2` |
| --- | --- | --- | --- |
| AgroCampo (KMP, Android) | https://github.com/07Ismael02/AgroCampo-Logacho_ExaU2 | `0a86326` (PR #6) | `dbaf20b` (PR #5) |
| AgroAPI (Spring Boot) | https://github.com/07Ismael02/AgroAPI-Logacho_ExaU2 | `fa6b809` (PR #5) | `fa6b809` (PR #5) |

La etiqueta de AgroCampo no incluye cambios posteriores a su creación: PR #6, la rama `fix/cerrar-sesion-logacho` ni las ramas de documentación. Todas las PR se fusionaron a `develop` con merge commit; `main` no recibió funcionalidades directamente.

## Archivos

| Archivo | Contenido |
| --- | --- |
| `capturas/01..07_*.png` | Capturas reales del emulador Pixel_8: login, lotes, labores, modo avión, fila pendiente, reconexión y vista de admin. |
| `capturas/08..10_*.png` | Prueba real de «Salir» con la corrección de `fix/cerrar-sesion-logacho`. |
| `capturas/git_grafo_*.png` | Capturas de la salida real de `git log --graph --all --decorate` de ambos repositorios. |
| `capturas/github_pr_*.png`, `capturas/github_red_*.png` | Capturas de GitHub: lista de PR y «Network graph». GitHub actualiza este último una vez al día. |
| `prueba-logout.txt` | Registro adb de la prueba de cierre de sesión: almacenamiento cifrado antes y después, pantalla y reapertura. |
| `http-verificacion.tsv`, `api-coleccion-ejecucion.txt` | Ejecución real de las peticiones de la colección: endpoint, usuario, código esperado, código obtenido y respuesta. |
| `AgroAPI_Logacho.postman_collection.json` | Colección Postman v2.1: las nueve rutas, un caso 400, un caso 401 y tres casos 403 (16 peticiones). |
| `gradle-pruebas-y-compilacion.log`, `resultados-pruebas-agrocampo.txt` | Ejecución en esta entrega sobre `develop dbaf20b` con `--rerun-tasks`: 41 pruebas, 0 fallos. |
| `resultados-pruebas-fix-logout.txt` | Ejecución en esta entrega sobre `fix/cerrar-sesion-logacho`: 41 pruebas, 0 fallos. |
| `agroapi-surefire-existente.txt` | Informes previos de AgroAPI (14:47, 21 pruebas, 0 fallos). No se reejecutaron en esta entrega. |
| `api-verificacion-real.txt` | Primera verificación HTTP (login, `/usuarios/me`, `/lotes`, 401). |
| `git-*.txt`, `ramas-*.txt` | Grafo, commits y ramas en texto, tomados antes de esta revisión. |

## Pruebas en `commonTest`

`SincronizadorLaboresTest`, `LaboresContratoTest`, `LotesContratoTest` y `AutenticacionTest` están en `commonTest` y usan `MockEngine`. Las que usan base de datos obtienen el controlador SQLite en memoria mediante `expect`/`actual`. El `actual` (JDBC) está en `androidHostTest`, por eso se ejecutan con `:shared:testAndroidHostTest`. El módulo solo declara destino Android: no se ha validado iOS.

## Limitaciones verificadas

- No hay captura de un 403 dentro de la app. La interfaz oculta las acciones sin privilegio. La única vía legítima es la regla de 24 h de edición, y hoy no es reproducible sin alterar datos o el reloj. El 403 se evidencia por HTTP.
- La corrección de «Salir» (la barra de estado tapaba el botón) está en `fix/cerrar-sesion-logacho`, pendiente de PR.
- La variante 200 de `POST /lotes/{id}/cerrar-campania` no se ejecutó, para no alterar la semilla.
- AgroAPI no ofrece idempotencia: un POST sin respuesta queda como envío incierto y requiere revisión manual (ver README principal).

## Cambio sorpresa

No asignado por el docente. No se implementó ninguna variante SC-A/B/C/D.
