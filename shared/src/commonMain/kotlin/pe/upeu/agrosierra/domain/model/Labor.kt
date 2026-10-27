package pe.upeu.agrosierra.domain.model

enum class TipoLabor { RIEGO, FUMIGACION, COSECHA }

data class Labor(
    val id: Long,
    val loteId: Long,
    val tipo: TipoLabor,
    val fecha: String,
    val responsable: String,
    val cantidad: Double,
    val observacion: String,
    val pendiente: Boolean,
    val operacionPendiente: OperacionPendiente? = null,
    val motivoRechazo: String? = null,
)

enum class OperacionPendiente { CREAR, CREAR_INCIERTO, EDITAR }
