package pe.upeu.agrosierra.domain.model

sealed class ErrorApi(open val mensaje: String) {
    data class Validacion(override val mensaje: String, val campos: Map<String, String>) : ErrorApi(mensaje)
    data class NoAutorizado(override val mensaje: String) : ErrorApi(mensaje)
    data class SinPrivilegio(override val mensaje: String) : ErrorApi(mensaje)
    data class NoEncontrado(override val mensaje: String) : ErrorApi(mensaje)
    data class Conflicto(override val mensaje: String) : ErrorApi(mensaje)
    data class Servidor(override val mensaje: String) : ErrorApi(mensaje)
    data object SinConexion : ErrorApi("Sin conexión con AgroAPI")
    data object TiempoAgotado : ErrorApi("La solicitud tardó demasiado")
}

class ErrorApiException(val error: ErrorApi) : Exception(error.mensaje)
