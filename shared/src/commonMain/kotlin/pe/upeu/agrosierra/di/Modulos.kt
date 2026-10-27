package pe.upeu.agrosierra.di

import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.module
import pe.upeu.agrosierra.data.remote.ApiConfig
import pe.upeu.agrosierra.data.local.InvalidacionesSesion
import pe.upeu.agrosierra.data.remote.SolicitudesApi
import pe.upeu.agrosierra.data.remote.LotesApi
import pe.upeu.agrosierra.data.local.BaseLocal
import pe.upeu.agrosierra.data.local.LaborLocal
import pe.upeu.agrosierra.data.remote.LaboresApi
import pe.upeu.agrosierra.data.repository.LaborRepositorio
import pe.upeu.agrosierra.domain.repository.LaborRepository
import pe.upeu.agrosierra.presentation.detalle.LaborViewModel
import pe.upeu.agrosierra.data.repository.LoteRepositorioLocalFirst
import pe.upeu.agrosierra.domain.repository.LoteRepository
import pe.upeu.agrosierra.presentation.listado.LoteViewModel
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
    single { InvalidacionesSesion() }
    single { SolicitudesApi(get<HttpClient>(), get<ApiConfig>(), get<Json>(), get(), get()) }
    single<AuthRepository> { AuthRepositorioRest(get(), get()) }
    single { BaseLocal(get()) }
    single { LaborLocal(get()) }
    single { LotesApi(get()) }
    single { LaboresApi(get()) }
    single<LoteRepository> { LoteRepositorioLocalFirst(get(), get()) }
    single<LaborRepository> { LaborRepositorio(get(), get(), get(), get(), get()) }
}

val domainModule = module {
    factory { IniciarSesionUseCase(get()) }
    factory { RestaurarSesionUseCase(get()) }
    factory { CerrarSesionUseCase(get()) }
}

val presentationModule = module {
    single { SesionViewModel(get(), get(), get(), get(), get()) }
    single { LoteViewModel(get()) }
    single { LaborViewModel(get()) }
}
