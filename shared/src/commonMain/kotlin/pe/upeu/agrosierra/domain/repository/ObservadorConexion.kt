package pe.upeu.agrosierra.domain.repository

import kotlinx.coroutines.flow.Flow

interface ObservadorConexion { fun cambios(): Flow<Boolean> }
