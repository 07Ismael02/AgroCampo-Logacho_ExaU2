package pe.upeu.agrosierra.domain.repository

import pe.upeu.agrosierra.domain.model.Sesion
import pe.upeu.agrosierra.domain.model.UsuarioAutenticado

interface AuthRepository {
    suspend fun iniciarSesion(usuario: String, contrasena: String): UsuarioAutenticado
    suspend fun restaurarSesion(): UsuarioAutenticado?
    suspend fun cerrarSesion()
    fun sesionActual(): Sesion?
}
