package pe.upeu.agrosierra.domain.repository

import kotlinx.coroutines.flow.Flow
import pe.upeu.agrosierra.domain.model.FiltroLotes
import pe.upeu.agrosierra.domain.model.Lote

interface LoteRepository {
    fun observar(filtro: FiltroLotes): Flow<List<Lote>>
    fun observarUltimaSincronizacion(): Flow<Long?>
    fun obtenerLocal(id: Long): Lote?
    suspend fun actualizar(): Int
    suspend fun actualizarDetalle(id: Long): Lote
}
