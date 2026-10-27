package pe.upeu.agrosierra.data.remote

import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pe.upeu.agrosierra.domain.model.ErrorApi
import pe.upeu.agrosierra.domain.model.ErrorApiException

@Serializable
data class ErrorRespuestaDto(
    val timestamp: String? = null,
    val status: Int? = null,
    val error: String? = null,
    val message: String? = null,
    val path: String? = null,
    val validationErrors: Map<String, String>? = null,
)

fun traducirEstado(status: Int, cuerpo: ErrorRespuestaDto?): ErrorApi {
    val mensaje = cuerpo?.message?.takeIf { it.isNotBlank() }
    return when (status) {
        400 -> ErrorApi.Validacion(mensaje ?: "Datos inválidos", cuerpo?.validationErrors.orEmpty())
        401 -> ErrorApi.NoAutorizado(mensaje ?: "Debe iniciar sesión")
        403 -> ErrorApi.SinPrivilegio(mensaje ?: "Privilegio insuficiente")
        404 -> ErrorApi.NoEncontrado(mensaje ?: "Recurso no encontrado")
        409 -> ErrorApi.Conflicto(mensaje ?: "Conflicto")
        else -> ErrorApi.Servidor("Error del servidor")
    }
}

suspend fun comprobarRespuesta(response: HttpResponse, json: Json) {
    if (response.status.value in 200..299) return // Incluye 200, 201 y 204 sin leer cuerpos vacíos.
    val cuerpo = runCatching { json.decodeFromString<ErrorRespuestaDto>(response.bodyAsText()) }.getOrNull()
    throw ErrorApiException(traducirEstado(response.status.value, cuerpo))
}

suspend inline fun <T> traducirFalloRed(block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: HttpRequestTimeoutException) {
    throw ErrorApiException(ErrorApi.TiempoAgotado)
} catch (e: ConnectTimeoutException) {
    throw ErrorApiException(ErrorApi.TiempoAgotado)
} catch (e: IOException) {
    throw ErrorApiException(ErrorApi.SinConexion)
}
