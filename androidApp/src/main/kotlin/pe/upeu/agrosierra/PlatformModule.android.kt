package pe.upeu.agrosierra

import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import pe.upeu.agrosierra.data.local.AlmacenSeguro
import pe.upeu.agrosierra.data.remote.ApiConfig
import pe.upeu.agrosierra.data.remote.crearClienteHttp
import pe.upeu.agrosierra.db.AgroDb

val platformModule = module {
    single { ApiConfig(BuildConfig.AGRO_API_BASE_URL) }
    single<AlmacenSeguro> { AlmacenSeguroAndroid(androidContext()) }
    single { crearClienteHttp(OkHttp.create(), get()) }
    single<AgroDb> { crearBaseLocalAndroid(androidContext()) }
}
