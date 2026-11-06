package ec.edu.pedidosbajocontrol

import java.math.BigDecimal
import java.time.LocalDateTime

data class Product(
    val id: Long? = null,
    val name: String,
    val sku: String,
    val price: BigDecimal,
    val stock: Int = 0,
    val active: Boolean = true,
    val createdAt: LocalDateTime? = null
)
