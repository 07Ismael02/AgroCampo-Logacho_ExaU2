# AgroCampo-Logacho

Aplicación Android/Kotlin Multiplatform del examen parcial II, Unidad 2, caso AgroSierra (versión A).

**Autor:** Richard Ismael Logacho Rivera.

Este repositorio es independiente de AgroAPI y de los proyectos Pharma. El paquete raíz es `pe.upeu.agrosierra`.

## Alcance de la base inicial

El módulo `shared` contiene código común Compose y un target Android. `androidApp` es la aplicación Android que lo ejecuta. La pantalla de texto actual verifica únicamente que el proyecto compila; no representa datos del servicio ni implementa todavía el examen.

Arquitectura prevista conforme a la sección 4.7 del PDF: `shared/src/commonMain/kotlin/pe/upeu/agrosierra/{domain/{model,repository,usecase},data/{remote/dto,local,repository},presentation/{sesion,listado,detalle,perfil,navigation,theme},di}` y `androidApp/src/main/kotlin/pe/upeu/agrosierra` para los adaptadores Android. Las capas se crearán al incorporar su funcionalidad real.

Ktor Client, Koin y SQLDelight están configurados como dependencias en `shared`, con `AgroDb` declarado para la generación posterior del esquema. Aún no se han implementado el cliente REST, tablas SQL, drivers, seguridad ni sincronización.

Para verificar la base desde Windows, con Android SDK y Java 21: `./gradlew.bat :androidApp:assembleDebug --no-daemon --console=plain`.
