package com.example.meeplekingdom.model

/** Estados posibles de un pedido y sus etiquetas para mostrar en pantalla. */
object OrderStatus {
    const val PENDIENTE_PAGO = "pendiente_pago"
    const val PAGADO = "pagado"
    const val ENVIADO = "enviado"
    const val ENTREGADO = "entregado"
    const val CANCELADO = "cancelado"

    /** Nombre legible del estado. */
    fun etiqueta(estado: String): String = when (estado) {
        PENDIENTE_PAGO -> "Pendiente de pago"
        PAGADO -> "Pagado"
        ENVIADO -> "Enviado"
        ENTREGADO -> "Entregado"
        CANCELADO -> "Cancelado"
        else -> estado
    }
}

/** Producto dentro de un pedido: copia congelada del nombre y precio al momento de comprar. */
data class OrderItem(
    val productId: String = "",
    val nombre: String = "",
    val precio: Double = 0.0,
    val cantidad: Int = 1,
    val imagenUrl: String = ""
)

/**
 * Pedido guardado en la colección `orders`. Cada pedido pertenece a UN solo vendedor:
 * si el carrito mezcla productos de varios vendedores se crea un pedido por vendedor,
 * y todos comparten el mismo [grupoId] (se pagan juntos).
 */
data class Order(
    val id: String = "",
    val grupoId: String = "",
    val compradorId: String = "",
    val compradorNombre: String = "",
    val compradorEmail: String = "",
    val vendedorId: String = "",
    val vendedorNombre: String = "",
    val items: List<OrderItem> = emptyList(),
    val total: Double = 0.0,
    val estado: String = OrderStatus.PENDIENTE_PAGO,
    val direccion: String = "",
    val latitud: Double = 0.0,
    val longitud: Double = 0.0,
    val metodoPago: String = "",
    val pagoRef: String = "",
    val creadoEn: Long = 0L
)
