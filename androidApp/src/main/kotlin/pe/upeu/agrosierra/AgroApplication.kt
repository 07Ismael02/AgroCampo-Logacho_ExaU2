package pe.upeu.agrosierra

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import pe.upeu.agrosierra.di.dataModule
import pe.upeu.agrosierra.di.domainModule
import pe.upeu.agrosierra.di.presentationModule
import pe.upeu.agrosierra.di.redModule

class AgroApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@AgroApplication)
            modules(platformModule, redModule, dataModule, domainModule, presentationModule)
        }
    }
}
