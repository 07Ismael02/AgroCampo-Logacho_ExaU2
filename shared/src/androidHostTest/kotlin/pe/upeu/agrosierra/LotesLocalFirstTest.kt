package pe.upeu.agrosierra

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import pe.upeu.agrosierra.data.local.AlmacenSeguro
import pe.upeu.agrosierra.data.local.BaseLocal
import pe.upeu.agrosierra.data.remote.ApiConfig
import pe.upeu.agrosierra.data.remote.LotesApi
import pe.upeu.agrosierra.data.remote.SolicitudesApi
import pe.upeu.agrosierra.data.remote.crearClienteHttp
import pe.upeu.agrosierra.data.repository.LoteRepositorioLocalFirst
import pe.upeu.agrosierra.db.AgroDb
import pe.upeu.agrosierra.domain.model.ErrorApi
import pe.upeu.agrosierra.domain.model.ErrorApiException
import pe.upeu.agrosierra.domain.model.FiltroLotes
import pe.upeu.agrosierra.domain.model.Lote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LotesLocalFirstTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun lote(id: Long, valle: String = "Mantaro", cerrada: Boolean = false) =
        Lote(id, "L-$id", "Parcela $id", 2.5, "Papa", valle, cerrada, 1.25)

    private fun base(): Pair<JdbcSqliteDriver, BaseLocal> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AgroDb.Schema.create(driver)
        return driver to BaseLocal(AgroDb(driver))
    }

    private class Almacen : AlmacenSeguro {
        var token: String? = "solo-test"
        override fun leerToken() = token
        override fun guardarToken(token: String) { this.token = token }
        override fun borrarToken() { token = null }
    }

    private fun repositorio(local: BaseLocal, almacen: Almacen, engine: MockEngine) =
        LoteRepositorioLocalFirst(local, LotesApi(SolicitudesApi(
            crearClienteHttp(engine, json), ApiConfig("http://test.local"), json, almacen)))

    @Test fun sqliteEmiteLotesYAplicaBusquedaYFiltros(): Unit = runBlocking {
        val (driver, local) = base()
        try {
            assertTrue(local.observarLotes(FiltroLotes()).first().isEmpty())
            local.reemplazarLotesCompleto(listOf(lote(1), lote(2, "Chao", true)), 1234L)
            assertEquals(listOf(1L, 2L), local.observarLotes(FiltroLotes()).first().map(Lote::id))
            assertEquals(listOf(2L), local.observarLotes(FiltroLotes(valle = "Chao", campaniaCerrada = true)).first().map(Lote::id))
            assertEquals(listOf(1L), local.observarLotes(FiltroLotes(busqueda = "parcela 1")).first().map(Lote::id))
            assertEquals(1234L, local.observarUltimaSincronizacion().first())
            assertNotNull(local.obtenerLote(1))
        } finally { driver.close() }
    }

    @Test fun descargaPaginadaSeGuardaAtomicamente(): Unit = runBlocking {
        val (driver, local) = base()
        try {
            val engine = MockEngine { request ->
                val pagina = request.url.parameters["pagina"]!!.toInt()
                val id = pagina + 1
                respond("""{"contenido":[{"id":$id,"codigo":"L-$id","nombre":"Parcela $id","hectareas":2.5,"cultivo":"Papa","valle":"Mantaro","campaniaCerrada":false,"dosisMaximaPorHectarea":1.25}],"pagina":$pagina,"tamanio":100,"totalElementos":2,"totalPaginas":2,"ultima":${pagina == 1}}""",
                    HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
            val repo = repositorio(local, Almacen(), engine)
            assertEquals(2, repo.actualizar())
            assertEquals(listOf(1L, 2L), repo.observar(FiltroLotes()).first().map(Lote::id))
            assertNotNull(repo.observarUltimaSincronizacion().first())
        } finally { driver.close() }
    }

    @Test fun falloEnSegundaPaginaConservaCacheYFecha(): Unit = runBlocking {
        val (driver, local) = base()
        try {
            local.reemplazarLotesCompleto(listOf(lote(9)), 1234L)
            val engine = MockEngine { request ->
                if (request.url.parameters["pagina"] == "0") respond("""{"contenido":[],"pagina":0,"tamanio":100,"totalElementos":2,"totalPaginas":2,"ultima":false}""",
                    HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                else respond("""{"status":500,"message":"Error interno"}""",
                    HttpStatusCode.InternalServerError, headersOf(HttpHeaders.ContentType, "application/json"))
            }
            val repo = repositorio(local, Almacen(), engine)
            assertIs<ErrorApi.Servidor>(assertFailsWith<ErrorApiException> { repo.actualizar() }.error)
            assertEquals(9L, repo.observar(FiltroLotes()).first().single().id)
            assertEquals(1234L, repo.observarUltimaSincronizacion().first())
        } finally { driver.close() }
    }

    @Test fun sinConexionConservaLotesYUltimaSincronizacion(): Unit = runBlocking {
        val (driver, local) = base()
        try {
            local.reemplazarLotesCompleto(listOf(lote(9)), 1234L)
            val engine = MockEngine { throw IOException("sin red de prueba") }
            val error = assertFailsWith<ErrorApiException> { repositorio(local, Almacen(), engine).actualizar() }.error
            assertIs<ErrorApi.SinConexion>(error)
            assertEquals(9L, local.observarLotes(FiltroLotes()).first().single().id)
            assertEquals(1234L, local.observarUltimaSincronizacion().first())
        } finally { driver.close() }
    }

    @Test fun errores401Y403ConservanFilasPeroSolo401BorraToken(): Unit = runBlocking {
        for ((codigo, borrar) in listOf(HttpStatusCode.Unauthorized to true, HttpStatusCode.Forbidden to false)) {
            val (driver, local) = base()
            try {
                local.reemplazarLotesCompleto(listOf(lote(9)), 1234L)
                val almacen = Almacen()
                val engine = MockEngine { respond("""{"status":${codigo.value},"message":"Acceso denegado"}""",
                    codigo, headersOf(HttpHeaders.ContentType, "application/json")) }
                val repo = repositorio(local, almacen, engine)
                val error = assertFailsWith<ErrorApiException> { repo.actualizar() }.error
                if (borrar) assertIs<ErrorApi.NoAutorizado>(error) else assertIs<ErrorApi.SinPrivilegio>(error)
                if (borrar) assertNull(almacen.token) else assertEquals("solo-test", almacen.token)
                assertEquals(9L, repo.observar(FiltroLotes()).first().single().id)
            } finally { driver.close() }
        }
    }

    @Test fun reemplazoFallidoRevierteTransaccion(): Unit = runBlocking {
        val (driver, local) = base()
        try {
            local.reemplazarLotesCompleto(listOf(lote(9)), 1234L)
            assertFailsWith<Exception> { local.reemplazarLotesCompleto(listOf(lote(1), lote(1)), 5678L) }
            assertEquals(9L, local.observarLotes(FiltroLotes()).first().single().id)
            assertEquals(1234L, local.observarUltimaSincronizacion().first())
        } finally { driver.close() }
    }
}
