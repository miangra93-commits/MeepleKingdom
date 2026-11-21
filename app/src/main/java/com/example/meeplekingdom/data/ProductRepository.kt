package com.example.meeplekingdom.data

import android.content.Context
import android.net.Uri
import com.example.meeplekingdom.model.Product
import com.example.meeplekingdom.util.ErrorMapper
import com.example.meeplekingdom.util.ImageUtils
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.storage.FirebaseStorage

/**
 * CRUD de productos: datos en Firestore (`products`) y fotos en Firebase Storage (`products/{id}.jpg`).
 */
object ProductRepository {
    private const val COLLECTION = "products"
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val storage: FirebaseStorage get() = FirebaseStorage.getInstance()

    /** Convierte un resultado de consulta en lista de productos, del más nuevo al más antiguo. */
    private fun toProducts(snapshot: QuerySnapshot?): List<Product> =
        snapshot?.documents
            ?.mapNotNull { d -> d.toObject(Product::class.java)?.copy(id = d.id) }
            .orEmpty()
            .sortedByDescending { it.creadoEn }

    /** READ: escucha los productos de un vendedor (pantalla "Mis Productos"). */
    fun listenBySeller(
        sellerId: String, onChange: (List<Product>) -> Unit, onError: (String) -> Unit
    ): ListenerRegistration =
        db.collection(COLLECTION).whereEqualTo("vendedorId", sellerId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    onError(ErrorMapper.message(e))
                    return@addSnapshotListener
                }
                onChange(toProducts(snapshot))
            }

    /** READ: escucha todos los productos (moderación del administrador). */
    fun listenAll(onChange: (List<Product>) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        db.collection(COLLECTION).addSnapshotListener { snapshot, e ->
            if (e != null) {
                onError(ErrorMapper.message(e))
                return@addSnapshotListener
            }
            onChange(toProducts(snapshot))
        }

    /** READ: escucha el catálogo público (activos y no bloqueados) que ve el comprador. */
    fun listenCatalog(onChange: (List<Product>) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        db.collection(COLLECTION).whereEqualTo("activo", true)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    onError(ErrorMapper.message(e))
                    return@addSnapshotListener
                }
                onChange(toProducts(snapshot).filter { !it.bloqueado })
            }

    /** READ: obtiene un producto por id (null si no existe). */
    fun get(id: String, onSuccess: (Product?) -> Unit, onError: (String) -> Unit) {
        db.collection(COLLECTION).document(id).get()
            .addOnSuccessListener { doc -> onSuccess(doc.toObject(Product::class.java)?.copy(id = doc.id)) }
            .addOnFailureListener { onError(ErrorMapper.message(it)) }
    }

    /**
     * CREATE / UPDATE: guarda el producto. Si [imageUri] no es null, primero comprime y sube la
     * foto a Storage y guarda en el documento la URL de descarga. Si el producto no tiene id,
     * se genera uno nuevo (creación); si lo tiene, se sobrescribe (edición).
     */
    fun save(
        context: Context, product: Product, imageUri: Uri?,
        onSuccess: () -> Unit, onError: (String) -> Unit
    ) {
        val docRef: DocumentReference =
            if (product.id.isEmpty()) db.collection(COLLECTION).document()
            else db.collection(COLLECTION).document(product.id)
        val base = product.copy(id = docRef.id)

        if (imageUri == null) {
            writeDocument(docRef, base, onSuccess, onError)
            return
        }

        val bytes = try {
            ImageUtils.compressToJpeg(context, imageUri)
        } catch (e: Exception) {
            onError("No se pudo procesar la imagen seleccionada.")
            return
        }

        val imageRef = storage.reference.child("products/${docRef.id}.jpg")
        imageRef.putBytes(bytes)
            .addOnSuccessListener {
                imageRef.downloadUrl
                    .addOnSuccessListener { url ->
                        writeDocument(docRef, base.copy(imagenUrl = url.toString()), onSuccess, onError)
                    }
                    .addOnFailureListener { onError(ErrorMapper.message(it)) }
            }
            .addOnFailureListener { onError(ErrorMapper.message(it)) }
    }

    /** Escribe el documento del producto en Firestore. */
    private fun writeDocument(
        docRef: DocumentReference, product: Product,
        onSuccess: () -> Unit, onError: (String) -> Unit
    ) {
        docRef.set(product)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(ErrorMapper.message(it)) }
    }

    /** UPDATE parcial: oculta o muestra el producto (acción del vendedor). */
    fun setActive(id: String, activo: Boolean, onError: (String) -> Unit) {
        db.collection(COLLECTION).document(id).update("activo", activo)
            .addOnFailureListener { onError(ErrorMapper.message(it)) }
    }

    /** UPDATE parcial: bloquea o desbloquea el producto por moderación (acción del administrador). */
    fun setBlocked(id: String, bloqueado: Boolean, onError: (String) -> Unit) {
        db.collection(COLLECTION).document(id).update("bloqueado", bloqueado)
            .addOnFailureListener { onError(ErrorMapper.message(it)) }
    }

    /** DELETE: elimina el documento y, después, intenta borrar su foto de Storage. */
    fun delete(product: Product, onSuccess: () -> Unit, onError: (String) -> Unit) {
        db.collection(COLLECTION).document(product.id).delete()
            .addOnSuccessListener {
                // Si la foto no existe o falla el borrado no es crítico: el producto ya no existe.
                storage.reference.child("products/${product.id}.jpg").delete()
                onSuccess()
            }
            .addOnFailureListener { onError(ErrorMapper.message(it)) }
    }
}
