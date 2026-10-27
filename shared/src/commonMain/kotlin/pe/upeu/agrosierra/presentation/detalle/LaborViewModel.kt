package pe.upeu.agrosierra.presentation.detalle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.upeu.agrosierra.domain.model.ErrorApi
import pe.upeu.agrosierra.domain.model.ErrorApiException
import pe.upeu.agrosierra.domain.model.Labor
import pe.upeu.agrosierra.domain.repository.LaborRepository

data class LaborUiState(
    val labores: List<Labor> = emptyList(),
    val cargando: Boolean = true,
    val procesando: Boolean = false,
    val mensaje: String? = null,
    val erroresCampo: Map<String, String> = emptyMap(),
    val sinConexion: Boolean = false,
)

class LaborViewModel(private val repositorio: LaborRepository) {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observacion: Job? = null
    private var ownerId: Long? = null
    private var loteId: Long? = null
    private val mutable = MutableStateFlow(LaborUiState())
    val estado = mutable.asStateFlow()

    fun abrir(owner: Long, lote: Long) {
        if (ownerId != owner) {
            scope.cancel()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            ownerId = owner
        }
        if (loteId == lote && observacion?.isActive == true) return
        observacion?.cancel()
        loteId = lote
        mutable.value = LaborUiState()
        observacion = scope.launch {
            try {
                repositorio.observar(owner, lote).collect { lista ->
                    mutable.update { it.copy(labores = lista, cargando = false) }
                }
            } catch (e: ErrorApiException) { mostrar(e) }
        }
        actualizar()
    }

    fun actualizar() = ejecutar { owner ->
        val lote = requireNotNull(loteId)
        val cantidad = repositorio.actualizar(owner, lote)
        mutable.update { it.copy(mensaje = "$cantidad labores actualizadas", sinConexion = false) }
    }

    fun crear(labor: Labor) = ejecutar { owner ->
        val resultado = repositorio.crear(owner, labor)
        mutable.update { it.copy(mensaje = if (resultado.pendiente) "Labor guardada pendiente de envío" else "Labor registrada") }
    }

    fun editar(labor: Labor) = ejecutar { owner ->
        val resultado = repositorio.editar(owner, labor)
        mutable.update { it.copy(mensaje = if (resultado.pendiente) "Corrección pendiente de envío" else "Labor actualizada") }
    }

    fun eliminar(id: Long) = ejecutar { owner ->
        repositorio.eliminar(owner, id)
        mutable.update { it.copy(mensaje = "Labor eliminada") }
    }

    private fun ejecutar(accion: suspend (Long) -> Unit) {
        val owner = ownerId ?: return
        scope.launch {
            mutable.update { it.copy(procesando = true, mensaje = null, erroresCampo = emptyMap()) }
            try {
                accion(owner)
            } catch (e: ErrorApiException) {
                mostrar(e)
            } catch (_: Exception) {
                mutable.update { it.copy(mensaje = "No se pudo completar la operación") }
            } finally {
                mutable.update { it.copy(procesando = false, cargando = false) }
            }
        }
    }

    private fun mostrar(e: ErrorApiException) {
        mutable.update { it.copy(mensaje = e.error.mensaje,
            erroresCampo = (e.error as? ErrorApi.Validacion)?.campos.orEmpty(),
            sinConexion = e.error is ErrorApi.SinConexion || e.error is ErrorApi.TiempoAgotado,
            cargando = false) }
    }

    fun desactivar() {
        scope.cancel()
        ownerId = null
        loteId = null
        observacion = null
        mutable.value = LaborUiState()
    }
}
