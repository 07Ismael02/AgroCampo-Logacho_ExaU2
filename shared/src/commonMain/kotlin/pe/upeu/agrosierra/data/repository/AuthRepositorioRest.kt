package pe.upeu.agrosierra.data.repository

import io.ktor.client.call.body
import pe.upeu.agrosierra.data.local.AlmacenSeguro
import pe.upeu.agrosierra.data.remote.SolicitudesApi
import pe.upeu.agrosierra.data.remote.dto.LoginPeticionDto
import pe.upeu.agrosierra.data.remote.dto.LoginRespuestaDto
import pe.upeu.agrosierra.data.remote.dto.UsuarioActualDto
import pe.upeu.agrosierra.domain.model.ErrorApi
import pe.upeu.agrosierra.domain.model.ErrorApiException
import pe.upeu.agrosierra.domain.model.Sesion
import pe.upeu.agrosierra.domain.model.UsuarioAutenticado
import pe.upeu.agrosierra.domain.repository.AuthRepository

class AuthRepositorioRest(
    private val api: SolicitudesApi,
    private val almacen: AlmacenSeguro,
) : AuthRepository {
    private var sesion: Sesion? = null

    override fun sesionActual(): Sesion? = sesion

    override suspend fun iniciarSesion(usuario: String, contrasena: String): UsuarioAutenticado {
        val respuesta = api.postPublico("api/v1/auth/login", LoginPeticionDto(usuario, contrasena))
        val nueva = respuesta.body<LoginRespuestaDto>().aDominio()
        val identidad = consultarUsuario(nueva.token) // /me confirma que el token emitido es utilizable.
        almacen.guardarToken(nueva.token)
        sesion = nueva.copy(usuario = identidad.usuario, rol = identidad.rol, privilegios = identidad.privilegios,
            id = identidad.id)
        return identidad
    }

    override suspend fun restaurarSesion(): UsuarioAutenticado? {
        val token = almacen.leerToken()?.takeIf { it.isNotBlank() } ?: return null
        return try {
            val identidad = consultarUsuario(token)
            sesion = Sesion(token, identidad.usuario, identidad.rol, identidad.privilegios, identidad.id)
            identidad
        } catch (e: ErrorApiException) {
            if (e.error is ErrorApi.NoAutorizado) cerrarSesion()
            throw e
        }
    }

    suspend fun consultarUsuarioActual(): UsuarioAutenticado {
        val token = sesion?.token ?: throw ErrorApiException(ErrorApi.NoAutorizado("Debe iniciar sesión"))
        return try {
            consultarUsuario(token)
        } catch (e: ErrorApiException) {
            if (e.error is ErrorApi.NoAutorizado) cerrarSesion()
            throw e
        }
    }

    private suspend fun consultarUsuario(token: String): UsuarioAutenticado {
        val respuesta = api.getProtegido("api/v1/usuarios/me", token)
        return respuesta.body<UsuarioActualDto>().aDominio()
    }

    override suspend fun cerrarSesion() {
        sesion = null
        almacen.borrarToken()
    }
}
