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
import pe.upeu.agrosierra.domain.repository.SincronizacionRepository
import pe.upeu.agrosierra.domain.repository.ObservadorConexion
import pe.upeu.agrosierra.domain.usecase.SincronizarUseCase
import kotlin.time.Instant

data class LaborUiState(
    val labores: List<Labor> = emptyList(),
    val cargando: Boolean = true,
    val procesando: Boolean = false,
    val mensaje: String? = null,
    val erroresCampo: Map<String, String> = emptyMap(),
    val sinConexion: Boolean = false,
    val sincronizando: Boolean = false,
    val pendientes: Long = 0,
    val ultimaSincronizacion: Long? = null,
)

class LaborViewModel(
    private val repositorio: LaborRepository,
    private val sincronizacion: SincronizacionRepository,
    private val sincronizarCaso: SincronizarUseCase,
    private val conexion: ObservadorConexion,
) {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observacion: Job? = null
    private var ownerId: Long? = null
    private var loteId: Long? = null
    private val mutable = MutableStateFlow(LaborUiState())
    val estado = mutable.asStateFlow()

    fun activar(owner: Long) {
        if (ownerId == owner) return
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        ownerId = owner
        loteId = null
        observacion = null
        mutable.value = LaborUiState(cargando = false)
        scope.launch { sincronizacion.observarPendientes(owner).collect { cantidad ->
            mutable.update { it.copy(pendientes = cantidad) }
        } }
        scope.launch { sincronizacion.observarUltimaSincronizacion(owner).collect { instante ->
            mutable.update { it.copy(ultimaSincronizacion = instante) }
        } }
        scope.launch { conexion.cambios().collect { disponible ->
            if (disponible && ownerId == owner) sincronizar()
        } }
    }

    fun abrir(owner: Long, lote: Long) {
        activar(owner)
        if (loteId == lote && observacion?.isActive == true) return
        observacion?.cancel()
        loteId = lote
        mutable.update { it.copy(labores = emptyList(), cargando = true, mensaje = null, erroresCampo = emptyMap()) }
        observacion = scope.launch {
            try {
                repositorio.observar(owner, lote).collect { lista ->
                    mutable.update { it.copy(labores = lista, cargando = false) }
                }
            } catch (e: ErrorApiException) { mostrar(e) }
        }
        sincronizar()
    }

    fun cerrarDetalle() {
        observacion?.cancel()
        observacion = null
        loteId = null
        mutable.update { it.copy(labores = emptyList(), cargando = false) }
    }

    fun sincronizar() {
        val owner = ownerId ?: return
        if (mutable.value.sincronizando) return
        scope.launch {
            if (mutable.value.sincronizando) return@launch
            mutable.update { it.copy(sincronizando = true, mensaje = null, erroresCampo = emptyMap()) }
            try {
                val resumen = sincronizarCaso(owner)
                mutable.update { it.copy(mensaje = "Sincronizado: ${resumen.enviados} enviados, ${resumen.recibidos} recibidos",
                    sinConexion = false) }
            } catch (e: ErrorApiException) {
                mostrar(e)
            } catch (_: Exception) {
                mutable.update { it.copy(mensaje = "No se pudo completar la sincronización") }
            } finally {
                mutable.update { it.copy(sincronizando = false, cargando = false) }
            }
        }
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
