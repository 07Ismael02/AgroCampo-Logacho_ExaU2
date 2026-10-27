package pe.upeu.agrosierra.data.remote.dto

import kotlinx.serialization.Serializable
import pe.upeu.agrosierra.domain.model.Labor
import pe.upeu.agrosierra.domain.model.TipoLabor

@Serializable
data class LaborPeticionDto(
    val loteId: Long,
    val tipo: String,
    val fecha: String,
    val cantidad: Double,
    val observacion: String,
)

@Serializable
data class LaborRespuestaDto(
    val id: Long,
    val loteId: Long,
    val tipo: String,
    val fecha: String,
    val responsable: String,
    val cantidad: Double,
    val observacion: String,
    val pendiente: Boolean,
) {
    fun aDominio() = Labor(id, loteId, TipoLabor.valueOf(tipo), fecha, responsable, cantidad, observacion, pendiente)
}

fun Labor.aPeticionDto() = LaborPeticionDto(loteId, tipo.name, fecha, cantidad, observacion)
