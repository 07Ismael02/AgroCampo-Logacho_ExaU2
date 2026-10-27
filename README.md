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
