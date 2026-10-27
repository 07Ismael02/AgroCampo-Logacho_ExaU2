package pe.upeu.agrosierra.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

interface ObservadorConexion { fun cambios(): Flow<Boolean> }

/** Una señal por cada paso a "con red", incluida la red ya disponible al empezar a observar. */
fun ObservadorConexion.reconexiones(): Flow<Unit> = cambios().distinctUntilChanged().filter { it }.map { }
