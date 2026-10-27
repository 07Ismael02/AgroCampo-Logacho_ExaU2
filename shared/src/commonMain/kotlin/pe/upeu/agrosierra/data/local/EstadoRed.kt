package pe.upeu.agrosierra.data.local

/** false garantiza que no se intentará enviar; true no garantiza que AgroAPI responda. */
fun interface EstadoRed { fun hayRed(): Boolean }
