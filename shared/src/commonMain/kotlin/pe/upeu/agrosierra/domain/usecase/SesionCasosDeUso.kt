package pe.upeu.agrosierra.domain.usecase

import pe.upeu.agrosierra.domain.repository.AuthRepository

class IniciarSesionUseCase(private val repositorio: AuthRepository) {
    suspend operator fun invoke(usuario: String, contrasena: String) = repositorio.iniciarSesion(usuario, contrasena)
}

class RestaurarSesionUseCase(private val repositorio: AuthRepository) {
    suspend operator fun invoke() = repositorio.restaurarSesion()
}

class CerrarSesionUseCase(private val repositorio: AuthRepository) {
    suspend operator fun invoke() = repositorio.cerrarSesion()
}
