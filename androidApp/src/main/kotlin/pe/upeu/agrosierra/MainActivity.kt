package pe.upeu.agrosierra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.koin.core.context.GlobalContext
import pe.upeu.agrosierra.data.remote.ApiConfig
import pe.upeu.agrosierra.presentation.sesion.SesionViewModel
import pe.upeu.agrosierra.presentation.listado.LoteViewModel
import pe.upeu.agrosierra.presentation.detalle.LaborViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val koin = GlobalContext.get()
        val viewModel = koin.get<SesionViewModel>()
        val lotes = koin.get<LoteViewModel>()
        val labores = koin.get<LaborViewModel>()
        val configurado = koin.get<ApiConfig>().configurado
        setContent { AgroApp(viewModel, lotes, labores, configurado) }
    }
}
