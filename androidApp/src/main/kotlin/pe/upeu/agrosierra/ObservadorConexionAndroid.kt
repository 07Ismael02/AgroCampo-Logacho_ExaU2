package pe.upeu.agrosierra

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import pe.upeu.agrosierra.domain.repository.ObservadorConexion

class ObservadorConexionAndroid(context: Context) : ObservadorConexion {
    private val gestor = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun cambios(): Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(false) }
        }
        val solicitud = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        gestor.registerNetworkCallback(solicitud, callback)
        awaitClose { gestor.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
