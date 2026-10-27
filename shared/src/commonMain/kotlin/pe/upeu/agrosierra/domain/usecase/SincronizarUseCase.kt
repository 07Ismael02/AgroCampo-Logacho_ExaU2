package pe.upeu.agrosierra.domain.usecase

import pe.upeu.agrosierra.domain.model.ResumenSincronizacion
import pe.upeu.agrosierra.domain.repository.SincronizacionRepository

class SincronizarUseCase(private val repositorio: SincronizacionRepository) {
    suspend operator fun invoke(ownerId: Long): ResumenSincronizacion = repositorio.sincronizar(ownerId)
}
