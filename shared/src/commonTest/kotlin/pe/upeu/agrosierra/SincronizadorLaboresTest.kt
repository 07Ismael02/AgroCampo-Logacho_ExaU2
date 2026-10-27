package pe.upeu.agrosierra

import app.cash.sqldelight.db.SqlDriver
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import pe.upeu.agrosierra.data.local.AlmacenSeguro
import pe.upeu.agrosierra.data.local.BaseLocal
import pe.upeu.agrosierra.data.local.EstadoRed
import pe.upeu.agrosierra.data.local.LaborLocal
import pe.upeu.agrosierra.data.remote.ApiConfig
import pe.upeu.agrosierra.data.remote.LaboresApi
import pe.upeu.agrosierra.data.remote.LotesApi
import pe.upeu.agrosierra.data.remote.SolicitudesApi
import pe.upeu.agrosierra.data.remote.crearClienteHttp
import pe.upeu.agrosierra.data.repository.ColaLabores
import pe.upeu.agrosierra.data.repository.SincronizadorLabores
import pe.upeu.agrosierra.db.AgroDb
import pe.upeu.agrosierra.domain.model.ErrorApi
import pe.upeu.agrosierra.domain.model.ErrorApiException
import pe.upeu.agrosierra.domain.model.FiltroLotes
import pe.upeu.agrosierra.domain.model.Labor
import pe.upeu.agrosierra.domain.model.Lote
import pe.upeu.agrosierra.domain.model.OperacionPendiente
import pe.upeu.agrosierra.domain.model.Sesion
import pe.upeu.agrosierra.domain.model.TipoLabor
import pe.upeu.agrosierra.domain.model.UsuarioAutenticado
import pe.upeu.agrosierra.domain.repository.AuthRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SincronizadorLaboresTest {
    private fun lote() = Lote(1, "L-1", "Lote original", 2.0, "Papa", "Mantaro", false, 3.0)
    private fun labor(id: Long = 0) = Labor(id, 1, TipoLabor.RIEGO, "2026-10-27", "tecnico", 1.0, "nota", false)

    private class Almacen : AlmacenSeguro {
        var token: String? = "token-A"
        override fun leerToken() = token
        override fun guardarToken(token: String) { this.token = token }
        override fun borrarToken() { token = null }
    }
    private class Auth(var actual: Sesion?) : AuthRepository {
        override suspend fun iniciarSesion(usuario: String, contrasena: String): UsuarioAutenticado = error("No usado")
        override suspend fun restaurarSesion(): UsuarioAutenticado? = error("No usado")
        override suspend fun cerrarSesion() { actual = null }
        override fun sesionActual() = actual
    }
    private class Servidor {
        val visitas = mutableListOf<String>()
        var postEstado: HttpStatusCode? = null
        var perderPost = false
        var preflight401 = false
        var fallarPaginaLabor: Int? = null
        var laborIds = listOf<Long>()
        var postIniciado: CompletableDeferred<Unit>? = null
        var liberarPost: CompletableDeferred<Unit>? = null

        fun engine() = MockEngine { request ->
            val ruta = request.url.encodedPath
            val metodo = request.method.value
            visitas += "$metodo $ruta"
            val token = request.headers[HttpHeaders.Authorization]
            require(token == "Bearer token-A" || token == "Bearer token-B")
            val cabeceras = headersOf(HttpHeaders.ContentType, "application/json")
            when {
                ruta.endsWith("/usuarios/me") -> {
                    if (preflight401) respond("""{"status":401,"message":"Sesión vencida"}""", HttpStatusCode.Unauthorized, cabeceras)
                    else respond("""{"id":${if (token == "Bearer token-B") 8 else 7},"usuario":"tecnico","rol":"TECNICO","privilegios":["LOTE_LEER","LABOR_LEER","LABOR_CREAR","LABOR_EDITAR"]}""",
                        HttpStatusCode.OK, cabeceras)
                }
                ruta.endsWith("/lotes") -> respond("""{"contenido":[${loteJson()}],"pagina":0,"tamanio":100,"totalElementos":1,"totalPaginas":1,"ultima":true}""", HttpStatusCode.OK, cabeceras)
                ruta.endsWith("/labores") && metodo == "GET" -> {
                    val pagina = request.url.parameters["pagina"]!!.toInt()
                    if (fallarPaginaLabor == pagina) respond("""{"status":500,"message":"Error"}""", HttpStatusCode.InternalServerError, cabeceras)
                    else {
                        val id = laborIds.getOrNull(pagina)
                        val items = id?.let(::laborJson).orEmpty()
                        respond("""{"contenido":[${items}],"pagina":$pagina,"tamanio":100,"totalElementos":${laborIds.size},"totalPaginas":${laborIds.size.coerceAtLeast(1)},"ultima":${pagina + 1 >= laborIds.size}}""", HttpStatusCode.OK, cabeceras)
                    }
                }
                ruta.endsWith("/labores") && metodo == "POST" -> {
                    postIniciado?.complete(Unit)
                    liberarPost?.await()
                    if (perderPost) throw IOException("Respuesta de POST perdida")
                    postEstado?.let { respond("""{"status":${it.value},"message":"Rechazo","validationErrors":{"cantidad":"Cantidad inválida"}}""", it, cabeceras) }
                        ?: run {
                            laborIds = listOf(41)
                            respond(laborJson(41), HttpStatusCode.Created, cabeceras)
                        }
                }
                ruta.endsWith("/41") && metodo == "PUT" -> respond(laborJson(41), HttpStatusCode.OK, cabeceras)
                else -> error("Ruta inesperada: $metodo $ruta")
            }
        }

        private fun loteJson() = """{"id":1,"codigo":"L-1","nombre":"Lote actualizado","hectareas":2.0,"cultivo":"Papa","valle":"Mantaro","campaniaCerrada":false,"dosisMaximaPorHectarea":3.0}"""
        private fun laborJson(id: Long) = """{"id":$id,"loteId":1,"tipo":"RIEGO","fecha":"2026-10-27","responsable":"tecnico","cantidad":1.0,"observacion":"nota","pendiente":false}"""
    }

    private class Entorno(val servidor: Servidor = Servidor()) {
        val driver = crearDriverEnMemoria()
        val db: AgroDb
        val base: BaseLocal
        val local: LaborLocal
        val almacen = Almacen()
        val auth = Auth(Sesion("token-A", "tecnico", "TECNICO", setOf("LOTE_LEER", "LABOR_LEER", "LABOR_CREAR", "LABOR_EDITAR"), 7))
        val sync: SincronizadorLabores
        init {
            AgroDb.Schema.create(driver)
            db = AgroDb(driver)
            base = BaseLocal(db)
            base.guardarLote(Lote(1, "L-1", "Lote original", 2.0, "Papa", "Mantaro", false, 3.0))
            local = LaborLocal(db)
            val json = Json { ignoreUnknownKeys = true }
            val solicitudes = SolicitudesApi(crearClienteHttp(servidor.engine(), json), ApiConfig("http://test.local"), json, almacen)
            sync = SincronizadorLabores(local, LotesApi(solicitudes), LaboresApi(solicitudes), solicitudes,
                auth, EstadoRed { true }, ColaLabores())
        }
        fun close() = driver.close()
    }

    @Test fun subeAntesDeDescargarYReconciliaIdNegativo(): Unit = runBlocking {
        val e = Entorno()
        try {
            val pendiente = e.local.guardarNueva(7, labor())
            val resultado = e.sync.sincronizar(7)
            assertEquals(1, resultado.enviados)
            assertEquals(1, resultado.recibidos)
            assertEquals(listOf("GET /api/v1/usuarios/me", "POST /api/v1/labores", "GET /api/v1/lotes", "GET /api/v1/lotes/1/labores"), e.servidor.visitas)
            assertNull(e.local.obtener(7, pendiente.id))
            assertEquals(41L, e.local.obtener(7, 41)?.id)
            assertTrue(e.local.pendientes(7).isEmpty())
            assertNotNull(e.sync.observarUltimaSincronizacion(7).first())
            assertEquals("Lote actualizado", e.base.obtenerLote(1)?.nombre)
        } finally { e.close() }
    }

    @Test fun segundaSincronizacionNoDuplicaFilas(): Unit = runBlocking {
        val e = Entorno()
        try {
            e.local.guardarNueva(7, labor())
            e.sync.sincronizar(7)
            e.sync.sincronizar(7)
            assertEquals(1, e.servidor.visitas.count { it == "POST /api/v1/labores" })
            assertEquals(listOf(41L), e.local.observar(7, 1).first().map(Labor::id))
        } finally { e.close() }
    }

    @Test fun paginaIntermediaFallidaConservaCacheYFecha(): Unit = runBlocking {
        val e = Entorno()
        try {
            e.local.reemplazarDesdeServidor(7, 1, listOf(labor(90)))
            e.servidor.laborIds = listOf(41, 42)
            e.servidor.fallarPaginaLabor = 1
            assertIs<ErrorApi.Servidor>(assertFailsWith<ErrorApiException> { e.sync.sincronizar(7) }.error)
            assertEquals(listOf(90L), e.local.observar(7, 1).first().map(Labor::id))
            assertNull(e.sync.observarUltimaSincronizacion(7).first())
            assertEquals("Lote original", e.base.obtenerLote(1)?.nombre)
        } finally { e.close() }
    }

    @Test fun respuestaPerdidaQuedaInciertaYNoSeReenvia(): Unit = runBlocking {
        val e = Entorno()
        try {
            e.local.guardarNueva(7, labor())
            e.servidor.perderPost = true
            assertIs<ErrorApi.SinConexion>(assertFailsWith<ErrorApiException> { e.sync.sincronizar(7) }.error)
            assertEquals(OperacionPendiente.CREAR_INCIERTO, e.local.pendientes(7).single().operacion)
            assertIs<ErrorApi.Conflicto>(assertFailsWith<ErrorApiException> { e.sync.sincronizar(7) }.error)
            assertEquals(1, e.servidor.visitas.count { it == "POST /api/v1/labores" })
            assertNull(e.sync.observarUltimaSincronizacion(7).first())
        } finally { e.close() }
    }

    @Test fun rechazos403Y409PreservanPendienteYToken(): Unit = runBlocking {
        for ((codigo, clase) in listOf(HttpStatusCode.Forbidden to ErrorApi.SinPrivilegio::class,
            HttpStatusCode.Conflict to ErrorApi.Conflicto::class,
            HttpStatusCode.BadRequest to ErrorApi.Validacion::class)) {
            val e = Entorno()
            try {
                e.local.guardarNueva(7, labor())
                e.servidor.postEstado = codigo
                val error = assertFailsWith<ErrorApiException> { e.sync.sincronizar(7) }.error
                assertEquals(clase, error::class)
                assertEquals(OperacionPendiente.CREAR, e.local.pendientes(7).single().operacion)
                assertEquals("token-A", e.almacen.token)
            } finally { e.close() }
        }
    }

    @Test fun respuesta401DetieneEnvioYBorraToken(): Unit = runBlocking {
        val e = Entorno()
        try {
            e.local.guardarNueva(7, labor())
            e.servidor.preflight401 = true
            assertIs<ErrorApi.NoAutorizado>(assertFailsWith<ErrorApiException> { e.sync.sincronizar(7) }.error)
            assertEquals(0, e.servidor.visitas.count { it == "POST /api/v1/labores" })
            assertNull(e.almacen.token)
            assertEquals(1, e.local.pendientes(7).size)
        } finally { e.close() }
    }

    @Test fun cuentaDistintaNoEnviaPendientesAjenos(): Unit = runBlocking {
        val e = Entorno()
        try {
            e.local.guardarNueva(7, labor())
            e.auth.actual = Sesion("token-B", "otro", "TECNICO", setOf("LOTE_LEER", "LABOR_LEER", "LABOR_CREAR"), 8)
            e.almacen.token = "token-B"
            e.sync.sincronizar(8)
            assertEquals(0, e.servidor.visitas.count { it == "POST /api/v1/labores" })
            assertEquals(1, e.local.pendientes(7).size)
            assertTrue(e.local.pendientes(8).isEmpty())
            assertNull(e.local.obtener(8, -1))
        } finally { e.close() }
    }

    @Test fun sincronizacionesConcurrentesNoReenvianUnPendiente(): Unit = runBlocking {
        val e = Entorno()
        try {
            e.local.guardarNueva(7, labor())
            val inicio = CompletableDeferred<Unit>()
            val liberar = CompletableDeferred<Unit>()
            e.servidor.postIniciado = inicio
            e.servidor.liberarPost = liberar
            val primera = async { e.sync.sincronizar(7) }
            inicio.await()
            val segunda = async { e.sync.sincronizar(7) }
            liberar.complete(Unit)
            primera.await()
            segunda.await()
            assertEquals(1, e.servidor.visitas.count { it == "POST /api/v1/labores" })
        } finally { e.close() }
    }
}

expect fun crearDriverEnMemoria(): SqlDriver
