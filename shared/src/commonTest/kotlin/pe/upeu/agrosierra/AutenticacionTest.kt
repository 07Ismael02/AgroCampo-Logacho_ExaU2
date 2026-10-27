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
import pe.upeu.agrosierra.data.remote.SolicitudesApi
import pe.upeu.agrosierra.data.remote.crearClienteHttp
import pe.upeu.agrosierra.data.remote.dto.LoginRespuestaDto
import pe.upeu.agrosierra.data.remote.traducirEstado
import pe.upeu.agrosierra.data.repository.AuthRepositorioRest
import pe.upeu.agrosierra.domain.model.ErrorApi
import pe.upeu.agrosierra.domain.model.ErrorApiException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class AutenticacionTest {
    private val json = Json { ignoreUnknownKeys = true }
    private class AlmacenMemoria : AlmacenSeguro {
        var token: String? = null
        override fun leerToken() = token
        override fun guardarToken(token: String) { this.token = token }
        override fun borrarToken() { token = null }
    }

    @Test fun dtoLoginConvierteADominio() {
        val dto = json.decodeFromString<LoginRespuestaDto>("""{"token":"prueba","usuario":"tecnico","rol":"TECNICO","privilegios":["LOTE_LEER"],"extra":1}""")
        assertEquals("TECNICO", dto.aDominio().rol)
        assertEquals(setOf("LOTE_LEER"), dto.aDominio().privilegios)
    }

    @Test fun error400ConservaDetalleDeCampo() {
        val error = traducirEstado(400, pe.upeu.agrosierra.data.remote.ErrorRespuestaDto(message="Datos inválidos", validationErrors=mapOf("cantidad" to "Exceso")))
        assertEquals("Exceso", assertIs<ErrorApi.Validacion>(error).campos["cantidad"])
    }

    @Test fun loginConsultaMeYLogoutBorraToken() = runBlocking {
        val almacen = AlmacenMemoria()
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/auth/login" -> {
                    assertNull(request.headers[HttpHeaders.Authorization])
                    respond("""{"token":"solo-test","usuario":"tecnico","rol":"TECNICO","privilegios":["LOTE_LEER"]}""",
                        HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
                "/api/v1/usuarios/me" -> {
                    assertEquals("Bearer solo-test", request.headers[HttpHeaders.Authorization])
                    respond("""{"id":3,"usuario":"tecnico","rol":"TECNICO","privilegios":["LOTE_LEER"]}""",
                        HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
                else -> error("Ruta inesperada")
            }
        }
        val repo = AuthRepositorioRest(SolicitudesApi(crearClienteHttp(engine, json), ApiConfig("http://test.local"), json, almacen), almacen)
        assertEquals(3L, repo.iniciarSesion("tecnico", "clave-de-prueba").id)
        assertEquals("solo-test", almacen.token)
        repo.cerrarSesion()
        assertNull(almacen.token)
        assertNull(repo.sesionActual())
    }

    @Test fun restaurar401BorraSesionPero403LaConserva() = runBlocking {
        for ((status, borra) in listOf(HttpStatusCode.Unauthorized to true, HttpStatusCode.Forbidden to false)) {
            val almacen = AlmacenMemoria().apply { token = "solo-test" }
            val engine = MockEngine { respond("""{"status":${status.value},"message":"denegado"}""",
                status, headersOf(HttpHeaders.ContentType, "application/json")) }
            val repo = AuthRepositorioRest(SolicitudesApi(crearClienteHttp(engine, json), ApiConfig("http://test.local"), json, almacen), almacen)
            val error = assertFailsWith<ErrorApiException> { repo.restaurarSesion() }
            if (borra) assertIs<ErrorApi.NoAutorizado>(error.error) else assertIs<ErrorApi.SinPrivilegio>(error.error)
            assertEquals(if (borra) null else "solo-test", almacen.token)
        }
    }
}
