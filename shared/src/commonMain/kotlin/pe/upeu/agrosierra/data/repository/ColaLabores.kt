package pe.upeu.agrosierra.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializa los envíos y las modificaciones locales de labores de todas las sesiones. */
class ColaLabores {
    private val mutex = Mutex()
    suspend fun <T> exclusivo(accion: suspend () -> T): T = mutex.withLock { accion() }
}
