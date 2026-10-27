package pe.upeu.agrosierra.presentation.sesion

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import pe.upeu.agrosierra.domain.model.ErrorApi
import pe.upeu.agrosierra.domain.model.ErrorApiException
import pe.upeu.agrosierra.domain.model.UsuarioAutenticado
import pe.upeu.agrosierra.domain.usecase.CerrarSesionUseCase
import pe.upeu.agrosierra.domain.usecase.IniciarSesionUseCase
import pe.upeu.agrosierra.domain.usecase.RestaurarSesionUseCase
import pe.upeu.agrosierra.domain.repository.AuthRepository
import pe.upeu.agrosierra.data.local.InvalidacionesSesion

sealed interface SesionUiState {
    data object Cargando : SesionUiState
    data class Login(val mensaje: String? = null) : SesionUiState
    data class Autenticado(val usuario: UsuarioAutenticado) : SesionUiState
}

class SesionViewModel(
    private val iniciar: IniciarSesionUseCase,
    private val restaurar: RestaurarSesionUseCase,
    private val cerrar: CerrarSesionUseCase,
    private val repositorio: AuthRepository,
    private val invalidaciones: InvalidacionesSesion,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mutable = MutableStateFlow<SesionUiState>(SesionUiState.Cargando)
    val estado = mutable.asStateFlow()

    init {
        scope.launch {
            invalidaciones.tokens.collect { rechazado ->
                if (repositorio.sesionActual()?.token == rechazado) {
                    cerrar.invoke()
                    mutable.value = SesionUiState.Login()
                }
            }
        }
        restaurar()
    }

    fun restaurar() = scope.launch {
        mutable.value = SesionUiState.Cargando
        try {
            mutable.value = restaurar.invoke()?.let(SesionUiState::Autenticado) ?: SesionUiState.Login()
        } catch (e: ErrorApiException) {
            mutable.value = SesionUiState.Login(if (e.error is ErrorApi.NoAutorizado) null else e.error.mensaje)
        } catch (_: Exception) {
            mutable.value = SesionUiState.Login("No se pudo recuperar la sesión")
        }
    }

    fun iniciar(usuario: String, contrasena: String) = scope.launch {
        if (usuario.isBlank() || contrasena.isBlank()) {
            mutable.value = SesionUiState.Login("Ingrese usuario y contraseña")
            return@launch
        }
        mutable.value = SesionUiState.Cargando
        try {
            mutable.value = SesionUiState.Autenticado(iniciar.invoke(usuario.trim(), contrasena))
        } catch (e: ErrorApiException) {
            mutable.value = SesionUiState.Login(if (e.error is ErrorApi.NoAutorizado) "Credenciales incorrectas" else e.error.mensaje)
        } catch (_: Exception) {
            mutable.value = SesionUiState.Login("No se pudo iniciar sesión")
        }
    }

    fun cerrar() = scope.launch {
        cerrar.invoke()
        mutable.value = SesionUiState.Login()
    }

    fun liberar() = scope.cancel()
}
