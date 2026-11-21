package com.example.meeplekingdom.data

import com.example.meeplekingdom.model.CartItem
import com.example.meeplekingdom.model.Product
import com.example.meeplekingdom.util.ErrorMapper
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * CRUD del carrito de compras. Cada comprador tiene su carrito en la subcolección
 * `users/{uid}/cart`; el id de cada documento es el id del producto.
 */
object CartRepository {
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    /** Referencia a la subcolección del carrito del usuario [uid]. */
    private fun cart(uid: String): CollectionReference =
        db.collection("users").document(uid).collection("cart")

    /** READ: escucha el carrito en tiempo real (se actualiza al agregar, cambiar o quitar). */
    fun listen(
        uid: String, onChange: (List<CartItem>) -> Unit, onError: (String) -> Unit
    ): ListenerRegistration =
        cart(uid).addSnapshotListener { snapshot, e ->
            if (e != null) {
                onError(ErrorMapper.message(e))
                return@addSnapshotListener
            }
            val items = snapshot?.documents
                ?.mapNotNull { d -> d.toObject(CartItem::class.java)?.copy(productId = d.id) }
                .orEmpty()
                .sortedBy { it.nombre.lowercase() }
            onChange(items)
        }

    /** READ (una sola vez): obtiene el carrito actual, usado al confirmar la compra. */
    fun get(uid: String, onSuccess: (List<CartItem>) -> Unit, onError: (String) -> Unit) {
        cart(uid).get()
            .addOnSuccessListener { snapshot ->
                onSuccess(
                    snapshot.documents
                        .mapNotNull { d -> d.toObject(CartItem::class.java)?.copy(productId = d.id) }
                        .sortedBy { it.nombre.lowercase() }
                )
            }
            .addOnFailureListener { onError(ErrorMapper.message(it)) }
    }

    /**
     * CREATE / UPDATE: agrega una unidad del [product]. Si ya estaba en el carrito suma 1,
     * sin superar el stock disponible.
     */
    fun add(uid: String, product: Product, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (product.stock <= 0) {
            onError("Este producto está agotado.")
            return
        }
        val ref = cart(uid).document(product.id)
        ref.get()
            .addOnSuccessListener { snapshot ->
                val actual = snapshot.toObject(CartItem::class.java)?.cantidad ?: 0
                if (actual + 1 > product.stock) {
                    onError("Ya tienes en el carrito todas las unidades disponibles (${product.stock}).")
                    return@addOnSuccessListener
                }
                val item = CartItem(
                    productId = product.id, nombre = product.nombre, precio = product.precio,
                    imagenUrl = product.imagenUrl, vendedorId = product.vendedorId,
                    vendedorNombre = product.vendedorNombre, cantidad = actual + 1, stock = product.stock
                )
                ref.set(item)
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onError(ErrorMapper.message(it)) }
            }
            .addOnFailureListener { onError(ErrorMapper.message(it)) }
    }

    /** UPDATE: cambia la cantidad de una línea del carrito. */
    fun setQuantity(uid: String, productId: String, cantidad: Int, onError: (String) -> Unit) {
        cart(uid).document(productId).update("cantidad", cantidad)
            .addOnFailureListener { onError(ErrorMapper.message(it)) }
    }

    /** DELETE: quita una línea del carrito. */
    fun remove(uid: String, productId: String, onError: (String) -> Unit) {
        cart(uid).document(productId).delete()
            .addOnFailureListener { onError(ErrorMapper.message(it)) }
    }
}
