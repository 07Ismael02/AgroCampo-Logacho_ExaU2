package pe.upeu.agrosierra.data.remote

import io.ktor.client.call.body
import io.ktor.http.encodeURLParameter
import pe.upeu.agrosierra.data.remote.dto.LoteDto
import pe.upeu.agrosierra.data.remote.dto.PaginaDto
import pe.upeu.agrosierra.domain.model.ErrorApi
import pe.upeu.agrosierra.domain.model.ErrorApiException
import pe.upeu.agrosierra.domain.model.Lote

class LotesApi(private val solicitudes: SolicitudesApi) {
    suspend fun pagina(numero: Int, tamanio: Int = 100, valle: String? = null,
                       estado: Boolean? = null, token: String? = null): PaginaDto<LoteDto> {
        val parametros = buildList {
            add("pagina=$numero")
            add("tamanio=$tamanio")
            valle?.let { add("valle=${it.encodeURLParameter()}") }
            estado?.let { add("estado=${if (it) "cerrada" else "abierta"}") }
        }.joinToString("&")
        return solicitudes.getProtegido("api/v1/lotes?$parametros", token).body()
    }

    suspend fun detalle(id: Long): Lote = solicitudes.getProtegido("api/v1/lotes/$id")
        .body<LoteDto>().aDominio()

    suspend fun todos(token: String? = null): List<Lote> {
        val resultado = mutableListOf<Lote>()
        var pagina = 0
        var paginasEsperadas: Int? = null
        var totalEsperado: Long? = null
        while (pagina < 1000) {
            val respuesta = pagina(pagina, token = token)
            if (respuesta.pagina != pagina || respuesta.tamanio != 100 ||
                respuesta.totalPaginas < 0 || respuesta.totalElementos < 0 ||
                (paginasEsperadas != null && respuesta.totalPaginas != paginasEsperadas) ||
                (totalEsperado != null && respuesta.totalElementos != totalEsperado)) {
                throw ErrorApiException(ErrorApi.Servidor("Paginación inconsistente"))
            }
            paginasEsperadas = respuesta.totalPaginas
            totalEsperado = respuesta.totalElementos
            resultado += respuesta.contenido.map(LoteDto::aDominio)
            if (respuesta.ultima) {
                if (resultado.size.toLong() != totalEsperado || resultado.map { it.id }.toSet().size != resultado.size ||
                    (respuesta.totalPaginas > 0 && pagina + 1 != respuesta.totalPaginas)) {
                    throw ErrorApiException(ErrorApi.Servidor("Descarga incompleta de lotes"))
                }
                return resultado
            }
            if (respuesta.contenido.isEmpty() || pagina + 1 >= respuesta.totalPaginas) {
                throw ErrorApiException(ErrorApi.Servidor("Descarga incompleta de lotes"))
            }
            pagina++
        }
        throw ErrorApiException(ErrorApi.Servidor("Demasiadas páginas de lotes"))
    }
}
