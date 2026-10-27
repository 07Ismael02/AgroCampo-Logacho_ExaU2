# AgroCampo-Logacho

Aplicación Android/Kotlin Multiplatform del examen parcial II, Unidad 2, caso AgroSierra (versión A).

**Autor:** Richard Ismael Logacho Rivera.

Este repositorio es independiente de AgroAPI y de los proyectos Pharma. El paquete raíz es `pe.upeu.agrosierra`.

## Alcance de la base inicial

El módulo `shared` contiene código común Compose y un target Android. `androidApp` es la aplicación Android que lo ejecuta. La pantalla de texto actual verifica únicamente que el proyecto compila; no representa datos del servicio ni implementa todavía el examen.

Arquitectura prevista conforme a la sección 4.7 del PDF: `shared/src/commonMain/kotlin/pe/upeu/agrosierra/{domain/{model,repository,usecase},data/{remote/dto,local,repository},presentation/{sesion,listado,detalle,perfil,navigation,theme},di}` y `androidApp/src/main/kotlin/pe/upeu/agrosierra` para los adaptadores Android. Las capas se crearán al incorporar su funcionalidad real.

Ktor Client, Koin y SQLDelight están configurados como dependencias en `shared`, con `AgroDb` declarado para la generación posterior del esquema. Aún no se han implementado el cliente REST, tablas SQL, drivers, seguridad ni sincronización.

Para verificar la base desde Windows, con Android SDK y Java 21: `./gradlew.bat :androidApp:assembleDebug --no-daemon --console=plain`.

## Autenticación (fase 5)

El login usa `POST /api/v1/auth/login` con `usuario` y `contrasena`; la identidad se confirma con `GET /api/v1/usuarios/me`. El token se guarda cifrado con una clave de Android Keystore, se comprueba al reabrir y se elimina al cerrar sesión o al recibir 401. Una respuesta 403 conserva la sesión. No se registran cuerpos ni cabeceras HTTP en Logcat.

La URL se establece fuera de Git en `local.properties` mediante `agro.api.baseUrl`. En el emulador Android se puede usar `http://10.0.2.2:8080`; en un dispositivo físico se utiliza una dirección accesible del equipo. Si la propiedad falta, la pantalla informa que se debe configurar y deshabilita el login. El backend debe estar iniciado por separado.

## Lotes locales (fase 6)

El listado y los filtros consultan `agrocampo.db` mediante SQLDelight; el GET paginado de AgroAPI solo actualiza la base. La descarga revisa todas las páginas y reemplaza los lotes junto con la fecha de actualización en una transacción. Una página fallida, un 403 o una interrupción de red no borran los lotes guardados. Un 401 elimina el token vigente y devuelve al login. El detalle abre primero la copia local y después consulta `/api/v1/lotes/{id}`.

El esquema incluye `lote`, `labor` y `sync_meta`. La fecha de sincronización de lotes se guarda como milisegundos UTC y se muestra en formato ISO-8601. La caché de lotes es común a la instalación; las labores se aíslan por el ID de cuenta confirmado por `/api/v1/usuarios/me`.

El tráfico HTTP sin cifrar al host `10.0.2.2` está permitido solo en la variante `debug` del emulador. La variante de producción no incluye esa excepción. Para un dispositivo físico se debe usar un endpoint HTTPS válido o configurar una excepción de desarrollo específica para su dirección, sin ampliarla en producción.

## Labores agrícolas (fase 7)

El detalle del lote muestra labores desde SQLDelight y permite consultar todas las páginas de `GET /api/v1/lotes/{id}/labores`. Crear, editar y eliminar utilizan los contratos REST reales con JWT y respetan 201, 200 y 204. El formulario aplica validaciones locales para fecha, cantidad, campaña cerrada y dosis de fumigación; AgroAPI conserva la decisión final de las reglas RN-01 a RN-04.

La migración `1.sqm` conserva las filas anteriores de `labor` en `labor_legacy` sin asignarles propietario. No se exponen ni se envían. Las nuevas filas usan `(ownerId, id)` como clave y `ownerId` procede exclusivamente de `/usuarios/me`. Los IDs de labores creadas sin red son negativos y las ediciones locales quedan marcadas como pendientes. Un técnico puede corregir su labor local pendiente. La edición offline de una labor remota se permite al jefe de campo; la restricción de 24 horas de un técnico requiere confirmación del servidor y, por ello, se valida online.

Una creación online confirmada reconcilia el ID local con el positivo del servidor. Si el POST sale pero la respuesta se pierde, la operación se marca como **envío incierto** y no debe reenviarse automáticamente: el backend no ofrece clave de idempotencia. La subida completa de pendientes y la reconciliación posterior corresponden a la fase 8. La eliminación de labores no se encola sin red.

## Sincronización offline-first (fase 8)

`SincronizadorLabores` ejecuta una sola sincronización a la vez (`ColaLabores`, compartida con las escrituras de `LaborRepositorio`) y siempre en este orden:

1. Comprueba la sesión local y que la cuenta solicitada sea la activa.
2. Consulta `/api/v1/usuarios/me` con el JWT de esa sesión: confirma acceso real al backend y la identidad.
3. Sube los pendientes de esa cuenta en orden de creación (POST para IDs negativos, PUT para correcciones).
4. Reconcilia cada creación confirmada: la fila con ID local negativo se sustituye por la del servidor.
5. Descarga todas las páginas de lotes y de labores de cada lote; cualquier página fallida o inconsistente cancela la descarga.
6. Aplica la descarga en una única transacción SQLite, conservando las filas pendientes.
7. Solo entonces guarda la marca `labores:<cuenta>` de última sincronización completa.

La pantalla observa SQLDelight; la respuesta HTTP nunca alimenta la interfaz directamente. Se sincroniza con el botón «Sincronizar» y automáticamente cuando Android informa que vuelve a haber red (`ObservadorConexionAndroid` → `reconexiones()`).

| Respuesta durante la subida | Efecto local |
| --- | --- |
| 400, 403, 404, 409 | La fila sigue pendiente, se guarda el motivo del servidor y se muestra como rechazada; la sesión se conserva. |
| 401 | La fila sigue pendiente (no queda incierta), se borra el token y se cierra la sesión. |
| 500, tiempo agotado, sin conexión durante un POST | La creación queda como **envío incierto**. |
| Sin red antes de empezar | No se contacta al servidor; nada cambia. |

Los PUT son idempotentes y se reintentan en la siguiente sincronización.

### Envíos inciertos: revisión manual

AgroAPI no ofrece clave de idempotencia ni correlación, así que la aplicación no puede saber si un POST sin respuesta creó la labor. Antes de cada POST la fila se marca como incierta (también cubre un cierre de la aplicación a mitad del envío). Mientras exista un envío incierto la sincronización se detiene antes de él y **no lo reenvía**. No se garantiza semántica de exactamente una vez.

Procedimiento seguro:

1. En la tarjeta marcada «Envío incierto», pulse «Revisar envío» y anote tipo, fecha, cantidad y lote.
2. Consulte las labores de ese lote en AgroAPI (o pida al jefe de campo que lo haga) y confirme con el responsable si la labor fue registrada. Una coincidencia de campos por sí sola no prueba que sea la misma labor: dos labores legítimas pueden ser iguales.
3. Si se confirma que existe, elija «Ya existe: descartar copia local»; la siguiente sincronización descargará la copia del servidor.
4. Si se confirma que no existe, elija «No existe: reenviar»; la fila vuelve a la cola y se enviará una vez en la siguiente sincronización.
5. Si no se puede confirmar, pulse «Cancelar» y deje la fila incierta.

### Pruebas

`SincronizadorLaboresTest` está en `commonTest` y usa `MockEngine` y SQLDelight. El controlador SQLite se declara con `expect` y su `actual` (JDBC en memoria y en archivo para simular reinicios) está en `androidHostTest`; por eso se ejecuta con `:shared:testAndroidHostTest`. El módulo `shared` solo declara el destino Android: no se ha compilado ni validado en iOS.
