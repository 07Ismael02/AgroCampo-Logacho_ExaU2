package pe.upeu.agrosierra

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import pe.upeu.agrosierra.data.local.AlmacenSeguro
import pe.upeu.agrosierra.data.remote.ApiConfig
import pe.upeu.agrosierra.data.remote.LotesApi
import pe.upeu.agrosierra.data.remote.SolicitudesApi
import pe.upeu.agrosierra.data.remote.crearClienteHttp
import pe.upeu.agrosierra.data.remote.dto.LoteDto
import pe.upeu.agrosierra.data.remote.dto.PaginaDto
import pe.upeu.agrosierra.domain.model.ErrorApi
import pe.upeu.agrosierra.domain.model.ErrorApiException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LotesContratoTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val lote = """{"id":1,"codigo":"L-01","nombre":"Parcela alta","hectareas":3.5,"cultivo":"Papa","valle":"Mantaro","campaniaCerrada":false,"dosisMaximaPorHectarea":2.0}"""

    @Test fun dtoRespetaContratoDelLote() {
        val dominio = json.decodeFromString<LoteDto>(lote).aDominio()
        assertEquals(1L, dominio.id)
        assertEquals(3.5, dominio.hectareas)
        assertEquals(false, dominio.campaniaCerrada)
        assertEquals(2.0, dominio.dosisMaximaPorHectarea)
    }

    @Test fun paginaNoSeDeserializaComoLista() {
        val pagina = json.decodeFromString<PaginaDto<LoteDto>>("""{"contenido":[$lote],"pagina":0,"tamanio":20,"totalElementos":1,"totalPaginas":1,"ultima":true}""")
        assertEquals(1, pagina.contenido.size)
        assertEquals(1L, pagina.totalElementos)
    }

    @Test fun descargaRecorreTodasLasPaginas(): Unit = runBlocking {
        val visitadas = mutableListOf<String>()
        val engine = MockEngine { request ->
            visitadas += request.url.parameters["pagina"].orEmpty()
            assertEquals("Bearer solo-test", request.headers[HttpHeaders.Authorization])
            val numero = request.url.parameters["pagina"]?.toInt() ?: error("Sin página")
            val actual = lote.replace("\"id\":1", "\"id\":${numero + 1}")
            respond("""{"contenido":[$actual],"pagina":$numero,"tamanio":100,"totalElementos":2,"totalPaginas":2,"ultima":${numero == 1}}""",
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val api = LotesApi(SolicitudesApi(crearClienteHttp(engine, json), ApiConfig("http://test.local"), json, TokenPrueba()))
        assertEquals(listOf(1L, 2L), api.todos().map { it.id })
        assertEquals(listOf("0", "1"), visitadas)
    }

    @Test fun paginaIncompletaNoSeAcepta(): Unit = runBlocking {
        val engine = MockEngine { respond("""{"contenido":[$lote],"pagina":0,"tamanio":100,"totalElementos":2,"totalPaginas":2,"ultima":true}""",
            HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val api = LotesApi(SolicitudesApi(crearClienteHttp(engine, json), ApiConfig("http://test.local"), json, TokenPrueba()))
        assertIs<ErrorApi.Servidor>(assertFailsWith<ErrorApiException> { api.todos() }.error)
    }

    private class TokenPrueba : AlmacenSeguro {
        override fun leerToken() = "solo-test"
        override fun guardarToken(token: String) = Unit
        override fun borrarToken() = Unit
    }
}
