package pe.upeu.agrosierra.data.remote

class ApiConfig(baseUrl: String) {
    val baseUrl: String = baseUrl.trim().trimEnd('/')
    val configurado: Boolean get() = baseUrl.startsWith("http://") || baseUrl.startsWith("https://")
    fun ruta(path: String): String {
        check(configurado) { "Configure agro.api.baseUrl en local.properties" }
        return "$baseUrl/${path.trimStart('/')}"
    }
}
