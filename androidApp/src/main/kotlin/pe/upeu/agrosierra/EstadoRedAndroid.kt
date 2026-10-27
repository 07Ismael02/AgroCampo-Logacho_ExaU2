package pe.upeu.agrosierra

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import pe.upeu.agrosierra.data.local.EstadoRed

class EstadoRedAndroid(private val context: Context) : EstadoRed {
    override fun hayRed(): Boolean {
        val gestor = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activa = gestor.activeNetwork ?: return false
        return gestor.getNetworkCapabilities(activa)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
}
