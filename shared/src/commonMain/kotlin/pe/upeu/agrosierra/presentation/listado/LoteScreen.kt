package pe.upeu.agrosierra.presentation.listado

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pe.upeu.agrosierra.domain.model.Lote
import pe.upeu.agrosierra.domain.model.UsuarioAutenticado
import pe.upeu.agrosierra.presentation.detalle.LaborViewModel
import pe.upeu.agrosierra.presentation.detalle.LaborScreen
import kotlin.time.Instant

@Composable
fun LoteScreen(usuario: UsuarioAutenticado, viewModel: LoteViewModel,
               labores: LaborViewModel, cerrarSesion: () -> Unit) {
    val estado by viewModel.estado.collectAsState()
    var detalleId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(usuario.id) { viewModel.sincronizar() }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("AgroSierra · ${usuario.usuario}", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = cerrarSesion) { Text("Salir") }
        }
        if (detalleId != null) {
            OutlinedButton(onClick = { detalleId = null; viewModel.cerrarDetalle(); labores.cerrarDetalle() }) { Text("Volver a lotes") }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                estado.detalle?.let {
                    DetalleLote(it)
                    if ("LABOR_LEER" in usuario.privilegios) LaborScreen(it, usuario, labores)
                } ?: Text("Cargando detalle…")
                estado.aviso?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            return@Column
        }
        Text("Lotes", style = MaterialTheme.typography.headlineMedium)
        Text(estado.ultimaSincronizacion?.let {
            "Última sincronización: ${Instant.fromEpochMilliseconds(it)}"
        } ?: "Nunca sincronizado")
        if (estado.sinConexion) Text("Sin conexión. Mostrando datos guardados.", color = MaterialTheme.colorScheme.error)
        estado.aviso?.let { Text(it) }
        Button(onClick = viewModel::sincronizar, enabled = !estado.sincronizando) {
            Text(if (estado.sincronizando) "Actualizando…" else "Actualizar")
        }
        val estadoLabores by labores.estado.collectAsState()
        Button(onClick = labores::sincronizar, enabled = !estadoLabores.sincronizando) {
            Text(if (estadoLabores.sincronizando) "Sincronizando labores…" else "Sincronizar labores")
        }
        Text("Labores pendientes: ${estadoLabores.pendientes}")
        Text(estadoLabores.ultimaSincronizacion?.let {
            "Labores sincronizadas: ${Instant.fromEpochMilliseconds(it)}"
        } ?: "Labores nunca sincronizadas")
        estadoLabores.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (estado.sincronizando) CircularProgressIndicator()
        OutlinedTextField(value = estado.filtro.busqueda, onValueChange = viewModel::buscar,
            label = { Text("Buscar código o nombre") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            var menuValle by remember { mutableStateOf(false) }
            var menuEstado by remember { mutableStateOf(false) }
            Column {
                OutlinedButton(onClick = { menuValle = true }) { Text(estado.filtro.valle ?: "Todos los valles") }
                DropdownMenu(expanded = menuValle, onDismissRequest = { menuValle = false }) {
                    DropdownMenuItem(text = { Text("Todos los valles") }, onClick = {
                        viewModel.filtrarValle(null); menuValle = false
                    })
                    estado.valles.forEach { valle -> DropdownMenuItem(text = { Text(valle) }, onClick = {
                        viewModel.filtrarValle(valle); menuValle = false
                    }) }
                }
            }
            Column {
                OutlinedButton(onClick = { menuEstado = true }) {
                    Text(when (estado.filtro.campaniaCerrada) { true -> "Cerradas"; false -> "Abiertas"; null -> "Todos" })
                }
                DropdownMenu(expanded = menuEstado, onDismissRequest = { menuEstado = false }) {
                    listOf(null to "Todos", false to "Abiertas", true to "Cerradas").forEach { (valor, titulo) ->
                        DropdownMenuItem(text = { Text(titulo) }, onClick = {
                            viewModel.filtrarEstado(valor); menuEstado = false
                        })
                    }
                }
            }
        }
        when {
            estado.cargandoLocal -> CircularProgressIndicator()
            estado.totalLocales == 0 && estado.ultimaSincronizacion == null -> Text("No hay datos guardados todavía. Conéctese y pulse Actualizar.")
            estado.lotes.isEmpty() -> Text("No hay lotes para estos filtros.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(estado.lotes, key = Lote::id) { lote ->
                    Card(Modifier.fillMaxWidth().clickable {
                        detalleId = lote.id
                        viewModel.abrirDetalle(lote.id)
                    }) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${lote.codigo} · ${lote.nombre}", style = MaterialTheme.typography.titleMedium)
                            Text("${lote.cultivo} · ${lote.valle} · ${lote.hectareas} ha")
                            Text(if (lote.campaniaCerrada) "Campaña cerrada" else "Campaña abierta")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetalleLote(lote: Lote) {
    Text("${lote.codigo} · ${lote.nombre}", style = MaterialTheme.typography.headlineSmall)
    Text("Cultivo: ${lote.cultivo}")
    Text("Valle: ${lote.valle}")
    Text("Hectáreas: ${lote.hectareas}")
    Text("Dosis máxima por hectárea: ${lote.dosisMaximaPorHectarea}")
    Text(if (lote.campaniaCerrada) "Campaña cerrada" else "Campaña abierta")
}
