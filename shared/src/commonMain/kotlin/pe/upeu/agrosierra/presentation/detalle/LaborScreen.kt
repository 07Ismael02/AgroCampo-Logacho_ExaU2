package pe.upeu.agrosierra.presentation.detalle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pe.upeu.agrosierra.domain.model.Labor
import pe.upeu.agrosierra.domain.model.Lote
import pe.upeu.agrosierra.domain.model.OperacionPendiente
import pe.upeu.agrosierra.domain.model.TipoLabor
import pe.upeu.agrosierra.domain.model.UsuarioAutenticado

@Composable
fun LaborScreen(lote: Lote, usuario: UsuarioAutenticado, viewModel: LaborViewModel) {
    val estado by viewModel.estado.collectAsState()
    var formulario by remember(lote.id, usuario.id) { mutableStateOf(false) }
    var editando by remember(lote.id, usuario.id) { mutableStateOf<Labor?>(null) }
    var eliminar by remember(lote.id, usuario.id) { mutableStateOf<Labor?>(null) }
    LaunchedEffect(lote.id, usuario.id) { viewModel.abrir(usuario.id, lote.id) }
    LaunchedEffect(estado.mensaje) {
        if (estado.mensaje in setOf("Labor registrada", "Labor guardada pendiente de envío",
                "Labor actualizada", "Corrección pendiente de envío")) formulario = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Labores", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if ("LABOR_CREAR" in usuario.privilegios && !lote.campaniaCerrada) {
                Button(onClick = { editando = null; formulario = true }) { Text("Registrar labor") }
            }
            OutlinedButton(onClick = viewModel::sincronizar,
                enabled = !estado.procesando && !estado.sincronizando) { Text("Sincronizar") }
        }
        if (estado.procesando || estado.cargando || estado.sincronizando) CircularProgressIndicator()
        Text("Pendientes de esta cuenta: ${estado.pendientes}")
        Text(estado.ultimaSincronizacion?.let {
            "Última sincronización completa: ${kotlin.time.Instant.fromEpochMilliseconds(it)}"
        } ?: "Labores nunca sincronizadas")
        if (estado.sinConexion) Text("Sin conexión. Se muestran labores guardadas.")
        estado.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (formulario) {
            FormularioLabor(lote, editando, estado.erroresCampo,
                guardar = { labor -> if (editando == null) viewModel.crear(labor) else viewModel.editar(labor) },
                cancelar = { formulario = false })
        }
        if (!estado.cargando && estado.labores.isEmpty()) Text("No hay labores guardadas para este lote")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            estado.labores.forEach { labor ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${labor.tipo} · ${labor.fecha}", style = MaterialTheme.typography.titleMedium)
                        Text("Responsable: ${labor.responsable} · Cantidad: ${labor.cantidad}")
                        if (labor.observacion.isNotBlank()) Text(labor.observacion)
                        if (labor.pendiente) Text(if (labor.operacionPendiente == OperacionPendiente.CREAR_INCIERTO)
                            "Envío incierto: verificar antes de reenviar" else "Pendiente de envío")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val editarPermitido = "LABOR_EDITAR" in usuario.privilegios && !lote.campaniaCerrada &&
                                (usuario.rol == "JEFE_CAMPO" || labor.responsable == usuario.usuario)
                            if (editarPermitido) OutlinedButton(onClick = { editando = labor; formulario = true }) { Text("Editar") }
                            if ("LABOR_ELIMINAR" in usuario.privilegios && labor.id > 0) {
                                OutlinedButton(onClick = { eliminar = labor }) { Text("Eliminar") }
                            }
                        }
                    }
                }
            }
        }
    }
    eliminar?.let { labor -> AlertDialog(onDismissRequest = { eliminar = null },
        title = { Text("Eliminar labor") }, text = { Text("¿Confirma la eliminación de la labor ${labor.id}?") },
        confirmButton = { TextButton(onClick = { viewModel.eliminar(labor.id); eliminar = null }) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { eliminar = null }) { Text("Cancelar") } }) }
}

@Composable
private fun FormularioLabor(lote: Lote, anterior: Labor?, errores: Map<String, String>,
                            guardar: (Labor) -> Unit, cancelar: () -> Unit) {
    var tipo by remember(anterior?.id) { mutableStateOf(anterior?.tipo ?: TipoLabor.RIEGO) }
    var fecha by remember(anterior?.id) { mutableStateOf(anterior?.fecha.orEmpty()) }
    var cantidad by remember(anterior?.id) { mutableStateOf(anterior?.cantidad?.toString().orEmpty()) }
    var observacion by remember(anterior?.id) { mutableStateOf(anterior?.observacion.orEmpty()) }
    var menuTipo by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (anterior == null) "Nueva labor" else "Corregir labor", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { menuTipo = true }) { Text("Tipo: $tipo") }
        DropdownMenu(expanded = menuTipo, onDismissRequest = { menuTipo = false }) {
            TipoLabor.entries.forEach { valor -> DropdownMenuItem(text = { Text(valor.name) },
                onClick = { tipo = valor; menuTipo = false }) }
        }
        OutlinedTextField(fecha, { fecha = it }, label = { Text("Fecha AAAA-MM-DD") },
            isError = errores.containsKey("fecha"))
        errores["fecha"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(cantidad, { cantidad = it }, label = { Text("Cantidad") },
            isError = errores.containsKey("cantidad"))
        errores["cantidad"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(observacion, { observacion = it }, label = { Text("Observación") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                guardar(Labor(anterior?.id ?: 0, lote.id, tipo, fecha, anterior?.responsable.orEmpty(),
                    cantidad.toDoubleOrNull() ?: 0.0, observacion, anterior?.pendiente ?: true))
            }) { Text("Guardar") }
            OutlinedButton(onClick = cancelar) { Text("Cancelar") }
        }
    }
}
