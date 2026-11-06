package ec.edu.pedidosbajocontrol

import java.math.BigDecimal
import java.time.LocalDateTime

data class Order(
    val id: Long? = null,
    val customerId: Long,
    val status: String = "PENDING",
    val total: BigDecimal = BigDecimal.ZERO,
    val notes: String? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
    val items: List<OrderItem> = emptyList()
)

data class OrderItem(
    val id: Long? = null,
    val orderId: Long? = null,
    val productId: Long,
    val quantity: Int,
    val unitPrice: BigDecimal
)
