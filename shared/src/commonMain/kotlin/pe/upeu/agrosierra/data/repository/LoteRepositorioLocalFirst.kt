package pe.upeu.agrosierra.data.repository

import kotlinx.coroutines.flow.Flow
import pe.upeu.agrosierra.data.local.BaseLocal
import pe.upeu.agrosierra.data.remote.LotesApi
import pe.upeu.agrosierra.domain.model.FiltroLotes
import pe.upeu.agrosierra.domain.model.Lote
import pe.upeu.agrosierra.domain.repository.LoteRepository
import kotlin.time.Clock

class LoteRepositorioLocalFirst(private val local: BaseLocal, private val remoto: LotesApi) : LoteRepository {
    override fun observar(filtro: FiltroLotes): Flow<List<Lote>> = local.observarLotes(filtro)
    override fun observarUltimaSincronizacion(): Flow<Long?> = local.observarUltimaSincronizacion()
    override fun obtenerLocal(id: Long): Lote? = local.obtenerLote(id)

    override suspend fun actualizar(): Int {
        val completos = remoto.todos() // No se modifica SQLite hasta completar todas las páginas.
        local.reemplazarLotesCompleto(completos, Clock.System.now().toEpochMilliseconds())
        return completos.size
    }

    override suspend fun actualizarDetalle(id: Long): Lote {
        val lote = remoto.detalle(id)
        local.guardarLote(lote) // No cambia la marca de sincronización del catálogo completo.
        return lote
    }
}
