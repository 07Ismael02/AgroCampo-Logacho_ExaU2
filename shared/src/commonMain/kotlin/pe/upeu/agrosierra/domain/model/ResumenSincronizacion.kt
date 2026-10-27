package pe.upeu.agrosierra.domain.model

data class ResumenSincronizacion(
    val enviados: Int,
    val recibidos: Int,
    val lotes: Int,
    val instanteEpochMs: Long,
)
