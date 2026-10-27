package pe.upeu.agrosierra.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pe.upeu.agrosierra.db.AgroDb
import pe.upeu.agrosierra.domain.model.FiltroLotes
import pe.upeu.agrosierra.domain.model.Lote
import pe.upeu.agrosierra.Lote as FilaLote

class BaseLocal(private val db: AgroDb) {
    private val consultas = db.agroDbQueries

    fun observarLotes(filtro: FiltroLotes): Flow<List<Lote>> = consultas.filtrarLotes(
        valle = filtro.valle,
        estado = filtro.campaniaCerrada?.let { if (it) 1L else 0L },
        busqueda = filtro.busqueda.trim(),
    ).asFlow().mapToList(Dispatchers.Default).map { filas -> filas.map(FilaLote::aDominio) }

    fun observarUltimaSincronizacion(): Flow<Long?> = consultas.obtenerUltimaSincronizacion()
        .asFlow().mapToOneOrNull(Dispatchers.Default)

    fun obtenerLote(id: Long): Lote? = consultas.obtenerLote(id).executeAsOneOrNull()?.aDominio()

    fun reemplazarLotesCompleto(lotes: List<Lote>, instanteEpochMs: Long) {
        db.transaction {
            consultas.borrarLotes()
            val ids = mutableSetOf<Long>()
            lotes.forEach {
                check(ids.add(it.id)) { "ID de lote duplicado en descarga" }
                insertarLote(it)
            }
            consultas.guardarUltimaSincronizacion(instanteEpochMs)
        }
    }

    fun guardarLote(lote: Lote) = db.transaction { insertarLote(lote) }

    private fun insertarLote(lote: Lote) {
        consultas.insertarLote(lote.id, lote.codigo, lote.nombre, lote.hectareas, lote.cultivo,
            lote.valle, if (lote.campaniaCerrada) 1L else 0L, lote.dosisMaximaPorHectarea)
    }
}

private fun FilaLote.aDominio() = Lote(id, codigo, nombre, hectareas, cultivo, valle,
    campaniaCerrada != 0L, dosisMaxima)
