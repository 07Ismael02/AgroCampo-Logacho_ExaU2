package pe.upeu.agrosierra.di

import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.module
import pe.upeu.agrosierra.data.remote.ApiConfig
import pe.upeu.agrosierra.data.remote.SolicitudesApi
import pe.upeu.agrosierra.data.repository.AuthRepositorioRest
import pe.upeu.agrosierra.domain.repository.AuthRepository
import pe.upeu.agrosierra.domain.usecase.CerrarSesionUseCase
import pe.upeu.agrosierra.domain.usecase.IniciarSesionUseCase
import pe.upeu.agrosierra.domain.usecase.RestaurarSesionUseCase
import pe.upeu.agrosierra.presentation.sesion.SesionViewModel

val redModule = module {
    single { Json { ignoreUnknownKeys = true } }
}

val dataModule = module {
    single { SolicitudesApi(get<HttpClient>(), get<ApiConfig>(), get<Json>(), get()) }
    single<AuthRepository> { AuthRepositorioRest(get(), get()) }
}

val domainModule = module {
    factory { IniciarSesionUseCase(get()) }
    factory { RestaurarSesionUseCase(get()) }
    factory { CerrarSesionUseCase(get()) }
}

val presentationModule = module {
    single { SesionViewModel(get(), get(), get()) }
}
