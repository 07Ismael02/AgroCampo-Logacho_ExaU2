package pe.upeu.agrosierra.data.repository

import io.ktor.client.call.body
import kotlinx.coroutines.flow.Flow
import pe.upeu.agrosierra.data.local.EstadoRed
import pe.upeu.agrosierra.data.local.LaborLocal
import pe.upeu.agrosierra.data.remote.LaboresApi
import pe.upeu.agrosierra.data.remote.LotesApi
import pe.upeu.agrosierra.data.remote.SolicitudesApi
import pe.upeu.agrosierra.data.remote.dto.UsuarioActualDto
import pe.upeu.agrosierra.domain.model.ErrorApi
import pe.upeu.agrosierra.domain.model.ErrorApiException
import pe.upeu.agrosierra.domain.model.OperacionPendiente
import pe.upeu.agrosierra.domain.model.ResumenSincronizacion
import pe.upeu.agrosierra.domain.model.Sesion
import pe.upeu.agrosierra.domain.repository.AuthRepository
import pe.upeu.agrosierra.domain.repository.SincronizacionRepository
import kotlin.time.Clock

/** Una cola por proceso; cada envío usa un snapshot de identidad y JWT, nunca el token global mutable. */
class SincronizadorLabores(
    private val local: LaborLocal,
    private val lotesApi: LotesApi,
    private val laboresApi: LaboresApi,
    private val solicitudes: SolicitudesApi,
    private val autenticacion: AuthRepository,
    private val red: EstadoRed,
    private val cola: ColaLabores,
) : SincronizacionRepository {
    override fun observarPendientes(ownerId: Long): Flow<Long> = local.observarCantidadPendiente(ownerId)
    override fun observarUltimaSincronizacion(ownerId: Long): Flow<Long?> = local.observarUltimaSincronizacion(ownerId)

    override suspend fun sincronizar(ownerId: Long): ResumenSincronizacion = cola.exclusivo {
        val sesion = autenticacion.sesionActual()
        if (sesion?.id != ownerId || ownerId <= 0) throw ErrorApiException(ErrorApi.NoAutorizado("Debe iniciar sesión"))
        if (!red.hayRed()) throw ErrorApiException(ErrorApi.SinConexion)

        // Una respuesta de /me confirma conectividad útil, identidad y validez del JWT.
        val identidad = solicitudes.getProtegido("api/v1/usuarios/me", sesion.token)
            .body<UsuarioActualDto>().aDominio()
        vigente(sesion)
        if (identidad.id != ownerId) throw ErrorApiException(ErrorApi.NoAutorizado("La identidad del servidor cambió"))

        var enviados = 0
        for (pendiente in local.pendientes(ownerId)) {
            vigente(sesion)
            when (pendiente.operacion) {
                OperacionPendiente.CREAR_INCIERTO -> throw ErrorApiException(ErrorApi.Conflicto(
                    "Existe un envío incierto. Revíselo en AgroAPI antes de continuar; no se reenviará automáticamente"))
                OperacionPendiente.CREAR -> {
                    if ("LABOR_CREAR" !in sesion.privilegios) throw ErrorApiException(ErrorApi.SinPrivilegio("No puede registrar labores"))
                    // Persistir la incertidumbre antes de salir a la red cubre cierre del proceso o respuesta perdida.
                    local.marcarCreacionIncierta(ownerId, pendiente.labor.id)
                    try {
                        val remota = laboresApi.crear(pendiente.labor, sesion.token)
                        vigente(sesion)
                        if (remota.id <= 0 || remota.loteId != pendiente.labor.loteId || remota.pendiente) {
                            throw ErrorApiException(ErrorApi.Servidor("Respuesta de creación inconsistente"))
                        }
                        local.confirmarCreacion(ownerId, pendiente.labor.id, remota)
                        enviados++
                    } catch (e: ErrorApiException) {
                        // 400/403/404/409 son rechazos explícitos: este POST no creó la labor.
                        if (e.error is ErrorApi.Validacion || e.error is ErrorApi.SinPrivilegio ||
                            e.error is ErrorApi.NoEncontrado || e.error is ErrorApi.Conflicto) {
                            vigente(sesion)
                            local.marcarCreacionLista(ownerId, pendiente.labor.id)
                        }
                        throw e
                    }
                }
                OperacionPendiente.EDITAR -> {
                    if ("LABOR_EDITAR" !in sesion.privilegios) throw ErrorApiException(ErrorApi.SinPrivilegio("No puede editar labores"))
                    val remota = laboresApi.editar(pendiente.labor, sesion.token)
                    vigente(sesion)
                    if (remota.id != pendiente.labor.id || remota.loteId != pendiente.labor.loteId || remota.pendiente) {
                        throw ErrorApiException(ErrorApi.Servidor("Respuesta de edición inconsistente"))
                    }
                    local.confirmarEdicion(ownerId, remota)
                    enviados++
                }
            }
        }

        // Descarga completa en memoria; un fallo de cualquier página deja SQLite intacta en esta etapa.
        vigente(sesion)
        val lotes = lotesApi.todos(sesion.token)
        val labores = linkedMapOf<Long, List<pe.upeu.agrosierra.domain.model.Labor>>()
        for (lote in lotes) {
            vigente(sesion)
            labores[lote.id] = laboresApi.todos(lote.id, sesion.token)
        }
        vigente(sesion)
        val instante = Clock.System.now().toEpochMilliseconds()
        local.aplicarDescargaCompleta(ownerId, lotes, labores, instante)
        ResumenSincronizacion(enviados, labores.values.sumOf { it.size }, lotes.size, instante)
    }

    private fun vigente(snapshot: Sesion) {
        if (autenticacion.sesionActual() !== snapshot) {
            throw ErrorApiException(ErrorApi.NoAutorizado("La sesión cambió durante la sincronización"))
        }
    }
}
