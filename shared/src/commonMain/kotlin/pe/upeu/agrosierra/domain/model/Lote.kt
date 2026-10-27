package pe.upeu.agrosierra.domain.model

data class Lote(
    val id: Long,
    val codigo: String,
    val nombre: String,
    val hectareas: Double,
    val cultivo: String,
    val valle: String,
    val campaniaCerrada: Boolean,
    val dosisMaximaPorHectarea: Double,
)

data class FiltroLotes(
    val busqueda: String = "",
    val valle: String? = null,
    val campaniaCerrada: Boolean? = null,
)
