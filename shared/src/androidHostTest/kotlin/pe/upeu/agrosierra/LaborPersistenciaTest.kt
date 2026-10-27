package pe.upeu.agrosierra

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import pe.upeu.agrosierra.data.local.BaseLocal
import pe.upeu.agrosierra.data.local.LaborLocal
import pe.upeu.agrosierra.db.AgroDb
import pe.upeu.agrosierra.domain.model.FiltroLotes
import pe.upeu.agrosierra.domain.model.Labor
import pe.upeu.agrosierra.domain.model.Lote
import pe.upeu.agrosierra.domain.model.OperacionPendiente
import pe.upeu.agrosierra.domain.model.TipoLabor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LaborPersistenciaTest {
    private fun lote() = Lote(1, "L-1", "Lote uno", 2.0, "Papa", "Mantaro", false, 3.0)
    private fun labor(id: Long = 0) = Labor(id, 1, TipoLabor.RIEGO, "2026-10-27", "tecnico", 1.0, "nota", false)

    @Test fun idsNegativosYDatosPersistenAlRecrearRepositorio(): Unit = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            AgroDb.Schema.create(driver)
            val db = AgroDb(driver)
            BaseLocal(db).guardarLote(lote())
            val primera = LaborLocal(db).guardarNueva(7, labor())
            val segunda = LaborLocal(db).guardarNueva(7, labor())
            assertEquals(-1L, primera.id)
            assertEquals(-2L, segunda.id)
            val recreado = LaborLocal(AgroDb(driver))
            assertEquals(setOf(-1L, -2L), recreado.pendientes(7).map { it.labor.id }.toSet())
            assertEquals(2, recreado.observar(7, 1).first().size)
            assertTrue(recreado.observar(8, 1).first().isEmpty())
            assertNull(recreado.obtener(8, -1))
        } finally { driver.close() }
    }

    @Test fun edicionOfflineConservaOperacionYPropietario(): Unit = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            AgroDb.Schema.create(driver)
            val db = AgroDb(driver)
            BaseLocal(db).guardarLote(lote())
            val local = LaborLocal(db)
            val nueva = local.guardarNueva(7, labor())
            local.guardarEdicionPendiente(7, nueva.copy(observacion = "corregida"))
            assertEquals("corregida", local.obtener(7, nueva.id)?.observacion)
            assertEquals(OperacionPendiente.CREAR, local.pendientes(7).single().operacion)
            local.reemplazarDesdeServidor(7, 1, listOf(labor(45)))
            local.guardarEdicionPendiente(7, labor(45).copy(observacion = "jefe"))
            assertEquals(OperacionPendiente.EDITAR, local.pendientes(7).first { it.labor.id == 45L }.operacion)
            assertNull(local.obtener(8, 45))
        } finally { driver.close() }
    }

    @Test fun actualizacionRemotaNoBorraPendientesNiLoteReferenciado(): Unit = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            AgroDb.Schema.create(driver)
            val db = AgroDb(driver)
            val base = BaseLocal(db)
            base.guardarLote(lote())
            val local = LaborLocal(db)
            val pendiente = local.guardarNueva(7, labor())
            local.reemplazarDesdeServidor(7, 1, listOf(labor(44)))
            local.reemplazarDesdeServidor(7, 1, listOf(labor(44), labor(45)))
            assertNotNull(local.obtener(7, pendiente.id))
            assertEquals(3, local.observar(7, 1).first().size)
            base.reemplazarLotesCompleto(emptyList(), 1234L)
            assertNotNull(base.obtenerLote(1)) // Preserva la referencia de la labor.
        } finally { driver.close() }
    }

    @Test fun migracionV1ConservaLaborSinDuenoEnCuarentena() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            driver.execute(null, "CREATE TABLE lote (id INTEGER NOT NULL PRIMARY KEY, codigo TEXT NOT NULL, nombre TEXT NOT NULL, hectareas REAL NOT NULL, cultivo TEXT NOT NULL, valle TEXT NOT NULL, campaniaCerrada INTEGER NOT NULL, dosisMaxima REAL NOT NULL)", 0)
            driver.execute(null, "CREATE TABLE labor (id INTEGER NOT NULL PRIMARY KEY, loteId INTEGER NOT NULL, tipo TEXT NOT NULL, fecha TEXT NOT NULL, responsable TEXT NOT NULL, cantidad REAL NOT NULL, observacion TEXT NOT NULL, pendiente INTEGER NOT NULL)", 0)
            driver.execute(null, "CREATE TABLE sync_meta (clave TEXT NOT NULL PRIMARY KEY, instanteEpochMs INTEGER NOT NULL)", 0)
            driver.execute(null, "INSERT INTO lote VALUES (1,'L-1','Lote uno',2.0,'Papa','Mantaro',0,3.0)", 0)
            driver.execute(null, "INSERT INTO labor VALUES (-1,1,'RIEGO','2026-10-27','sin-propietario',1.0,'antigua',1)", 0)
            AgroDb.Schema.migrate(driver, 1, AgroDb.Schema.version)
            val db = AgroDb(driver)
            assertEquals(1L, db.agroDbQueries.contarLaboresLegadas().executeAsOne())
            assertTrue(LaborLocal(db).pendientes(7).isEmpty())
            assertNotNull(BaseLocal(db).obtenerLote(1))
        } finally { driver.close() }
    }

    @Test fun noSeGuardaLaborSinLoteLocal() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            AgroDb.Schema.create(driver)
            assertFailsWith<IllegalArgumentException> { LaborLocal(AgroDb(driver)).guardarNueva(7, labor()) }
        } finally { driver.close() }
    }
}
