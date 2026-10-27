package pe.upeu.agrosierra.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pe.upeu.agrosierra.db.AgroDb
import pe.upeu.agrosierra.domain.model.Labor
import pe.upeu.agrosierra.domain.model.Lote
import pe.upeu.agrosierra.domain.model.OperacionPendiente
import pe.upeu.agrosierra.domain.model.TipoLabor
import kotlin.time.Clock
import pe.upeu.agrosierra.Labor as FilaLabor

data class LaborPorEnviar(val labor: Labor, val operacion: OperacionPendiente)

class LaborLocal(private val db: AgroDb) {
    private val q = db.agroDbQueries

    fun observar(ownerId: Long, loteId: Long): Flow<List<Labor>> =
        q.observarLaboresPorLote(ownerId, loteId).asFlow().mapToList(Dispatchers.Default)
            .map { filas -> filas.map(FilaLabor::aDominio) }

    fun obtener(ownerId: Long, id: Long): Labor? = q.obtenerLabor(ownerId, id).executeAsOneOrNull()?.aDominio()

    fun pendientes(ownerId: Long): List<LaborPorEnviar> = q.listarPendientes(ownerId).executeAsList().map { fila ->
        LaborPorEnviar(fila.aDominio(), requireNotNull(fila.operacionPendiente).let(OperacionPendiente::valueOf))
    }

    fun observarCantidadPendiente(ownerId: Long): Flow<Long> =
        q.contarPendientes(ownerId).asFlow().mapToOne(Dispatchers.Default)

    fun observarUltimaSincronizacion(ownerId: Long): Flow<Long?> =
        q.observarUltimaSincronizacionLabor(ownerId.toString()).asFlow().mapToOneOrNull(Dispatchers.Default)

    fun guardarNueva(ownerId: Long, labor: Labor): Labor {
        require(ownerId > 0 && labor.loteId > 0)
        var guardada: Labor? = null
        db.transaction {
            requireNotNull(q.obtenerLote(labor.loteId).executeAsOneOrNull()) { "Lote no disponible localmente" }
            val siguiente = minOf(-1L, q.siguienteIdLocal(ownerId).executeAsOne() - 1L)
            val nueva = labor.copy(id = siguiente, pendiente = true, operacionPendiente = OperacionPendiente.CREAR)
            insertar(ownerId, nueva, OperacionPendiente.CREAR, Clock.System.now().toEpochMilliseconds())
            guardada = nueva
        }
        return requireNotNull(guardada)
    }

    fun guardarEdicionPendiente(ownerId: Long, labor: Labor): Labor {
        require(ownerId > 0)
        var guardada: Labor? = null
        db.transaction {
            val previa = requireNotNull(q.obtenerLabor(ownerId, labor.id).executeAsOneOrNull())
            require(previa.loteId == labor.loteId) { "No puede cambiar el lote" }
            val tipoOperacion = if (labor.id < 0) {
                if (previa.operacionPendiente == OperacionPendiente.CREAR_INCIERTO.name)
                    OperacionPendiente.CREAR_INCIERTO else OperacionPendiente.CREAR
            } else OperacionPendiente.EDITAR
            val actualizada = labor.copy(pendiente = true, operacionPendiente = tipoOperacion)
            insertar(ownerId, actualizada, tipoOperacion, previa.creadoEpochMs ?: Clock.System.now().toEpochMilliseconds())
            guardada = actualizada
        }
        return requireNotNull(guardada)
    }

    fun marcarCreacionIncierta(ownerId: Long, id: Long) {
        db.transaction {
            val previa = requireNotNull(q.obtenerLabor(ownerId, id).executeAsOneOrNull())
            require(id < 0 && previa.operacionPendiente == OperacionPendiente.CREAR.name)
            insertar(ownerId, previa.aDominio(), OperacionPendiente.CREAR_INCIERTO, previa.creadoEpochMs)
        }
    }

    fun marcarCreacionLista(ownerId: Long, id: Long) {
        db.transaction {
            val previa = requireNotNull(q.obtenerLabor(ownerId, id).executeAsOneOrNull())
            require(id < 0 && previa.operacionPendiente == OperacionPendiente.CREAR_INCIERTO.name)
            insertar(ownerId, previa.aDominio(), OperacionPendiente.CREAR, previa.creadoEpochMs)
        }
    }

    /** Conserva la fila pendiente y deja visible por qué el servidor la rechazó. */
    fun registrarRechazo(ownerId: Long, id: Long, motivo: String) = q.registrarRechazo(motivo, ownerId, id)

    fun observarInciertos(ownerId: Long): Flow<Long> =
        q.contarInciertos(ownerId).asFlow().mapToOne(Dispatchers.Default)

    fun observarRechazadas(ownerId: Long): Flow<Long> =
        q.contarRechazadas(ownerId).asFlow().mapToOne(Dispatchers.Default)

    /** El usuario comprobó en AgroAPI que la labor sí se creó: la copia remota llegará con la descarga. */
    fun descartarIncierta(ownerId: Long, id: Long) {
        db.transaction {
            val previa = requireNotNull(q.obtenerLabor(ownerId, id).executeAsOneOrNull())
            require(id < 0 && previa.operacionPendiente == OperacionPendiente.CREAR_INCIERTO.name)
            q.borrarLabor(ownerId, id)
        }
    }

    /** Aplica una descarga completa solo después de recibir y verificar todas las páginas. */
    fun aplicarDescargaCompleta(ownerId: Long, lotes: List<Lote>, labores: Map<Long, List<Labor>>, instante: Long) {
        require(ownerId > 0 && lotes.map(Lote::id).toSet().size == lotes.size)
        require(labores.keys == lotes.map(Lote::id).toSet())
        val ids = mutableSetOf<Long>()
        labores.forEach { (loteId, filas) -> filas.forEach { labor ->
            require(labor.id > 0 && labor.loteId == loteId && ids.add(labor.id))
        } }
        db.transaction {
            val pendientes = q.listarPendientes(ownerId).executeAsList().map(FilaLabor::id).toSet()
            q.borrarSincronizadasDeCuenta(ownerId)
            q.borrarLotes() // Conservará los lotes referenciados por pendientes o filas legadas.
            lotes.forEach { lote ->
                q.insertarLote(lote.id, lote.codigo, lote.nombre, lote.hectareas,
                    lote.cultivo, lote.valle, if (lote.campaniaCerrada) 1L else 0L, lote.dosisMaximaPorHectarea)
                q.actualizarLote(lote.codigo, lote.nombre, lote.hectareas, lote.cultivo,
                    lote.valle, if (lote.campaniaCerrada) 1L else 0L, lote.dosisMaximaPorHectarea, lote.id)
            }
            labores.values.flatten().forEach { labor ->
                if (labor.id !in pendientes) insertar(ownerId, labor.copy(pendiente = false), null, null)
            }
            q.guardarUltimaSincronizacion(instante)
            q.guardarUltimaSincronizacionLabor(ownerId.toString(), instante)
        }
    }

    fun reemplazarDesdeServidor(ownerId: Long, loteId: Long, remotas: List<Labor>) {
        db.transaction {
            requireNotNull(q.obtenerLote(loteId).executeAsOneOrNull())
            val pendientes = q.listarPendientes(ownerId).executeAsList().map(FilaLabor::id).toSet()
            q.borrarSincronizadasDelLote(ownerId, loteId)
            remotas.forEach { labor ->
                require(labor.id > 0 && labor.loteId == loteId)
                if (labor.id !in pendientes) insertar(ownerId, labor.copy(pendiente = false), null, null)
            }
        }
    }

    fun confirmarCreacion(ownerId: Long, idLocal: Long, remota: Labor) {
        require(idLocal < 0 && remota.id > 0)
        db.transaction {
            val previa = requireNotNull(q.obtenerLabor(ownerId, idLocal).executeAsOneOrNull())
            require(previa.operacionPendiente in setOf(OperacionPendiente.CREAR.name,
                OperacionPendiente.CREAR_INCIERTO.name) && previa.loteId == remota.loteId)
            require(q.obtenerLabor(ownerId, remota.id).executeAsOneOrNull() == null) {
                "El ID remoto ya existe localmente; se conserva la fila pendiente para revisión"
            }
            q.borrarLabor(ownerId, idLocal)
            insertar(ownerId, remota.copy(pendiente = false), null, null)
        }
    }

    fun confirmarEdicion(ownerId: Long, remota: Labor) {
        require(remota.id > 0)
        db.transaction {
            requireNotNull(q.obtenerLabor(ownerId, remota.id).executeAsOneOrNull())
            insertar(ownerId, remota.copy(pendiente = false), null, null)
        }
    }

    fun borrarConfirmada(ownerId: Long, id: Long) = db.transaction { q.borrarLabor(ownerId, id) }

    private fun insertar(ownerId: Long, labor: Labor, operacion: OperacionPendiente?, creadoEpochMs: Long?) {
        q.insertarLabor(ownerId, labor.id, labor.loteId, labor.tipo.name, labor.fecha,
            labor.responsable, labor.cantidad, labor.observacion, if (labor.pendiente) 1L else 0L,
            operacion?.name, creadoEpochMs)
    }
}

private fun FilaLabor.aDominio() = Labor(id, loteId, TipoLabor.valueOf(tipo), fecha, responsable,
    cantidad, observacion, pendiente != 0L, operacionPendiente?.let(OperacionPendiente::valueOf), motivoRechazo)
