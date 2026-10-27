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
import pe.upeu.agrosierra.data.remote.LaboresApi
import pe.upeu.agrosierra.data.remote.SolicitudesApi
import pe.upeu.agrosierra.data.remote.crearClienteHttp
import pe.upeu.agrosierra.data.remote.dto.LaborRespuestaDto
import pe.upeu.agrosierra.data.remote.dto.aPeticionDto
import pe.upeu.agrosierra.domain.model.ErrorApi
import pe.upeu.agrosierra.domain.model.ErrorApiException
import pe.upeu.agrosierra.domain.model.Labor
import pe.upeu.agrosierra.domain.model.TipoLabor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LaboresContratoTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val laborJson = """{"id":7,"loteId":1,"tipo":"RIEGO","fecha":"2026-10-27","responsable":"tecnico","cantidad":1.0,"observacion":"nota","pendiente":false}"""
    private val labor = Labor(7, 1, TipoLabor.RIEGO, "2026-10-27", "tecnico", 1.0, "nota", false)

    @Test fun dtoYPeticionRespetanContrato() {
        assertEquals(labor, json.decodeFromString<LaborRespuestaDto>(laborJson).aDominio())
        val body = json.encodeToString(labor.aPeticionDto())
        assertTrue(body.contains("\"loteId\":1"))
        assertTrue(body.contains("\"tipo\":\"RIEGO\""))
        assertTrue(!body.contains("responsable") && !body.contains("pendiente") && !body.contains("\"id\""))
    }

    @Test fun getRecorrePaginasYPostPutDeleteUsanMetodosYCodigos(): Unit = runBlocking {
        val metodos = mutableListOf<String>()
        val engine = MockEngine { request ->
            metodos += request.method.value
            assertEquals("Bearer prueba", request.headers[HttpHeaders.Authorization])
            when (request.method.value) {
                "GET" -> {
                    val pagina = request.url.parameters["pagina"]!!.toInt()
                    val item = laborJson.replace("\"id\":7", "\"id\":${pagina + 7}")
                    respond("""{"contenido":[$item],"pagina":$pagina,"tamanio":100,"totalElementos":2,"totalPaginas":2,"ultima":${pagina == 1}}""",
                        HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
                "POST" -> respond(laborJson, HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/json"))
                "PUT" -> respond(laborJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                else -> respond("", HttpStatusCode.NoContent)
            }
        }
        val api = LaboresApi(SolicitudesApi(crearClienteHttp(engine, json), ApiConfig("http://test.local"), json, TokenPrueba()))
        assertEquals(listOf(7L, 8L), api.todos(1, "prueba").map { it.id })
        assertEquals(7L, api.crear(labor, "prueba").id)
        assertEquals(7L, api.editar(labor, "prueba").id)
        api.eliminar(7, "prueba")
        assertEquals(listOf("GET", "GET", "POST", "PUT", "DELETE"), metodos)
    }

    @Test fun errores401403Y409ConservanSemantica(): Unit = runBlocking {
        val casos = listOf(
            HttpStatusCode.Unauthorized to ErrorApi.NoAutorizado::class,
            HttpStatusCode.Forbidden to ErrorApi.SinPrivilegio::class,
            HttpStatusCode.Conflict to ErrorApi.Conflicto::class,
        )
        for ((codigo, tipo) in casos) {
            val engine = MockEngine { respond("""{"timestamp":"2026-10-27T00:00:00","status":${codigo.value},"error":"Error","message":"denegado","path":"/api/v1/labores","validationErrors":null}""",
                codigo, headersOf(HttpHeaders.ContentType, "application/json")) }
            val api = LaboresApi(SolicitudesApi(crearClienteHttp(engine, json), ApiConfig("http://test.local"), json, TokenPrueba()))
            val error = assertFailsWith<ErrorApiException> { api.crear(labor, "prueba") }.error
            assertEquals(tipo, error::class)
        }
    }

    private class TokenPrueba : AlmacenSeguro {
        override fun leerToken() = "prueba"
        override fun guardarToken(token: String) = Unit
        override fun borrarToken() = Unit
    }
}
