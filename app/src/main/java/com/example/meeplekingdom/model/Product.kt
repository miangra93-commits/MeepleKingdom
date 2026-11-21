package com.example.meeplekingdom.model

/**
 * Producto (juego de mesa) guardado en la colección `products` de Firestore.
 *
 * @property activo    visible para compradores; lo controla el vendedor.
 * @property bloqueado oculto por moderación; solo lo cambia el administrador.
 * @property imagenUrl URL de descarga de la foto en Firebase Storage.
 */
data class Product(
    val id: String = "",
    val nombre: String = "",
    val descripcion: String = "",
    val categoria: String = "",
    val precio: Double = 0.0,
    val stock: Int = 0,
    val imagenUrl: String = "",
    val vendedorId: String = "",
    val vendedorNombre: String = "",
    val activo: Boolean = true,
    val bloqueado: Boolean = false,
    val creadoEn: Long = 0L
)
