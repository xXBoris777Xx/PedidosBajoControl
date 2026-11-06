package ec.edu.pedidosbajocontrol

import io.r2dbc.spi.Readable
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.LocalDateTime

@RestController
@RequestMapping("/api")
class CustomerController(private val databaseClient: DatabaseClient) {

    @GetMapping("/customers")
    fun listCustomers(): Flux<Customer> =
        databaseClient.sql("SELECT * FROM customers ORDER BY id")
            .map { row -> toCustomer(row) }
            .all()

    @PostMapping("/customers")
    fun createCustomer(@RequestBody request: CustomerRequest): Mono<Customer> {
        val name = requireNotBlank(request.name, "name")
        val email = requireNotBlank(request.email, "email")
        return databaseClient.sql(
            "INSERT INTO customers (name, email, phone, active) VALUES (:name, :email, :phone, :active)"
        )
            .bind("name", name)
            .bind("email", email)
            .bind("phone", request.phone ?: "")
            .bind("active", request.active)
            .fetch()
            .rowsUpdated()
            .flatMap {
                databaseClient.sql("SELECT * FROM customers WHERE email = :email ORDER BY id DESC LIMIT 1")
                    .bind("email", email)
                    .map { row -> toCustomer(row) }
                    .one()
            }
    }
}

@RestController
@RequestMapping("/api")
class ProductController(private val databaseClient: DatabaseClient) {

    @GetMapping("/products")
    fun listProducts(): Flux<Product> =
        databaseClient.sql("SELECT * FROM products ORDER BY id")
            .map { row -> toProduct(row) }
            .all()

    @PostMapping("/products")
    fun createProduct(@RequestBody request: ProductRequest): Mono<Product> {
        val name = requireNotBlank(request.name, "name")
        val sku = requireNotBlank(request.sku, "sku")
        return databaseClient.sql(
            "INSERT INTO products (name, sku, price, stock, active) VALUES (:name, :sku, :price, :stock, :active)"
        )
            .bind("name", name)
            .bind("sku", sku)
            .bind("price", request.price)
            .bind("stock", request.stock)
            .bind("active", request.active)
            .fetch()
            .rowsUpdated()
            .flatMap {
                databaseClient.sql("SELECT * FROM products WHERE sku = :sku ORDER BY id DESC LIMIT 1")
                    .bind("sku", sku)
                    .map { row -> toProduct(row) }
                    .one()
            }
    }
}

@RestController
@RequestMapping("/api")
class OrderController(private val databaseClient: DatabaseClient) {

    @GetMapping("/orders")
    fun listOrders(): Flux<Order> =
        databaseClient.sql("SELECT * FROM customer_orders ORDER BY id")
            .map { row -> toOrder(row) }
            .all()

    @PostMapping("/orders")
    fun createOrder(@RequestBody request: CreateOrderRequest): Mono<Order> {
        val customerId = requirePositive(request.customerId, "customerId")
        val total = request.items.fold(BigDecimal.ZERO) { acc, item ->
            acc + item.unitPrice.multiply(BigDecimal(item.quantity))
        }

        return databaseClient.sql(
            "INSERT INTO customer_orders (customer_id, status, total, notes) VALUES (:customerId, :status, :total, :notes)"
        )
            .bind("customerId", customerId)
            .bind("status", "PENDING")
            .bind("total", total)
            .bind("notes", request.notes ?: "")
            .fetch()
            .rowsUpdated()
            .flatMap {
                databaseClient.sql("SELECT * FROM customer_orders WHERE customer_id = :customerId ORDER BY id DESC LIMIT 1")
                    .bind("customerId", customerId)
                    .map { row -> toOrder(row) }
                    .one()
            }
            .flatMap { order ->
                val orderId = requirePositive(order.id, "order.id")
                val items = request.items.map { item ->
                    OrderItem(
                        productId = requirePositive(item.productId, "productId"),
                        quantity = requirePositive(item.quantity, "quantity"),
                        unitPrice = item.unitPrice
                    )
                }

                Flux.fromIterable(items)
                    .flatMap { item ->
                        databaseClient.sql(
                            "INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES (:orderId, :productId, :quantity, :unitPrice)"
                        )
                            .bind("orderId", orderId)
                            .bind("productId", item.productId)
                            .bind("quantity", item.quantity)
                            .bind("unitPrice", item.unitPrice)
                            .then()
                    }
                    .then(Mono.just(order.copy(items = items.map { it.copy(orderId = orderId) })))
            }
    }
}

data class CustomerRequest(
    val name: String,
    val email: String,
    val phone: String? = null,
    val active: Boolean = true
)

data class ProductRequest(
    val name: String,
    val sku: String,
    val price: BigDecimal,
    val stock: Int = 0,
    val active: Boolean = true
)

data class CreateOrderRequest(
    val customerId: Long,
    val notes: String? = null,
    val items: List<OrderItemRequest>
)

data class OrderItemRequest(
    val productId: Long,
    val quantity: Int,
    val unitPrice: BigDecimal
)

private fun requireNotBlank(value: String?, fieldName: String): String {
    return value?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("$fieldName is required")
}

private fun requirePositive(value: Long?, fieldName: String): Long {
    return value ?: throw IllegalArgumentException("$fieldName is required")
}

private fun requirePositive(value: Int?, fieldName: String): Int {
    return value ?: throw IllegalArgumentException("$fieldName is required")
}

private fun toCustomer(row: Readable): Customer = Customer(
    id = row.get("id") as? Long,
    name = row.get("name") as? String ?: "",
    email = row.get("email") as? String ?: "",
    phone = row.get("phone") as? String,
    active = row.get("active") as? Boolean ?: true,
    createdAt = row.get("created_at") as? LocalDateTime
)

private fun toProduct(row: Readable): Product = Product(
    id = row.get("id") as? Long,
    name = row.get("name") as? String ?: "",
    sku = row.get("sku") as? String ?: "",
    price = row.get("price") as? BigDecimal ?: BigDecimal.ZERO,
    stock = row.get("stock") as? Int ?: 0,
    active = row.get("active") as? Boolean ?: true,
    createdAt = row.get("created_at") as? LocalDateTime
)

private fun toOrder(row: Readable): Order = Order(
    id = row.get("id") as? Long,
    customerId = row.get("customer_id") as? Long ?: 0L,
    status = row.get("status") as? String ?: "PENDING",
    total = row.get("total") as? BigDecimal ?: BigDecimal.ZERO,
    notes = row.get("notes") as? String,
    createdAt = row.get("created_at") as? LocalDateTime,
    updatedAt = row.get("updated_at") as? LocalDateTime,
    items = emptyList()
)
