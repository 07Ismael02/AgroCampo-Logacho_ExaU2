package pe.upeu.agrosierra.domain.repository

import kotlinx.coroutines.flow.Flow
import pe.upeu.agrosierra.domain.model.Labor

interface LaborRepository {
    fun observar(ownerId: Long, loteId: Long): Flow<List<Labor>>
    suspend fun actualizar(ownerId: Long, loteId: Long): Int
    suspend fun crear(ownerId: Long, labor: Labor): Labor
    suspend fun editar(ownerId: Long, labor: Labor): Labor
    suspend fun eliminar(ownerId: Long, id: Long)
}
