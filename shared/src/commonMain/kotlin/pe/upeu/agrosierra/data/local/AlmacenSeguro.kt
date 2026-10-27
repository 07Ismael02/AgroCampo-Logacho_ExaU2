package pe.upeu.agrosierra.data.local

interface AlmacenSeguro {
    fun leerToken(): String?
    fun guardarToken(token: String)
    fun borrarToken()
}
