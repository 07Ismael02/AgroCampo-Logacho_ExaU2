package pe.upeu.agrosierra

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

actual fun crearDriverEnMemoria(): SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

actual fun abrirDriverPersistente(nombre: String, nuevo: Boolean): SqlDriver {
    val archivo = File(System.getProperty("java.io.tmpdir"), "agrocampo-prueba-$nombre.db")
    if (nuevo) archivo.delete()
    return JdbcSqliteDriver("jdbc:sqlite:${archivo.absolutePath}")
}
