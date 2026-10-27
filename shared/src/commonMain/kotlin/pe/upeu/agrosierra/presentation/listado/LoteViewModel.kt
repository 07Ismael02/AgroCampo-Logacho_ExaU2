package pe.upeu.agrosierra.presentation.listado

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import pe.upeu.agrosierra.domain.model.ErrorApi
import pe.upeu.agrosierra.domain.model.ErrorApiException
import pe.upeu.agrosierra.domain.model.FiltroLotes
import pe.upeu.agrosierra.domain.model.Lote
import pe.upeu.agrosierra.domain.repository.LoteRepository

data class LoteUiState(
    val lotes: List<Lote> = emptyList(),
    val totalLocales: Int = 0,
    val valles: List<String> = emptyList(),
    val filtro: FiltroLotes = FiltroLotes(),
    val ultimaSincronizacion: Long? = null,
    val cargandoLocal: Boolean = true,
    val sincronizando: Boolean = false,
    val aviso: String? = null,
    val sinConexion: Boolean = false,
    val detalle: Lote? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class LoteViewModel(private val repositorio: LoteRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val filtro = MutableStateFlow(FiltroLotes())
    private val mutable = MutableStateFlow(LoteUiState())
    val estado = mutable.asStateFlow()
    private val mutex = Mutex()

    init {
        scope.launch {
            filtro.flatMapLatest(repositorio::observar).collect { lista ->
                mutable.update { it.copy(lotes = lista, filtro = filtro.value, cargandoLocal = false) }
            }
        }
        scope.launch {
            repositorio.observar(FiltroLotes()).collect { lista ->
                mutable.update { it.copy(totalLocales = lista.size, valles = lista.map(Lote::valle).distinct().sorted()) }
            }
        }
        scope.launch {
            repositorio.observarUltimaSincronizacion().collect { instante ->
                mutable.update { it.copy(ultimaSincronizacion = instante) }
            }
        }
    }

    fun buscar(texto: String) { filtro.value = filtro.value.copy(busqueda = texto) }
    fun filtrarValle(valle: String?) { filtro.value = filtro.value.copy(valle = valle) }
    fun filtrarEstado(cerrada: Boolean?) { filtro.value = filtro.value.copy(campaniaCerrada = cerrada) }

    fun sincronizar() = scope.launch {
        if (!mutex.tryLock()) return@launch
        mutable.update { it.copy(sincronizando = true, aviso = null, sinConexion = false) }
        try {
            val cantidad = repositorio.actualizar()
            mutable.update { it.copy(aviso = "$cantidad lotes actualizados") }
        } catch (e: ErrorApiException) {
            mutable.update { it.copy(aviso = e.error.mensaje,
                sinConexion = e.error is ErrorApi.SinConexion || e.error is ErrorApi.TiempoAgotado) }
        } catch (_: Exception) {
            mutable.update { it.copy(aviso = "No se pudieron actualizar los lotes") }
        } finally {
            mutable.update { it.copy(sincronizando = false) }
            mutex.unlock()
        }
    }

    fun abrirDetalle(id: Long) = scope.launch {
        mutable.update { it.copy(detalle = repositorio.obtenerLocal(id), aviso = null) }
        try {
            val actualizado = repositorio.actualizarDetalle(id)
            mutable.update { it.copy(detalle = actualizado) }
        } catch (e: ErrorApiException) {
            mutable.update { it.copy(aviso = e.error.mensaje,
                sinConexion = e.error is ErrorApi.SinConexion || e.error is ErrorApi.TiempoAgotado) }
            if (e.error is ErrorApi.NoEncontrado) sincronizar()
        }
    }

    fun cerrarDetalle() { mutable.update { it.copy(detalle = null, aviso = null) } }
}
