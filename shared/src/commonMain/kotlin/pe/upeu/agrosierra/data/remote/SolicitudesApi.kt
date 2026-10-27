package pe.upeu.agrosierra.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.delete
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import pe.upeu.agrosierra.data.local.AlmacenSeguro
import pe.upeu.agrosierra.data.local.InvalidacionesSesion
import pe.upeu.agrosierra.data.remote.dto.LoginPeticionDto
import pe.upeu.agrosierra.data.remote.dto.LaborPeticionDto
import pe.upeu.agrosierra.domain.model.ErrorApi
import pe.upeu.agrosierra.domain.model.ErrorApiException

/** Único punto que añade Bearer a una petición protegida; el login nunca lo recibe. */
class SolicitudesApi(
    private val cliente: HttpClient,
    private val config: ApiConfig,
    private val json: Json,
    private val almacen: AlmacenSeguro,
    private val invalidaciones: InvalidacionesSesion = InvalidacionesSesion(),
) {
    suspend fun postPublico(path: String, body: LoginPeticionDto): HttpResponse {
        val respuesta = traducirFalloRed {
            cliente.post(config.ruta(path)) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        comprobarRespuesta(respuesta, json)
        return respuesta
    }

    suspend fun getProtegido(path: String, token: String? = null): HttpResponse {
        return protegido(token) { usado -> cliente.get(config.ruta(path)) {
            header(HttpHeaders.Authorization, "Bearer $usado")
        } }
    }

    suspend fun postProtegido(path: String, body: LaborPeticionDto, token: String): HttpResponse =
        protegido(token) { usado -> cliente.post(config.ruta(path)) {
            header(HttpHeaders.Authorization, "Bearer $usado")
            contentType(ContentType.Application.Json)
            setBody(body)
        } }

    suspend fun putProtegido(path: String, body: LaborPeticionDto, token: String): HttpResponse =
        protegido(token) { usado -> cliente.put(config.ruta(path)) {
            header(HttpHeaders.Authorization, "Bearer $usado")
            contentType(ContentType.Application.Json)
            setBody(body)
        } }

    suspend fun deleteProtegido(path: String, token: String): HttpResponse =
        protegido(token) { usado -> cliente.delete(config.ruta(path)) {
            header(HttpHeaders.Authorization, "Bearer $usado")
        } }

    private suspend fun protegido(token: String?, ejecutar: suspend (String) -> HttpResponse): HttpResponse {
        val usado = token ?: almacen.leerToken()
            ?: throw ErrorApiException(ErrorApi.NoAutorizado("Debe iniciar sesión"))
        val respuesta = traducirFalloRed { ejecutar(usado) }
        try {
            comprobarRespuesta(respuesta, json)
        } catch (e: ErrorApiException) {
            if (e.error is ErrorApi.NoAutorizado && almacen.leerToken() == usado) {
                almacen.borrarToken()
                invalidaciones.avisar(usado)
            }
            throw e
        }
        return respuesta
    }
}
