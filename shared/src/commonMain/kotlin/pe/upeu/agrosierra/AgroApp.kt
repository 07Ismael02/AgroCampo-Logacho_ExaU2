package pe.upeu.agrosierra

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import pe.upeu.agrosierra.presentation.sesion.SesionUiState
import pe.upeu.agrosierra.presentation.sesion.SesionViewModel

@Composable
fun AgroApp(viewModel: SesionViewModel, configurado: Boolean) {
    val estado by viewModel.estado.collectAsState()
    MaterialTheme {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            when (val actual = estado) {
                SesionUiState.Cargando -> CircularProgressIndicator()
                is SesionUiState.Login -> {
                    var usuario by remember { mutableStateOf("") }
                    var contrasena by remember { mutableStateOf("") }
                    Text("AgroSierra", style = MaterialTheme.typography.headlineMedium)
                    OutlinedTextField(usuario, { usuario = it }, label = { Text("Usuario") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(contrasena, { contrasena = it }, label = { Text("Contraseña") },
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    actual.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (!configurado) Text("Configure la URL de AgroAPI en local.properties")
                    Button(onClick = { viewModel.iniciar(usuario, contrasena) }, enabled = configurado) { Text("Iniciar sesión") }
                }
                is SesionUiState.Autenticado -> {
                    Text("Sesión iniciada", style = MaterialTheme.typography.headlineMedium)
                    Text("Usuario: ${actual.usuario.usuario}")
                    Text("Rol: ${actual.usuario.rol}")
                    Text("Privilegios: ${actual.usuario.privilegios.sorted().joinToString()}")
                    Button(onClick = viewModel::cerrar) { Text("Cerrar sesión") }
                }
            }
        }
    }
}
