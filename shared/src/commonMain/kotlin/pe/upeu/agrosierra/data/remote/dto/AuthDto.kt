package pe.upeu.agrosierra.data.remote.dto

import kotlinx.serialization.Serializable
import pe.upeu.agrosierra.domain.model.Sesion
import pe.upeu.agrosierra.domain.model.UsuarioAutenticado

@Serializable
data class LoginPeticionDto(val usuario: String, val contrasena: String)

@Serializable
data class LoginRespuestaDto(
    val token: String,
    val usuario: String,
    val rol: String,
    val privilegios: Set<String>,
) {
    fun aDominio() = Sesion(token, usuario, rol, privilegios)
}

@Serializable
data class UsuarioActualDto(
    val id: Long,
    val usuario: String,
    val rol: String,
    val privilegios: Set<String>,
) {
    fun aDominio() = UsuarioAutenticado(id, usuario, rol, privilegios)
}
