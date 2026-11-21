package com.example.meeplekingdom

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.meeplekingdom.data.ProductRepository
import com.example.meeplekingdom.databinding.ActivityAdminProductsBinding
import com.example.meeplekingdom.model.Product
import com.example.meeplekingdom.model.Roles
import com.example.meeplekingdom.ui.ProductAdapter
import com.example.meeplekingdom.util.Navigator
import com.google.firebase.firestore.ListenerRegistration

/** Moderación del Administrador: ve todos los productos, puede bloquearlos/desbloquearlos o eliminarlos. */
class AdminProductsActivity : AppCompatActivity() {
    private var listener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Navigator.requireRole(this, Roles.ADMIN)) return
        val binding = ActivityAdminProductsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = ProductAdapter(
            onToggleBlock = { product ->
                ProductRepository.setBlocked(product.id, !product.bloqueado) {
                    Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                }
            },
            onDelete = { product -> confirmarEliminar(product) }
        )
        binding.rvAdminProductos.layoutManager = LinearLayoutManager(this)
        binding.rvAdminProductos.adapter = adapter
        binding.btnVolverAdminProd.setOnClickListener { finish() }

        listener = ProductRepository.listenAll(
            onChange = { products ->
                adapter.submitList(products)
                binding.tvVacioAdminProductos.visibility = if (products.isEmpty()) View.VISIBLE else View.GONE
                binding.tvVacioAdminProductos.text = "No hay productos publicados"
            },
            onError = { mensaje ->
                binding.tvVacioAdminProductos.visibility = View.VISIBLE
                binding.tvVacioAdminProductos.text = mensaje
            }
        )
    }

    /** Pide confirmación antes de eliminar un producto (y su foto). */
    private fun confirmarEliminar(product: Product) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar producto")
            .setMessage("¿Eliminar \"${product.nombre}\" de ${product.vendedorNombre}?")
            .setPositiveButton("Eliminar") { _, _ ->
                ProductRepository.delete(
                    product,
                    onSuccess = { Toast.makeText(this, "Producto eliminado", Toast.LENGTH_SHORT).show() },
                    onError = { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
                )
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Deja de escuchar Firestore al cerrar la pantalla. */
    override fun onDestroy() {
        listener?.remove()
        super.onDestroy()
    }
}
