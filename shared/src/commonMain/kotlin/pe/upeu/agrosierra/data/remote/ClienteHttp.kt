package pe.upeu.agrosierra.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

fun crearClienteHttp(engine: HttpClientEngine, json: Json): HttpClient = HttpClient(engine) {
    install(ContentNegotiation) { json(json) }
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 20_000
        socketTimeoutMillis = 20_000
    }
    expectSuccess = false
    // Sin Logging: nunca se escriben credenciales ni cabeceras Authorization en Logcat.
}
