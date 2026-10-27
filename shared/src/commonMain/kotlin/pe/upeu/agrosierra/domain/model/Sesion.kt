package pe.upeu.agrosierra.domain.model

data class Sesion(
    val token: String,
    val usuario: String,
    val rol: String,
    val privilegios: Set<String>,
)

data class UsuarioAutenticado(
    val id: Long,
    val usuario: String,
    val rol: String,
    val privilegios: Set<String>,
)
