package ec.edu.pedidosbajocontrol

import java.time.LocalDateTime

data class Customer(
    val id: Long? = null,
    val name: String,
    val email: String,
    val phone: String? = null,
    val active: Boolean = true,
    val createdAt: LocalDateTime? = null
)
