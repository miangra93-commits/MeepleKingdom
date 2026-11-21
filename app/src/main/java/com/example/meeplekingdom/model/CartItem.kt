package com.example.meeplekingdom.model

/**
 * Línea del carrito, guardada en `users/{uid}/cart/{productId}` en Firestore.
 * Guarda una copia de los datos del producto para pintar el carrito sin consultas adicionales.
 * El precio y el stock reales se vuelven a validar al confirmar el pedido.
 */
data class CartItem(
    val productId: String = "",
    val nombre: String = "",
    val precio: Double = 0.0,
    val imagenUrl: String = "",
    val vendedorId: String = "",
    val vendedorNombre: String = "",
    val cantidad: Int = 1,
    val stock: Int = 0
)
