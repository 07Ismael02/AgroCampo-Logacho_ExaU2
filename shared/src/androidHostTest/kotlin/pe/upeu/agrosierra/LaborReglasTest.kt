package pe.upeu.agrosierra

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import pe.upeu.agrosierra.data.local.AlmacenSeguro
import pe.upeu.agrosierra.data.local.BaseLocal
import pe.upeu.agrosierra.data.local.EstadoRed
import pe.upeu.agrosierra.data.local.LaborLocal
import pe.upeu.agrosierra.data.remote.ApiConfig
import pe.upeu.agrosierra.data.remote.LaboresApi
import pe.upeu.agrosierra.data.remote.SolicitudesApi
import pe.upeu.agrosierra.data.remote.crearClienteHttp
import pe.upeu.agrosierra.data.repository.LaborRepositorio
import pe.upeu.agrosierra.db.AgroDb
import pe.upeu.agrosierra.domain.model.ErrorApi
import pe.upeu.agrosierra.domain.model.ErrorApiException
import pe.upeu.agrosierra.domain.model.Labor
import pe.upeu.agrosierra.domain.model.Lote
import pe.upeu.agrosierra.domain.model.OperacionPendiente
import pe.upeu.agrosierra.domain.model.Sesion
import pe.upeu.agrosierra.domain.model.TipoLabor
import pe.upeu.agrosierra.domain.model.UsuarioAutenticado
import pe.upeu.agrosierra.domain.repository.AuthRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LaborReglasTest {
    private fun labor(tipo: TipoLabor = TipoLabor.RIEGO, cantidad: Double = 1.0) =
        Labor(0, 1, tipo, "2026-10-27", "", cantidad, "nota", false)

    private fun entorno(rol: String, privilegios: Set<String>, cerrada: Boolean = false): Entorno {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AgroDb.Schema.create(driver)
        val db = AgroDb(driver)
        val base = BaseLocal(db)
        base.guardarLote(Lote(1, "L-1", "Lote", 2.0, "Papa", "Mantaro", cerrada, 3.0))
        val auth = AutenticacionPrueba(Sesion("token-A", "tecnico", rol, privilegios, 7))
        val almacen = object : AlmacenSeguro {
            override fun leerToken() = "token-A"
            override fun guardarToken(token: String) = Unit
            override fun borrarToken() = Unit
        }
        val engine = MockEngine { error("No debe ocurrir una solicitud HTTP en esta prueba") }
        val json = Json { ignoreUnknownKeys = true }
        val api = LaboresApi(SolicitudesApi(crearClienteHttp(engine, json), ApiConfig("http://test.local"), json, almacen))
        val local = LaborLocal(db)
        return Entorno(driver, local, auth, LaborRepositorio(local, base, api, auth, object : EstadoRed {
            override fun hayRed() = false
        }))
    }

    @Test fun creacionOfflineYEdicionPropiaConservanPendiente(): Unit = runBlocking {
        val e = entorno("TECNICO", setOf("LABOR_LEER", "LABOR_CREAR", "LABOR_EDITAR"))
        try {
            val nueva = e.repo.crear(7, labor())
            assertEquals(-1L, nueva.id)
            assertTrue(nueva.pendiente)
            val editada = e.repo.editar(7, nueva.copy(observacion = "corregida"))
            assertEquals("corregida", editada.observacion)
            assertEquals(OperacionPendiente.CREAR, e.local.pendientes(7).single().operacion)
            e.auth.actual = Sesion("token-B", "otro", "TECNICO", setOf("LABOR_LEER"), 8)
            assertNull(e.local.obtener(8, nueva.id))
            assertIs<ErrorApi.NoAutorizado>(assertFailsWith<ErrorApiException> { e.repo.crear(7, labor()) }.error)
        } finally { e.driver.close() }
    }

    @Test fun privilegiosYReglasImpidenEscriturasInvalidas(): Unit = runBlocking {
        val admin = entorno("ADMINISTRADOR", setOf("LABOR_LEER"))
        val jefe = entorno("JEFE_CAMPO", setOf("LABOR_LEER", "LABOR_CREAR", "LABOR_EDITAR", "LABOR_ELIMINAR"))
        val cerrada = entorno("TECNICO", setOf("LABOR_CREAR"), cerrada = true)
        try {
            assertIs<ErrorApi.SinPrivilegio>(assertFailsWith<ErrorApiException> { admin.repo.crear(7, labor()) }.error)
            val dosis = assertFailsWith<ErrorApiException> { jefe.repo.crear(7, labor(TipoLabor.FUMIGACION, 7.0)) }.error
            assertEquals("Supera la dosis máxima", (dosis as ErrorApi.Validacion).campos["cantidad"])
            assertIs<ErrorApi.Conflicto>(assertFailsWith<ErrorApiException> { cerrada.repo.crear(7, labor()) }.error)
            assertTrue(jefe.repo.crear(7, labor()).pendiente)
        } finally { admin.driver.close(); jefe.driver.close(); cerrada.driver.close() }
    }

    private data class Entorno(
        val driver: JdbcSqliteDriver,
        val local: LaborLocal,
        val auth: AutenticacionPrueba,
        val repo: LaborRepositorio,
    )

    private class AutenticacionPrueba(var actual: Sesion?) : AuthRepository {
        override suspend fun iniciarSesion(usuario: String, contrasena: String): UsuarioAutenticado = error("No usado")
        override suspend fun restaurarSesion(): UsuarioAutenticado? = error("No usado")
        override suspend fun cerrarSesion() { actual = null }
        override fun sesionActual() = actual
    }
}
