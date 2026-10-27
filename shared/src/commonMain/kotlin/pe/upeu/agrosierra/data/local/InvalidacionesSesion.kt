package pe.upeu.agrosierra.data.local

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Solo comunica el token rechazado; evita invalidar un login posterior por un 401 tardío. */
class InvalidacionesSesion {
    private val eventos = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val tokens = eventos.asSharedFlow()
    fun avisar(token: String) { eventos.tryEmit(token) }
}
