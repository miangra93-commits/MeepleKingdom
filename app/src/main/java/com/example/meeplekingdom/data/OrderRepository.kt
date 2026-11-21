package com.example.meeplekingdom.data

import com.example.meeplekingdom.model.AppUser
import com.example.meeplekingdom.model.CartItem
import com.example.meeplekingdom.model.Order
import com.example.meeplekingdom.model.OrderItem
import com.example.meeplekingdom.model.OrderStatus
import com.example.meeplekingdom.model.Product
import com.example.meeplekingdom.util.ErrorMapper
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Transaction

/** Pedidos (colección `orders`): creación desde el carrito, consulta por rol y cambios de estado. */
object OrderRepository {
    private const val COLLECTION = "orders"
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    /** Lanza un error de negocio que ErrorMapper mostrará tal cual al usuario. */
    private fun fallo(mensaje: String): FirebaseFirestoreException =
        FirebaseFirestoreException(mensaje, FirebaseFirestoreException.Code.FAILED_PRECONDITION)

    /** Convierte una consulta en lista de pedidos, del más reciente al más antiguo. */
    private fun toOrders(snapshot: QuerySnapshot?): List<Order> =
        snapshot?.documents
            ?.mapNotNull { d -> d.toObject(Order::class.java)?.copy(id = d.id) }
            .orEmpty()
            .sortedByDescending { it.creadoEn }

    /** Escucha en tiempo real los pedidos que cumplen [query]. */
    private fun listen(
        query: Query, onChange: (List<Order>) -> Unit, onError: (String) -> Unit
    ): ListenerRegistration =
        query.addSnapshotListener { snapshot, e ->
            if (e != null) {
                onError(ErrorMapper.message(e))
                return@addSnapshotListener
            }
            onChange(toOrders(snapshot))
        }

    /** READ: pedidos de un comprador. */
    fun listenByBuyer(uid: String, onChange: (List<Order>) -> Unit, onError: (String) -> Unit) =
        listen(db.collection(COLLECTION).whereEqualTo("compradorId", uid), onChange, onError)

    /** READ: pedidos recibidos por un vendedor. */
    fun listenBySeller(uid: String, onChange: (List<Order>) -> Unit, onError: (String) -> Unit) =
        listen(db.collection(COLLECTION).whereEqualTo("vendedorId", uid), onChange, onError)

    /** READ (una sola vez): los pedidos de un comprador que pertenecen a un mismo pago (grupo). */
    fun getByGroup(uid: String, grupoId: String, onSuccess: (List<Order>) -> Unit, onError: (String) -> Unit) {
        db.collection(COLLECTION)
            .whereEqualTo("compradorId", uid)
            .whereEqualTo("grupoId", grupoId)
            .get()
            .addOnSuccessListener { onSuccess(toOrders(it)) }
            .addOnFailureListener { onError(ErrorMapper.message(it)) }
    }

    /** READ: todos los pedidos (reporte del administrador). */
    fun listenAll(onChange: (List<Order>) -> Unit, onError: (String) -> Unit) =
        listen(db.collection(COLLECTION), onChange, onError)

    /**
     * CREATE: convierte el carrito en pedidos dentro de UNA transacción atómica:
     *  1. Lee cada producto y verifica que siga disponible y con stock suficiente.
     *  2. Descuenta el stock.
     *  3. Crea un pedido por vendedor (con precios tomados de Firestore, no del carrito).
     *  4. Vacía el carrito.
     * Si algo falla, no se escribe nada.
     */
    fun placeOrder(
        buyer: AppUser, items: List<CartItem>, direccion: String, latitud: Double, longitud: Double,
        onSuccess: (List<Order>) -> Unit, onError: (String) -> Unit
    ) {
        if (items.isEmpty()) {
            onError("Tu carrito está vacío.")
            return
        }
        val productsCol = db.collection("products")
        val cartCol = db.collection("users").document(buyer.uid).collection("cart")
        val grupoId = db.collection(COLLECTION).document().id
        val ahora = System.currentTimeMillis()

        db.runTransaction(Transaction.Function<List<Order>> { tx ->
            // 1) Todas las lecturas primero (regla de Firestore: lecturas antes que escrituras).
            val snapshots = items.map { tx.get(productsCol.document(it.productId)) }
            val productos: List<Product?> = snapshots.map { it.toObject(Product::class.java) }

            items.forEachIndexed { i, item ->
                val p = productos[i]
                if (p == null || !p.activo || p.bloqueado) {
                    throw fallo("\"${item.nombre}\" ya no está disponible. Quítalo del carrito.")
                }
                if (p.stock < item.cantidad) {
                    throw fallo("Stock insuficiente de \"${item.nombre}\": quedan ${p.stock}.")
                }
            }

            // 2) Un pedido por vendedor.
            val indicesPorVendedor = items.indices.groupBy { productos[it]?.vendedorId.orEmpty() }
            val pedidos = indicesPorVendedor.map { (vendedorId, indices) ->
                val orderRef = db.collection(COLLECTION).document()
                val lineas = indices.map { i ->
                    val p = productos[i]
                    OrderItem(
                        productId = items[i].productId,
                        nombre = p?.nombre.orEmpty(),
                        precio = p?.precio ?: 0.0,
                        cantidad = items[i].cantidad,
                        imagenUrl = p?.imagenUrl.orEmpty()
                    )
                }
                val pedido = Order(
                    id = orderRef.id, grupoId = grupoId,
                    compradorId = buyer.uid, compradorNombre = buyer.nombre, compradorEmail = buyer.email,
                    vendedorId = vendedorId,
                    vendedorNombre = productos[indices.first()]?.vendedorNombre.orEmpty(),
                    items = lineas, total = lineas.sumOf { it.precio * it.cantidad },
                    estado = OrderStatus.PENDIENTE_PAGO, direccion = direccion,
                    latitud = latitud, longitud = longitud, creadoEn = ahora
                )
                tx.set(orderRef, pedido)
                pedido
            }

            // 3) Descontar stock y vaciar el carrito.
            items.forEachIndexed { i, item ->
                tx.update(productsCol.document(item.productId), "stock", (productos[i]?.stock ?: 0) - item.cantidad)
                tx.delete(cartCol.document(item.productId))
            }
            pedidos
        })
            .addOnSuccessListener { onSuccess(it) }
            .addOnFailureListener { onError(ErrorMapper.message(it)) }
    }

    /** UPDATE: cambia el estado de un pedido (p. ej. pagado -> enviado -> entregado). */
    fun updateStatus(orderId: String, estado: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        db.collection(COLLECTION).document(orderId).update("estado", estado)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(ErrorMapper.message(it)) }
    }

    /**
     * Cancela un pedido (acción del vendedor o administrador) y devuelve al inventario las
     * unidades reservadas, todo en una transacción. Solo se puede cancelar si está pendiente
     * de pago o pagado.
     */
    fun cancel(order: Order, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val orderRef = db.collection(COLLECTION).document(order.id)
        val productRefs = order.items.map { db.collection("products").document(it.productId) }

        db.runTransaction(Transaction.Function<Void?> { tx ->
            val estado = tx.get(orderRef).getString("estado")
            if (estado != OrderStatus.PENDIENTE_PAGO && estado != OrderStatus.PAGADO) {
                throw fallo("Este pedido ya no se puede cancelar.")
            }
            val snapshots = productRefs.map { tx.get(it) }
            order.items.forEachIndexed { i, item ->
                if (snapshots[i].exists()) {
                    tx.update(productRefs[i], "stock", (snapshots[i].getLong("stock") ?: 0L) + item.cantidad)
                }
            }
            tx.update(orderRef, "estado", OrderStatus.CANCELADO)
            null
        })
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(ErrorMapper.message(it)) }
    }
}
