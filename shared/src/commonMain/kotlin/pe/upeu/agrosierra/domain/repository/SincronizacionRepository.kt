package pe.upeu.agrosierra.domain.repository

import kotlinx.coroutines.flow.Flow
import pe.upeu.agrosierra.domain.model.ResumenSincronizacion

interface SincronizacionRepository {
    fun observarPendientes(ownerId: Long): Flow<Long>
    fun observarUltimaSincronizacion(ownerId: Long): Flow<Long?>
    fun observarInciertos(ownerId: Long): Flow<Long>
    fun observarRechazadas(ownerId: Long): Flow<Long>
    suspend fun sincronizar(ownerId: Long): ResumenSincronizacion

    /** Decisión manual tras revisar AgroAPI: descartar la copia local o volver a enviarla. */
    suspend fun resolverIncierto(ownerId: Long, id: Long, existeEnServidor: Boolean)
}
