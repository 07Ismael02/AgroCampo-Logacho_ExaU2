package pe.upeu.agrosierra.data.remote

import io.ktor.client.call.body
import pe.upeu.agrosierra.data.remote.dto.LaborRespuestaDto
import pe.upeu.agrosierra.data.remote.dto.PaginaDto
import pe.upeu.agrosierra.data.remote.dto.aPeticionDto
import pe.upeu.agrosierra.domain.model.ErrorApi
import pe.upeu.agrosierra.domain.model.ErrorApiException
import pe.upeu.agrosierra.domain.model.Labor

class LaboresApi(private val solicitudes: SolicitudesApi) {
    suspend fun todos(loteId: Long, token: String): List<Labor> {
        val resultado = mutableListOf<Labor>()
        var pagina = 0
        var total: Long? = null
        var totalPaginas: Int? = null
        while (pagina < 1000) {
            val respuesta: PaginaDto<LaborRespuestaDto> = solicitudes.getProtegido(
                "api/v1/lotes/$loteId/labores?pagina=$pagina&tamanio=100", token).body()
            if (respuesta.pagina != pagina || respuesta.tamanio != 100 || respuesta.totalElementos < 0 ||
                respuesta.totalPaginas < 0 || (total != null && respuesta.totalElementos != total) ||
                (totalPaginas != null && respuesta.totalPaginas != totalPaginas)) {
                throw ErrorApiException(ErrorApi.Servidor("Paginación de labores inconsistente"))
            }
            total = respuesta.totalElementos
            totalPaginas = respuesta.totalPaginas
            resultado += respuesta.contenido.map(LaborRespuestaDto::aDominio)
            if (respuesta.ultima) {
                if (resultado.size.toLong() != total || resultado.map(Labor::id).toSet().size != resultado.size ||
                    (respuesta.totalPaginas > 0 && pagina + 1 != respuesta.totalPaginas)) {
                    throw ErrorApiException(ErrorApi.Servidor("Descarga incompleta de labores"))
                }
                return resultado
            }
            if (respuesta.contenido.isEmpty() || pagina + 1 >= respuesta.totalPaginas) {
                throw ErrorApiException(ErrorApi.Servidor("Descarga incompleta de labores"))
            }
            pagina++
        }
        throw ErrorApiException(ErrorApi.Servidor("Demasiadas páginas de labores"))
    }

    suspend fun crear(labor: Labor, token: String): Labor = solicitudes.postProtegido(
        "api/v1/labores", labor.aPeticionDto(), token).body<LaborRespuestaDto>().aDominio()

    suspend fun editar(labor: Labor, token: String): Labor = solicitudes.putProtegido(
        "api/v1/labores/${labor.id}", labor.aPeticionDto(), token).body<LaborRespuestaDto>().aDominio()

    suspend fun eliminar(id: Long, token: String) {
        solicitudes.deleteProtegido("api/v1/labores/$id", token) // 204: sin deserializar cuerpo.
    }
}
