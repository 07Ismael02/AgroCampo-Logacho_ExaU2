package pe.upeu.agrosierra.data.repository

import kotlinx.coroutines.flow.Flow
import pe.upeu.agrosierra.data.local.BaseLocal
import pe.upeu.agrosierra.data.local.EstadoRed
import pe.upeu.agrosierra.data.local.LaborLocal
import pe.upeu.agrosierra.data.remote.LaboresApi
import pe.upeu.agrosierra.domain.model.ErrorApi
import pe.upeu.agrosierra.domain.model.ErrorApiException
import pe.upeu.agrosierra.domain.model.Labor
import pe.upeu.agrosierra.domain.model.Sesion
import pe.upeu.agrosierra.domain.model.TipoLabor
import pe.upeu.agrosierra.domain.repository.AuthRepository
import pe.upeu.agrosierra.domain.repository.LaborRepository

/** El ID confirmado por /usuarios/me delimita cada lectura y escritura local. */
class LaborRepositorio(
    private val local: LaborLocal,
    private val lotes: BaseLocal,
    private val remoto: LaboresApi,
    private val autenticacion: AuthRepository,
    private val red: EstadoRed,
    private val cola: ColaLabores,
) : LaborRepository {
    override fun observar(ownerId: Long, loteId: Long): Flow<List<Labor>> {
        sesion(ownerId, "LABOR_LEER")
        return local.observar(ownerId, loteId)
    }

    override suspend fun actualizar(ownerId: Long, loteId: Long): Int = cola.exclusivo {
        val actual = sesion(ownerId, "LABOR_LEER")
        val completas = remoto.todos(loteId, actual.token)
        vigente(actual)
        local.reemplazarDesdeServidor(ownerId, loteId, completas)
        completas.size
    }

    override suspend fun crear(ownerId: Long, labor: Labor): Labor = cola.exclusivo {
        val actual = sesion(ownerId, "LABOR_CREAR")
        validarLabor(labor)
        val localNueva = local.guardarNueva(ownerId, labor.copy(responsable = actual.usuario))
        if (!red.hayRed()) return@exclusivo localNueva
        vigente(actual)
        local.marcarCreacionIncierta(ownerId, localNueva.id) // También cubre cierre inesperado tras enviar.
        val confirmada = try {
            remoto.crear(localNueva, actual.token)
        } catch (e: ErrorApiException) {
            if (e.error is ErrorApi.SinConexion || e.error is ErrorApi.TiempoAgotado) {
                return@exclusivo requireNotNull(local.obtener(ownerId, localNueva.id))
            }
            if (e.error.esRechazoDelServidor()) {
                local.marcarCreacionLista(ownerId, localNueva.id)
                local.registrarRechazo(ownerId, localNueva.id, e.error.mensaje)
            }
            throw e
        }
        vigente(actual)
        local.confirmarCreacion(ownerId, localNueva.id, confirmada)
        confirmada
    }

    override suspend fun editar(ownerId: Long, labor: Labor): Labor = cola.exclusivo {
        val actual = sesion(ownerId, "LABOR_EDITAR")
        validarLabor(labor)
        val anterior = local.obtener(ownerId, labor.id)
            ?: throw ErrorApiException(ErrorApi.NoEncontrado("Labor no encontrada localmente"))
        if (anterior.loteId != labor.loteId) {
            throw ErrorApiException(ErrorApi.Validacion("No puede cambiar el lote", mapOf("loteId" to "No puede cambiar el lote")))
        }
        if (labor.id < 0) {
            if (anterior.responsable != actual.usuario) throw ErrorApiException(ErrorApi.SinPrivilegio("Solo puede corregir su labor local"))
            vigente(actual)
            return@exclusivo local.guardarEdicionPendiente(ownerId, labor.copy(responsable = anterior.responsable))
        }
        if (anterior.pendiente) {
            if (actual.rol != "JEFE_CAMPO") throw ErrorApiException(ErrorApi.SinPrivilegio("Esta edición pendiente requiere verificación"))
            vigente(actual)
            return@exclusivo local.guardarEdicionPendiente(ownerId, labor.copy(responsable = anterior.responsable))
        }
        if (!red.hayRed()) {
            if (actual.rol != "JEFE_CAMPO") throw ErrorApiException(ErrorApi.SinConexion)
            vigente(actual)
            return@exclusivo local.guardarEdicionPendiente(ownerId, labor.copy(responsable = anterior.responsable))
        }
        try {
            val confirmada = remoto.editar(labor, actual.token)
            vigente(actual)
            local.confirmarEdicion(ownerId, confirmada)
            confirmada
        } catch (e: ErrorApiException) {
            if ((e.error is ErrorApi.SinConexion || e.error is ErrorApi.TiempoAgotado) && actual.rol == "JEFE_CAMPO") {
                vigente(actual)
                return@exclusivo local.guardarEdicionPendiente(ownerId, labor.copy(responsable = anterior.responsable))
            }
            throw e
        }
    }

    override suspend fun eliminar(ownerId: Long, id: Long) = cola.exclusivo {
        val actual = sesion(ownerId, "LABOR_ELIMINAR")
        if (id < 0 || !red.hayRed()) throw ErrorApiException(ErrorApi.SinConexion)
        remoto.eliminar(id, actual.token)
        vigente(actual)
        local.borrarConfirmada(ownerId, id)
    }

    private fun sesion(ownerId: Long, privilegio: String): Sesion {
        val actual = autenticacion.sesionActual()
        if (actual?.id != ownerId || ownerId <= 0) throw ErrorApiException(ErrorApi.NoAutorizado("Debe iniciar sesión"))
        if (privilegio !in actual.privilegios) throw ErrorApiException(ErrorApi.SinPrivilegio("Privilegio insuficiente"))
        return actual
    }

    private fun vigente(snapshot: Sesion) {
        val actual = autenticacion.sesionActual()
        if (actual !== snapshot) {
            throw ErrorApiException(ErrorApi.NoAutorizado("La sesión cambió durante la operación"))
        }
    }

    private fun validarLabor(labor: Labor) {
        val lote = lotes.obtenerLote(labor.loteId)
            ?: throw ErrorApiException(ErrorApi.NoEncontrado("Lote no disponible localmente"))
        if (lote.campaniaCerrada) throw ErrorApiException(ErrorApi.Conflicto("No se permiten labores en una campaña cerrada"))
        if (labor.fecha.isBlank() || !Regex("\\d{4}-\\d{2}-\\d{2}").matches(labor.fecha)) {
            throw ErrorApiException(ErrorApi.Validacion("Fecha ISO-8601 inválida", mapOf("fecha" to "Use AAAA-MM-DD")))
        }
        if (labor.cantidad <= 0) throw ErrorApiException(ErrorApi.Validacion("Cantidad inválida", mapOf("cantidad" to "Debe ser positiva")))
        if (labor.tipo == TipoLabor.FUMIGACION && labor.cantidad > lote.dosisMaximaPorHectarea * lote.hectareas) {
            throw ErrorApiException(ErrorApi.Validacion("La cantidad supera la dosis máxima del lote",
                mapOf("cantidad" to "Supera la dosis máxima")))
        }
    }
}
