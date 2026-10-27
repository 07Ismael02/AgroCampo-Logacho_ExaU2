package pe.upeu.agrosierra.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import pe.upeu.agrosierra.data.local.AlmacenSeguro
import pe.upeu.agrosierra.data.remote.dto.LoginPeticionDto
import pe.upeu.agrosierra.domain.model.ErrorApi
import pe.upeu.agrosierra.domain.model.ErrorApiException

/** Único punto que añade Bearer a una petición protegida; el login nunca lo recibe. */
class SolicitudesApi(
    private val cliente: HttpClient,
    private val config: ApiConfig,
    private val json: Json,
    private val almacen: AlmacenSeguro,
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
        val usado = token ?: almacen.leerToken()
            ?: throw ErrorApiException(ErrorApi.NoAutorizado("Debe iniciar sesión"))
        val respuesta = traducirFalloRed {
            cliente.get(config.ruta(path)) {
                header(HttpHeaders.Authorization, "Bearer $usado")
            }
        }
        comprobarRespuesta(respuesta, json)
        return respuesta
    }
}
